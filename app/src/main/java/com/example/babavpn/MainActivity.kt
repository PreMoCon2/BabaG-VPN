package com.example.babavpn

import android.app.Activity
import android.widget.ImageView
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.babavpn.ui.theme.BabaGVPNTheme
import com.example.babavpn.ui.theme.CyberBlack
import com.example.babavpn.ui.theme.CyberBlue
import com.example.babavpn.ui.theme.CyberCyan
import com.example.babavpn.ui.theme.CyberGreen
import com.example.babavpn.ui.theme.CyberMagenta
import com.example.babavpn.ui.theme.CyberPanel
import com.example.babavpn.ui.theme.CyberPanelBorder
import com.example.babavpn.ui.theme.CyberTextMuted
import com.example.babavpn.ui.theme.CyberTextPrimary
import com.example.babavpn.vpn.AppRoutingPreferences
import com.example.babavpn.vpn.BabaVpnController
import com.example.babavpn.vpn.TorConnectionMode
import com.example.babavpn.vpn.BabaVpnService
import com.example.babavpn.vpn.InstalledAppCatalog
import com.example.babavpn.vpn.InstalledAppOption
import com.example.babavpn.vpn.VpnTunnelStage
import com.example.babavpn.vpn.VpnTunnelUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            BabaGVPNTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BabaVpnApp()
                }
            }
        }
    }
}

