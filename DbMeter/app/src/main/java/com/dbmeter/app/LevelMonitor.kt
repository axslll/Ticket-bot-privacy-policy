package com.dbmeter.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

/** What the UI shows. [db] is null while nothing is playing. */
data class MonitorState(
    val running: Boolean = false,
    val db: Double? = null,
    val peakDb: Double? = null,
    val level: SafetyLevel? = null,
    val error: String? = null,
)

/** Process-wide hand-off between [CaptureService] (producer) and [MainActivity] (consumer). */
object LevelMonitor {
    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    /** User trim (dB) added to the estimate; set from the calibration slider. */
    @Volatile
    var calibrationDb: Double = 0.0

    fun started() {
        _state.value = MonitorState(running = true)
    }

    fun update(db: Double?) {
        _state.update { s ->
            s.copy(
                db = db,
                peakDb = if (db != null) max(s.peakDb ?: db, db) else s.peakDb,
                level = db?.let(SoundLevel::classify),
            )
        }
    }

    fun resetPeak() {
        _state.update { it.copy(peakDb = it.db) }
    }

    fun stopped(error: String? = null) {
        _state.value = MonitorState(error = error)
    }
}
