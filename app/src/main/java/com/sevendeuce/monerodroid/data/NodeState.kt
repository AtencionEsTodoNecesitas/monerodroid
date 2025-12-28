package com.sevendeuce.monerodroid.data

data class NodeState(
    val isRunning: Boolean = false,
    val useExternalStorage: Boolean = false,
    val storageUsedPercent: Int = 0,
    val storageFreeGb: Float = 0f,
    val storageTotalGb: Float = 0f,
    val numberOfPeers: Int = 0,
    val outgoingConnections: Int = 0,
    val incomingConnections: Int = 0,
    val rpcConnections: Int = 0,
    val syncProgress: Float = 0f,
    val currentHeight: Long = 0,
    val targetHeight: Long = 0,
    val isNodeOutOfDate: Boolean = false,
    val hasAdequateStorage: Boolean = true,
    val nodeVersion: String = "",
    val errorMessage: String? = null,
    val isInitializing: Boolean = false,
    val isStopping: Boolean = false,
    val isPruned: Boolean = true,
    val startTime: Long = 0
) {
    val isSynced: Boolean
        get() = syncProgress >= 99.9f && currentHeight > 0

    val totalPeers: Int
        get() = outgoingConnections + incomingConnections
}

enum class StorageLocation {
    INTERNAL,
    EXTERNAL
}

data class StorageInfo(
    val location: StorageLocation,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isAvailable: Boolean
) {
    val totalGb: Float
        get() = totalBytes / (1024f * 1024f * 1024f)

    val freeGb: Float
        get() = freeBytes / (1024f * 1024f * 1024f)

    val usedPercent: Int
        get() = if (totalBytes > 0) {
            ((totalBytes - freeBytes) * 100 / totalBytes).toInt()
        } else 0

    // Minimum 50GB for pruned node, 300GB for full node
    fun hasAdequateStorage(isPruned: Boolean): Boolean {
        val requiredGb = if (isPruned) 50f else 300f
        return freeGb >= requiredGb
    }
}

data class NodeConfig(
    val dataDir: String = "",
    val pruneBlockchain: Boolean = true,
    val p2pBindPort: Int = 18080,
    val rpcBindPort: Int = 18081,
    val restrictedRpcPort: Int = 18089,
    val outPeers: Int = 32,
    val inPeers: Int = 32,
    val limitRateUp: Int = 1048576,
    val limitRateDown: Int = 1048576,
    val dbSyncMode: String = "fast:async:1000000",
    val noZmq: Boolean = true,
    val noIgd: Boolean = true,
    val enableDnsBlocklist: Boolean = true,
    val disableDnsCheckpoints: Boolean = true
)
