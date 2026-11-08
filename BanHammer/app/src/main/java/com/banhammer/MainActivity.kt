package com.banhammer

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private data class AppEntry(val label: String, val pkg: String, val icon: Drawable)

    private val blocked = mutableSetOf<String>()
    private var apps: List<AppEntry> = emptyList()

    private lateinit var statusText: TextView
    private lateinit var statusButton: Button
    private lateinit var banSwitch: Switch
    private lateinit var lockText: TextView
    private lateinit var sitesEdit: EditText
    private val adapter = AppAdapter()

    private val fg = Color.parseColor("#FFF2F3F5")
    private val dim = Color.parseColor("#FF9AA0AA")
    private val red = Color.parseColor("#FFFF453A")
    private val green = Color.parseColor("#FF30D158")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blocked.addAll(Prefs.blockedApps(this))

        val list = ListView(this).apply {
            divider = null
            addHeaderView(buildHeader(), null, false)
            setAdapter(adapter)
            clipToPadding = false
        }
        setContentView(list)

        thread {
            val loaded = loadApps()
            runOnUiThread { apps = loaded; adapter.notifyDataSetChanged() }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .map { (pkg, ri) -> AppEntry(ri.loadLabel(pm).toString(), pkg, ri.loadIcon(pm)) }
            .sortedWith(compareBy({ it.pkg !in blocked }, { it.label.lowercase() }))
    }

    // ---------------------------------------------------------------- header

    private fun text(s: String, size: Float, color: Int = fg, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun buildHeader(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(8))
        }
        box.addView(text("BanHammer", 30f, bold = true))
        box.addView(text("Opening a banned app or site sends you straight back home.", 14f, dim).apply {
            setPadding(0, dp(4), 0, dp(20))
        })

        // Service status
        statusText = text("", 15f, bold = true)
        statusButton = Button(this).apply {
            text = "Turn on in Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        box.addView(statusText)
        box.addView(statusButton)

        // Master switch
        banSwitch = Switch(this).apply {
            text = "Ban active"
            textSize = 17f
            setTextColor(fg)
            setPadding(0, dp(16), 0, dp(8))
            setOnCheckedChangeListener { v, checked ->
                if (v.isPressed && !Prefs.setEnabled(this@MainActivity, checked)) {
                    toast("Locked. You can't turn the ban off yet.")
                    refresh()
                }
            }
        }
        box.addView(banSwitch)

        // Lock
        box.addView(text("Lock it (no undo)", 17f, bold = true).apply { setPadding(0, dp(16), 0, 0) })
        lockText = text("", 13f, dim).apply { setPadding(0, dp(2), 0, dp(6)) }
        box.addView(lockText)
        val lockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("1 day" to 1, "7 days" to 7, "30 days" to 30).forEach { (label, days) ->
            lockRow.addView(Button(this).apply {
                text = label
                setOnClickListener { confirmLock(label, days) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(lockRow)

        // Websites
        box.addView(text("Banned websites", 17f, bold = true).apply { setPadding(0, dp(16), 0, 0) })
        box.addView(text("Comma separated. Works in Chrome, Brave, Edge, Firefox, Samsung Internet, Opera and DuckDuckGo.", 13f, dim))
        sitesEdit = EditText(this).apply {
            setText(Prefs.blockedSites(this@MainActivity).sorted().joinToString(", "))
            setTextColor(fg)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_URI
        }
        box.addView(sitesEdit)
        box.addView(Button(this).apply {
            text = "Save websites"
            setOnClickListener { saveSites() }
        })

        box.addView(text("Banned apps", 17f, bold = true).apply { setPadding(0, dp(20), 0, dp(4)) })
        return box
    }

    private fun refresh() {
        val on = isServiceEnabled()
        statusText.text = if (on) "● Blocker is running" else "● Blocker is OFF"
        statusText.setTextColor(if (on) green else red)
        statusButton.visibility = if (on) View.GONE else View.VISIBLE

        banSwitch.isChecked = Prefs.isEnabled(this)
        banSwitch.isEnabled = !Prefs.isLocked(this)

        lockText.text = if (Prefs.isLocked(this))
            "Locked for ${Prefs.describeRemaining(this)}. You can add to the ban but not remove anything."
        else
            "While locked you can't switch the ban off or remove anything from it."

        val current = Prefs.blockedApps(this)
        blocked.clear(); blocked.addAll(current)
        adapter.notifyDataSetChanged()
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.let { s -> s.packageName == packageName && s.name == BlockerService::class.java.name } }
    }

    private fun confirmLock(label: String, days: Int) {
        AlertDialog.Builder(this)
            .setTitle("Lock the ban for $label?")
            .setMessage("You won't be able to turn it off or remove anything until the time is up. The only ways out are disabling the accessibility service or uninstalling the app, so you'll have to go out of your way to cheat.")
            .setPositiveButton("Lock it") { _, _ ->
                Prefs.lockFor(this, days * 24L * 60 * 60 * 1000)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveSites() {
        val sites = Prefs.parseSites(sitesEdit.text.toString())
        if (Prefs.setBlockedSites(this, sites)) {
            toast("Saved")
        } else {
            toast("Locked. You can add sites but not remove them.")
            sitesEdit.setText((sites + Prefs.blockedSites(this)).sorted().joinToString(", "))
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------- list

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(8))
                addView(ImageView(context).apply { id = ID_ICON }, LinearLayout.LayoutParams(dp(40), dp(40)))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply { id = ID_LABEL; textSize = 16f; setTextColor(fg) })
                    addView(TextView(context).apply { id = ID_PKG; textSize = 11f; setTextColor(dim) })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
                addView(CheckBox(context).apply { id = ID_CHECK; isClickable = false; isFocusable = false })
            }
            val app = apps[position]
            row.findViewById<ImageView>(ID_ICON).setImageDrawable(app.icon)
            row.findViewById<TextView>(ID_LABEL).text = app.label
            row.findViewById<TextView>(ID_PKG).text = app.pkg
            row.findViewById<CheckBox>(ID_CHECK).isChecked = app.pkg in blocked
            row.setOnClickListener { toggle(app.pkg) }
            return row
        }
    }

    private fun toggle(pkg: String) {
        val next = blocked.toMutableSet()
        if (!next.add(pkg)) next.remove(pkg)
        if (Prefs.setBlockedApps(this, next)) {
            blocked.clear(); blocked.addAll(next)
            adapter.notifyDataSetChanged()
        } else {
            toast("Locked. You can ban more apps but not unban.")
        }
    }

    private companion object {
        const val ID_ICON = 0x7f0a0001
        const val ID_LABEL = 0x7f0a0002
        const val ID_PKG = 0x7f0a0003
        const val ID_CHECK = 0x7f0a0004
    }
}
