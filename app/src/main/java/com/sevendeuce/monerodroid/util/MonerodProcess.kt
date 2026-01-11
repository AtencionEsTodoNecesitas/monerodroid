package com.sevendeuce.monerodroid.util

import android.content.Context
import android.util.Log
import com.sevendeuce.monerodroid.data.NodeConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

class MonerodProcess(private val context: Context) {

    companion object {
        private const val TAG = "MonerodProcess"
    }

    private val storageManager = StorageManager(context)
    private val configManager = ConfigManager(context)
    private val binaryManager = MonerodBinaryManager(context)
    private val rpcClient = NodeRpcClient()

    private var process: Process? = null
    private var credentialsInitialized = false

    private suspend fun ensureRpcCredentials(config: NodeConfig? = null) {
        if (!credentialsInitialized || config != null) {
            val username: String
            val password: String
            val rpcHost: String
            val rpcPort: Int
            if (config != null) {
                // Use credentials from config to ensure consistency
                username = config.rpcUsername
                password = config.rpcPassword
                rpcHost = config.rpcBindIp
                rpcPort = config.rpcBindPort
            } else {
                username = configManager.rpcUsername.first()
                password = configManager.generateRpcPasswordIfNeeded()
                rpcHost = configManager.rpcBindIp.first()
                rpcPort = configManager.getNodeConfig().rpcBindPort
            }
            rpcClient.setHost(rpcHost, rpcPort)
            rpcClient.setCredentials(username, password)
            credentialsInitialized = true
            Log.d(TAG, "RPC initialized: host=$rpcHost:$rpcPort, user=$username")
        }
    }

    val isRunning: Boolean
        get() = try {
            process?.isAlive == true || isProcessRunningByName()
        } catch (e: Exception) {
            false
        }

