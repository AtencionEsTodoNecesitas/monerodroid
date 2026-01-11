package com.sevendeuce.monerodroid.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.net.Inet4Address
import java.net.NetworkInterface
import com.sevendeuce.monerodroid.data.NodeState
import com.sevendeuce.monerodroid.data.StorageLocation
import com.sevendeuce.monerodroid.service.NodeService
import com.sevendeuce.monerodroid.util.*
import com.sevendeuce.monerodroid.util.UpdateStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val context = application.applicationContext
    private val storageManager = StorageManager(context)
    private val configManager = ConfigManager(context)
    private val binaryManager = MonerodBinaryManager(context)
    private val rpcClient = NodeRpcClient()

    private var nodeService: NodeService? = null
    private var serviceBound = false

    private val _nodeState = MutableStateFlow(NodeState())
    val nodeState: StateFlow<NodeState> = _nodeState.asStateFlow()

    private val _binaryStatus = MutableStateFlow<BinaryStatus>(BinaryStatus.NotInstalled)
    val binaryStatus: StateFlow<BinaryStatus> = _binaryStatus.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    val useExternalStorage = configManager.useExternalStorage.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val pruneBlockchain = configManager.pruneBlockchain.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    val rpcUsername = configManager.rpcUsername.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "monero"
    )

    val rpcPassword = configManager.rpcPassword.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    val startOnBoot = configManager.startOnBoot.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val torEnabled = configManager.torEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val onionAddress = configManager.onionAddress.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    private val orbotManager = OrbotManager(context)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as NodeService.LocalBinder
            nodeService = binder.getService()
            serviceBound = true

            // Observe service state
            viewModelScope.launch {
                nodeService?.nodeInfo?.collect { info ->
                    if (info != null) {
                        updateStateFromRpc(info)
                    }
                }
            }

            viewModelScope.launch {
                nodeService?.isRunning?.collect { running ->
                    _nodeState.update { it.copy(isRunning = running) }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            nodeService = null
            serviceBound = false
        }
    }

    init {
        checkBinaryStatus()
        updateStorageInfo()
        updateLocalIpAddress()
        initRpcCredentialsAndStartPolling()
        checkArchitecture()
    }

    private fun initRpcCredentialsAndStartPolling() {
        viewModelScope.launch {
            // Initialize RPC credentials first - get the config which ensures password exists
            val config = configManager.getNodeConfig()
            rpcClient.setHost(config.rpcBindIp, config.rpcBindPort)
            rpcClient.setCredentials(config.rpcUsername, config.rpcPassword)
            Log.d(TAG, "RPC initialized: host=${config.rpcBindIp}:${config.rpcBindPort}, user=${config.rpcUsername}")

            // Then start polling
            startStatusPolling()
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            // Try using ConnectivityManager first (preferred on newer Android)
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)

            linkProperties?.linkAddresses?.forEach { linkAddress ->
                val address = linkAddress.address
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address.hostAddress ?: ""
                }
            }

            // Fallback to NetworkInterface enumeration
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    networkInterface.inetAddresses.toList().forEach { address ->
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            return address.hostAddress ?: ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return ""
    }

    private fun updateLocalIpAddress() {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = getLocalIpAddress()
            _nodeState.update { it.copy(localIpAddress = ip) }
        }
    }

    private fun checkArchitecture() {
        if (!ArchitectureDetector.isSupported()) {
            _nodeState.update {
                it.copy(errorMessage = "Unsupported CPU architecture: ${ArchitectureDetector.getArchitectureName()}")
            }
        }
    }

    private fun checkBinaryStatus() {
        _binaryStatus.value = if (binaryManager.isBinaryInstalled()) {
            BinaryStatus.Installed
        } else {
            BinaryStatus.NotInstalled
        }

        // Update node version if available
        binaryManager.getBinaryVersion()?.let { version ->
            _nodeState.update { it.copy(nodeVersion = version) }
        }
    }

    private fun updateStorageInfo() {
        viewModelScope.launch {
            var useExternal = useExternalStorage.first()
            val isPruned = pruneBlockchain.first()

            // If external storage is selected but not available, reset to internal
            if (useExternal && !storageManager.isExternalStorageAvailable()) {
                configManager.setUseExternalStorage(false)
                useExternal = false
            }

            val storageInfo = storageManager.getSelectedStorageInfo(useExternal)

            _nodeState.update {
                it.copy(
                    useExternalStorage = useExternal,
                    storageUsedPercent = storageInfo.usedPercent,
                    storageFreeGb = storageInfo.freeGb,
                    storageTotalGb = storageInfo.totalGb,
                    hasAdequateStorage = storageInfo.hasAdequateStorage(isPruned),
                    isPruned = isPruned
                )
            }
        }
    }

    private fun startStatusPolling() {
        viewModelScope.launch {
            while (true) {
                if (_nodeState.value.isRunning || serviceBound) {
                    try {
                        val result = rpcClient.getInfo()
                        if (result.isSuccess) {
                            result.getOrNull()?.let { info ->
                                updateStateFromRpc(info)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Status poll failed: ${e.message}")
                    }
                }

                // Update storage info less frequently (every 30 seconds)
                delay(3000) // Poll every 3 seconds for responsive UI

                // Update storage and IP every 10th poll (every 30s)
                if (System.currentTimeMillis() % 30000 < 3000) {
                    updateStorageInfo()
                    updateLocalIpAddress()
                }
            }
        }
    }

    private fun updateStateFromRpc(info: com.sevendeuce.monerodroid.data.GetInfoResult) {
        // Calculate sync progress properly:
        // - If targetHeight > height, we're syncing: progress = height/targetHeight
        // - If targetHeight == 0 and height > 1000 AND we have peers, node is likely synced
        // - If we have no peers or low height, we're still connecting/discovering
        val totalPeers = info.outgoingConnectionsCount + info.incomingConnectionsCount

        val syncProgress = when {
            // Still syncing - target is ahead of current height
            info.targetHeight > info.height -> {
                (info.height.toFloat() / info.targetHeight * 100).coerceAtMost(99.9f)
            }

            // Fully synced - we've reached or passed target height
            info.targetHeight > 0 && info.height >= info.targetHeight -> {
                100f
            }

            // Likely synced - targetHeight becomes 0 when fully synced
            // BUT only if we have peers (otherwise we might just not have internet)
            info.height > 1000 && info.targetHeight == 0L && totalPeers > 0 -> {
                100f
            }

            // No peers or just starting - show 0% (not connected/synced)
            else -> {
                0f
            }
        }

        val storageFreeGb = info.freeSpace / (1024f * 1024f * 1024f)

        _nodeState.update {
            it.copy(
                isRunning = true,
                numberOfPeers = info.outgoingConnectionsCount + info.incomingConnectionsCount,
                outgoingConnections = info.outgoingConnectionsCount,
                incomingConnections = info.incomingConnectionsCount,
                rpcConnections = info.rpcConnectionsCount,
                syncProgress = syncProgress,
                currentHeight = info.height,
                targetHeight = info.targetHeight,
                isNodeOutOfDate = info.updateAvailable,
                nodeVersion = info.version,
                storageFreeGb = storageFreeGb,
                startTime = info.startTime,
                errorMessage = null
            )
        }
    }

    fun downloadBinary() {
        viewModelScope.launch {
            // Use installBinary which first tries bundled, then downloads
            binaryManager.installBinary().collect { status ->
                _binaryStatus.value = status

                if (status is BinaryStatus.Installed) {
                    checkBinaryStatus()
                }
            }
        }
    }

    fun isBundledBinaryAvailable(): Boolean {
        return binaryManager.isBundledBinaryAvailable()
    }

    fun setUseExternalStorage(external: Boolean) {
        viewModelScope.launch {
            configManager.setUseExternalStorage(external)
            updateStorageInfo()
        }
    }

    fun setPruneBlockchain(prune: Boolean) {
        viewModelScope.launch {
            configManager.setPruneBlockchain(prune)
            updateStorageInfo()
        }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            configManager.setStartOnBoot(enabled)
        }
    }

    fun setTorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configManager.setTorEnabled(enabled)
        }
    }

    fun setOnionAddress(address: String) {
        viewModelScope.launch {
            configManager.setOnionAddress(address)
        }
    }

    fun isOrbotInstalled(): Boolean {
        return orbotManager.isOrbotInstalled()
    }

    fun getOrbotState(): StateFlow<OrbotManager.OrbotState> {
        return orbotManager.state
    }

    fun initOrbot() {
        orbotManager.init()
    }

    fun startNode() {
        if (!binaryManager.isBinaryInstalled()) {
            _nodeState.update { it.copy(errorMessage = "Monerod binary not installed") }
            return
        }

        viewModelScope.launch {
            val isPruned = pruneBlockchain.first()
            val useExternal = useExternalStorage.first()

            if (!storageManager.hasAdequateStorage(useExternal, isPruned)) {
                _nodeState.update { it.copy(errorMessage = "Not enough storage space") }
                return@launch
            }

            _nodeState.update { it.copy(isInitializing = true, errorMessage = null) }

            // Start the foreground service
            NodeService.startService(context)

            // Bind to service
            val intent = Intent(context, NodeService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

            // Wait a bit and update state
            delay(3000)
            _nodeState.update { it.copy(isInitializing = false, isRunning = true) }
        }
    }

    fun stopNode() {
        // Set stopping state immediately on main thread
        _nodeState.update { it.copy(isStopping = true) }
        Log.d(TAG, "Stopping node, isStopping = true")

        // Use GlobalScope to ensure stop completes even if ViewModel is cleared
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            // Unbind from service first (must be on main thread)
            withContext(Dispatchers.Main) {
                if (serviceBound) {
                    try {
                        context.unbindService(serviceConnection)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unbinding service", e)
                    }
                    serviceBound = false
                    nodeService = null
                }
            }

            // Stop the service (must be on main thread)
            withContext(Dispatchers.Main) {
                NodeService.stopService(context)
            }

            // Wait for the node to actually stop (max 8 seconds - force kill happens in MonerodProcess after 3s)
            var attempts = 0
            while (attempts < 8) {
                delay(1000)
                attempts++

                // Check if node is still responding
                try {
                    val result = rpcClient.getInfo()
                    if (result.isFailure) {
                        Log.d(TAG, "Node stopped responding after $attempts seconds")
                        break
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Node stopped: ${e.message}")
                    break
                }
            }

            // Brief delay to ensure UI updates cleanly
            delay(500)

            withContext(Dispatchers.Main) {
                _nodeState.update {
                    it.copy(
                        isRunning = false,
                        isStopping = false,
                        numberOfPeers = 0,
                        syncProgress = 0f,
                        currentHeight = 0,
                        targetHeight = 0
                    )
                }
                Log.d(TAG, "Node stopped, isStopping = false")
            }
        }
    }

    fun toggleSettings() {
        _showSettings.value = !_showSettings.value
    }

    fun hideSettings() {
        _showSettings.value = false
    }

    fun isExternalStorageAvailable(): Boolean {
        return storageManager.isExternalStorageAvailable()
    }

    fun clearError() {
        _nodeState.update { it.copy(errorMessage = null) }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateStatus.value = UpdateStatus.Checking
            _updateStatus.value = binaryManager.checkForUpdate()
        }
    }

    fun updateMonerod() {
        if (_nodeState.value.isRunning) {
            _updateStatus.value = UpdateStatus.Error("Stop the node before updating")
            return
        }

        viewModelScope.launch {
            binaryManager.updateBinary().collect { status ->
                _updateStatus.value = status

                if (status is UpdateStatus.Success) {
                    // Refresh version info
                    checkBinaryStatus()
                }
            }
        }
    }

    fun resetUpdateStatus() {
        _updateStatus.value = UpdateStatus.Idle
    }

    override fun onCleared() {
        super.onCleared()
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service", e)
            }
        }
    }
}
