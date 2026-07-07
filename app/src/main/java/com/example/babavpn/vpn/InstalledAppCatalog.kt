package com.example.babavpn.vpn

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

data class InstalledAppOption(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean
)

object InstalledAppCatalog {
    fun loadInstalledApps(context: Context): List<InstalledAppOption> {
        val packageManager = context.packageManager
        return packageManager
            .queryInstalledApplications()
            .asSequence()
            .filter { appInfo ->
                appInfo.enabled && appInfo.packageName != context.packageName
            }
            .mapNotNull { appInfo ->
                val packageName = appInfo.packageName
                val label = packageManager.getApplicationLabel(appInfo)
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: packageName
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                InstalledAppOption(
                    packageName = packageName,
                    label = label,
                    isSystemApp = isSystemApp
                )
            }
            .distinctBy { it.packageName }
            .sortedWith { first, second ->
                if (first.isSystemApp != second.isSystemApp) {
                    return@sortedWith if (first.isSystemApp) 1 else -1
                }
                val labelComparison = first.label.compareTo(second.label, ignoreCase = true)
                if (labelComparison != 0) {
                    labelComparison
                } else {
                    first.packageName.compareTo(second.packageName, ignoreCase = true)
                }
            }
            .toList()
    }

    private fun PackageManager.queryInstalledApplications(): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getInstalledApplications(0)
        }
    }
}
