package com.sevendeuce.monerodroid.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sevendeuce.monerodroid.util.ConfigManager
import com.sevendeuce.monerodroid.util.MonerodBinaryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {

            Log.d(TAG, "Boot completed received")

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val configManager = ConfigManager(context)
                    val binaryManager = MonerodBinaryManager(context)

                    val startOnBoot = configManager.startOnBoot.first()
                    val binaryInstalled = binaryManager.isBinaryInstalled()

                    Log.d(TAG, "Start on boot: $startOnBoot, Binary installed: $binaryInstalled")

                    if (startOnBoot && binaryInstalled) {
                        Log.d(TAG, "Starting NodeService on boot")
                        NodeService.startService(context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in boot receiver", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
