package com.example.babavpn.vpn

import IPtProxy.Controller
import IPtProxy.IPtProxy
import IPtProxy.OnTransportEvents
import android.content.Context
import android.util.Log
import java.io.File

data class SnowflakeTransportConfig(
    val socksPort: Int,
    val bridgeLines: List<String>
)

class TorPluggableTransportManager(
    private val context: Context
) {
    private val stateDir = File(context.filesDir, "iptproxy").apply { mkdirs() }
    private val controller: Controller by lazy {
        Controller(
            stateDir.absolutePath,
            true,
            false,
            "INFO",
            object : OnTransportEvents {
                override fun connected(name: String?) {
                    if (name != null) {
                        Log.d(TAG, "$name connected")
                    }
                }

                override fun error(name: String?, error: Exception?) {
                    if (name != null) {
                        Log.e(TAG, "$name error", error)
                    }
                }

                override fun stopped(name: String?, error: Exception?) {
                    if (name != null) {
                        Log.d(TAG, "$name stopped", error)
                    }
                }
            }
        )
    }

    private var snowflakeRunning = false

    fun startSnowflake(): SnowflakeTransportConfig {
        stopAll()

        val bridges = BuiltInBridgeCatalog.loadSnowflakeBridges(context)
        val primaryBridge = bridges.firstOrNull()
            ?: throw IllegalStateException("No built-in Snowflake bridges are available.")

        controller.snowflakeIceServers = primaryBridge.ice.orEmpty()
        controller.snowflakeBrokerUrl = primaryBridge.url.orEmpty()
        controller.snowflakeFrontDomains = primaryBridge.fronts.joinToString(",")
        controller.snowflakeAmpCacheUrl = ""
        controller.snowflakeSqsUrl = ""
        controller.snowflakeSqsCreds = ""

        controller.start(IPtProxy.Snowflake, null)
        snowflakeRunning = true

        val port = controller.port(IPtProxy.Snowflake).toInt()
        require(port in 1..65535) { "Snowflake transport did not expose a valid local port." }

        return SnowflakeTransportConfig(
            socksPort = port,
            bridgeLines = bridges.map { it.raw }
        )
    }

    fun stopAll() {
        if (!snowflakeRunning) {
            return
        }

        runCatching {
            controller.stop(IPtProxy.Snowflake)
        }

        snowflakeRunning = false
    }

    private companion object {
        const val TAG = "TorTransportManager"
    }
}
