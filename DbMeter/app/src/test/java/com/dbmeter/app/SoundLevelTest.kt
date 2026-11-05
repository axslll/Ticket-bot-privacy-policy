package com.dbmeter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SoundLevelTest {

    @Test
    fun fullScaleSquareWaveIsZeroDbfs() {
        val samples = ShortArray(1000) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        assertEquals(0.0, SoundLevel.rmsDbfs(samples), 0.01)
    }

    @Test
    fun halfAmplitudeIsMinusSixDb() {
        val samples = ShortArray(1000) { if (it % 2 == 0) 16384 else -16384 }
        assertEquals(-6.02, SoundLevel.rmsDbfs(samples), 0.01)
    }

    @Test
    fun silenceHitsTheFloor() {
        assertEquals(SoundLevel.FLOOR_DBFS, SoundLevel.rmsDbfs(ShortArray(512)), 0.0)
        assertEquals(SoundLevel.FLOOR_DBFS, SoundLevel.rmsDbfs(ShortArray(0)), 0.0)
    }

    @Test
    fun onlyTheFirstCountSamplesAreUsed() {
        val samples = ShortArray(1000) { if (it < 10) 16384 else 0 }
        assertEquals(-6.02, SoundLevel.rmsDbfs(samples, 10), 0.01)
    }

    @Test
    fun classifyUsesGreenOrangeRedBoundaries() {
        assertEquals(SafetyLevel.SAFE, SoundLevel.classify(0.0))
        assertEquals(SafetyLevel.SAFE, SoundLevel.classify(69.9))
        assertEquals(SafetyLevel.CAUTION, SoundLevel.classify(70.0))
        assertEquals(SafetyLevel.CAUTION, SoundLevel.classify(84.9))
        assertEquals(SafetyLevel.DANGER, SoundLevel.classify(85.0))
        assertEquals(SafetyLevel.DANGER, SoundLevel.classify(120.0))
    }

    @Test
    fun estimateAtFullScaleAndFullVolumeIsReferenceLevel() {
        assertEquals(100.0, SoundLevel.estimateSpl(0.0, 1.0), 0.001)
    }

    @Test
    fun halvingVolumeDropsAboutSixDb() {
        assertEquals(93.98, SoundLevel.estimateSpl(0.0, 0.5), 0.01)
    }

    @Test
    fun calibrationShiftsTheResult() {
        assertEquals(95.0, SoundLevel.estimateSpl(-10.0, 1.0, calibrationDb = 5.0), 0.001)
    }

    @Test
    fun estimateIsClamped() {
        assertEquals(0.0, SoundLevel.estimateSpl(-120.0, 0.001), 0.0)
        assertEquals(SoundLevel.MAX_DB, SoundLevel.estimateSpl(0.0, 1.0, calibrationDb = 50.0), 0.0)
    }

    @Test
    fun safeListeningTimeHalvesEveryThreeDb() {
        assertNull(SoundLevel.safeListeningMinutes(84.9))
        assertEquals(480.0, SoundLevel.safeListeningMinutes(85.0)!!, 0.001)
        assertEquals(240.0, SoundLevel.safeListeningMinutes(88.0)!!, 0.001)
        assertEquals(15.0, SoundLevel.safeListeningMinutes(100.0)!!, 0.5)
        assertNotNull(SoundLevel.safeListeningMinutes(110.0))
    }

    @Test
    fun smoothingRisesInstantlyAndFallsGradually() {
        assertEquals(80.0, SoundLevel.smooth(null, 80.0), 0.0)
        assertEquals(90.0, SoundLevel.smooth(80.0, 90.0), 0.0)
        assertEquals(76.5, SoundLevel.smooth(80.0, 70.0), 0.001)
    }
}
