package com.dbmeter.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var card: MaterialCardView
    private lateinit var dbText: TextView
    private lateinit var statusText: TextView
    private lateinit var adviceText: TextView
    private lateinit var peakText: TextView
    private lateinit var errorText: TextView
    private lateinit var calibText: TextView
    private lateinit var levelBar: LevelBar
    private lateinit var toggleButton: MaterialButton
    private lateinit var calibSlider: Slider

    private var running = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasAudioPermission()) {
                requestProjection()
            } else {
                showError(getString(R.string.error_no_permission))
            }
        }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                CaptureService.start(this, result.resultCode, data)
            } else {
                showError(getString(R.string.error_no_projection))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        card = findViewById(R.id.card)
        dbText = findViewById(R.id.dbText)
        statusText = findViewById(R.id.statusText)
        adviceText = findViewById(R.id.adviceText)
        peakText = findViewById(R.id.peakText)
        errorText = findViewById(R.id.errorText)
        calibText = findViewById(R.id.calibText)
        levelBar = findViewById(R.id.levelBar)
        toggleButton = findViewById(R.id.toggleButton)
        calibSlider = findViewById(R.id.calibSlider)

        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val saved = prefs.getInt(PREF_CALIBRATION, 0).toFloat().coerceIn(-20f, 20f)
        calibSlider.value = saved
        LevelMonitor.calibrationDb = saved.toDouble()
        showCalibration(saved)
        calibSlider.addOnChangeListener { _, value, _ ->
            LevelMonitor.calibrationDb = value.toDouble()
            prefs.edit().putInt(PREF_CALIBRATION, value.roundToInt()).apply()
            showCalibration(value)
        }

        toggleButton.setOnClickListener {
            if (running) CaptureService.stop(this) else start()
        }
        peakText.setOnClickListener { LevelMonitor.resetPeak() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                LevelMonitor.state.collect(::render)
            }
        }
    }

    // ---- starting: permissions -> screen-capture consent -> foreground service --------------

    private fun start() {
        errorText.visibility = TextView.GONE
        val needed = buildList {
            if (!hasAudioPermission()) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) requestProjection() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun requestProjection() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    // ---- rendering --------------------------------------------------------------------------

    private fun render(s: MonitorState) {
        running = s.running
        toggleButton.setText(if (s.running) R.string.stop else R.string.start)

        val db = s.db
        val level = s.level
        val color = when {
            !s.running || db == null || level == null -> R.color.idle
            level == SafetyLevel.SAFE -> R.color.safe
            level == SafetyLevel.CAUTION -> R.color.caution
            else -> R.color.danger
        }
        card.setCardBackgroundColor(ContextCompat.getColor(this, color))

        dbText.text = if (s.running && db != null) db.roundToInt().toString() else "--"
        levelBar.value = if (s.running) db else null

        statusText.setText(
            when {
                !s.running -> R.string.status_idle
                db == null || level == null -> R.string.status_silent
                level == SafetyLevel.SAFE -> R.string.status_safe
                level == SafetyLevel.CAUTION -> R.string.status_caution
                else -> R.string.status_danger
            },
        )

        adviceText.text = when {
            !s.running -> getString(R.string.advice_idle)
            db == null || level == null -> getString(R.string.advice_silent)
            level == SafetyLevel.SAFE -> getString(R.string.advice_safe)
            level == SafetyLevel.CAUTION -> getString(R.string.advice_caution)
            else -> getString(R.string.advice_danger, formatDuration(SoundLevel.safeListeningMinutes(db)))
        }

        peakText.text = if (s.running && s.peakDb != null) {
            getString(R.string.peak, s.peakDb.roundToInt())
        } else {
            getString(R.string.peak_none)
        }

        s.error?.let(::showError)
    }

    private fun formatDuration(minutes: Double?): String {
        if (minutes == null) return ""
        val total = minutes.roundToInt()
        return when {
            minutes < 1.0 -> getString(R.string.duration_under_minute)
            total < 60 -> getString(R.string.duration_minutes, total)
            else -> getString(R.string.duration_hours, total / 60, total % 60)
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = TextView.VISIBLE
    }

    private fun showCalibration(value: Float) {
        calibText.text = getString(R.string.calibration_value, value.roundToInt())
    }

    companion object {
        private const val PREF_CALIBRATION = "calibration_db"
    }
}
