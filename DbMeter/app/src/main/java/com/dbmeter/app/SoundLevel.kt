package com.dbmeter.app

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Traffic-light safety classification of a sound level. */
enum class SafetyLevel { SAFE, CAUTION, DANGER }

/**
 * Pure maths for turning raw audio samples into a decibel figure and a safety level.
 * No Android dependencies, so it can be unit-tested on the JVM.
 */
object SoundLevel {

    /** Below this: green. Comfortable for all-day listening. */
    const val CAUTION_DB = 70.0

    /** At or above this: red. 85 dB is the WHO/NIOSH limit for 8 hours of listening. */
    const val DANGER_DB = 85.0

    /** RMS level (dBFS) below which we treat the signal as silence / nothing playing. */
    const val SILENCE_DBFS = -75.0

    /** Lowest value reported by [rmsDbfs]. */
    const val FLOOR_DBFS = -120.0

    /**
     * Assumed ear-level loudness of a full-scale signal at maximum volume.
     * Typical earphones/headphones reach ~95-105 dB SPL, so 100 is a sensible default.
     * Users can trim it with the calibration slider.
     */
    const val FULL_SCALE_SPL = 100.0

    /** Highest value [estimateSpl] returns. */
    const val MAX_DB = 130.0

    /** RMS level of 16-bit PCM samples in dB relative to full scale (0 dBFS = full-scale square wave). */
    fun rmsDbfs(samples: ShortArray, count: Int = samples.size): Double {
        val n = min(count, samples.size)
        if (n <= 0) return FLOOR_DBFS
        var sumSquares = 0.0
        for (i in 0 until n) {
            val s = samples[i] / 32768.0
            sumSquares += s * s
        }
        val rms = sqrt(sumSquares / n)
        return if (rms <= 0.0) FLOOR_DBFS else max(FLOOR_DBFS, 20.0 * log10(rms))
    }

    /**
     * Estimates the sound pressure level reaching the ear.
     *
     * A phone cannot know what its speaker or headphones really emit, so this is an
     * estimate: a full-scale signal at full volume is assumed to produce [fullScaleSpl];
     * every dB of digital level or volume attenuation lowers the result by one dB.
     *
     * @param dbfs digital level of the captured audio (<= 0)
     * @param volumeFraction system media volume, 0.0 (min) to 1.0 (max)
     * @param calibrationDb user trim to match a real sound-level meter
     */
    fun estimateSpl(
        dbfs: Double,
        volumeFraction: Double,
        calibrationDb: Double = 0.0,
        fullScaleSpl: Double = FULL_SCALE_SPL,
    ): Double {
        val volumeDb = 20.0 * log10(volumeFraction.coerceIn(0.001, 1.0))
        return (fullScaleSpl + dbfs + volumeDb + calibrationDb).coerceIn(0.0, MAX_DB)
    }

    fun classify(db: Double): SafetyLevel = when {
        db >= DANGER_DB -> SafetyLevel.DANGER
        db >= CAUTION_DB -> SafetyLevel.CAUTION
        else -> SafetyLevel.SAFE
    }

    /**
     * Recommended maximum daily listening time in minutes (NIOSH: 8 h at 85 dB,
     * halving for every extra 3 dB). Null when the level is below 85 dB.
     */
    fun safeListeningMinutes(db: Double): Double? =
        if (db < DANGER_DB) null else 480.0 / 2.0.pow((db - DANGER_DB) / 3.0)

    /** Fast attack, slow release smoothing so the display is readable but reacts to loud peaks. */
    fun smooth(previous: Double?, current: Double, release: Double = 0.35): Double =
        if (previous == null || current >= previous) current
        else previous + (current - previous) * release
}
