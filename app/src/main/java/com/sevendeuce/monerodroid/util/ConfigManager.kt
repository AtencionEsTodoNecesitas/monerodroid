package com.sevendeuce.monerodroid.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.sevendeuce.monerodroid.data.NodeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "monerodroid_settings")

class ConfigManager(private val context: Context) {

    companion object {
        private val KEY_USE_EXTERNAL_STORAGE = booleanPreferencesKey("use_external_storage")
        private val KEY_PRUNE_BLOCKCHAIN = booleanPreferencesKey("prune_blockchain")
        private val KEY_START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        private val KEY_OUT_PEERS = intPreferencesKey("out_peers")
        private val KEY_IN_PEERS = intPreferencesKey("in_peers")
        private val KEY_P2P_PORT = intPreferencesKey("p2p_port")
        private val KEY_RPC_PORT = intPreferencesKey("rpc_port")
        private val KEY_RESTRICTED_RPC_PORT = intPreferencesKey("restricted_rpc_port")
        private val KEY_CUSTOM_FLAGS = stringPreferencesKey("custom_flags")
        private val KEY_FIRST_RUN = booleanPreferencesKey("first_run")
        private val KEY_RPC_USERNAME = stringPreferencesKey("rpc_username")
        private val KEY_RPC_PASSWORD = stringPreferencesKey("rpc_password")
    }

    val useExternalStorage: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_USE_EXTERNAL_STORAGE] ?: false
    }

    val pruneBlockchain: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PRUNE_BLOCKCHAIN] ?: true
    }

    val startOnBoot: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_START_ON_BOOT] ?: false
    }

    val customFlags: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_FLAGS] ?: ""
    }

    val isFirstRun: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FIRST_RUN] ?: true
    }

    val rpcUsername: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_RPC_USERNAME] ?: "monero"
    }

    val rpcPassword: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_RPC_PASSWORD] ?: ""
    }

    suspend fun setUseExternalStorage(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_EXTERNAL_STORAGE] = value
        }
    }

    suspend fun setPruneBlockchain(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PRUNE_BLOCKCHAIN] = value
        }
    }

    suspend fun setStartOnBoot(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_START_ON_BOOT] = value
        }
    }

    suspend fun setCustomFlags(value: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_FLAGS] = value
        }
    }

    suspend fun setFirstRunComplete() {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIRST_RUN] = false
        }
    }

    suspend fun setRpcCredentials(username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RPC_USERNAME] = username
            prefs[KEY_RPC_PASSWORD] = password
        }
    }

    suspend fun generateRpcPasswordIfNeeded(): String {
        val currentPassword = rpcPassword.first()
        if (currentPassword.isEmpty()) {
            val newPassword = generateSecurePassword()
            setRpcCredentials("monero", newPassword)
            return newPassword
        }
        return currentPassword
    }

    private fun generateSecurePassword(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        return (1..16).map { chars.random() }.joinToString("")
    }

    suspend fun getNodeConfig(): NodeConfig {
        val prefs = context.dataStore.data.first()
        // Generate password if not exists
        val password = generateRpcPasswordIfNeeded()
        return NodeConfig(
            pruneBlockchain = prefs[KEY_PRUNE_BLOCKCHAIN] ?: true,
            p2pBindPort = prefs[KEY_P2P_PORT] ?: 18080,
            rpcBindPort = prefs[KEY_RPC_PORT] ?: 18081,
            restrictedRpcPort = prefs[KEY_RESTRICTED_RPC_PORT] ?: 18089,
            outPeers = prefs[KEY_OUT_PEERS] ?: 32,
            inPeers = prefs[KEY_IN_PEERS] ?: 32,
            rpcUsername = prefs[KEY_RPC_USERNAME] ?: "monero",
            rpcPassword = password
        )
    }

    fun generateConfigFile(dataDir: String, config: NodeConfig): String {
        return """
# MoneroDroid Configuration
# Data directory (blockchain db and indices)
data-dir=$dataDir

# Log file
log-file=/dev/null
max-log-file-size=0

# Blockchain pruning
prune-blockchain=${if (config.pruneBlockchain) 1 else 0}

# P2P (seeding) binds
p2p-bind-ip=0.0.0.0
p2p-bind-port=${config.p2pBindPort}

# Restricted RPC binds (allow restricted access from LAN/WAN)
rpc-restricted-bind-ip=0.0.0.0
rpc-restricted-bind-port=${config.restrictedRpcPort}

# Unrestricted RPC binds (all interfaces with authentication)
rpc-bind-ip=0.0.0.0
rpc-bind-port=${config.rpcBindPort}
confirm-external-bind=1

# RPC Authentication (required for external access)
rpc-login=${config.rpcUsername}:${config.rpcPassword}

# Services
rpc-ssl=autodetect
no-zmq=${if (config.noZmq) 1 else 0}
no-igd=${if (config.noIgd) 1 else 0}
db-sync-mode=${config.dbSyncMode}

# DNS settings
disable-dns-checkpoints=${if (config.disableDnsCheckpoints) 1 else 0}
enable-dns-blocklist=${if (config.enableDnsBlocklist) 1 else 0}

# Connection Limits
out-peers=${config.outPeers}
in-peers=${config.inPeers}
limit-rate-up=${config.limitRateUp}
limit-rate-down=${config.limitRateDown}
        """.trimIndent()
    }

    fun writeConfigFile(configFile: File, dataDir: String, config: NodeConfig) {
        configFile.parentFile?.mkdirs()
        configFile.writeText(generateConfigFile(dataDir, config))
    }
}
