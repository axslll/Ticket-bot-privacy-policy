package com.banhammer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen "nope" shown right after a banned app/site is sent away. Closes itself. */
class BlockedActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var title: TextView
    private lateinit var sub: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FF0F1115"))
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "🚫"
            textSize = 72f
            gravity = Gravity.CENTER
        })
        title = TextView(this).apply {
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        sub = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#FF9AA0AA"))
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        }
        root.addView(title)
        root.addView(sub)
        root.addView(Button(this).apply {
            text = "OK, go home"
            setOnClickListener { finish() }
        })
        setContentView(root)
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun render(intent: Intent) {
        val what = intent.getStringExtra(EXTRA_WHAT) ?: "That"
        title.text = "$what is banned"
        val left = Prefs.describeRemaining(this)
        sub.text = if (left.isNotEmpty()) "You locked this ban. $left left." else "You banned this. Go do something else."
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ finish() }, 3500)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_WHAT = "what"
    }
}
