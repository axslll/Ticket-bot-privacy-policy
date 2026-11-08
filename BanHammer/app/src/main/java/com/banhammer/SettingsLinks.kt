package com.banhammer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/** Opens the exact Android settings page for each thing BanHammer needs. Each tries the most specific page first. */
object SettingsLinks {

    /** Accessibility → BanHammer's own page (falls back to the list, highlighting BanHammer). */
    fun openAccessibility(ctx: Context): Boolean {
        val cn = ComponentName(ctx, BlockerService::class.java).flattenToString()
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, cn)
        val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", cn)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", cn) })
        return launch(ctx, details, list, Intent(Settings.ACTION_SETTINGS))
    }

    /** App info → BanHammer. The ⋮ menu there has "Allow restricted settings". */
    fun openAppInfo(ctx: Context): Boolean = launch(ctx, appInfo(ctx), Intent(Settings.ACTION_SETTINGS))

    /** "Always allow BanHammer to run in the background" dialog (falls back to the battery list, then app info). */
    fun openBattery(ctx: Context): Boolean = launch(
        ctx,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appInfo(ctx),
    )

    private fun appInfo(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))

    private fun launch(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
                // not available on this phone, try the next, less specific page
            }
        }
        return false
    }
}
