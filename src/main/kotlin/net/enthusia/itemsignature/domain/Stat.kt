package net.enthusia.itemsignature.domain

enum class Stat(val id: String, val label: String) {
    PLAYER_KILLS("player_kills", "Player Kills"),
    MOB_KILLS("mob_kills", "Mob Kills"),
    BLOCKS_BROKEN("blocks_broken", "Blocks Mined");
    companion object { fun from(id: String) = entries.firstOrNull { it.id == id.lowercase() } }
}
