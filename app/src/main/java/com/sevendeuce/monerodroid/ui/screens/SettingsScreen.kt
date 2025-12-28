package com.sevendeuce.monerodroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sevendeuce.monerodroid.ui.theme.*
import com.sevendeuce.monerodroid.util.ArchitectureDetector
import com.sevendeuce.monerodroid.util.UpdateStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    pruneBlockchain: Boolean,
    startOnBoot: Boolean,
    nodeVersion: String,
    storageFreeGb: Float,
    updateStatus: UpdateStatus,
    isNodeRunning: Boolean,
    onPruneToggle: (Boolean) -> Unit,
    onStartOnBootToggle: (Boolean) -> Unit,
    onCheckForUpdate: () -> Unit,
    onUpdateMonerod: () -> Unit,
    onResetUpdateStatus: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MoneroOrange
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBackground
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Node Configuration Section
            SettingsSection(title = "NODE CONFIGURATION") {
                SettingsSwitch(
                    title = "Pruned Node",
                    description = "Run a pruned node to save storage (requires ~50GB). Full node requires ~300GB.",
                    checked = pruneBlockchain,
                    onCheckedChange = onPruneToggle
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSwitch(
                    title = "Start on Boot",
                    description = "Automatically start the Monero node when device boots",
                    checked = startOnBoot,
                    onCheckedChange = onStartOnBootToggle
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Device Info Section
            SettingsSection(title = "DEVICE INFO") {
                SettingsInfo(
                    title = "CPU Architecture",
                    value = ArchitectureDetector.getArchitectureName()
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsInfo(
                    title = "Available Storage",
                    value = "${String.format("%.1f", storageFreeGb)} GB"
                )

                if (nodeVersion.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsInfo(
                        title = "Monerod Version",
                        value = nodeVersion
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Update Section
            SettingsSection(title = "MONEROD UPDATE") {
                UpdateSection(
                    updateStatus = updateStatus,
                    isNodeRunning = isNodeRunning,
                    nodeVersion = nodeVersion,
                    onCheckForUpdate = onCheckForUpdate,
                    onUpdateMonerod = onUpdateMonerod,
                    onResetUpdateStatus = onResetUpdateStatus
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Network Ports Section
            SettingsSection(title = "NETWORK PORTS") {
                SettingsInfo(
                    title = "P2P Port",
                    value = "18080"
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsInfo(
                    title = "RPC Port (Local)",
                    value = "18081"
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsInfo(
                    title = "RPC Port (Restricted)",
                    value = "18089"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About Section
            SettingsSection(title = "ABOUT") {
                SettingsInfo(
                    title = "MoneroDroid",
                    value = "v1.0.0"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "A simple Android GUI for running a Monero full node on your phone.",
                    color = TextGray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Open source - MIT License",
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = MoneroOrange,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = TextGray,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextWhite,
                checkedTrackColor = MoneroOrange,
                uncheckedThumbColor = TextGray,
                uncheckedTrackColor = CardBackgroundLight
            )
        )
    }
}

@Composable
fun SettingsInfo(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = TextGray,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun UpdateSection(
    updateStatus: UpdateStatus,
    isNodeRunning: Boolean,
    nodeVersion: String,
    onCheckForUpdate: () -> Unit,
    onUpdateMonerod: () -> Unit,
    onResetUpdateStatus: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        when (updateStatus) {
            is UpdateStatus.Idle -> {
                Text(
                    text = "Check if a newer version of monerod is available",
                    color = TextGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCheckForUpdate,
                    colors = ButtonDefaults.buttonColors(containerColor = MoneroOrange),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check for Updates", color = TextWhite)
                }
            }

            is UpdateStatus.Checking -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        color = MoneroOrange,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Checking for updates...",
                        color = TextGray,
                        fontSize = 14.sp
                    )
                }
            }

            is UpdateStatus.Available -> {
                Text(
                    text = "Update Available!",
                    color = MoneroOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Current: v${updateStatus.currentVersion}",
                    color = TextGray,
                    fontSize = 12.sp
                )
                Text(
                    text = "Latest: v${updateStatus.latestVersion}",
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (isNodeRunning) {
                    Text(
                        text = "Stop the node before updating",
                        color = ErrorRed,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = onUpdateMonerod,
                    enabled = !isNodeRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MoneroOrange,
                        disabledContainerColor = CardBackgroundLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Update Monerod",
                        color = if (isNodeRunning) TextGray else TextWhite
                    )
                }
            }

            is UpdateStatus.UpToDate -> {
                Text(
                    text = "You're up to date!",
                    color = MoneroOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Current version: v$nodeVersion",
                    color = TextGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onResetUpdateStatus,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check Again", color = MoneroOrange)
                }
            }

            is UpdateStatus.Downloading, is UpdateStatus.Progress -> {
                val progress = if (updateStatus is UpdateStatus.Progress) {
                    updateStatus.progress
                } else 0

                Text(
                    text = "Downloading update...",
                    color = MoneroOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MoneroOrange,
                    trackColor = ProgressBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (updateStatus is UpdateStatus.Progress) {
                    Text(
                        text = "${String.format("%.1f", updateStatus.downloadedMb)} / ${String.format("%.1f", updateStatus.totalMb)} MB",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }
            }

            is UpdateStatus.Extracting -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        color = MoneroOrange,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Extracting update...",
                        color = MoneroOrange,
                        fontSize = 14.sp
                    )
                }
            }

            is UpdateStatus.Success -> {
                Text(
                    text = "Update Successful!",
                    color = MoneroOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Monerod has been updated to the latest version",
                    color = TextGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onResetUpdateStatus,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", color = MoneroOrange)
                }
            }

            is UpdateStatus.Error -> {
                Text(
                    text = "Update Failed",
                    color = ErrorRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = updateStatus.message,
                    color = TextGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onResetUpdateStatus,
                    colors = ButtonDefaults.buttonColors(containerColor = MoneroOrange),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try Again", color = TextWhite)
                }
            }
        }
    }
}