@Composable
fun BabaVpnApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vpnState by BabaVpnController.uiState.collectAsState()
    var showConnectChooser by remember { mutableStateOf(false) }
    var showAppRoutingDialog by remember { mutableStateOf(false) }
    var selectedConnectionMode by remember { mutableStateOf(TorConnectionMode.Smart) }
    var pendingConnectionMode by remember { mutableStateOf(TorConnectionMode.Smart) }
    var selectedTorApps by remember { mutableStateOf(AppRoutingPreferences.selectedPackages(context)) }
    var installedApps by remember { mutableStateOf<List<InstalledAppOption>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }
    var appLoadError by remember { mutableStateOf<String?>(null) }
    val accentColor by animateColorAsState(
        targetValue = vpnState.stage.accentColor(),
        label = "accentColor"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (
            vpnState.stage == VpnTunnelStage.TorReady ||
            vpnState.stage == VpnTunnelStage.Connected
        ) {
            1.03f
        } else {
            1f
        },
        label = "buttonScale"
    )
    val ringWidth by animateDpAsState(
        targetValue = if (
            vpnState.stage == VpnTunnelStage.TorReady ||
            vpnState.stage == VpnTunnelStage.Connected
        ) {
            5.dp
        } else {
            3.dp
        },
        label = "ringWidth"
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            BabaVpnService.start(context, pendingConnectionMode)
        } else {
            BabaVpnController.onPermissionDenied()
        }
    }

    LaunchedEffect(showAppRoutingDialog) {
        if (!showAppRoutingDialog || isLoadingApps) {
            return@LaunchedEffect
        }

        isLoadingApps = true
        appLoadError = null
        runCatching {
            withContext(Dispatchers.IO) {
                InstalledAppCatalog.loadInstalledApps(context)
            }
        }.onSuccess { apps ->
            installedApps = apps
        }.onFailure { error ->
            appLoadError = error.message ?: "Could not load installed apps."
        }
        isLoadingApps = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF05020B),
                        Color(0xFF14091E),
                        Color(0xFF08040E),
                        CyberBlack
                    )
                )
            )
    ) {
        CyberpunkBackdrop()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val compactWidth = maxWidth < 380.dp
            val compactHeight = maxHeight < 760.dp
            val tinyHeight = maxHeight < 700.dp
            val screenIsDense = compactWidth || compactHeight || tinyHeight
            val stackStatusCards = maxWidth < 360.dp || tinyHeight
            val horizontalPadding = if (compactWidth) 16.dp else 24.dp
            val verticalPadding = if (tinyHeight) 10.dp else if (compactHeight) 12.dp else 18.dp
            val sectionSpacing = if (tinyHeight) 8.dp else if (compactHeight) 10.dp else 18.dp
            val statusLineMaxLines = if (screenIsDense) 2 else 3
            val activityLineMaxLines = if (tinyHeight) 1 else 2
            val activityDetailMaxLines = if (screenIsDense) 2 else 3
            val powerButtonSize = minOf(
                maxWidth * if (compactWidth) 0.66f else 0.62f,
                maxHeight * if (tinyHeight) 0.18f else if (compactHeight) 0.21f else 0.24f
            ).coerceIn(if (tinyHeight) 160.dp else 176.dp, 252.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Header(
                    vpnState = vpnState,
                    accentColor = accentColor,
                    compactLayout = compactWidth || compactHeight
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .align(Alignment.CenterHorizontally),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TorPowerButton(
                        vpnState = vpnState,
                        accentColor = accentColor,
                        ringWidth = ringWidth,
                        scale = buttonScale,
                        buttonSize = powerButtonSize,
                        compactLayout = screenIsDense,
                        onTap = {
                            // First tap asks Android for VPN control; later taps act
                            // as a simple start/stop switch for the running service.
                            when (vpnState.stage) {
                                VpnTunnelStage.Offline,
                                VpnTunnelStage.PermissionDenied,
                                VpnTunnelStage.Error -> {
                                    showConnectChooser = true
                                }

                                VpnTunnelStage.RequestingPermission,
                                VpnTunnelStage.StartingTor -> Unit

                                VpnTunnelStage.TorReady,
                                VpnTunnelStage.Connected -> BabaVpnService.stop(context)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(sectionSpacing))
                    Text(
                        text = vpnState.statusLine,
                        style = if (screenIsDense) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = CyberTextMuted,
                        textAlign = TextAlign.Center,
                        maxLines = statusLineMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .align(Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(if (tinyHeight) 10.dp else 12.dp)
                ) {
                    ConnectionStats(
                        vpnState = vpnState,
                        accentColor = accentColor,
                        stackCards = stackStatusCards,
                        compactLayout = screenIsDense,
                        cardPadding = if (screenIsDense) 10.dp else 16.dp,
                        cardSpacing = if (screenIsDense) 8.dp else 12.dp,
                        activityLineMaxLines = activityLineMaxLines,
                        activityDetailMaxLines = activityDetailMaxLines,
                        modifier = Modifier.fillMaxWidth()
                    )

                    AppRoutingCard(
                        selectedPackages = selectedTorApps,
                        vpnStage = vpnState.stage,
                        compactLayout = screenIsDense,
                        onClick = { showAppRoutingDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (showConnectChooser) {
            ConnectModeDialog(
                selectedMode = selectedConnectionMode,
                onModeSelected = { selectedConnectionMode = it },
                onDismiss = { showConnectChooser = false },
                onConnect = {
                    showConnectChooser = false
                    pendingConnectionMode = selectedConnectionMode
                    val permissionIntent = VpnService.prepare(context)
                    if (permissionIntent != null) {
                        BabaVpnController.onPermissionRequested(selectedConnectionMode)
                        permissionLauncher.launch(permissionIntent)
                    } else {
                        BabaVpnService.start(context, selectedConnectionMode)
                    }
                }
            )
        }

        if (showAppRoutingDialog) {
            AppRoutingDialog(
                apps = installedApps,
                selectedPackages = selectedTorApps,
                isLoading = isLoadingApps,
                loadError = appLoadError,
                onDismiss = { showAppRoutingDialog = false },
                onSave = { selectedPackages ->
                    AppRoutingPreferences.setSelectedPackages(context, selectedPackages)
                    selectedTorApps = selectedPackages
                    showAppRoutingDialog = false

                    if (vpnState.stage == VpnTunnelStage.Connected || vpnState.stage == VpnTunnelStage.TorReady) {
                        Toast.makeText(
                            context,
                            "App routing saved. Disconnect and reconnect to apply it.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BabaVpnAppPreview() {
    BabaGVPNTheme(darkTheme = true, dynamicColor = false) {
        BabaVpnApp()
    }
}

@Composable
private fun Header(
    vpnState: VpnTunnelUiState,
    accentColor: Color,
    compactLayout: Boolean
) {
    val badge: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(50),
            color = accentColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f))
        ) {
            Text(
                text = vpnState.badgeLabel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
        }
    }

    if (compactLayout) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    text = "BABAG VPN",
                    style = MaterialTheme.typography.headlineSmall,
                    color = CyberTextPrimary
                )
                Text(
                    text = "FULL DEVICE TOR STACK",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberBlue
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                badge()
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BABAG VPN",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CyberTextPrimary
                )
                Text(
                    text = "FULL DEVICE TOR STACK",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberBlue
                )
            }
            badge()
        }
    }
}

@Composable
private fun ConnectModeDialog(
    selectedMode: TorConnectionMode,
    onModeSelected: (TorConnectionMode) -> Unit,
    onDismiss: () -> Unit,
    onConnect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onConnect) {
                Text(text = "Connect")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        title = {
            Text(
                text = "Choose How To Connect",
                color = CyberTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Pick the Tor route you want BabaG VPN to use for this session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberTextMuted
                )

                ConnectModeOption(
                    mode = TorConnectionMode.Direct,
                    selected = selectedMode == TorConnectionMode.Direct,
                    onClick = { onModeSelected(TorConnectionMode.Direct) }
                )

                ConnectModeOption(
                    mode = TorConnectionMode.Snowflake,
                    selected = selectedMode == TorConnectionMode.Snowflake,
                    onClick = { onModeSelected(TorConnectionMode.Snowflake) }
                )

                ConnectModeOption(
                    mode = TorConnectionMode.Smart,
                    selected = selectedMode == TorConnectionMode.Smart,
                    onClick = { onModeSelected(TorConnectionMode.Smart) }
                )

                Text(
                    text = selectedMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (
                        selectedMode == TorConnectionMode.Smart ||
                        selectedMode == TorConnectionMode.Snowflake
                    ) {
                        CyberBlue
                    } else {
                        CyberTextMuted
                    }
                )
            }
        },
        containerColor = Color(0xFF121020),
        tonalElevation = 0.dp
    )
}

@Composable
private fun ConnectModeOption(
    mode: TorConnectionMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) CyberPanel.copy(alpha = 0.96f) else CyberPanel.copy(alpha = 0.72f)
        ),
        border = BorderStroke(
            1.dp,
            if (selected) CyberMagenta else CyberPanelBorder
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = mode.title,
                style = MaterialTheme.typography.titleMedium,
                color = CyberTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = CyberTextMuted
            )
        }
    }
}

