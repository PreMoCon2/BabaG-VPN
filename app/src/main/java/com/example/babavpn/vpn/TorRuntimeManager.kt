package com.example.babavpn.vpn

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import net.freehaven.tor.control.TorControlCommands
import net.freehaven.tor.control.TorControlConnection
import org.torproject.jni.TorService
import java.io.File
import java.util.concurrent.Executors
import java.util.regex.Pattern

data class TorRuntimePorts(
    val socksPort: Int,
    val httpPort: Int,
    val dnsPort: Int,
    val transPort: Int
)

interface TorRuntimeListener {
    fun onBootstrap(progress: Int?, summary: String?)
    fun onTorReady(ports: TorRuntimePorts)
    fun onTorError(message: String)
}

class TorRuntimeManager(
    private val context: Context,
    private val listener: TorRuntimeListener
) {
    // Binder and control-port work stay off the main thread so the VPN service
    // can keep updating the UI and foreground notification without blocking.
    private val executor = Executors.newSingleThreadExecutor()
    private val transportManager = TorPluggableTransportManager(context)
    private var torServiceConnection: ServiceConnection? = null
    @Volatile
    private var isRunning = false
    @Volatile
    private var activeSessionId = 0
    private var controlConnection: TorControlConnection? = null
    @Volatile
    var activeRoute: TorConnectionRoute = TorConnectionRoute.Direct
        private set

    fun start(mode: TorConnectionMode = TorConnectionMode.Direct) {
        if (isRunning) {
            return
        }

        isRunning = true

        try {
            executor.execute {
                try {
                    startProfile(
                        route = TorConnectionRoute.Direct,
                        mode = mode
                    )
                } catch (error: Throwable) {
                    isRunning = false
                    listener.onTorError(error.message ?: "Unable to start the Tor runtime.")
                }
            }
        } catch (error: Throwable) {
            isRunning = false
            listener.onTorError(error.message ?: "Unable to start the Tor runtime.")
        }
    }

    fun switchToSnowflake() {
        if (!isRunning || activeRoute == TorConnectionRoute.Snowflake) {
            return
        }

        executor.execute {
            try {
                shutdownCurrentProfile()
                Thread.sleep(750)
                startProfile(
                    route = TorConnectionRoute.Snowflake,
                    mode = TorConnectionMode.Smart
                )
            } catch (error: Throwable) {
                if (isRunning) {
                    listener.onTorError(error.message ?: "Unable to switch to a Snowflake bridge.")
                }
            }
        }
    }

    fun stop() {
        if (!isRunning) {
            return
        }

        isRunning = false
        activeSessionId += 1

        shutdownCurrentProfile()
        transportManager.stopAll()
    }

    private fun startProfile(
        route: TorConnectionRoute,
        mode: TorConnectionMode
    ) {
        activeRoute = route
        activeSessionId += 1
        val sessionId = activeSessionId

        val snowflakeConfig = when (route) {
            TorConnectionRoute.Direct -> {
                transportManager.stopAll()
                null
            }

            TorConnectionRoute.Snowflake -> transportManager.startSnowflake()
        }

        installTorConfig(
            mode = mode,
            route = route,
            snowflakeConfig = snowflakeConfig
        )
        bindTorService(sessionId)
    }

    private fun bindTorService(sessionId: Int) {
        // TorService comes from the tor-android dependency and owns the actual
        // Tor process plus the control connection we query later.
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                executor.execute {
                    try {
                        val binder = service as? TorService.LocalBinder
                            ?: throw IllegalStateException("Tor binder was not available.")
                        val torService = binder.service
                        waitForControlConnection(torService, sessionId)
                    } catch (error: Throwable) {
                        if (isRunning && sessionId == activeSessionId) {
                            listener.onTorError(error.message ?: "Tor service binding failed.")
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (isRunning && sessionId == activeSessionId) {
                    listener.onTorError("Tor service disconnected unexpectedly.")
                }
            }

            override fun onBindingDied(name: ComponentName?) {
                if (isRunning && sessionId == activeSessionId) {
                    listener.onTorError("Tor service binding died.")
                }
            }
        }

        torServiceConnection = connection
        val bound = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.bindService(
                android.content.Intent(context, TorService::class.java),
                Context.BIND_AUTO_CREATE,
                executor,
                connection
            )
        } else {
            context.bindService(
                android.content.Intent(context, TorService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        }

        if (!bound) {
            throw IllegalStateException("Android could not bind to the embedded Tor service.")
        }
    }

    private fun waitForControlConnection(torService: TorService, sessionId: Int) {
        // The Tor process starts before its control socket is ready, so we poll
        // briefly until the control connection becomes available.
        repeat(120) {
            if (!isRunning || sessionId != activeSessionId) {
                return
            }

            val connection = torService.torControlConnection
            if (connection != null) {
                controlConnection = connection
                monitorBootstrap(connection, sessionId)
                return
            }

            Thread.sleep(500)
        }

        if (isRunning && sessionId == activeSessionId) {
            listener.onTorError("Tor control connection did not come online in time.")
        }
    }

    private fun monitorBootstrap(connection: TorControlConnection, sessionId: Int) {
        while (isRunning && sessionId == activeSessionId) {
            val bootstrapStatus = runCatching {
                connection.getInfo("status/bootstrap-phase")
            }.getOrNull()

            val progress = bootstrapStatus?.let(::parseProgress)
            val summary = bootstrapStatus?.let(::parseSummary)
            listener.onBootstrap(progress, summary)

            // Once bootstrap reaches 100%, Tor has opened the listeners that the
            // VPN bridge needs for SOCKS, DNS, and transparent routing.
            if (progress != null && progress >= 100) {
                listener.onTorReady(resolvePorts(connection))
                return
            }

            Thread.sleep(1000)
        }
    }

    private fun resolvePorts(connection: TorControlConnection): TorRuntimePorts {
        return TorRuntimePorts(
            socksPort = resolveListenerPort(connection.getInfo("net/listeners/socks")),
            httpPort = resolveListenerPort(connection.getInfo("net/listeners/httptunnel")),
            dnsPort = resolveListenerPort(connection.getInfo("net/listeners/dns")),
            transPort = resolveListenerPort(connection.getInfo("net/listeners/trans"))
        )
    }

    private fun resolveListenerPort(listenerInfo: String?): Int {
        if (listenerInfo.isNullOrBlank()) {
            return 0
        }

        val firstToken = listenerInfo.trim().split(" ").firstOrNull().orEmpty()
        val match = Regex(":(\\d+)").find(firstToken)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun parseProgress(bootstrapStatus: String): Int? {
        val match = PROGRESS_PATTERN.matcher(bootstrapStatus)
        return if (match.find()) match.group(1)?.toIntOrNull() else null
    }

    private fun parseSummary(bootstrapStatus: String): String? {
        val match = SUMMARY_PATTERN.matcher(bootstrapStatus)
        return if (match.find()) match.group(1) else null
    }

    private fun installTorConfig(
        mode: TorConnectionMode,
        route: TorConnectionRoute,
        snowflakeConfig: SnowflakeTransportConfig?
    ) {
        // The defaults torrc keeps transparent listeners disabled until our
        // runtime config is written, so the service does not expose stale ports
        // during startup.
        writeFile(
            TorService.getDefaultsTorrc(context),
            """
            DNSPort 0
            TransPort 0
            DisableNetwork 1
            """.trimIndent()
        )

        val torrcLines = mutableListOf(
            "RunAsDaemon 1",
            "AvoidDiskWrites 1",
            "SOCKSPort auto",
            "HTTPTunnelPort auto",
            "DNSPort auto",
            "TransPort auto",
            "SafeSocks 0",
            "TestSocks 0",
            "VirtualAddrNetwork 10.192.0.0/10",
            "AutomapHostsOnResolve 1",
            "DormantClientTimeout 10 minutes",
            "DormantCanceledByStartup 1",
            "DisableNetwork 0"
        )

        when (route) {
            TorConnectionRoute.Direct -> {
                torrcLines.add(0, "UseBridges 0")
            }

            TorConnectionRoute.Snowflake -> {
                val config = snowflakeConfig
                    ?: throw IllegalStateException("Snowflake profile requested without transport config.")
                torrcLines.add(0, "UseBridges 1")
                torrcLines.add(
                    1,
                    "ClientTransportPlugin snowflake socks5 127.0.0.1:${config.socksPort}"
                )
                config.bridgeLines.forEach { bridgeLine ->
                    torrcLines.add("Bridge $bridgeLine")
                }
            }
        }

        writeFile(
            TorService.getTorrc(context),
            buildString {
                if (mode == TorConnectionMode.Smart) {
                    appendLine("# Smart Connect starts direct and can reconfigure into Snowflake if needed.")
                }
                appendLine("# Auto ports let Tor pick open localhost listeners that we discover over the control connection before handing them to the VPN bridge.")
                torrcLines.forEach(::appendLine)
            }.trim()
        )
    }

    private fun writeFile(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText("$content\n")
    }

    private fun shutdownCurrentProfile() {
        try {
            controlConnection?.shutdownTor(TorControlCommands.SIGNAL_SHUTDOWN)
        } catch (_: Throwable) {
        }

        controlConnection = null

        torServiceConnection?.let {
            try {
                context.unbindService(it)
            } catch (_: Throwable) {
            }
        }
        torServiceConnection = null
    }

    private companion object {
        val PROGRESS_PATTERN: Pattern = Pattern.compile("PROGRESS=(\\d+)")
        val SUMMARY_PATTERN: Pattern = Pattern.compile("SUMMARY=\"([^\"]+)\"")
    }
}
