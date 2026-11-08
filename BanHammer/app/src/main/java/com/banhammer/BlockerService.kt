package com.banhammer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches which app is on screen. If it's a banned app (or a banned website in a supported
 * browser) it presses Home and shows [BlockedActivity].
 */
class BlockerService : AccessibilityService() {

    private var active = true
    private var apps: Set<String> = emptySet()
    private var sites: Set<String> = emptySet()
    private var lastBlockAt = 0L
    private var lastWebCheckAt = 0L

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reload() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        reload()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

    private fun reload() {
        active = Prefs.isEnabled(this)
        apps = Prefs.blockedApps(this)
        sites = Prefs.blockedSites(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // The lock can expire without any pref change, so re-check cheaply.
        if (!active && Prefs.isEnabled(this)) reload()
        if (!active) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (pkg in apps) {
            val isLaunch = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            // Content events can come from background windows; only act if it's really in front.
            if (isLaunch || rootInActiveWindow?.packageName?.toString() == pkg) {
                block(appLabel(pkg))
            }
            return
        }

        val urlBarId = BROWSER_URL_BARS[pkg]
        if (urlBarId != null && sites.isNotEmpty()) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastWebCheckAt < 250) return
            lastWebCheckAt = now
            if (isBannedSiteOpen(urlBarId)) block("That website")
        }
    }

    private fun isBannedSiteOpen(urlBarId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(urlBarId) ?: return false
        return nodes.any { node: AccessibilityNodeInfo ->
            Prefs.hostMatches(node.text?.toString().orEmpty(), sites)
        }
    }

    private fun block(what: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 800) return
        lastBlockAt = now
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