@Composable
private fun TorPowerButton(
    vpnState: VpnTunnelUiState,
    accentColor: Color,
    ringWidth: Dp,
    scale: Float,
    buttonSize: Dp,
    compactLayout: Boolean,
    onTap: () -> Unit
) {
    val outerGlow = accentColor.copy(alpha = 0.25f)
    val coreGlow = accentColor.copy(alpha = 0.42f)
    val innerButtonSize = (buttonSize * 0.81f).coerceAtLeast(144.dp)
    val innerSpacing = if (compactLayout) 6.dp else 10.dp

    Box(
        modifier = Modifier
            .size(buttonSize)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                enabled = vpnState.stage != VpnTunnelStage.RequestingPermission &&
                    vpnState.stage != VpnTunnelStage.StartingTor,
                onClick = onTap
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(outerGlow, Color.Transparent)
                    )
                )
                .border(ringWidth, accentColor, CircleShape)
        )

        Box(
            modifier = Modifier
                .size(innerButtonSize)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            coreGlow,
                            Color(0xFF13162B),
                            Color(0xFF090A14)
                        )
                    )
                )
                .border(1.dp, CyberPanelBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = vpnState.buttonTopLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(innerSpacing))
                Text(
                    text = vpnState.buttonMainLine,
                    style = if (compactLayout) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
                    color = CyberTextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(innerSpacing))
                Text(
                    text = vpnState.buttonBottomLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberTextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ConnectionStats(
    vpnState: VpnTunnelUiState,
    accentColor: Color,
    stackCards: Boolean,
    compactLayout: Boolean,
    cardPadding: Dp,
    cardSpacing: Dp,
    activityLineMaxLines: Int,
    activityDetailMaxLines: Int,
    modifier: Modifier = Modifier
) {
    val activityMetrics = remember(vpnState.activityLine) {
        vpnState.activityLine.toActivityMetrics()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(cardSpacing)
    ) {
        if (stackCards) {
            StatusCard(
                title = "Circuit",
                value = vpnState.circuitLabel,
                accentColor = accentColor,
                compactLayout = compactLayout,
                contentPadding = cardPadding,
                modifier = Modifier.fillMaxWidth()
            )
            StatusCard(
                title = "Shield",
                value = vpnState.shieldLabel,
                secondaryValue = vpnState.shieldRouteHint(),
                accentColor = CyberBlue,
                compactLayout = compactLayout,
                contentPadding = cardPadding,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing)
            ) {
                StatusCard(
                    title = "Circuit",
                    value = vpnState.circuitLabel,
                    accentColor = accentColor,
                    compactLayout = compactLayout,
                    contentPadding = cardPadding,
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    title = "Shield",
                    value = vpnState.shieldLabel,
                    secondaryValue = vpnState.shieldRouteHint(),
                    accentColor = CyberBlue,
                    compactLayout = compactLayout,
                    contentPadding = cardPadding,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberPanel.copy(alpha = 0.9f)),
            border = BorderStroke(1.dp, CyberPanelBorder),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(cardPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleSmall,
                        color = CyberTextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accentColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = vpnState.stage.activityBadgeLabel(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (activityMetrics.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activityMetrics.forEach { metric ->
                            ActivityMetricChip(
                                label = metric.label,
                                value = metric.value,
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Text(
                        text = vpnState.activityLine,
                        style = if (compactLayout) {
                            MaterialTheme.typography.bodySmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = accentColor,
                        maxLines = activityLineMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = vpnState.detailLine,
                    style = if (compactLayout) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = CyberTextMuted,
                    maxLines = activityDetailMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AppRoutingCard(
    selectedPackages: Set<String>,
    vpnStage: VpnTunnelStage,
    compactLayout: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CyberPanel.copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, CyberPanelBorder),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compactLayout) 14.dp else 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "APP ROUTING",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberTextMuted
                )
                Text(
                    text = selectedPackages.routingSummary(),
                    style = if (compactLayout) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    color = CyberCyan
                )
                Text(
                    text = selectedPackages.routingDetail(vpnStage),
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = CyberMagenta.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, CyberPanelBorder)
            ) {
                Text(
                    text = "EDIT",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberTextPrimary
                )
            }
        }
    }
}

@Composable
private fun AppRoutingDialog(
    apps: List<InstalledAppOption>,
    selectedPackages: Set<String>,
    isLoading: Boolean,
    loadError: String?,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var draftSelection by remember(selectedPackages) { mutableStateOf(selectedPackages) }
    val filteredApps = remember(apps, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121020)),
            border = BorderStroke(1.dp, CyberPanelBorder),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose Apps For Tor",
                    style = MaterialTheme.typography.headlineSmall,
                    color = CyberTextPrimary
                )
                Text(
                    text = "Select apps that should use Tor. Leave everything unchecked to keep full-device mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberTextMuted
                )
                Text(
                    text = if (apps.isEmpty()) {
                        "Loading installed apps..."
                    } else {
                        "${apps.size} apps found on this device"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberBlue
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search apps") },
                    singleLine = true
                )

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CyberCyan)
                        }
                    }

                    loadError != null -> {
                        Text(
                            text = loadError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF6B6B)
                        )
                    }

                    filteredApps.isEmpty() -> {
                        Text(
                            text = "No apps matched your search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CyberTextMuted
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AppRoutingOptionRow(
                                    app = app,
                                    selected = draftSelection.contains(app.packageName),
                                    onToggle = {
                                        draftSelection = if (draftSelection.contains(app.packageName)) {
                                            draftSelection - app.packageName
                                        } else {
                                            draftSelection + app.packageName
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (draftSelection.isEmpty()) {
                        "Current mode: Full-device Tor"
                    } else {
                        "${draftSelection.size} selected app${if (draftSelection.size == 1) "" else "s"} will use Tor"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberBlue
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { draftSelection = emptySet() }) {
                        Text("Clear")
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(onClick = { onSave(draftSelection) }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRoutingOptionRow(
    app: InstalledAppOption,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) CyberPanel.copy(alpha = 0.96f) else CyberPanel.copy(alpha = 0.72f)
        ),
        border = BorderStroke(
            1.dp,
            if (selected) CyberCyan else CyberPanelBorder
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() }
            )
            AppIcon(packageName = app.packageName)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = CyberTextPrimary
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberTextMuted
                )
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    AndroidView(
        factory = { viewContext ->
            ImageView(viewContext).apply {
                val size = (40 * viewContext.resources.displayMetrics.density).toInt()
                layoutParams = android.view.ViewGroup.LayoutParams(size, size)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView ->
            val drawable = runCatching {
                packageManager.getApplicationIcon(packageName)
            }.getOrNull()
            imageView.setImageDrawable(drawable)
        },
        modifier = Modifier
            .padding(start = 6.dp)
            .size(40.dp)
    )
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    secondaryValue: String? = null,
    accentColor: Color,
    compactLayout: Boolean,
    contentPadding: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CyberPanel.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, CyberPanelBorder),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = CyberTextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = if (compactLayout) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                color = accentColor
            )
            if (secondaryValue != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = secondaryValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberTextMuted
                )
            }
        }
    }
}

