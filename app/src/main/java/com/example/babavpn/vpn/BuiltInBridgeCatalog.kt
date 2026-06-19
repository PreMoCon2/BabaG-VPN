package com.example.babavpn.vpn

import android.content.Context
import org.json.JSONObject

data class BuiltInBridgeLine(
    val raw: String
) {
    private val pieces: List<String> = raw.split(" ")

    private fun option(name: String): String? {
        return pieces.firstOrNull { it.startsWith("$name=") }?.substringAfter("=")
    }

    val url: String?
        get() = option("url")

    val ice: String?
        get() = option("ice")

    val fronts: List<String>
        get() = option("fronts")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
}

object BuiltInBridgeCatalog {
    private const val FILE_NAME = "builtin-bridges.json"

    fun loadSnowflakeBridges(context: Context): List<BuiltInBridgeLine> {
        val json = context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val entries = root.optJSONArray("snowflake") ?: return emptyList()

        return buildList {
            for (index in 0 until entries.length()) {
                val raw = entries.optString(index).trim()
                if (raw.isNotEmpty()) {
                    add(BuiltInBridgeLine(raw))
                }
            }
        }
    }
}
