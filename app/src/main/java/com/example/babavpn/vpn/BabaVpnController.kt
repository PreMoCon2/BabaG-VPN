package com.example.babavpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnTunnelStage {
    Offline,
    RequestingPermission,
    StartingTor,
    TorReady,
    Connected,
    PermissionDenied,
    Error
}

data class VpnTunnelUiState(
    val stage: VpnTunnelStage,
    val badgeLabel: String,
    val buttonTopLine: String,
    val buttonMainLine: String,
    val buttonBottomLine: String,
    val statusLine: String,
    val activityLine: String,
    val detailLine: String,
    val circuitLabel: String,
    val shieldLabel: String
)

object BabaVpnController {
    // Compose reads this as the single source of truth for the dashboard state.
    private val _uiState = MutableStateFlow(offlineState())
    val uiState: StateFlow<VpnTunnelUiState> = _uiState.asStateFlow()

    fun onPermissionRequested() {
        _uiState.value = VpnTunnelUiState(
            stage = VpnTunnelStage.RequestingPermission,
            badgeLabel = "GRANT VPN",
            buttonTopLine = "ALLOW",
            buttonMainLine = "VPN",
            buttonBottomLine = "ANDROID SYSTEM PROMPT",
            statusLine = "Approve the Android VPN dialog so Baba VPN can control device-wide traffic.",
            activityLine = "Android consent dialog is waiting",
            detailLine = "This is the first real system-level step for a full-device tunnel. Until Android grants control, the app cannot create or manage a VPN interface.",
            circuitLabel = "AUTH",
            shieldLabel = "ASK"
        )
    }

    fun onStartingTor(progress: Int? = null, summary: String? = null) {
        val progressLabel = progress?.let { "$it%" } ?: "..."
        val summaryLine = summary ?: "Negotiating the Tor network path"
        _uiState.value = VpnTunnelUiState(
            stage = VpnTunnelStage.StartingTor,
            badgeLabel = "TOR BOOT",
            buttonTopLine = "STARTING",
            buttonMainLine = "TOR",
            buttonBottomLine = "FULL DEVICE CORE",
            statusLine = "VPN permission granted. Baba VPN is booting a real embedded Tor backend.",
            activityLine = "Bootstrap $progressLabel - $summaryLine",
            detailLine = "This is now beyond UI: the app is waiting for Tor to create live local listener ports before the device-wide packet bridge is allowed to come online.",
            circuitLabel = progressLabel,
            shieldLabel = "TOR"
        )
    }

    fun onTorReady(ports: TorRuntimePorts) {
        // TorReady means the backend is live, but the Android TUN handoff is
        // still in progress. Connected is the state after the bridge is active.
        _uiState.value = VpnTunnelUiState(
            stage = VpnTunnelStage.TorReady,
            badgeLabel = "LINKING",
            buttonTopLine = "ARMING",
            buttonMainLine = "TUN",
            buttonBottomLine = "TOR BRIDGE",
            statusLine = "Embedded Tor is online. Baba VPN is now creating the Android tunnel and attaching the native Tor packet bridge.",
            activityLine = "SOCKS ${ports.socksPort}  HTTP ${ports.httpPort}  DNS ${ports.dnsPort}",
            detailLine = "This handoff is where Android gives Baba VPN the device-wide interface and the native tunnel process begins forwarding those packets into Tor instead of out to the normal network.",
            circuitLabel = "SOCKS ${ports.socksPort}",
            shieldLabel = "LINK"
        )
    }

    fun onTunnelConnected(ports: TorRuntimePorts, stats: LongArray?) {
        val statsLine = if (stats != null && stats.size >= 4) {
            "TX ${stats[1]}B  RX ${stats[3]}B  DNS ${ports.dnsPort}"
        } else {
            "SOCKS ${ports.socksPort}  DNS ${ports.dnsPort}  TUN ACTIVE"
        }

        _uiState.value = VpnTunnelUiState(
            stage = VpnTunnelStage.Connected,
            badgeLabel = "TOR LIVE",
            buttonTopLine = "STOP",
            buttonMainLine = "TOR",
            buttonBottomLine = "DEVICE ROUTED",
            statusLine = "Full-device Tor VPN is online. New app traffic should now leave through the Tor network instead of your normal IP.",
            activityLine = statsLine,
            detailLine = "Test it in Chrome with 'what's my IP'. You should see a Tor exit IP, not your home or carrier address. Some apps that depend on raw UDP may still misbehave because Tor is fundamentally TCP-based.",
            circuitLabel = "SOCKS ${ports.socksPort}",
            shieldLabel = "LIVE"
        )
    }

    fun onPermissionDenied() {
        _uiState.value = VpnTunnelUiState(
            stage = VpnTunnelStage.PermissionDenied,
            badgeLabel = "DENIED",
            buttonTopLine = "RETRY",
            buttonMainLine = "VPN",
            buttonBottomLine = "ACCESS NEEDED",
            statusLine = "Android denied VPN control. Tap again and accept the permission dialog to continue.",
            activityLine = "System VPN permission was rejected",
            detailLine = "Without the Android VPN grant, no app can route device-wide traffic. Once you approve it, we can continue bootstrapping the full-device tunnel path.",
            circuitLabel = "LOCKED",
            shieldLabel = "BLOCK"
        )
    }

    fun onError(message: String) {
        _uiState.value = VpnTunnelUiState(
            stage = VpnTunnelStage.Error,
            badgeLabel = "ERROR",
            buttonTopLine = "RESET",
            buttonMainLine = "VPN",
            buttonBottomLine = "CHECK LOGS",
            statusLine = message,
            activityLine = "VPN bootstrap hit an exception",
            detailLine = "The app caught a startup error before the tunnel could move forward. Tap again after the issue is fixed to retry the Android VPN bootstrap.",
            circuitLabel = "FAIL",
            shieldLabel = "ALERT"
        )
    }

    fun onStopped() {
        _uiState.value = offlineState()
    }

    private fun offlineState() = VpnTunnelUiState(
        stage = VpnTunnelStage.Offline,
        badgeLabel = "OFFLINE",
        buttonTopLine = "ENABLE",
        buttonMainLine = "VPN",
        buttonBottomLine = "FULL DEVICE MODE",
        statusLine = "Tap the core to let Android hand control of the device VPN tunnel to Baba VPN.",
        activityLine = "Waiting for Android VPN permission",
        detailLine = "This path is the real Orbot-style route: Android grants a device-wide VPN, then the app boots a Tor daemon, then a packet bridge forwards all tunnel traffic into Tor.",
        circuitLabel = "--",
        shieldLabel = "IDLE"
    )
}
