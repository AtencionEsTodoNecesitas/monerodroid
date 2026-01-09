package com.sevendeuce.monerodroid.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import info.guardianproject.netcipher.proxy.OrbotHelper
import info.guardianproject.netcipher.proxy.StatusCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Orbot/Tor integration for routing monerod traffic through Tor
 * and creating hidden services for remote access.
 */
class OrbotManager(private val context: Context) {

    companion object {
        private const val TAG = "OrbotManager"
        const val DEFAULT_SOCKS_PORT = 9050
        const val MONEROD_RPC_PORT = 18081
        const val HIDDEN_SERVICE_REQUEST_CODE = 9999
    }

    data class OrbotState(
        val isInstalled: Boolean = false,
        val isRunning: Boolean = false,
        val isConnecting: Boolean = false,
        val socksHost: String = "127.0.0.1",
        val socksPort: Int = DEFAULT_SOCKS_PORT,
        val onionAddress: String? = null,
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(OrbotState())
    val state: StateFlow<OrbotState> = _state.asStateFlow()

    private var orbotHelper: OrbotHelper? = null
    private var statusCallback: StatusCallback? = null

    /**
     * Initialize OrbotManager and check if Orbot is installed
     */
    fun init(): Boolean {
        orbotHelper = OrbotHelper.get(context)
        val isInstalled = orbotHelper?.init() ?: false

        _state.value = _state.value.copy(isInstalled = isInstalled)

        if (isInstalled) {
            Log.d(TAG, "Orbot is installed")
            setupStatusCallback()
        } else {
            Log.d(TAG, "Orbot is not installed")
        }

        return isInstalled
    }

    private fun setupStatusCallback() {
        statusCallback = object : StatusCallback {
            override fun onEnabled(statusIntent: Intent?) {
                Log.d(TAG, "Orbot is enabled and connected to Tor")

                // Orbot always uses 127.0.0.1:9050 for SOCKS proxy
                val socksHost = "127.0.0.1"
                val socksPort = DEFAULT_SOCKS_PORT

                _state.value = _state.value.copy(
                    isRunning = true,
                    isConnecting = false,
                    socksHost = socksHost,
                    socksPort = socksPort,
                    errorMessage = null
                )
            }

            override fun onStarting() {
                Log.d(TAG, "Orbot is starting...")
                _state.value = _state.value.copy(
                    isConnecting = true,
                    errorMessage = null
                )
            }

            override fun onStopping() {
                Log.d(TAG, "Orbot is stopping...")
                _state.value = _state.value.copy(
                    isConnecting = false
                )
            }

            override fun onDisabled() {
                Log.d(TAG, "Orbot is disabled")
                _state.value = _state.value.copy(
                    isRunning = false,
                    isConnecting = false
                )
            }

            override fun onStatusTimeout() {
                Log.w(TAG, "Orbot status timeout")
                _state.value = _state.value.copy(
                    isConnecting = false,
                    errorMessage = "Failed to connect to Orbot (timeout)"
                )
            }

            override fun onNotYetInstalled() {
                Log.d(TAG, "Orbot not yet installed")
                _state.value = _state.value.copy(
                    isInstalled = false,
                    isRunning = false,
                    isConnecting = false
                )
            }
        }

        orbotHelper?.addStatusCallback(statusCallback)
    }

    /**
     * Check if Orbot is installed on the device
     */
    fun isOrbotInstalled(): Boolean {
        // Try NetCipher's check first
        if (OrbotHelper.isOrbotInstalled(context)) {
            return true
        }

        // Fallback: manually check for Orbot package
        return try {
            context.packageManager.getPackageInfo("org.torproject.android", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Request Orbot to start Tor in the background
     */
    fun requestStartTor() {
        if (!isOrbotInstalled()) {
            _state.value = _state.value.copy(
                errorMessage = "Orbot is not installed"
            )
            return
        }

        Log.d(TAG, "Requesting Orbot to start Tor")
        _state.value = _state.value.copy(isConnecting = true)
        OrbotHelper.requestStartTor(context)
    }

    /**
     * Show Orbot UI to the user
     */
    fun showOrbotStart(activity: Activity) {
        OrbotHelper.requestShowOrbotStart(activity)
    }

    /**
     * Request a hidden service for the monerod RPC port
     * The .onion address will be returned via onActivityResult
     */
    fun requestHiddenService(activity: Activity) {
        Log.d(TAG, "Requesting hidden service for port $MONEROD_RPC_PORT")

        // Request V3 onion service for monerod RPC
        val intent = Intent("org.torproject.android.REQUEST_V3_ONION_SERVICE")
        intent.putExtra("localPort", MONEROD_RPC_PORT)
        intent.putExtra("onionPort", MONEROD_RPC_PORT)
        intent.putExtra("name", "MoneroDroid")

        try {
            activity.startActivityForResult(intent, HIDDEN_SERVICE_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request hidden service", e)
            _state.value = _state.value.copy(
                errorMessage = "Failed to request hidden service: ${e.message}"
            )
        }
    }

    /**
     * Handle the result from hidden service request
     * Call this from your Activity's onActivityResult
     */
    fun handleHiddenServiceResult(requestCode: Int, resultCode: Int, data: Intent?): String? {
        if (requestCode != HIDDEN_SERVICE_REQUEST_CODE) return null

        if (resultCode == Activity.RESULT_OK && data != null) {
            val onionAddress = data.getStringExtra("hs_host")
            if (onionAddress != null) {
                Log.d(TAG, "Hidden service created: $onionAddress")
                _state.value = _state.value.copy(
                    onionAddress = onionAddress
                )
                return onionAddress
            }
        }

        Log.w(TAG, "Failed to create hidden service")
        _state.value = _state.value.copy(
            errorMessage = "Failed to create hidden service"
        )
        return null
    }

    /**
     * Set the onion address (e.g., loaded from saved preferences)
     */
    fun setOnionAddress(address: String?) {
        _state.value = _state.value.copy(onionAddress = address)
    }

    /**
     * Open Play Store or F-Droid to install Orbot
     */
    fun installOrbot(activity: Activity) {
        orbotHelper?.installOrbot(activity)
    }

    /**
     * Get the SOCKS proxy configuration for monerod
     * Returns null if Tor is not running
     */
    fun getSocksProxy(): Pair<String, Int>? {
        val currentState = _state.value
        return if (currentState.isRunning) {
            Pair(currentState.socksHost, currentState.socksPort)
        } else {
            null
        }
    }

    /**
     * Get monerod command line arguments for Tor mode
     */
    fun getMonerodTorArgs(): List<String> {
        val currentState = _state.value

        if (!currentState.isRunning) {
            return emptyList()
        }

        val args = mutableListOf<String>()

        // Route outbound P2P traffic through Tor SOCKS proxy
        args.add("--proxy=${currentState.socksHost}:${currentState.socksPort}")

        // Bind P2P to localhost only (we're using Tor)
        args.add("--p2p-bind-ip=127.0.0.1")

        // Disable UPnP/IGD (useless with Tor)
        args.add("--no-igd")

        // Don't broadcast our port (we're hidden)
        args.add("--hide-my-port")

        // If we have an onion address, configure anonymous inbound
        currentState.onionAddress?.let { onion ->
            args.add("--anonymous-inbound=$onion:$MONEROD_RPC_PORT,127.0.0.1:$MONEROD_RPC_PORT")
        }

        return args
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        statusCallback?.let { callback ->
            orbotHelper?.removeStatusCallback(callback)
        }
        statusCallback = null
    }
}
