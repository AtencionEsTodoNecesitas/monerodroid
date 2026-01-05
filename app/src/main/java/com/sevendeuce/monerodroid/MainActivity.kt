package com.sevendeuce.monerodroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sevendeuce.monerodroid.ui.screens.MainScreen
import com.sevendeuce.monerodroid.ui.screens.SettingsScreen
import com.sevendeuce.monerodroid.ui.theme.*
import com.sevendeuce.monerodroid.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private var showStoragePermissionPrompt by mutableStateOf(false)
    private var storagePermissionGranted by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if permission was granted after returning from settings
        checkStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestRequiredPermissions()
        checkStoragePermission()

        setContent {
            MonerodroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    MoneroDroidApp(
                        showStoragePrompt = showStoragePermissionPrompt,
                        onRequestStoragePermission = { requestStoragePermission() },
                        onDismissStoragePrompt = { showStoragePermissionPrompt = false }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check storage permission when returning to the app
        checkStoragePermission()
    }

    private fun checkStoragePermission() {
        storagePermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        // Show prompt if permission not granted (on first run or if user hasn't granted yet)
        if (!storagePermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showStoragePermissionPrompt = true
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires special "All Files Access" permission
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                // Fallback to general settings if specific intent fails
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            // Android 10 and below - request standard storage permission
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
        showStoragePermissionPrompt = false
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Foreground service permission for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
fun MoneroDroidApp(
    viewModel: MainViewModel = viewModel(),
    showStoragePrompt: Boolean = false,
    onRequestStoragePermission: () -> Unit = {},
    onDismissStoragePrompt: () -> Unit = {}
) {
    val nodeState by viewModel.nodeState.collectAsState()
    val binaryStatus by viewModel.binaryStatus.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val pruneBlockchain by viewModel.pruneBlockchain.collectAsState()
    val startOnBoot by viewModel.startOnBoot.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val rpcUsername by viewModel.rpcUsername.collectAsState()
    val rpcPassword by viewModel.rpcPassword.collectAsState()

    // Handle back button when in settings
    BackHandler(enabled = showSettings) {
        viewModel.hideSettings()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = showSettings,
            transitionSpec = {
                if (targetState) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "screen_transition"
        ) { isSettings ->
            if (isSettings) {
                SettingsScreen(
                    pruneBlockchain = pruneBlockchain,
                    startOnBoot = startOnBoot,
                    nodeVersion = nodeState.nodeVersion,
                    storageFreeGb = nodeState.storageFreeGb,
                    updateStatus = updateStatus,
                    isNodeRunning = nodeState.isRunning,
                    onPruneToggle = viewModel::setPruneBlockchain,
                    onStartOnBootToggle = viewModel::setStartOnBoot,
                    onCheckForUpdate = viewModel::checkForUpdate,
                    onUpdateMonerod = viewModel::updateMonerod,
                    onResetUpdateStatus = viewModel::resetUpdateStatus,
                    onBack = viewModel::hideSettings
                )
            } else {
                MainScreen(
                    nodeState = nodeState,
                    binaryStatus = binaryStatus,
                    isExternalAvailable = viewModel.isExternalStorageAvailable(),
                    rpcUsername = rpcUsername,
                    rpcPassword = rpcPassword,
                    onStorageToggle = viewModel::setUseExternalStorage,
                    onStartStopToggle = {
                        if (nodeState.isRunning) {
                            viewModel.stopNode()
                        } else {
                            viewModel.startNode()
                        }
                    },
                    onDownloadBinary = viewModel::downloadBinary,
                    onSettingsClick = viewModel::toggleSettings
                )
            }
        }

        // Storage Permission Dialog
        if (showStoragePrompt) {
            StoragePermissionDialog(
                onGrantPermission = onRequestStoragePermission,
                onDismiss = onDismissStoragePrompt
            )
        }
    }
}

@Composable
fun StoragePermissionDialog(
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = {
            Text(
                text = "Storage Permission Required",
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "MoneroDroid needs access to storage to:",
                    color = TextWhite,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "• Store the Monero blockchain data\n• Use external SD card for storage\n• Manage node configuration files",
                    color = TextGray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "The blockchain requires 50-300GB of storage depending on node type.",
                    color = MoneroOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGrantPermission,
                colors = ButtonDefaults.buttonColors(containerColor = MoneroOrange)
            ) {
                Text("Grant Permission", color = TextWhite)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = TextGray)
            }
        }
    )
}
