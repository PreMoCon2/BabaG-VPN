package com.example.babavpn.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.babavpn.MainActivity
import com.example.babavpn.R

class BabaVpnService : VpnService() {
    // TorRuntimeManager boots the embedded Tor daemon; TorVpnBridgeManager only
    // takes over once Tor has exposed the local listener ports we need.
    private var torRuntimeManager: TorRuntimeManager? = null
    private var torVpnBridgeManager: TorVpnBridgeManager? = null
    private var connectionMode: TorConnectionMode = TorConnectionMode.Direct
    private var smartFallbackTriggered = false
    private var connectStartedAtMs = 0L
    private var lastBootstrapProgress: Int? = null
    private var lastBootstrapUpdateAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        torVpnBridgeManager = TorVpnBridgeManager(this)
        torRuntimeManager = TorRuntimeManager(
            context = this,
            listener = object : TorRuntimeListener {
                override fun onBootstrap(progress: Int?, summary: String?) {
                    val adjustedSummary = maybeTriggerSmartFallback(progress, summary)
                    BabaVpnController.onStartingTor(
                        mode = connectionMode,
                        progress = progress,
                        summary = adjustedSummary
                    )
                    updateNotification(
                        text = if (progress != null) {
                            "Bootstrapping Tor $progress%"
                        } else {
                            "Bootstrapping Tor"
                        }
                    )
                }

                override fun onTorReady(ports: TorRuntimePorts) {
                    val route = torRuntimeManager?.activeRoute ?: TorConnectionRoute.Direct
                    BabaVpnController.onTorReady(
                        mode = connectionMode,
                        route = route,
                        ports = ports
                    )
                    updateNotification(
                        when (route) {
                            TorConnectionRoute.Direct -> "Tor ready. Establishing device tunnel"
                            TorConnectionRoute.Snowflake -> "Snowflake route ready. Establishing device tunnel"
                        }
                    )

                    // We only establish the default-route VPN after Tor is fully ready.
                    // Doing this earlier would capture device traffic before the bridge
                    // has somewhere safe to forward it.
                    runCatching {
                        torVpnBridgeManager?.start(ports)
                    }.onSuccess {
                        BabaVpnController.onTunnelConnected(
                            mode = connectionMode,
                            route = route,
                            ports = ports,
                            stats = torVpnBridgeManager?.stats()
                        )
                        updateNotification("Full-device Tor VPN connected")
                    }.onFailure { error ->
                        handleRuntimeError(error.message ?: "The Tor VPN bridge failed to start.")
                    }
                }

                override fun onTorError(message: String) {
                    handleRuntimeError(message)
                }
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mode = TorConnectionMode.fromWireValue(intent.getStringExtra(EXTRA_CONNECT_MODE))
                bootstrapVpnStack(mode)
            }
            ACTION_STOP -> stopVpnStack()
        }

        return START_NOT_STICKY
    }

    override fun onRevoke() {
        // Android calls this when the user disables the VPN outside the app.
        BabaVpnController.onStopped()
        torVpnBridgeManager?.stop()
        torRuntimeManager?.stop()
        stopSelf()
    }

    private fun bootstrapVpnStack(mode: TorConnectionMode) {
        try {
            connectionMode = mode
            smartFallbackTriggered = false
            connectStartedAtMs = SystemClock.elapsedRealtime()
            lastBootstrapProgress = null
            lastBootstrapUpdateAtMs = connectStartedAtMs
            startForegroundIfNeeded("Starting Tor core")
            BabaVpnController.onStartingTor(mode = mode)
            torRuntimeManager?.start(mode)
        } catch (error: Throwable) {
            handleRuntimeError(error.message ?: "VPN bootstrap failed.")
        }
    }

    private fun stopVpnStack() {
        torVpnBridgeManager?.stop()
        torRuntimeManager?.stop()
        BabaVpnController.onStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        torVpnBridgeManager?.stop()
        torRuntimeManager?.stop()
        super.onDestroy()
    }

    private fun handleRuntimeError(message: String) {
        BabaVpnController.onError(message)
        updateNotification(message)
        torVpnBridgeManager?.stop()
        torRuntimeManager?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun maybeTriggerSmartFallback(progress: Int?, summary: String?): String? {
        if (connectionMode != TorConnectionMode.Smart) {
            return summary
        }

        if (torRuntimeManager?.activeRoute != TorConnectionRoute.Direct) {
            return summary
        }

        val now = SystemClock.elapsedRealtime()
        val previousProgress = lastBootstrapProgress

        if (progress != null && (previousProgress == null || progress > previousProgress)) {
            lastBootstrapProgress = progress
            lastBootstrapUpdateAtMs = now
            return summary
        }

        val stalledForMs = now - lastBootstrapUpdateAtMs
        if (!smartFallbackTriggered && stalledForMs >= SMART_DIRECT_TIMEOUT_MS) {
            smartFallbackTriggered = true
            lastBootstrapProgress = null
            lastBootstrapUpdateAtMs = now
            torRuntimeManager?.switchToSnowflake()
            updateNotification("Smart Connect is switching to a Snowflake bridge")
            return "Direct path looks blocked. Smart Connect is switching to a Snowflake bridge."
        }

        return summary
    }

    private fun startForegroundIfNeeded(text: String) {
        createNotificationChannel()
        val notification = buildNotification(text)

        // Android 14+ requires an explicit foreground-service type for VPN flows.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setOngoing(true)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) {
            return
        }

        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.vpn_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_notification_channel_description)
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val ACTION_START = "com.example.babavpn.action.START_VPN"
        private const val ACTION_STOP = "com.example.babavpn.action.STOP_VPN"
        private const val EXTRA_CONNECT_MODE = "com.example.babavpn.extra.CONNECT_MODE"
        private const val NOTIFICATION_CHANNEL_ID = "baba_vpn_runtime"
        private const val NOTIFICATION_ID = 1001
        private const val SMART_DIRECT_TIMEOUT_MS = 15_000L

        fun start(context: Context, mode: TorConnectionMode) {
            val intent = Intent(context, BabaVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONNECT_MODE, mode.wireValue)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BabaVpnService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
