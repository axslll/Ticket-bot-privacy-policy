package com.dbmeter.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Foreground service that listens to everything the phone is playing (via AudioPlaybackCapture),
 * works out its loudness about ten times a second and publishes it through [LevelMonitor].
 */
class CaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var shutDown = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_STOP -> shutdown(null)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        if (job != null) return
        createChannel()
        // Must be in the foreground *before* the MediaProjection token is used (Android 14+).
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notif_starting)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            return shutdown(getString(R.string.error_no_projection))
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return shutdown(getString(R.string.error_no_permission))
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        val proj = manager.getMediaProjection(resultCode, resultData)
        proj.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() = shutdown(null)
            },
            Handler(Looper.getMainLooper()),
        )
        projection = proj

        val rec = try {
            buildRecorder(proj)
        } catch (e: Exception) {
            return shutdown(getString(R.string.error_recorder, e.message ?: e.javaClass.simpleName))
        }
        recorder = rec

        LevelMonitor.started()
        job = scope.launch { measureLoop(rec) }
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked in startCapture()
    private fun buildRecorder(proj: MediaProjection): AudioRecord {
        val config = AudioPlaybackCaptureConfiguration.Builder(proj)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val rec = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(max(minBytes, FRAME_SAMPLES * 2 * 4))
            .setAudioPlaybackCaptureConfig(config)
            .build()
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialise" }
        return rec
    }

    private suspend fun measureLoop(rec: AudioRecord) {
        val audio = getSystemService(AudioManager::class.java)
        val buffer = ShortArray(FRAME_SAMPLES)
        var smoothed: Double? = null
        var lastLevel: SafetyLevel? = null
        try {
            rec.startRecording()
            while (scope.isActive) {
                val read = rec.read(buffer, 0, buffer.size) // blocks ~100 ms
                if (read < 0) {
                    shutdown(getString(R.string.error_read))
                    return
                }
                if (read == 0) continue

                val dbfs = SoundLevel.rmsDbfs(buffer, read)
                if (dbfs < SoundLevel.SILENCE_DBFS) {
                    smoothed = null
                    LevelMonitor.update(null)
                    if (lastLevel != null) {
                        lastLevel = null
                        notify(getString(R.string.notif_silent))
                    }
                    continue
                }

                // Captured audio is taken before the system volume is applied, so fold the
                // current media volume back in to estimate what actually reaches the ear.
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toDouble() / max
                val spl = SoundLevel.estimateSpl(dbfs, volume, LevelMonitor.calibrationDb)
                smoothed = SoundLevel.smooth(smoothed, spl)
                LevelMonitor.update(smoothed)

                val level = SoundLevel.classify(smoothed)
                if (level != lastLevel) {
                    lastLevel = level
                    notify(
                        getString(
                            when (level) {
                                SafetyLevel.SAFE -> R.string.notif_safe
                                SafetyLevel.CAUTION -> R.string.notif_caution
                                SafetyLevel.DANGER -> R.string.notif_danger
                            },
                            smoothed.roundToInt(),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            // stop() from shutdown() makes a blocked read fail; that is expected.
            if (!shutDown) shutdown(getString(R.string.error_recorder, e.message ?: e.javaClass.simpleName))
        }
    }

    @Synchronized
    private fun shutdown(error: String?) {
        if (shutDown) return
        shutDown = true
        job?.cancel()
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        runCatching { projection?.stop() }
        recorder = null
        projection = null
        LevelMonitor.stopped(error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        shutdown(null)
        scope.cancel()
        super.onDestroy()
    }

    // ---- notification ----------------------------------------------------------------------

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speaker)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.dbmeter.app.START"
        private const val ACTION_STOP = "com.dbmeter.app.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "meter"
        private const val NOTIFICATION_ID = 1

        private const val SAMPLE_RATE = 44_100
        private const val FRAME_SAMPLES = SAMPLE_RATE / 10 * 2 // 100 ms of interleaved stereo

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, CaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CaptureService::class.java).setAction(ACTION_STOP))
        }
    }
}
