package com.sevendeuce.monerodroid.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sevendeuce.monerodroid.MainActivity
import com.sevendeuce.monerodroid.R
import com.sevendeuce.monerodroid.data.GetInfoResult
import com.sevendeuce.monerodroid.util.ConfigManager
import com.sevendeuce.monerodroid.util.MonerodProcess
import com.sevendeuce.monerodroid.util.NodeRpcClient
import com.sevendeuce.monerodroid.util.RpcProxyServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class NodeService : Service() {

    companion object {
        private const val TAG = "NodeService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "monerodroid_node_channel"
        private const val ACTION_STOP = "com.sevendeuce.monerodroid.STOP_NODE"

        fun startService(context: Context) {
            val intent = Intent(context, NodeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, NodeService::class.java)
            context.stopService(intent)
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var monerodProcess: MonerodProcess
    private lateinit var configManager: ConfigManager
    private lateinit var rpcClient: NodeRpcClient
    private var proxyServer: RpcProxyServer? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var statusUpdateJob: Job? = null

    private val _nodeInfo = MutableStateFlow<GetInfoResult?>(null)
    val nodeInfo: StateFlow<GetInfoResult?> = _nodeInfo

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    inner class LocalBinder : Binder() {
        fun getService(): NodeService = this@NodeService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NodeService created")

        monerodProcess = MonerodProcess(this)
        configManager = ConfigManager(this)
        rpcClient = NodeRpcClient()

        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "NodeService onStartCommand")

        if (intent?.action == ACTION_STOP) {
            stopNode()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Starting Monero Node..."))
        startNode()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "NodeService destroyed")

        statusUpdateJob?.cancel()
        stopProxyServer()
        releaseWakeLock()

        // Stop the node process in a separate thread to avoid blocking main thread
        Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    monerodProcess.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping monerod in onDestroy", e)
            }
        }.start()

        // Cancel scope
        serviceScope.cancel()
    }

    private fun startNode() {
        serviceScope.launch {
            try {
                _errorMessage.value = null

                val useExternal = configManager.useExternalStorage.first()
                Log.d(TAG, "Starting node with external storage: $useExternal")

                Log.d(TAG, "Calling monerodProcess.start()")
                val result = monerodProcess.start(useExternal)
                Log.d(TAG, "monerodProcess.start() call completed")

                Log.d(TAG, "MonerodProcess.start() returned: ${result.isSuccess}")

                if (result.isSuccess) {
                    Log.d(TAG, "Node started successfully, initializing services")
                    _isRunning.value = true
                    Log.d(TAG, "Starting status updates")
                    startStatusUpdates()
                    Log.d(TAG, "Starting proxy server")
                    startProxyServer()
                    Log.d(TAG, "Updating notification")
                    updateNotification("Monero Node Running")
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to start node"
                    updateNotification("Node Start Failed")
                    stopSelf()
                }
            } catch (e: CancellationException) {
                // Normal cancellation, don't treat as error
                Log.d(TAG, "Node start cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error starting node", e)
                _errorMessage.value = e.message
                stopSelf()
            }
        }
    }

    private fun stopNode() {
        serviceScope.launch {
            try {
                statusUpdateJob?.cancel()
                stopProxyServer()
                withContext(NonCancellable) {
                    monerodProcess.stop()
                }
                _isRunning.value = false
                _nodeInfo.value = null
            } catch (e: CancellationException) {
                // Normal cancellation during shutdown
                Log.d(TAG, "Stop node cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping node", e)
            } finally {
                stopSelf()
            }
        }
    }

    private fun startProxyServer() {
        serviceScope.launch {
            try {
                val username = configManager.rpcUsername.first()
                val password = configManager.generateRpcPasswordIfNeeded()

                Log.d(TAG, "Starting RPC Proxy with credentials: user=$username")

                proxyServer = RpcProxyServer(proxyPort = 8081).apply {
                    setCredentials(username, password)
                    startProxy()
                }
                Log.d(TAG, "RPC Proxy server started on port 8081")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start proxy server", e)
                e.printStackTrace()
            }
        }
    }

    private fun stopProxyServer() {
        try {
            proxyServer?.stopProxy()
            proxyServer = null
            Log.d(TAG, "RPC Proxy server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping proxy server", e)
        }
    }

    private fun startStatusUpdates() {
        statusUpdateJob?.cancel()
        statusUpdateJob = serviceScope.launch {
            while (isActive) {
                try {
                    val result = rpcClient.getInfo()
                    if (result.isSuccess) {
                        val info = result.getOrNull()
                        _nodeInfo.value = info
                        _isRunning.value = true

                        // Update notification with sync status
                        info?.let {
                            val syncPercent = if (it.targetHeight > 0) {
                                (it.height.toFloat() / it.targetHeight * 100).coerceAtMost(100f)
                            } else 0f

                            val status = if (syncPercent >= 99.9f) {
                                "Synced | Peers: ${it.outgoingConnectionsCount + it.incomingConnectionsCount}"
                            } else {
                                "Syncing: ${String.format("%.1f", syncPercent)}% | Peers: ${it.outgoingConnectionsCount + it.incomingConnectionsCount}"
                            }
                            updateNotification("Monero Node | $status")
                        }
                    } else {
                        // Node might be starting up
                        if (!monerodProcess.isRunning) {
                            _isRunning.value = false
                        }
                    }
                } catch (e: CancellationException) {
                    // Normal cancellation, exit loop
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Status update error", e)
                }

                delay(5000) // Update every 5 seconds
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monero Node Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for Monero Node background service"
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

        // Main activity intent
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, pendingIntentFlags
        )

        // Stop action intent
        val stopIntent = Intent(this, NodeService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MoneroDroid")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MoneroDroid::NodeWakeLock"
        ).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
}
