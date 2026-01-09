package com.sevendeuce.monerodroid.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sevendeuce.monerodroid.data.NodeState
import com.sevendeuce.monerodroid.ui.theme.*
import com.sevendeuce.monerodroid.util.BinaryStatus

@Composable
fun MainScreen(
    nodeState: NodeState,
    binaryStatus: BinaryStatus,
    isExternalAvailable: Boolean,
    rpcUsername: String = "",
    rpcPassword: String = "",
    torEnabled: Boolean = false,
    onionAddress: String = "",
    onStorageToggle: (Boolean) -> Unit,
    onStartStopToggle: () -> Unit,
    onDownloadBinary: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showStorageDialog by remember { mutableStateOf(false) }
    var pendingStorageChoice by remember { mutableStateOf(false) }

    // Calculate uptime
    val uptime = if (nodeState.isRunning && nodeState.startTime > 0) {
        val seconds = (System.currentTimeMillis() / 1000) - nodeState.startTime
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    } else "--"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 40.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Node Status + Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Node status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    nodeState.isInitializing -> MoneroOrange.copy(alpha = 0.5f)
                                    nodeState.isRunning -> Color(0xFF4CAF50) // Green
                                    else -> TextGray
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            nodeState.isInitializing -> "STARTING..."
                            nodeState.isStopping -> "STOPPING..."
                            nodeState.isRunning -> "RUNNING"
                            else -> "STOPPED"
                        },
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Settings
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MoneroOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Binary Status / Download Section
            if (binaryStatus !is BinaryStatus.Installed) {
                BinaryStatusCard(
                    status = binaryStatus,
                    onDownload = onDownloadBinary
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Sync Progress Bar (prominent, full width)
            if (nodeState.isRunning || nodeState.syncProgress > 0) {
                SyncProgressCard(
                    syncProgress = nodeState.syncProgress,
                    currentHeight = nodeState.currentHeight,
                    targetHeight = nodeState.targetHeight,
                    isRunning = nodeState.isRunning
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Stats Grid - 2x2 layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "PEERS",
                    value = if (nodeState.isRunning) "${nodeState.numberOfPeers}" else "--",
                    subValue = if (nodeState.isRunning) "↑${nodeState.outgoingConnections} ↓${nodeState.incomingConnections}" else null
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "HEIGHT",
                    value = if (nodeState.currentHeight > 0) formatNumber(nodeState.currentHeight) else "--",
                    subValue = null
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "UPTIME",
                    value = uptime,
                    subValue = null
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "RPC",
                    value = if (nodeState.isRunning) "${nodeState.rpcConnections}" else "--",
                    subValue = if (nodeState.localIpAddress.isNotEmpty()) ":18081" else null
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Network Info Card
            NetworkInfoCard(
                localIpAddress = nodeState.localIpAddress,
                nodeVersion = nodeState.nodeVersion,
                isRunning = nodeState.isRunning,
                rpcUsername = rpcUsername,
                rpcPassword = rpcPassword,
                torEnabled = torEnabled,
                onionAddress = onionAddress
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Storage Section (more compact)
            StorageCard(
                useExternalStorage = nodeState.useExternalStorage,
                storageUsedPercent = nodeState.storageUsedPercent,
                storageFreeGb = nodeState.storageFreeGb,
                isExternalAvailable = isExternalAvailable,
                isNodeRunning = nodeState.isRunning,
                onStorageToggle = { newValue ->
                    if (newValue == nodeState.useExternalStorage) return@StorageCard
                    if (newValue && !isExternalAvailable) return@StorageCard
                    if (showStorageDialog) return@StorageCard
                    pendingStorageChoice = newValue
                    showStorageDialog = true
                }
            )

            // Warnings (compact, only if needed)
            if (nodeState.isNodeOutOfDate || !nodeState.hasAdequateStorage || nodeState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                WarningsCard(
                    isNodeOutOfDate = nodeState.isNodeOutOfDate,
                    hasAdequateStorage = nodeState.hasAdequateStorage,
                    errorMessage = nodeState.errorMessage
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start/Stop Button at the bottom
            StartStopButton(
                isRunning = nodeState.isRunning,
                isInitializing = nodeState.isInitializing,
                isStopping = nodeState.isStopping,
                isBinaryInstalled = binaryStatus is BinaryStatus.Installed,
                onToggle = onStartStopToggle
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Storage change confirmation dialog
        if (showStorageDialog) {
            AlertDialog(
                onDismissRequest = { showStorageDialog = false },
                title = {
                    Text(
                        text = "Change Storage Location",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = if (pendingStorageChoice) {
                            "Switching to external storage will use a separate blockchain data directory. Any existing sync on internal storage will be preserved for later use.\n\nDo you want to continue?"
                        } else {
                            "Switching to internal storage will use a separate blockchain data directory. Any existing sync on external storage will be preserved for later use.\n\nDo you want to continue?"
                        },
                        color = TextGray
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showStorageDialog = false
                            onStorageToggle(pendingStorageChoice)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MoneroOrange)
                    ) {
                        Text("Switch", color = TextWhite)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStorageDialog = false }) {
                        Text("Cancel", color = TextGray)
                    }
                },
                containerColor = CardBackground
            )
        }

        // Stopping overlay
        if (nodeState.isStopping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MoneroOrange,
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "STOPPING NODE...",
                        color = MoneroOrange,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please wait while the node shuts down safely",
                        color = TextGray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// Helper function to format large numbers
private fun formatNumber(num: Long): String {
    return when {
        num >= 1_000_000 -> String.format("%.2fM", num / 1_000_000.0)
        num >= 1_000 -> String.format("%.1fK", num / 1_000.0)
        else -> num.toString()
    }
}

@Composable
fun CompactPowerButton(
    isRunning: Boolean,
    isInitializing: Boolean,
    isStopping: Boolean,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    val canClick = isEnabled && !isInitializing && !isStopping

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                when {
                    isRunning -> MoneroOrange
                    else -> CardBackgroundLight
                }
            )
            .then(
                if (canClick) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggle() }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isInitializing) {
            CircularProgressIndicator(
                color = MoneroOrange,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            // Power icon
            Canvas(modifier = Modifier.size(20.dp)) {
                val color = if (isRunning) TextWhite else TextGray
                // Vertical line at top
                drawLine(
                    color = color,
                    start = Offset(size.width / 2, 2.dp.toPx()),
                    end = Offset(size.width / 2, 8.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
                // Arc
                drawArc(
                    color = color,
                    startAngle = -60f,
                    sweepAngle = 300f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx()),
                    topLeft = Offset(3.dp.toPx(), 4.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(14.dp.toPx(), 14.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun SyncProgressCard(
    syncProgress: Float,
    currentHeight: Long,
    targetHeight: Long,
    isRunning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYNC PROGRESS",
                    color = TextGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (syncProgress >= 99.9f) "SYNCED" else "${String.format("%.1f", syncProgress)}%",
                    color = if (syncProgress >= 99.9f) Color(0xFF4CAF50) else MoneroOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ProgressBackground)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(syncProgress / 100f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (syncProgress >= 99.9f) Color(0xFF4CAF50) else MoneroOrange)
                )
            }
            if (targetHeight > 0 && currentHeight > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$currentHeight / $targetHeight blocks",
                    color = TextGray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subValue: String?
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = TextGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = TextWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            if (subValue != null) {
                Text(
                    text = subValue,
                    color = MoneroOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun NetworkInfoCard(
    localIpAddress: String,
    nodeVersion: String,
    isRunning: Boolean,
    rpcUsername: String = "",
    rpcPassword: String = "",
    torEnabled: Boolean = false,
    onionAddress: String = ""
) {
    var showSensitiveInfo by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var copiedField by remember { mutableStateOf<String?>(null) }

    // Reset copied indicator after delay
    LaunchedEffect(copiedField) {
        if (copiedField != null) {
            kotlinx.coroutines.delay(1500)
            copiedField = null
        }
    }

    fun copyToClipboard(text: String, field: String) {
        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
        copiedField = field
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header with SHOW/HIDE toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONNECTION INFO",
                    color = TextGray,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (showSensitiveInfo) "HIDE" else "SHOW",
                    color = MoneroOrange,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { showSensitiveInfo = !showSensitiveInfo }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = if (showSensitiveInfo && localIpAddress.isNotEmpty()) {
                        Modifier.clickable {
                            val ip = if (isRunning) "$localIpAddress:18081" else localIpAddress
                            copyToClipboard(ip, "ip")
                        }
                    } else Modifier
                ) {
                    Text(
                        text = if (copiedField == "ip") "COPIED!" else "LOCAL IP",
                        color = if (copiedField == "ip") MoneroOrange else TextGray,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (showSensitiveInfo) {
                            if (localIpAddress.isNotEmpty()) {
                                if (isRunning) "$localIpAddress:18081" else localIpAddress
                            } else "--"
                        } else {
                            "***.***.***"
                        },
                        color = if (isRunning && showSensitiveInfo) MoneroOrange else TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "VERSION",
                        color = TextGray,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = nodeVersion.ifEmpty { "--" },
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // RPC Credentials section
            if (rpcUsername.isNotEmpty() && rpcPassword.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = TextGray.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "RPC LOGIN (tap to copy)",
                    color = TextGray,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (showSensitiveInfo) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.clickable { copyToClipboard(rpcUsername, "user") }
                        ) {
                            Text(
                                text = if (copiedField == "user") "COPIED!" else "USER",
                                color = if (copiedField == "user") MoneroOrange else TextGray,
                                fontSize = 9.sp
                            )
                            Text(
                                text = rpcUsername,
                                color = MoneroOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.clickable { copyToClipboard(rpcPassword, "pass") }
                        ) {
                            Text(
                                text = if (copiedField == "pass") "COPIED!" else "PASS",
                                color = if (copiedField == "pass") MoneroOrange else TextGray,
                                fontSize = 9.sp
                            )
                            Text(
                                text = rpcPassword,
                                color = MoneroOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Tap SHOW to reveal credentials",
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }
            }

            // Tor/.onion section
            if (torEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = TextGray.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TOR STATUS",
                        color = TextGray,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) Color(0xFF9C27B0) else TextGray) // Purple for Tor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "ENABLED" else "PENDING",
                            color = if (isRunning) Color(0xFF9C27B0) else TextGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (onionAddress.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = if (showSensitiveInfo) {
                            Modifier.clickable {
                                val fullAddress = "$onionAddress:18081"
                                copyToClipboard(fullAddress, "onion")
                            }
                        } else Modifier
                    ) {
                        Text(
                            text = if (copiedField == "onion") "COPIED!" else ".ONION ADDRESS (tap to copy)",
                            color = if (copiedField == "onion") MoneroOrange else TextGray,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (showSensitiveInfo) {
                                "$onionAddress:18081"
                            } else {
                                "xxxxxxxx...xxxx.onion:18081"
                            },
                            color = Color(0xFF9C27B0), // Purple for Tor
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configure .onion address in Settings",
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StorageCard(
    useExternalStorage: Boolean,
    storageUsedPercent: Int,
    storageFreeGb: Float,
    isExternalAvailable: Boolean,
    isNodeRunning: Boolean,
    onStorageToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STORAGE",
                        color = TextGray,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (useExternalStorage) "EXTERNAL" else "INTERNAL",
                            color = MoneroOrange,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${String.format("%.1f", storageFreeGb)} GB free",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }
                }
                StorageToggle(
                    isExternal = useExternalStorage,
                    isEnabled = !isNodeRunning,
                    canSwitchToExternal = isExternalAvailable,
                    onToggle = onStorageToggle
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ProgressBackground)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(storageUsedPercent / 100f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                storageUsedPercent > 90 -> ErrorRed
                                storageUsedPercent > 75 -> MoneroOrange
                                else -> Color(0xFF4CAF50)
                            }
                        )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$storageUsedPercent% used",
                color = TextGray,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun BinaryStatusCard(
    status: BinaryStatus,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (status) {
                is BinaryStatus.NotInstalled -> {
                    Text(
                        text = "MONEROD NOT INSTALLED",
                        color = MoneroOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Download the Monero daemon to run a node",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = MoneroOrange)
                    ) {
                        Text("Download Monerod", color = TextWhite)
                    }
                }

                is BinaryStatus.Downloading -> {
                    Text(
                        text = "DOWNLOADING...",
                        color = MoneroOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        color = MoneroOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }

                is BinaryStatus.InstallingBundled -> {
                    Text(
                        text = "INSTALLING BUNDLED BINARY...",
                        color = MoneroOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        color = MoneroOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }

                is BinaryStatus.DownloadProgress -> {
                    Text(
                        text = "DOWNLOADING: ${status.progress}%",
                        color = MoneroOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { status.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MoneroOrange,
                        trackColor = ProgressBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${String.format("%.1f", status.downloadedMb)} / ${
                            String.format(
                                "%.1f",
                                status.totalMb
                            )
                        } MB",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }

                is BinaryStatus.Extracting -> {
                    Text(
                        text = "EXTRACTING...",
                        color = MoneroOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        color = MoneroOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }

                is BinaryStatus.Error -> {
                    Text(
                        text = "DOWNLOAD ERROR",
                        color = ErrorRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = status.message,
                        color = TextGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = MoneroOrange)
                    ) {
                        Text("Retry Download", color = TextWhite)
                    }
                }

                is BinaryStatus.Installed -> {
                    // This won't be shown, handled in parent
                }

                is BinaryStatus.Updating -> {
                    Text(
                        text = "UPDATING...",
                        color = MoneroOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        color = MoneroOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StorageSection(
    useExternalStorage: Boolean,
    storageUsedPercent: Int,
    isExternalAvailable: Boolean,
    isNodeRunning: Boolean = false,
    onStorageToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Storage Toggle with labels
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "STORAGE",
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // INTERNAL label
                Text(
                    text = "INTERNAL",
                    color = if (!useExternalStorage) MoneroOrange else TextGray,
                    fontSize = 11.sp,
                    fontWeight = if (!useExternalStorage) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                StorageToggle(
                    isExternal = useExternalStorage,
                    isEnabled = !isNodeRunning,
                    canSwitchToExternal = isExternalAvailable,
                    onToggle = onStorageToggle
                )
                Spacer(modifier = Modifier.width(8.dp))
                // EXTERNAL label
                Text(
                    text = "EXTERNAL",
                    color = when {
                        !isExternalAvailable -> TextGray.copy(alpha = 0.5f)
                        useExternalStorage -> MoneroOrange
                        else -> TextGray
                    },
                    fontSize = 11.sp,
                    fontWeight = if (useExternalStorage && isExternalAvailable) FontWeight.Bold else FontWeight.Medium
                )
            }
            if (!isExternalAvailable) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No SD card detected",
                    color = TextGray.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
            if (isNodeRunning) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Stop node to change storage",
                    color = TextGray.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }

        // Storage Capacity
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CAPACITY",
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            StorageProgressBar(
                usedPercent = storageUsedPercent
            )
        }
    }
}

@Composable
fun StorageToggle(
    isExternal: Boolean,
    isEnabled: Boolean,
    canSwitchToExternal: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    val toggleWidth = 56.dp
    val toggleHeight = 28.dp
    val thumbSize = 24.dp

    // State for bounce-back animation
    var bounceToExternal by remember { mutableStateOf(false) }

    // Animate with spring for bounce effect when bouncing back
    val thumbOffset by animateFloatAsState(
        targetValue = when {
            bounceToExternal -> 1f
            isExternal -> 1f
            else -> 0f
        },
        animationSpec = if (bounceToExternal) {
            tween(150)
        } else {
            spring(
                dampingRatio = 0.6f,
                stiffness = 400f
            )
        },
        finishedListener = {
            if (bounceToExternal) {
                bounceToExternal = false
            }
        },
        label = "thumb"
    )

    Box(
        modifier = Modifier
            .width(toggleWidth)
            .height(toggleHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    !isEnabled -> CardBackgroundLight.copy(alpha = 0.5f)
                    bounceToExternal -> MoneroOrange.copy(alpha = 0.5f)
                    isExternal -> MoneroOrange
                    else -> CardBackgroundLight
                }
            )
            .then(
                if (isEnabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val newValue = !isExternal
                        // If trying to switch to external but not available, do bounce animation
                        if (newValue && !canSwitchToExternal) {
                            bounceToExternal = true
                            return@clickable
                        }
                        onToggle(newValue)
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .offset(x = (thumbOffset * (toggleWidth - thumbSize - 4.dp).value).dp)
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (isEnabled) TextWhite else TextGray)
        )
    }
}

@Composable
fun StorageProgressBar(
    usedPercent: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ProgressBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(usedPercent / 100f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MoneroOrange)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MoneroOrange)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$usedPercent% USED",
                color = TextWhite,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PeerSyncStatusCard(
    numberOfPeers: Int,
    syncProgress: Float,
    currentHeight: Long,
    targetHeight: Long,
    isRunning: Boolean,
    nodeVersion: String,
    localIpAddress: String = ""
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "PEER & SYNC STATUS",
                color = MoneroOrange,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isRunning) {
                Row {
                    Text(
                        text = "NUMBER OF PEERS: ",
                        color = TextWhite,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "$numberOfPeers",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                    Text(
                        text = "SYNC STATUS: ",
                        color = TextWhite,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${
                            String.format(
                                "%.1f",
                                syncProgress
                            )
                        }% ${if (syncProgress >= 99.9f) "(Fully Synced)" else "(Syncing...)"}",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (currentHeight > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text(
                            text = "HEIGHT: ",
                            color = TextWhite,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (targetHeight > 0) "$currentHeight / $targetHeight" else "$currentHeight",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (nodeVersion.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text(
                            text = "VERSION: ",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                        Text(
                            text = nodeVersion,
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }
                }

                if (localIpAddress.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text(
                            text = "LOCAL IP: ",
                            color = TextWhite,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "$localIpAddress:18081",
                            color = MoneroOrange,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                if (localIpAddress.isNotEmpty()) {
                    Row {
                        Text(
                            text = "LOCAL IP: ",
                            color = TextGray,
                            fontSize = 14.sp
                        )
                        Text(
                            text = localIpAddress,
                            color = TextGray,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "Node is not running",
                    color = TextGray,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun WarningsCard(
    isNodeOutOfDate: Boolean,
    hasAdequateStorage: Boolean,
    errorMessage: String?
) {
    if (!isNodeOutOfDate && hasAdequateStorage && errorMessage == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            if (errorMessage != null) {
                WarningItem(text = errorMessage, isError = true)
                if (isNodeOutOfDate || !hasAdequateStorage) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            if (isNodeOutOfDate) {
                WarningItem(text = "Node Out of Date")
                if (!hasAdequateStorage) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            if (!hasAdequateStorage) {
                WarningItem(text = "Not Adequate Storage")
            }
        }
    }
}

@Composable
fun WarningItem(text: String, isError: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        WarningIcon(isError = isError)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = if (isError) ErrorRed else MoneroOrange,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun WarningIcon(isError: Boolean = false) {
    val color = if (isError) ErrorRed else MoneroOrange

    Canvas(
        modifier = Modifier.size(24.dp)
    ) {
        val path = Path().apply {
            moveTo(size.width / 2, 2.dp.toPx())
            lineTo(size.width - 2.dp.toPx(), size.height - 2.dp.toPx())
            lineTo(2.dp.toPx(), size.height - 2.dp.toPx())
            close()
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))

        // Exclamation mark
        drawLine(
            color = color,
            start = Offset(size.width / 2, 8.dp.toPx()),
            end = Offset(size.width / 2, 14.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )
        drawCircle(
            color = color,
            radius = 1.5.dp.toPx(),
            center = Offset(size.width / 2, 18.dp.toPx())
        )
    }
}

@Composable
fun StartStopButton(
    isRunning: Boolean,
    isInitializing: Boolean,
    isStopping: Boolean = false,
    isBinaryInstalled: Boolean,
    onToggle: () -> Unit
) {
    val isEnabled = isBinaryInstalled && !isInitializing && !isStopping

    // Infinite rotation animation for the flowing effect
    val infiniteTransition = rememberInfiniteTransition(label = "flowAnimation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulsing glow effect when running
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Particle positions (8 particles flowing around)
    val particleOffsets = remember {
        listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
    }

    Box(
        modifier = Modifier
            .size(140.dp)
            .then(
                if (isEnabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggle() }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow when running
        if (isRunning && !isStopping) {
            Canvas(modifier = Modifier.size(140.dp)) {
                drawCircle(
                    color = MoneroOrange.copy(alpha = glowAlpha * 0.3f),
                    radius = size.minDimension / 2
                )
            }
        }

        // Flowing particles around the button when running
        if (isRunning && !isStopping) {
            Canvas(modifier = Modifier.size(130.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 8.dp.toPx()

                particleOffsets.forEachIndexed { index, baseAngle ->
                    val angle = Math.toRadians((baseAngle + rotation).toDouble())
                    val particleX = center.x + radius * kotlin.math.cos(angle).toFloat()
                    val particleY = center.y + radius * kotlin.math.sin(angle).toFloat()

                    // Varying particle sizes and alphas for depth
                    val particleSize = (3.dp.toPx() + (index % 3) * 1.dp.toPx())
                    val alpha = 0.5f + (index % 2) * 0.3f

                    drawCircle(
                        color = MoneroOrange.copy(alpha = alpha),
                        radius = particleSize,
                        center = Offset(particleX, particleY)
                    )

                    // Trail effect
                    val trailAngle = Math.toRadians((baseAngle + rotation - 15).toDouble())
                    val trailX = center.x + radius * kotlin.math.cos(trailAngle).toFloat()
                    val trailY = center.y + radius * kotlin.math.sin(trailAngle).toFloat()
                    drawCircle(
                        color = MoneroOrange.copy(alpha = alpha * 0.4f),
                        radius = particleSize * 0.6f,
                        center = Offset(trailX, trailY)
                    )
                }
            }
        }

        // Main button circle
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isRunning -> CardBackground
                        else -> CardBackground
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Outer ring
            Canvas(modifier = Modifier.size(100.dp)) {
                val ringColor = when {
                    !isEnabled -> TextGray.copy(alpha = 0.3f)
                    isRunning -> MoneroOrange
                    else -> TextGray.copy(alpha = 0.6f)
                }
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension / 2 - 4.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            if (isInitializing) {
                CircularProgressIndicator(
                    color = MoneroOrange,
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp
                )
            } else {
                // Power icon
                Canvas(modifier = Modifier.size(40.dp)) {
                    val iconColor = when {
                        !isEnabled -> TextGray.copy(alpha = 0.3f)
                        isRunning -> MoneroOrange
                        else -> TextGray
                    }

                    // Vertical line at top of power icon
                    drawLine(
                        color = iconColor,
                        start = Offset(size.width / 2, 4.dp.toPx()),
                        end = Offset(size.width / 2, 16.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    // Arc (broken circle)
                    drawArc(
                        color = iconColor,
                        startAngle = -60f,
                        sweepAngle = 300f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        topLeft = Offset(6.dp.toPx(), 8.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(28.dp.toPx(), 28.dp.toPx())
                    )
                }
            }
        }
    }
}
