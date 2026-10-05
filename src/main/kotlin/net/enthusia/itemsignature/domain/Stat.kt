package net.enthusia.itemsignature.domain

enum class Stat(val id: String, val label: String, val itemFamily: String? = null, val distance: Boolean = false) {
    PLAYER_KILLS("player_kills", "Player Kills"),
    MOB_KILLS("mob_kills", "Mob Kills"),
    BLOCKS_BROKEN("blocks_broken", "Blocks Mined"),
    DISTANCE_FLOWN("distance_flown", "Distance Flown", "ELYTRA", true),
    DISTANCE_WALKED("distance_walked", "Distance Walked", "BOOTS", true),
    SHIELDS_DISABLED("shields_disabled", "Shields Disabled", "AXE"),
    TIMES_FISHED("times_fished", "Times Fished", "FISHING_ROD"),
    PORTALS_IGNITED("portals_ignited", "Portals Ignited", "FLINT_AND_STEEL"),
    TIMES_LUNGED("times_lunged", "Times Lunged", "SPEAR"),
    ARROWS_SHOT("arrows_shot", "Arrows Shot", "BOW"),
    TIMES_RIPTIDED("times_riptided", "Times Riptided", "TRIDENT"),
    TIMES_THROWN("times_thrown", "Times Thrown", "TRIDENT"),
    LAND_TILLED("land_tilled", "Land Tilled", "HOE"),
    SHEEP_SHEARED("sheep_sheared", "Sheep Sheared", "SHEARS"),
    TIMES_SIFTED("times_sifted", "Times Sifted", "BRUSH");

    fun accepts(material: String): Boolean = when (itemFamily) {
        null -> true // Existing generic trackers retain their attachment semantics.
        "BOW" -> material == "BOW" || material == "CROSSBOW"
        "BOOTS", "AXE", "HOE", "SPEAR" -> !material.startsWith("LEGACY_") && material.endsWith("_$itemFamily")
        else -> material == itemFamily
    }
    companion object { fun from(id: String) = entries.firstOrNull { it.id == id.lowercase() } }
}
