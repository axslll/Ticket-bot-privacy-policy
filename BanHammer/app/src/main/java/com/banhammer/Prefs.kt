package com.banhammer

import android.content.Context
import android.content.SharedPreferences

/**
 * All settings live here. While a "lock" is active the ban can only get stricter:
 * nothing can be removed from the blocklists and the ban can't be switched off.
 */
object Prefs {
    private const val FILE = "banhammer"
    private const val K_APPS = "blocked_apps"
    private const val K_SITES = "blocked_sites"
    private const val K_ENABLED = "enabled"
    private const val K_LOCK_UNTIL = "lock_until"

    // Twitter's package name never changed after the rebrand to X.
    val DEFAULT_APPS = setOf("com.twitter.android", "com.twitter.android.lite")
    val DEFAULT_SITES = setOf("x.com", "twitter.com")

    fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun blockedApps(c: Context): Set<String> =
        sp(c).getStringSet(K_APPS, null)?.toSet() ?: DEFAULT_APPS

    fun blockedSites(c: Context): Set<String> =
        sp(c).getStringSet(K_SITES, null)?.toSet() ?: DEFAULT_SITES

    fun lockUntil(c: Context): Long = sp(c).getLong(K_LOCK_UNTIL, 0L)

    fun isLocked(c: Context): Boolean = System.currentTimeMillis() < lockUntil(c)

    /** The ban is always on while locked. */
    fun isEnabled(c: Context): Boolean = isLocked(c) || sp(c).getBoolean(K_ENABLED, true)

    /** @return false if refused because a lock is active. */
    fun setEnabled(c: Context, enabled: Boolean): Boolean {
        if (!enabled && isLocked(c)) return false
        sp(c).edit().putBoolean(K_ENABLED, enabled).apply()
        return true
    }

    /** @return false if refused because it would remove an entry while locked. */
    fun setBlockedApps(c: Context, apps: Set<String>): Boolean {
        if (isLocked(c) && !apps.containsAll(blockedApps(c))) return false
        sp(c).edit().putStringSet(K_APPS, apps.toSet()).apply()
        return true
    }

    /** @return false if refused because it would remove an entry while locked. */
    fun setBlockedSites(c: Context, sites: Set<String>): Boolean {
        if (isLocked(c) && !sites.containsAll(blockedSites(c))) return false
        sp(c).edit().putStringSet(K_SITES, sites.toSet()).apply()
        return true
    }

    /** Locks for [durationMs] from now. Never shortens an existing lock. */
    fun lockFor(c: Context, durationMs: Long) {
        val until = maxOf(lockUntil(c), System.currentTimeMillis() + durationMs)
        sp(c).edit().putLong(K_LOCK_UNTIL, until).putBoolean(K_ENABLED, true).apply()
    }

    fun parseSites(text: String): Set<String> =
        text.split(Regex("[,;\\s]+")).map { normalizeDomain(it) }.filter { it.isNotEmpty() }.toSet()

    /** "https://www.X.com/home?x=1" -> "x.com" */
    fun normalizeDomain(raw: String): String {
        var s = raw.trim().lowercase().substringBefore(' ')
        s = s.substringAfter("://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        return s.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
    }

    fun hostMatches(text: String, sites: Set<String>): Boolean {
        val host = normalizeDomain(text)
        if (host.isEmpty()) return false
        return sites.any { host == it || host.endsWith(".$it") }
    }

    fun describeRemaining(c: Context): String {
        val ms = lockUntil(c) - System.currentTimeMillis()
        if (ms <= 0) return ""
        val mins = ms / 60_000
        val d = mins / (60 * 24)
        val h = (mins / 60) % 24
        val m = mins % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${maxOf(m, 1)}m"
        }
    }
}
