package com.example.babavpn.vpn

enum class TorConnectionMode(
    val wireValue: String,
    val title: String,
    val description: String
) {
    Direct(
        wireValue = "direct",
        title = "Direct Connection",
        description = "Connects straight to Tor. Best if Tor is not blocked on this network."
    ),
    Snowflake(
        wireValue = "snowflake",
        title = "Snowflake",
        description = "Starts on a Snowflake bridge right away. Best when Tor is blocked, but usually slower."
    ),
    Smart(
        wireValue = "smart",
        title = "Smart Connect",
        description = "Starts with a direct Tor connection, then switches to a Snowflake bridge if the network looks blocked."
    );

    companion object {
        fun fromWireValue(value: String?): TorConnectionMode {
            return entries.firstOrNull { it.wireValue == value } ?: Direct
        }
    }
}
