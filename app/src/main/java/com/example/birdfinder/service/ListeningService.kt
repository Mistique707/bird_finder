package com.example.birdfinder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.birdfinder.BirdFinderApp
import com.example.birdfinder.R
import com.example.birdfinder.pipeline.DetectionPipeline
import com.example.birdfinder.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground service hosting the [DetectionPipeline] so listening continues with the
 * screen off. The Activity binds for [pipelineState]; explicit start/stop are issued
 * via the Activity's start/stop service intents.
 */
class ListeningService : Service() {

    inner class LocalBinder : Binder() {
        val state: StateFlow<com.example.birdfinder.pipeline.ListenState> get() = pipeline.state
    }

    private lateinit var pipeline: DetectionPipeline
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var bindings: Int = 0
    private var started = false

    val pipelineState: StateFlow<com.example.birdfinder.pipeline.ListenState> get() = pipeline.state

    override fun onCreate() {
        super.onCreate()
        pipeline = (applicationContext as BirdFinderApp).pipeline
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startListening()
            ACTION_STOP -> stopListening()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        bindings++
        return LocalBinder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bindings--
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startListening() {
        if (started) return
        started = true
        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), type)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        scope.launch { pipeline.start() }
    }

    private fun stopListening() {
        scope.launch {
            pipeline.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            started = false
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(BirdFinderApp.NOTIFICATION_CHANNEL_LISTENING) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        BirdFinderApp.NOTIFICATION_CHANNEL_LISTENING,
                        "Listening",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = "Bird Finder is listening for bird calls" },
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val activityPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, BirdFinderApp.NOTIFICATION_CHANNEL_LISTENING)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Listening for bird calls…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(activityPi)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.example.birdfinder.START"
        private const val ACTION_STOP = "com.example.birdfinder.STOP"
        private const val NOTIF_ID = 7001

        fun start(context: Context) {
            val intent = Intent(context, ListeningService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ListeningService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
