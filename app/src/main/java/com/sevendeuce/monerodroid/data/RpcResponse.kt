package com.sevendeuce.monerodroid.data

import com.google.gson.annotations.SerializedName

data class RpcRequest(
    val jsonrpc: String = "2.0",
    val id: String = "0",
    val method: String,
    val params: Map<String, Any>? = null
)

data class RpcResponse<T>(
    val jsonrpc: String?,
    val id: String?,
    val result: T?,
    val error: RpcError?
)

data class RpcError(
    val code: Int,
    val message: String
)

data class GetInfoResult(
    val height: Long = 0,
    @SerializedName("target_height")
    val targetHeight: Long = 0,
    val difficulty: Long = 0,
    @SerializedName("tx_count")
    val txCount: Long = 0,
    @SerializedName("tx_pool_size")
    val txPoolSize: Int = 0,
    @SerializedName("alt_blocks_count")
    val altBlocksCount: Int = 0,
    @SerializedName("outgoing_connections_count")
    val outgoingConnectionsCount: Int = 0,
    @SerializedName("incoming_connections_count")
    val incomingConnectionsCount: Int = 0,
    @SerializedName("rpc_connections_count")
    val rpcConnectionsCount: Int = 0,
    @SerializedName("white_peerlist_size")
    val whitePeerlistSize: Int = 0,
    @SerializedName("grey_peerlist_size")
    val greyPeerlistSize: Int = 0,
    @SerializedName("mainnet")
    val mainnet: Boolean = true,
    @SerializedName("testnet")
    val testnet: Boolean = false,
    @SerializedName("stagenet")
    val stagenet: Boolean = false,
    @SerializedName("top_block_hash")
    val topBlockHash: String = "",
    @SerializedName("cumulative_difficulty")
    val cumulativeDifficulty: Long = 0,
    @SerializedName("block_size_limit")
    val blockSizeLimit: Long = 0,
    @SerializedName("block_weight_limit")
    val blockWeightLimit: Long = 0,
    @SerializedName("block_size_median")
    val blockSizeMedian: Long = 0,
    @SerializedName("block_weight_median")
    val blockWeightMedian: Long = 0,
    @SerializedName("start_time")
    val startTime: Long = 0,
    @SerializedName("free_space")
    val freeSpace: Long = 0,
    val offline: Boolean = false,
    val untrusted: Boolean = false,
    @SerializedName("bootstrap_daemon_address")
    val bootstrapDaemonAddress: String = "",
    @SerializedName("height_without_bootstrap")
    val heightWithoutBootstrap: Long = 0,
    @SerializedName("was_bootstrap_ever_used")
    val wasBootstrapEverUsed: Boolean = false,
    @SerializedName("database_size")
    val databaseSize: Long = 0,
    @SerializedName("update_available")
    val updateAvailable: Boolean = false,
    val version: String = "",
    val status: String = ""
)

data class SyncInfoResult(
    val height: Long = 0,
    @SerializedName("target_height")
    val targetHeight: Long = 0,
    @SerializedName("next_needed_pruning_seed")
    val nextNeededPruningSeed: Int = 0,
    val peers: List<PeerInfo> = emptyList(),
    val spans: List<SpanInfo> = emptyList(),
    val status: String = "",
    val untrusted: Boolean = false
)

data class PeerInfo(
    val info: ConnectionInfo
)

data class ConnectionInfo(
    val address: String = "",
    @SerializedName("avg_download")
    val avgDownload: Long = 0,
    @SerializedName("avg_upload")
    val avgUpload: Long = 0,
    @SerializedName("connection_id")
    val connectionId: String = "",
    @SerializedName("current_download")
    val currentDownload: Long = 0,
    @SerializedName("current_upload")
    val currentUpload: Long = 0,
    val height: Long = 0,
    val host: String = "",
    val incoming: Boolean = false,
    val ip: String = "",
    @SerializedName("live_time")
    val liveTime: Long = 0,
    @SerializedName("local_ip")
    val localIp: Boolean = false,
    val localhost: Boolean = false,
    @SerializedName("peer_id")
    val peerId: String = "",
    val port: String = "",
    @SerializedName("recv_count")
    val recvCount: Long = 0,
    @SerializedName("recv_idle_time")
    val recvIdleTime: Long = 0,
    @SerializedName("send_count")
    val sendCount: Long = 0,
    @SerializedName("send_idle_time")
    val sendIdleTime: Long = 0,
    val state: String = "",
    @SerializedName("support_flags")
    val supportFlags: Int = 0
)

data class SpanInfo(
    @SerializedName("connection_id")
    val connectionId: String = "",
    val nblocks: Long = 0,
    val rate: Long = 0,
    @SerializedName("remote_address")
    val remoteAddress: String = "",
    val size: Long = 0,
    val speed: Long = 0,
    @SerializedName("start_block_height")
    val startBlockHeight: Long = 0
)
