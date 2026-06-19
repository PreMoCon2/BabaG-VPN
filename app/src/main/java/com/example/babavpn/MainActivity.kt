package com.example.babavpn

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.babavpn.ui.theme.BabaVPNTheme
import com.example.babavpn.ui.theme.CyberBlack
import com.example.babavpn.ui.theme.CyberBlue
import com.example.babavpn.ui.theme.CyberCyan
import com.example.babavpn.ui.theme.CyberGreen
import com.example.babavpn.ui.theme.CyberMagenta
import com.example.babavpn.ui.theme.CyberPanel
import com.example.babavpn.ui.theme.CyberPanelBorder
import com.example.babavpn.ui.theme.CyberTextMuted
import com.example.babavpn.ui.theme.CyberTextPrimary
import com.example.babavpn.vpn.BabaVpnController
import com.example.babavpn.vpn.TorConnectionMode
import com.example.babavpn.vpn.BabaVpnService
import com.example.babavpn.vpn.VpnTunnelStage
import com.example.babavpn.vpn.VpnTunnelUiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BabaVPNTheme(darkTheme = true, dynamicColor = false) {
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
    var selectedConnectionMode by remember { mutableStateOf(TorConnectionMode.Smart) }
    var pendingConnectionMode by remember { mutableStateOf(TorConnectionMode.Smart) }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Header(vpnState = vpnState, accentColor = accentColor)

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TorPowerButton(
                    vpnState = vpnState,
                    accentColor = accentColor,
                    ringWidth = ringWidth,
                    scale = buttonScale,
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
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = vpnState.statusLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CyberTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ConnectionStats(vpnState = vpnState, accentColor = accentColor)
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
    }
}

@Preview(showBackground = true)
@Composable
fun BabaVpnAppPreview() {
    BabaVPNTheme(darkTheme = true, dynamicColor = false) {
        BabaVpnApp()
    }
}

@Composable
private fun Header(
    vpnState: VpnTunnelUiState,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "BABA VPN",
                style = MaterialTheme.typography.headlineMedium,
                color = CyberTextPrimary
            )
            Text(
                text = "FULL DEVICE TOR STACK",
                style = MaterialTheme.typography.labelLarge,
                color = CyberBlue
            )
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = accentColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f))
        ) {
            Text(
                text = vpnState.badgeLabel,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
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
                    text = "Pick the Tor route you want BabaVPN to use for this session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberTextMuted
                )

                ConnectModeOption(
                    mode = TorConnectionMode.Direct,
                    selected = selectedMode == TorConnectionMode.Direct,
                    onClick = { onModeSelected(TorConnectionMode.Direct) }
                )

                ConnectModeOption(
                    mode = TorConnectionMode.Smart,
                    selected = selectedMode == TorConnectionMode.Smart,
                    onClick = { onModeSelected(TorConnectionMode.Smart) }
                )

                Text(
                    text = selectedMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedMode == TorConnectionMode.Smart) CyberBlue else CyberTextMuted
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
    onTap: () -> Unit
) {
    val outerGlow = accentColor.copy(alpha = 0.25f)
    val coreGlow = accentColor.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .size(290.dp)
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
                .size(235.dp)
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = vpnState.buttonMainLine,
                    style = MaterialTheme.typography.displaySmall,
                    color = CyberTextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = vpnState.buttonBottomLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberTextMuted
                )
            }
        }
    }
}

@Composable
private fun ConnectionStats(
    vpnState: VpnTunnelUiState,
    accentColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusCard(
                title = "Circuit",
                value = vpnState.circuitLabel,
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "Shield",
                value = vpnState.shieldLabel,
                accentColor = CyberBlue,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberPanel.copy(alpha = 0.9f)),
            border = BorderStroke(1.dp, CyberPanelBorder),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Activity Feed",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = vpnState.activityLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = vpnState.detailLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberTextMuted
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CyberPanel.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, CyberPanelBorder),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = CyberTextMuted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = accentColor
            )
        }
    }
}

@Composable
private fun CyberpunkBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridColor = CyberMagenta.copy(alpha = 0.08f)
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
            color = CyberMagenta.copy(alpha = 0.18f),
            radius = size.minDimension * 0.35f,
            center = Offset(size.width * 0.78f, size.height * 0.18f)
        )
        drawCircle(
            color = CyberBlue.copy(alpha = 0.18f),
            radius = size.minDimension * 0.45f,
            center = Offset(size.width * 0.12f, size.height * 0.82f)
        )
        drawCircle(
            color = CyberCyan.copy(alpha = 0.56f),
            radius = size.minDimension * 0.16f,
            center = center,
            style = Stroke(width = 3f)
        )
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
