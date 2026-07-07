package com.example.babavpn.vpn

import android.content.Context

object AppRoutingPreferences {
    private const val PREFS_NAME = "babag_vpn_prefs"
    private const val KEY_SELECTED_PACKAGES = "selected_packages"

    fun selectedPackages(context: Context): Set<String> {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_SELECTED_PACKAGES, emptySet())
            ?.toSet()
            ?: emptySet()
    }

    fun setSelectedPackages(context: Context, packages: Set<String>) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_SELECTED_PACKAGES, packages.toSortedSet())
            .apply()
    }
}
