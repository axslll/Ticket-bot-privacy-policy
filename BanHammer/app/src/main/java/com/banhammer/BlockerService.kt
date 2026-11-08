package com.banhammer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends you home when a banned app (or a banned website in a supported browser) is on screen.
 *
 * Speed rules: the event handler (main thread) only does set lookups and a Home press. Anything
 * that reads another app's screen runs on a background thread, never once per content event.
 *
 *  1. Window-change events catch the moment a banned app opens.
 *  2. A background poll every [POLL_MS] while the screen is on catches anything that slipped
 *     through, and presses Home again if the app is still there.
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

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reload() }

    private val poll = object : Runnable {
        override fun run() {
            try { checkForeground() } catch (_: Exception) { }
            bg?.postDelayed(this, POLL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        shutdown()
        connected = true
        reload()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
        val t = HandlerThread("banhammer-poll").also { it.start() }
        worker = t
        bg = Handler(t.looper).also { it.post(poll) }
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
        bg?.removeCallbacksAndMessages(null)
        worker?.quitSafely()
        worker = null
        bg = null
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun reload() {
        active = Prefs.isEnabled(this)
        apps = Prefs.blockedApps(this)
        sites = Prefs.blockedSites(this)
    }

    // ------------------------------------------------------------ events (main thread, cheap)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                seen(pkg)
                if (!active) return
                if (pkg in apps) block(pkg) else if (pkg in BROWSER_URL_BARS) scheduleWebCheck(pkg)
            }
            // Fires constantly (every scroll). Only browsers care, for URL changes, and only
            // via a throttled background check. Never touch the banned app's tree here.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (active && pkg in BROWSER_URL_BARS) scheduleWebCheck(pkg)
            }
        }
    }

    private fun scheduleWebCheck(pkg: String) {
        if (sites.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastWebAt < 300) return
        lastWebAt = now
        if (!webBusy.compareAndSet(false, true)) return
        val h = bg
        if (h == null) { webBusy.set(false); return }
        h.post {
            try { checkBrowser(pkg, null) } catch (_: Exception) { } finally { webBusy.set(false) }
        }
    }

    // ------------------------------------------------------------ background work

    private fun checkForeground() {
        active = Prefs.isEnabled(this) // picks up a lock expiring / toggling without an event
        if (!active) return
        if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive) return
        val root = lightRoot() ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg == packageName) return
        seen(pkg)
        if (pkg in apps) block(pkg) else if (pkg in BROWSER_URL_BARS) checkBrowser(pkg, root)
    }

    private fun checkBrowser(pkg: String, knownRoot: AccessibilityNodeInfo?) {
        val barId = BROWSER_URL_BARS[pkg] ?: return
        if (sites.isEmpty()) return
        val root = knownRoot ?: lightRoot() ?: return
        if (root.packageName?.toString() != pkg) return // browser isn't actually in front
        val bars = root.findAccessibilityNodeInfosByViewId(barId) ?: return
        if (bars.any { Prefs.hostMatches(it.text?.toString().orEmpty(), sites) }) block(null)
    }

    /** The foreground window's root without pre-fetching its whole subtree (fast on heavy apps). */
    private fun lightRoot(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 33) {
            try { return getRootInActiveWindow(0) } catch (_: Exception) { }
        }
        return rootInActiveWindow
    }

    private fun seen(pkg: String) {
        if (pkg == packageName || pkg == "com.android.systemui") return
        lastSeenPkg = pkg
        lastSeenAt = System.currentTimeMillis()
    }

    /** [pkg] is the banned app, or null for a banned website. */
    @Synchronized
    private fun block(pkg: String?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 300) return
        lastBlockAt = now

        performGlobalAction(GLOBAL_ACTION_HOME) // first, before anything slow

        val what = if (pkg == null) "That website" else appLabel(pkg)
        lastBlockedWhat = what
        lastBlockedAt = System.currentTimeMillis()

        if (now - lastToastAt > 2500) {
            lastToastAt = now
            val left = Prefs.describeRemaining(this)
            val msg = "$what is banned" + if (left.isNotEmpty()) " ($left left on your lock)" else ""
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
        private const val POLL_MS = 400L

        // Live status the app screen shows (same process).
        @Volatile var connected = false
        @Volatile var lastSeenPkg = ""
        @Volatile var lastSeenAt = 0L
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
