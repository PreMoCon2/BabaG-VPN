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
    val shieldLabel: String,
    val routeLabel: String
)

object BabaVpnController {
    // Compose reads this as the single source of truth for the dashboard state.
    private val _uiState = MutableStateFlow(offlineState())
    val uiState: StateFlow<VpnTunnelUiState> = _uiState.asStateFlow()

    fun onPermissionRequested(mode: TorConnectionMode) {
        _uiState.value = VpnTunnelUiState(
            stage = VpnTunnelStage.RequestingPermission,
            badgeLabel = when (mode) {
                TorConnectionMode.Smart -> "SMART VPN"
                TorConnectionMode.Snowflake -> "SNOW VPN"
                TorConnectionMode.Direct -> "GRANT VPN"
            },
            buttonTopLine = "ALLOW",
            buttonMainLine = "VPN",
            buttonBottomLine = mode.title.uppercase(),
            statusLine = "Approve the Android VPN dialog to start ${mode.title} mode.",
            activityLine = "VPN permission pending",
            detailLine = "Android must grant VPN control before BabaG VPN can route traffic.",
            circuitLabel = "AUTH",
            shieldLabel = mode.shieldLabel(),
            routeLabel = "PENDING"
        )
    }

    fun onStartingTor(
        mode: TorConnectionMode,
        progress: Int? = null,
        summary: String? = null
    ) {
        val progressLabel = progress?.let { "$it%" } ?: "..."
        val summaryLine = summary ?: "Negotiating the Tor network path"
        _uiState.value = VpnTunnelUiState(
            stage = VpnTunnelStage.StartingTor,
            badgeLabel = when (mode) {
                TorConnectionMode.Smart -> "SMART TOR"
                TorConnectionMode.Snowflake -> "SNOW TOR"
                TorConnectionMode.Direct -> "TOR BOOT"
            },
            buttonTopLine = "STARTING",
            buttonMainLine = "TOR",
            buttonBottomLine = mode.title.uppercase(),
            statusLine = when (mode) {
                TorConnectionMode.Smart ->
                    "Smart Connect is testing the best Tor path for this network."
                TorConnectionMode.Snowflake ->
                    "Snowflake mode is starting a bridge-based Tor path."
                TorConnectionMode.Direct ->
                    "Direct mode is starting a standard Tor path."
            },
            activityLine = "Bootstrap $progressLabel - $summaryLine",
            detailLine = when (mode) {
                TorConnectionMode.Smart ->
                    "It starts direct first, then falls back to Snowflake if the route struggles."
                TorConnectionMode.Snowflake ->
                    "Snowflake is slower, but works better on restrictive networks."
                TorConnectionMode.Direct ->
                    "BabaG VPN is waiting for Tor to expose its local ports before the device tunnel can start."
            },
            circuitLabel = progressLabel,
            shieldLabel = mode.shieldLabel(),
            routeLabel = when (mode) {
                TorConnectionMode.Smart -> "TRY DIRECT"
                TorConnectionMode.Snowflake -> TorConnectionRoute.Snowflake.label
                TorConnectionMode.Direct -> TorConnectionRoute.Direct.label
            }
        )
    }

    fun onTorReady(
        mode: TorConnectionMode,
        route: TorConnectionRoute,
        ports: TorRuntimePorts
    ) {
        // TorReady means the backend is live, but the Android TUN handoff is
        // still in progress. Connected is the state after the bridge is active.
        _uiState.value = VpnTunnelUiState(
            stage = VpnTunnelStage.TorReady,
            badgeLabel = "LINKING",
            buttonTopLine = "ARMING",
            buttonMainLine = "TUN",
            buttonBottomLine = route.label,
            statusLine = when {
                mode == TorConnectionMode.Smart && route == TorConnectionRoute.Snowflake ->
                    "Smart Connect found a working Snowflake bridge and is linking the Android tunnel."
                mode == TorConnectionMode.Snowflake ->
                    "Snowflake is online and BabaG VPN is linking the Android tunnel."
                else ->
                    "Tor is online and BabaG VPN is linking the Android tunnel."
            },
            activityLine = "SOCKS ${ports.socksPort}  HTTP ${ports.httpPort}  DNS ${ports.dnsPort}",
            detailLine = when {
                mode == TorConnectionMode.Smart && route == TorConnectionRoute.Direct ->
                    "Smart Connect stayed on the faster direct route."
                mode == TorConnectionMode.Smart ->
                    "Smart Connect switched to Snowflake before bringing the tunnel fully online."
                mode == TorConnectionMode.Snowflake ->
                    "The bridge route stays active while Android hands over the VPN interface."
                else ->
                    "Android is handing BabaG VPN the device-wide tunnel so traffic can flow into Tor."
            },
            circuitLabel = "SOCKS ${ports.socksPort}",
            shieldLabel = mode.shieldLabel(),
            routeLabel = route.label
        )
    }

    fun onTunnelConnected(
        mode: TorConnectionMode,
        route: TorConnectionRoute,
        ports: TorRuntimePorts,
        stats: LongArray?
    ) {
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
            buttonBottomLine = route.label,
            statusLine = when {
                mode == TorConnectionMode.Smart && route == TorConnectionRoute.Snowflake ->
                    "Smart Connect is live over a Snowflake bridge."
                mode == TorConnectionMode.Smart ->
                    "Smart Connect is live on the direct Tor path."
                mode == TorConnectionMode.Snowflake ->
                    "Snowflake mode is live over a Tor bridge."
                else ->
                    "Full-device Tor VPN is live."
            },
            activityLine = statsLine,
            detailLine = when {
                mode == TorConnectionMode.Smart && route == TorConnectionRoute.Snowflake ->
                    "Bridge mode is slower, but better on blocked networks. Check 'what's my IP' in Chrome."
                mode == TorConnectionMode.Snowflake ->
                    "Snowflake helps when normal Tor is blocked. Check 'what's my IP' in Chrome."
                else ->
                    "Check 'what's my IP' in Chrome. UDP-heavy apps may still misbehave because Tor is TCP-based."
            },
            circuitLabel = "SOCKS ${ports.socksPort}",
            shieldLabel = mode.shieldLabel(),
            routeLabel = route.label
        )
    }

    fun onPermissionDenied() {
        _uiState.value = VpnTunnelUiState(
            stage = VpnTunnelStage.PermissionDenied,
            badgeLabel = "DENIED",
            buttonTopLine = "RETRY",
            buttonMainLine = "VPN",
            buttonBottomLine = "ACCESS NEEDED",
            statusLine = "Android denied VPN control. Tap again and allow the VPN prompt.",
            activityLine = "VPN permission rejected",
            detailLine = "Without Android VPN access, BabaG VPN cannot route device traffic.",
            circuitLabel = "LOCKED",
            shieldLabel = "BLOCK",
            routeLabel = "WAITING"
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
            activityLine = "VPN startup error",
            detailLine = "The tunnel hit a startup problem. Fix it, then tap again to retry.",
            circuitLabel = "FAIL",
            shieldLabel = "ALERT",
            routeLabel = "ERROR"
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
        statusLine = "Tap the core to start the Android VPN tunnel.",
        activityLine = "Waiting for VPN permission",
        detailLine = "Android VPN + embedded Tor + a native packet bridge.",
        circuitLabel = "--",
        shieldLabel = "IDLE",
        routeLabel = "WAITING"
    )

    private fun TorConnectionMode.shieldLabel(): String = when (this) {
        TorConnectionMode.Direct -> "DIRECT"
        TorConnectionMode.Snowflake -> "SNOWFLAKE"
        TorConnectionMode.Smart -> "SMART"
    }
}
