package com.sevendeuce.monerodroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sevendeuce.monerodroid.MainActivity
import com.sevendeuce.monerodroid.R
import com.sevendeuce.monerodroid.util.BinaryStatus
import com.sevendeuce.monerodroid.util.MonerodBinaryManager
import com.sevendeuce.monerodroid.util.UpdateStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BinaryDownloadService : Service() {

    companion object {
        private const val TAG = "BinaryDownloadService"
        private const val CHANNEL_ID = "monerodroid_download_channel"
        private const val NOTIFICATION_ID = 2

        const val ACTION_DOWNLOAD = "com.sevendeuce.monerodroid.DOWNLOAD_BINARY"
        const val ACTION_UPDATE = "com.sevendeuce.monerodroid.UPDATE_BINARY"

        private val _binaryStatusFlow = MutableStateFlow<BinaryStatus>(BinaryStatus.NotInstalled)
        val binaryStatusFlow: StateFlow<BinaryStatus> = _binaryStatusFlow.asStateFlow()

        private val _updateStatusFlow = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
        val updateStatusFlow: StateFlow<UpdateStatus> = _updateStatusFlow.asStateFlow()

        fun startDownload(context: Context) {
            val intent = Intent(context, BinaryDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startUpdate(context: Context) {
            val intent = Intent(context, BinaryDownloadService::class.java).apply {
                action = ACTION_UPDATE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var binaryManager: MonerodBinaryManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BinaryDownloadService created")
        binaryManager = MonerodBinaryManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        startForeground(NOTIFICATION_ID, createNotification("Downloading..."))

        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                serviceScope.launch {
                    binaryManager.installBinary().collect { status ->
                        _binaryStatusFlow.value = status
                        when (status) {
                            is BinaryStatus.Downloading -> {
                                updateNotification("Downloading monerod...")
                            }
                            is BinaryStatus.DownloadProgress -> {
                                updateNotification(
                                    "Downloading: ${status.progress}% — ${
                                        String.format("%.1f", status.downloadedMb)
                                    } / ${String.format("%.1f", status.totalMb)} MB"
                                )
                            }
                            is BinaryStatus.Extracting -> {
                                updateNotification("Extracting monerod...")
                            }
                            is BinaryStatus.InstallingBundled -> {
                                updateNotification("Installing bundled binary...")
                            }
                            is BinaryStatus.Installed -> {
                                updateNotification("Download complete")
                                stopSelf()
                            }
                            is BinaryStatus.Error -> {
                                updateNotification("Download failed: ${status.message}")
                                stopSelf()
                            }
                            else -> {}
                        }
                    }
                }
            }
            ACTION_UPDATE -> {
                serviceScope.launch {
                    binaryManager.updateBinary().collect { status ->
                        _updateStatusFlow.value = status
                        when (status) {
                            is UpdateStatus.Downloading -> {
                                updateNotification("Updating monerod...")
                            }
                            is UpdateStatus.Progress -> {
                                updateNotification(
                                    "Updating: ${status.progress}% — ${
                                        String.format("%.1f", status.downloadedMb)
                                    } / ${String.format("%.1f", status.totalMb)} MB"
                                )
                            }
                            is UpdateStatus.Extracting -> {
                                updateNotification("Extracting update...")
                            }
                            is UpdateStatus.Success -> {
                                updateNotification("Update complete")
                                stopSelf()
                            }
                            is UpdateStatus.Error -> {
                                updateNotification("Update failed: ${status.message}")
                                stopSelf()
                            }
                            else -> {}
                        }
                    }
                }
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BinaryDownloadService destroyed")
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Binary Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for monerod binary download/update progress"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MoneroDroid")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }
}
