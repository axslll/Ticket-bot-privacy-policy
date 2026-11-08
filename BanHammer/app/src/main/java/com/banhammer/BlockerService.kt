package com.banhammer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Sends you home when a banned app (or a banned website in a supported browser) is on screen.
 *
 * Two layers so one missed event can't let a banned app through:
 *  1. reacts to accessibility events, and
 *  2. checks the foreground window once a second while the screen is on.
 */
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var active = true
    private var apps: Set<String> = emptySet()
    private var sites: Set<String> = emptySet()
    private var lastBlockAt = 0L
    private var lastWebCheckAt = 0L

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reload() }

    private val poll = object : Runnable {
        override fun run() {
            try { checkForeground() } catch (_: Exception) { }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        reload()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
        handler.removeCallbacks(poll)
        handler.post(poll)
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
        handler.removeCallbacks(poll)
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun reload() {
        active = Prefs.isEnabled(this)
        apps = Prefs.blockedApps(this)
        sites = Prefs.blockedSites(this)
    }

    // ------------------------------------------------------------ detection

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        seen(pkg)
        if (!active) return
        // A launch/window change names the app directly. Content events can come from
        // background windows, so for those we confirm the window is really in front.
        val isWindowChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        evaluate(pkg, trusted = isWindowChange)
    }

    private fun checkForeground() {
        active = Prefs.isEnabled(this) // lets a lock expiry / toggle take effect without an event
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return
        seen(pkg)
        if (active) evaluate(pkg, trusted = true)
    }

    private fun evaluate(pkg: String, trusted: Boolean) {
        if (pkg == packageName) return

        if (pkg in apps) {
            if (trusted || rootInActiveWindow?.packageName?.toString() == pkg) block(appLabel(pkg))
            return
        }

        val urlBarId = BROWSER_URL_BARS[pkg]
        if (urlBarId != null && sites.isNotEmpty()) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastWebCheckAt < 250) return
            lastWebCheckAt = now
            val root = rootInActiveWindow ?: return
            val bars = root.findAccessibilityNodeInfosByViewId(urlBarId) ?: return
            if (bars.any { Prefs.hostMatches(it.text?.toString().orEmpty(), sites) }) block("That website")
        }
    }

    private fun seen(pkg: String) {
        if (pkg == packageName || pkg == "com.android.systemui") return
        lastSeenPkg = pkg
        lastSeenAt = System.currentTimeMillis()
    }

    private fun block(what: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 800) return
        lastBlockAt = now
        lastBlockedWhat = what
        lastBlockedAt = System.currentTimeMillis()
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(BlockedActivity.EXTRA_WHAT, what)
        )
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    override fun onInterrupt() {}

    companion object {
        private const val POLL_MS = 1000L

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