@Composable
private fun CyberpunkBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridColor = CyberMagenta.copy(alpha = 0.04f)
        val verticalStep = size.width / 7f
        val horizontalStep = size.height / 12f

        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            x += verticalStep
        }

        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += horizontalStep
        }

        drawCircle(
            color = CyberMagenta.copy(alpha = 0.11f),
            radius = size.minDimension * 0.35f,
            center = Offset(size.width * 0.78f, size.height * 0.18f)
        )
        drawCircle(
            color = CyberBlue.copy(alpha = 0.1f),
            radius = size.minDimension * 0.45f,
            center = Offset(size.width * 0.12f, size.height * 0.82f)
        )
        drawCircle(
            color = CyberCyan.copy(alpha = 0.22f),
            radius = size.minDimension * 0.16f,
            center = center,
            style = Stroke(width = 3f)
        )
    }
}

private data class ActivityMetric(
    val label: String,
    val value: String
)

@Composable
private fun ActivityMetricChip(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CyberTextMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

private fun VpnTunnelStage.accentColor(): Color = when (this) {
    VpnTunnelStage.Offline -> CyberMagenta
    VpnTunnelStage.RequestingPermission -> CyberMagenta
    VpnTunnelStage.StartingTor -> CyberBlue
    VpnTunnelStage.TorReady -> CyberCyan
    VpnTunnelStage.Connected -> CyberMagenta
    VpnTunnelStage.PermissionDenied -> CyberMagenta
    VpnTunnelStage.Error -> Color(0xFFFF6B6B)
}

private fun VpnTunnelStage.activityBadgeLabel(): String = when (this) {
    VpnTunnelStage.Offline -> "READY"
    VpnTunnelStage.RequestingPermission -> "WAIT"
    VpnTunnelStage.StartingTor -> "BOOT"
    VpnTunnelStage.TorReady -> "LINK"
    VpnTunnelStage.Connected -> "LIVE"
    VpnTunnelStage.PermissionDenied -> "BLOCK"
    VpnTunnelStage.Error -> "ERROR"
}

private fun VpnTunnelUiState.shieldRouteHint(): String? {
    if (
        stage == VpnTunnelStage.Offline ||
        stage == VpnTunnelStage.PermissionDenied ||
        stage == VpnTunnelStage.Error
    ) {
        return null
    }

    return if (routeLabel != shieldLabel) {
        "PATH $routeLabel"
    } else {
        null
    }
}

private fun String.toActivityMetrics(): List<ActivityMetric> {
    val tokens = trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    if (tokens.size < 4 || tokens.size % 2 != 0) {
        return emptyList()
    }

    return tokens
        .chunked(2)
        .take(3)
        .map { pair -> ActivityMetric(label = pair[0], value = pair[1]) }
}

private fun Set<String>.routingSummary(): String {
    return if (isEmpty()) {
        "FULL DEVICE"
    } else {
        "$size APP${if (size == 1) "" else "S"}"
    }
}

private fun Set<String>.routingDetail(vpnStage: VpnTunnelStage): String {
    if (isEmpty()) {
        return if (vpnStage == VpnTunnelStage.Connected || vpnStage == VpnTunnelStage.TorReady) {
            "All apps are routed through Tor right now."
        } else {
            "All apps will use Tor in full-device mode."
        }
    }

    return if (vpnStage == VpnTunnelStage.Connected || vpnStage == VpnTunnelStage.TorReady) {
        "$size selected app${if (size == 1) "" else "s"} are set for Tor routing."
    } else {
        "$size selected app${if (size == 1) "" else "s"} will use Tor on next connect."
    }
}
