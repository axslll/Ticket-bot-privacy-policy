package com.banhammer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends you home the moment a banned app (or banned website in a supported browser) is on screen.
 *
 * Built to cost next to nothing:
 *  - No polling and no timers. It only runs when Android hands it an event.
 *  - Android only sends events from the apps in [applySubscription]: your banned apps, plus the
 *    browsers if (and only if) you've banned websites. Every other app, the launcher, the keyboard
 *    and so on never wake this service at all. While the ban is paused it listens to nothing.
 *  - Handling an event for a banned app is one set lookup and a Home press. It never reads the
 *    app's screen contents. Only the browser address-bar check does, off the main thread, rate-limited.
 */
class BlockerService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var worker: HandlerThread? = null
    private var bg: Handler? = null

    @Volatile private var active = true
    @Volatile private var apps: Set<String> = emptySet()
    @Volatile private var sites: Set<String> = emptySet()
    @Volatile private var lastWebAt = 0L
    private var lastBlockAt = 0L
    private var lastToastAt = 0L
    private val webBusy = AtomicBoolean(false)

    private var watched: Set<String>? = null
    private var watchedTimeout = -1L

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reload() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        shutdown()
        connected = true
        watched = null
        reload()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        connected = false
        worker?.quitSafely()
        worker = null
        bg = null
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun reload() {
        active = Prefs.isEnabled(this)
        apps = Prefs.blockedApps(this)
        sites = Prefs.blockedSites(this)
        applySubscription()
    }

    /** Tells Android which apps' events to deliver. Everything else is filtered out by the system. */
    private fun applySubscription() {
        val browsers = if (sites.isNotEmpty()) BROWSER_URL_BARS.keys else emptySet()
        val want: Set<String> = if (active) apps + browsers else emptySet()
        // Throttle only matters for noisy browser content events; keep app launches undelayed.
        val timeout = if (active && browsers.isNotEmpty()) 150L else 0L
        if (want == watched && timeout == watchedTimeout) return
        val info = serviceInfo ?: return
        // A null/empty list would mean "every app", so watch our own package when idle.
        info.packageNames = if (want.isEmpty()) arrayOf(packageName) else want.toTypedArray()
        info.notificationTimeout = timeout
        serviceInfo = info
        watched = want
        watchedTimeout = timeout
    }

    // ------------------------------------------------------------ events (main thread, cheap)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!active) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Both event types name the window's app, and Android only reports the app in front, so a
        // banned package here means it's on screen. No need to inspect anything.
        if (pkg in apps) {
            block(pkg)
            return
        }
        if (pkg in BROWSER_URL_BARS) scheduleWebCheck(pkg)
    }

    private fun scheduleWebCheck(pkg: String) {
        if (sites.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastWebAt < 300) return
        lastWebAt = now
        if (!webBusy.compareAndSet(false, true)) return
        val h = ensureWorker()
        h.post {
            try { checkBrowser(pkg) } catch (_: Exception) { } finally { webBusy.set(false) }
        }
    }

    private fun ensureWorker(): Handler = bg ?: synchronized(this) {
        bg ?: Handler(HandlerThread("banhammer-web").also { it.start(); worker = it }.looper).also { bg = it }
    }

    private fun checkBrowser(pkg: String) {
        val barId = BROWSER_URL_BARS[pkg] ?: return
        val siteList = sites
        if (siteList.isEmpty()) return
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return // browser isn't actually in front
        val bars = root.findAccessibilityNodeInfosByViewId(barId) ?: return
        if (bars.any { Prefs.hostMatches(it.text?.toString().orEmpty(), siteList) }) block(null)
    }

    /** [pkg] is the banned app, or null for a banned website. */
    @Synchronized
    private fun block(pkg: String?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 400) return
        lastBlockAt = now

        performGlobalAction(GLOBAL_ACTION_HOME) // first, before anything slower

        lastBlockedWhat = if (pkg == null) "That website" else appLabel(pkg)
        lastBlockedAt = System.currentTimeMillis()

        if (now - lastToastAt > 2500) {
            lastToastAt = now
            val left = Prefs.describeRemaining(this)
            val msg = "$lastBlockedWhat is banned" + if (left.isNotEmpty()) " ($left left on your lock)" else ""
            main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    override fun onInterrupt() {}

    companion object {
        // Live status the app screen shows (same process).
        @Volatile var connected = false
        @Volatile var lastBlockedWhat = ""
        @Volatile var lastBlockedAt = 0L

        /** Address-bar view ids of common browsers (needed to see which site is open). */
        val BROWSER_URL_BARS = mapOf(
            "com.android.chrome" to "com.android.chrome:id/url_bar",
            "com.brave.browser" to "com.brave.browser:id/url_bar",
            "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.opera.browser" to "com.opera.browser:id/url_field",
            "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
        )
    }
}
