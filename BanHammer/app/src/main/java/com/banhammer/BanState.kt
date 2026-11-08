package com.banhammer

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.concurrent.thread

class AppItem(val label: String, val pkg: String, val icon: ImageBitmap?, val installed: Boolean)

/** Everything the UI shows. Reads/writes go through [Prefs] so the service sees the same data. */
class BanState(private val ctx: Context) {
    var installed by mutableStateOf<List<AppItem>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var blocked by mutableStateOf(Prefs.blockedApps(ctx)); private set
    var sites by mutableStateOf(Prefs.blockedSites(ctx)); private set
    var enabled by mutableStateOf(Prefs.isEnabled(ctx)); private set
    var lockUntil by mutableLongStateOf(Prefs.lockUntil(ctx)); private set
    var now by mutableLongStateOf(System.currentTimeMillis()); private set

    var serviceOn by mutableStateOf(false); private set        // switched on in Android settings
    var serviceRunning by mutableStateOf(false); private set   // actually connected
    var lastSeenPkg by mutableStateOf(""); private set
    var lastSeenAt by mutableLongStateOf(0L); private set
    var lastBlockedWhat by mutableStateOf(""); private set
    var lastBlockedAt by mutableLongStateOf(0L); private set

    val locked: Boolean get() = now < lockUntil

    private val main = Handler(Looper.getMainLooper())
    private var loadGen = 0

    /** Cheap; called every second while the screen is visible. */
    fun tick() {
        now = System.currentTimeMillis()
        blocked = Prefs.blockedApps(ctx)
        sites = Prefs.blockedSites(ctx)
        enabled = Prefs.isEnabled(ctx)
        lockUntil = Prefs.lockUntil(ctx)

        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        serviceOn = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.resolveInfo.serviceInfo.let { s -> s.packageName == ctx.packageName && s.name == BlockerService::class.java.name }
        }
        serviceRunning = BlockerService.connected
        lastSeenPkg = BlockerService.lastSeenPkg
        lastSeenAt = BlockerService.lastSeenAt
        lastBlockedWhat = BlockerService.lastBlockedWhat
        lastBlockedAt = BlockerService.lastBlockedAt
    }

    /** Reloads the installed-app list (call whenever the app comes to the front). */
    fun loadApps() {
        val gen = ++loadGen
        loading = installed.isEmpty()
        thread {
            val pm = ctx.packageManager
            val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val items = pm.queryIntentActivities(launcher, 0)
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map { ri ->
                    val icon = try { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() } catch (_: Exception) { null }
                    AppItem(ri.loadLabel(pm).toString(), ri.activityInfo.packageName, icon, true)
                }
                .sortedBy { it.label.lowercase() }
            main.post {
                if (gen == loadGen) { installed = items; loading = false }
            }
        }
    }

    // ---- actions; each returns an error message to show, or null on success ----

    fun setApp(pkg: String, ban: Boolean): String? {
        val next = if (ban) blocked + pkg else blocked - pkg
        if (!Prefs.setBlockedApps(ctx, next)) return "Locked. You can ban more apps but not unban."
        blocked = next
        return null
    }

    fun addPackage(raw: String): String? {
        val pkg = raw.trim()
        if (!PKG_REGEX.matches(pkg)) return "That doesn't look like a package name (e.g. com.reddit.frontpage)"
        return setApp(pkg, true)
    }

    fun addSite(raw: String): String? {
        val parsed = Prefs.parseSites(raw)
        if (parsed.isEmpty()) return "Type a website like x.com"
        Prefs.setBlockedSites(ctx, sites + parsed)
        sites = Prefs.blockedSites(ctx)
        return null
    }

    fun removeSite(site: String): String? {
        if (!Prefs.setBlockedSites(ctx, sites - site)) return "Locked. You can add websites but not remove them."
        sites = sites - site
        return null
    }

    fun setEnabled(on: Boolean): String? {
        if (!Prefs.setEnabled(ctx, on)) return "Locked. You can't pause the ban yet."
        enabled = on
        return null
    }

    fun lock(days: Int) {
        Prefs.lockFor(ctx, days * 24L * 60 * 60 * 1000)
        tick()
    }

    companion object {
        val PKG_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    }
}
