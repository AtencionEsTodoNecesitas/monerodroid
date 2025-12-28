package com.sevendeuce.monerodroid.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
    onStorageToggle: (Boolean) -> Unit,
    onStartStopToggle: () -> Unit,
    onDownloadBinary: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showStorageDialog by remember { mutableStateOf(false) }
    var pendingStorageChoice by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 24.dp), // Extra top padding for status bar
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Settings Icon - with extra spacing from top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MoneroOrange,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Binary Status / Download Section
            if (binaryStatus !is BinaryStatus.Installed) {
                BinaryStatusCard(
                    status = binaryStatus,
                    onDownload = onDownloadBinary
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Storage Section
            StorageSection(
                useExternalStorage = nodeState.useExternalStorage,
                storageUsedPercent = nodeState.storageUsedPercent,
                isExternalAvailable = isExternalAvailable,
                isNodeRunning = nodeState.isRunning,
                onStorageToggle = { newValue ->
                    // Only proceed if this is actually a change
                    if (newValue == nodeState.useExternalStorage) {
                        return@StorageSection
                    }
                    // If trying to switch to external but it's not available, do nothing
                    if (newValue && !isExternalAvailable) {
                        return@StorageSection
                    }
                    // Don't show dialog if one is already showing
                    if (showStorageDialog) {
                        return@StorageSection
                    }
                    // Show confirmation dialog
                    pendingStorageChoice = newValue
                    showStorageDialog = true
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Peer & Sync Status Card
            PeerSyncStatusCard(
                numberOfPeers = nodeState.numberOfPeers,
                syncProgress = nodeState.syncProgress,
                currentHeight = nodeState.currentHeight,
                targetHeight = nodeState.targetHeight,
                isRunning = nodeState.isRunning,
                nodeVersion = nodeState.nodeVersion
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Warnings Card
            WarningsCard(
                isNodeOutOfDate = nodeState.isNodeOutOfDate,
                hasAdequateStorage = nodeState.hasAdequateStorage,
                errorMessage = nodeState.errorMessage
            )

            Spacer(modifier = Modifier.weight(1f))

            // Start/Stop Button
            StartStopButton(
                isRunning = nodeState.isRunning,
                isInitializing = nodeState.isInitializing,
                isStopping = nodeState.isStopping,
                isBinaryInstalled = binaryStatus is BinaryStatus.Installed,
                onToggle = onStartStopToggle
            )

            Spacer(modifier = Modifier.height(32.dp))
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
                text = "STORAGE CAPACITY",
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
        horizontalAlignment = Alignment.End
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
    nodeVersion: String
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
            } else {
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

    // Colors based on running state
    val activeColor = if (isRunning) MoneroOrange else TextGray
    val ringColor = when {
        !isEnabled -> TextGray.copy(alpha = 0.3f)
        isRunning -> MoneroOrange
        else -> TextGray
    }
    val trackColor = when {
        !isEnabled -> TextGray.copy(alpha = 0.3f)
        isRunning -> MoneroOrange
        else -> CardBackgroundLight
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "STOP",
            color = when {
                !isEnabled -> TextGray.copy(alpha = 0.5f)
                isRunning -> MoneroOrange
                else -> TextGray
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(DarkBackground)
                .then(
                    if (isEnabled) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onToggle()
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            // Outer ring - orange when ON, gray when OFF
            Canvas(modifier = Modifier.size(120.dp)) {
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension / 2 - 4.dp.toPx(),
                    style = Stroke(width = 4.dp.toPx())
                )
            }

            if (isInitializing) {
                CircularProgressIndicator(
                    color = MoneroOrange,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                // Inner toggle track
                val toggleTrackWidth = 70.dp
                val toggleTrackHeight = 36.dp
                val toggleThumbSize = 32.dp

                val thumbOffsetAnim by animateFloatAsState(
                    targetValue = if (isRunning) 1f else 0f,
                    animationSpec = tween(200),
                    label = "startStopThumb"
                )

                Box(
                    modifier = Modifier
                        .width(toggleTrackWidth)
                        .height(toggleTrackHeight)
                        .clip(RoundedCornerShape(18.dp))
                        .background(trackColor),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .offset(x = (thumbOffsetAnim * (toggleTrackWidth - toggleThumbSize - 4.dp).value).dp)
                            .size(toggleThumbSize)
                            .clip(CircleShape)
                            .background(DarkBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        // Arrow icon - matches track color
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val arrowPath = Path().apply {
                                moveTo(4.dp.toPx(), size.height / 2)
                                lineTo(12.dp.toPx(), size.height / 2)
                                moveTo(8.dp.toPx(), 4.dp.toPx())
                                lineTo(12.dp.toPx(), size.height / 2)
                                lineTo(8.dp.toPx(), 12.dp.toPx())
                            }
                            drawPath(
                                arrowPath,
                                trackColor,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "START",
            color = when {
                !isEnabled -> TextGray.copy(alpha = 0.5f)
                !isRunning -> MoneroOrange
                else -> TextGray
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