    private fun isProcessRunningByName(): Boolean {
        return try {
            // Try pidof first
            val pidofProcess = ProcessBuilder("pidof", "monerod")
                .redirectErrorStream(true)
                .start()
            val output = pidofProcess.inputStream.bufferedReader().readText().trim()
            pidofProcess.waitFor(2, TimeUnit.SECONDS)
            if (output.isNotEmpty()) return true

            // Also check for libmonerod.so (bundled binary name)
            val pidofProcess2 = ProcessBuilder("pidof", "libmonerod.so")
                .redirectErrorStream(true)
                .start()
            val output2 = pidofProcess2.inputStream.bufferedReader().readText().trim()
            pidofProcess2.waitFor(2, TimeUnit.SECONDS)
            output2.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun start(useExternalStorage: Boolean, torArgs: List<String> = emptyList()): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isRunning) {
                Log.d(TAG, "Node is already running")
                return@withContext Result.success(Unit)
            }

            // Get binary path (prefers native lib directory)
            val binaryPath = binaryManager.getBinaryPath()
            val binaryFile = File(binaryPath)

            if (!binaryFile.exists()) {
                return@withContext Result.failure(Exception("Monerod binary not found at: $binaryPath"))
            }

            Log.d(TAG, "Using binary at: $binaryPath")

            // Prepare data directory
            val dataDir = storageManager.getMoneroDataDir(useExternalStorage)
            dataDir.mkdirs()

            // Generate and write config file
            val config = configManager.getNodeConfig()
            val configFile = storageManager.getConfigFilePath()
            configManager.writeConfigFile(configFile, dataDir.absolutePath, config)

            // Initialize RPC credentials IMMEDIATELY after writing config to ensure they match
            ensureRpcCredentials(config)

            Log.d(TAG, "Starting monerod with config: ${configFile.absolutePath}")
            Log.d(TAG, "Data directory: ${dataDir.absolutePath}")

            // Build command - don't use --detach, run in foreground and manage process ourselves
            val command = mutableListOf(
                binaryPath,
                "--config-file", configFile.absolutePath,
                "--non-interactive"
            )

            // Add Tor arguments if provided
            if (torArgs.isNotEmpty()) {
                command.addAll(torArgs)
                Log.d(TAG, "Added Tor arguments: ${torArgs.joinToString(" ")}")
            }

            // Add custom flags if any (use first() instead of collect to avoid blocking)
            try {
                val customFlags = configManager.customFlags.first()
                if (customFlags.isNotEmpty()) {
                    command.addAll(customFlags.split(" ").filter { it.isNotBlank() })
                }
            } catch (e: Exception) {
                Log.d(TAG, "No custom flags: ${e.message}")
            }

            Log.d(TAG, "Command: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command)
                .directory(dataDir)
                .redirectErrorStream(true)

            // Set environment variables
            processBuilder.environment()["HOME"] = context.filesDir.absolutePath
            processBuilder.environment()["TMPDIR"] = context.cacheDir.absolutePath

            Log.d(TAG, "Starting process...")

            try {
                process = processBuilder.start()
                Log.d(TAG, "Process started successfully, isAlive: ${process?.isAlive}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start process", e)
                return@withContext Result.failure(e)
            }

            // Start a coroutine to read output (use GlobalScope so it doesn't block withContext)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val reader = process?.inputStream?.bufferedReader()
                    if (reader != null) {
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.d(TAG, "monerod: $line")
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Output stream closed: ${e.message}")
                }
            }

            // Wait a moment and check if process started
            delay(3000)

            Log.d(TAG, "After 3s, process isAlive: ${process?.isAlive}")

            // Check if daemon is running via RPC
            var attempts = 0
            while (attempts < 15) {
                delay(2000)

                // Check if process died
                if (process?.isAlive == false) {
                    val exitCode = process?.exitValue()
                    Log.e(TAG, "Process died with exit code: $exitCode")
                    return@withContext Result.failure(Exception("Monerod exited with code: $exitCode"))
                }

                ensureRpcCredentials()
                if (rpcClient.isNodeRunning()) {
                    Log.d(TAG, "Node is responding to RPC")
                    Log.d(TAG, "Returning success from MonerodProcess.start()")
                    return@withContext Result.success(Unit)
                }
                attempts++
                Log.d(TAG, "Waiting for node to start... attempt $attempts")
            }

            // Check if process is still alive
            if (process?.isAlive == true) {
                Log.d(TAG, "Process is running but not responding to RPC yet")
                return@withContext Result.success(Unit)
            }

            Result.failure(Exception("Node failed to start"))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start monerod", e)
            Result.failure(e)
        }
    }

    suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Stopping monerod...")

            // Try graceful shutdown via RPC first
            try {
                ensureRpcCredentials()
                rpcClient.stopDaemon()
                Log.d(TAG, "Sent stop command via RPC")
            } catch (e: Exception) {
                Log.d(TAG, "RPC stop failed: ${e.message}")
            }

            // Wait for graceful shutdown (only 3 seconds - monerod during sync rarely stops gracefully)
            var attempts = 0
            while (attempts < 3) {
                delay(1000)
                attempts++

                val processAlive = try {
                    process?.isAlive == true
                } catch (e: Exception) {
                    false
                }
                val processRunning = try {
                    isProcessRunningByName()
                } catch (e: Exception) {
                    false
                }

                if (!processAlive && !processRunning) {
                    Log.d(TAG, "Process stopped gracefully after $attempts seconds")
                    process = null
                    return@withContext Result.success(Unit)
                }
                Log.d(TAG, "Waiting for graceful stop... attempt $attempts")
            }

            // Force kill - use destroy() first, then destroyForcibly()
            Log.d(TAG, "Graceful shutdown timed out, force killing...")

            try {
                if (process?.isAlive == true) {
                    Log.d(TAG, "Force stopping process via destroy()")
                    process?.destroy()
                    delay(2000)

                    if (process?.isAlive == true) {
                        Log.d(TAG, "Using destroyForcibly()")
                        process?.destroyForcibly()
                        delay(1000)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying process: ${e.message}")
            }

            process = null

            Log.d(TAG, "Monerod force stopped")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop monerod", e)
            process = null
            Result.failure(e)
        }
    }

    fun getLogOutput(): String {
        // Since we're logging to /dev/null, provide status via RPC instead
        return "Logs disabled. Use RPC status for node information."
    }
}
