package com.example.babavpn.vpn

import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import java.io.File

class TorVpnBridgeManager(
    private val service: VpnService
) {
    private var vpnInterface: ParcelFileDescriptor? = null

    fun start(ports: TorRuntimePorts) {
        require(ports.socksPort > 0) { "Tor did not expose a valid SOCKS port." }

        stop()
        val selectedPackages = AppRoutingPreferences.selectedPackages(service)

        // This TUN becomes the device-wide default route. The native bridge then
        // reads packets from the file descriptor and forwards them into Tor.
        val builder = service.Builder()
            .setSession(service.packageName)
            .setMtu(TProxyService.TUNNEL_MTU)
            .addAddress(TProxyService.VIRTUAL_GATEWAY_IPV4, 32)
            .addDnsServer(TProxyService.FAKE_DNS)
            .addRoute("0.0.0.0", 0)
            .addAddress(TProxyService.VIRTUAL_GATEWAY_IPV6, 128)
            .addRoute("::", 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
            builder.allowFamily(OsConstants.AF_INET)
            builder.allowFamily(OsConstants.AF_INET6)
        }

        if (selectedPackages.isEmpty()) {
            // Excluding our own package avoids the app tunneling its management
            // traffic back into itself during full-device mode.
            builder.addDisallowedApplication(service.packageName)
        } else {
            var appliedPackages = 0

            selectedPackages.sorted().forEach { packageName ->
                val added = runCatching {
                    builder.addAllowedApplication(packageName)
                }.isSuccess
                if (added) {
                    appliedPackages += 1
                }
            }

            if (appliedPackages == 0) {
                // If the saved list is stale, fall back to the old full-device
                // behavior instead of creating a VPN profile that matches no apps.
                builder.addDisallowedApplication(service.packageName)
            }
        }

        val establishedInterface = builder.establish()
            ?: throw IllegalStateException("Android did not establish the VPN tunnel.")

        vpnInterface = establishedInterface

        val configFile = writeTunnelConfig(ports.socksPort)
        TProxyService.TProxyStartService(configFile.absolutePath, establishedInterface.fd)
    }

    fun stop() {
        runCatching { TProxyService.TProxyStopService() }
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    fun stats(): LongArray? = runCatching { TProxyService.TProxyGetStats() }.getOrNull()

    private fun writeTunnelConfig(socksPort: Int): File {
        val configFile = File(service.cacheDir, "tproxy.conf")
        configFile.writeText(
            """
            misc:
              log-level: warn
              task-stack-size: ${TProxyService.TASK_SIZE}
            tunnel:
              ipv4: ${TProxyService.VIRTUAL_GATEWAY_IPV4}
              ipv6: '${TProxyService.VIRTUAL_GATEWAY_IPV6}'
              mtu: ${TProxyService.TUNNEL_MTU}
            socks5:
              port: $socksPort
              address: 127.0.0.1
              udp: 'udp'
            mapdns:
              # The native bridge synthesizes DNS answers inside the VPN and
              # maps them back into Tor-friendly hostname resolution.
              address: ${TProxyService.FAKE_DNS}
              port: 53
              network: 240.0.0.0
              netmask: 240.0.0.0
              cache-size: 10000
            """.trimIndent() + "\n"
        )
        return configFile
    }
}
