package net.enthusia.itemsignature

import net.enthusia.itemsignature.infrastructure.*
import net.enthusia.itemsignature.domain.Stat
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit

class SpecificTrackerTest {
    @AfterEach fun close() { MockBukkit.unmock() }
    private val pairs = mapOf(
        "distance_flown" to "ELYTRA", "distance_walked" to "DIAMOND_BOOTS",
        "shields_disabled" to "IRON_AXE", "times_fished" to "FISHING_ROD",
        "portals_ignited" to "FLINT_AND_STEEL", "times_lunged" to "COPPER_SPEAR",
        "arrows_shot" to "CROSSBOW", "times_riptided" to "TRIDENT", "times_thrown" to "TRIDENT",
        "land_tilled" to "GOLDEN_HOE", "sheep_sheared" to "SHEARS", "times_sifted" to "BRUSH")

    @Test fun `new trackers bind to matching materials with permissions and persistent counters`() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        val player = server.addPlayer()
        for ((id, name) in pairs) {
            val stat = assertNotNull(Stat.from(id)).let { Stat.from(id)!! }
            player.addAttachment(plugin, "itemsignature.track.$id", true)
            val item = ItemStack(Material.valueOf(name))
            plugin.service.track(player, item, stat)
            assertTrue(plugin.service.increment(item, stat))
            assertEquals(1L, ItemData.number(item.itemMeta!!, "value"))
            assertEquals(player.uniqueId.toString(), ItemData.string(item.itemMeta!!, "tracker_owner"))
            val wrong = ItemStack(Material.DIAMOND_SWORD)
            assertThrows(InputFailure::class.java) { plugin.service.track(player, wrong, stat) }
            val forged = item.clone().also { it.type = Material.DIAMOND_SWORD }
            val before = forged.clone()
            assertFalse(plugin.service.increment(forged, stat))
            assertEquals(before, forged)
            player.addAttachment(plugin, "itemsignature.track.$id", false)
            assertThrows(InputFailure::class.java) { plugin.service.track(player, ItemStack(Material.valueOf(name)), stat) }
        }
    }

    @Test fun `every real material variant is accepted and old generic trackers remain unrestricted`() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        val player = server.addPlayer()
        for ((id, suffix) in mapOf("distance_walked" to "_BOOTS", "shields_disabled" to "_AXE", "land_tilled" to "_HOE", "times_lunged" to "_SPEAR")) {
            val stat = Stat.from(id) ?: fail("Missing $id")
            player.addAttachment(plugin, "itemsignature.track.$id", true)
            for (material in Material.entries.filter { !it.isLegacy && it.name.endsWith(suffix) }) {
                plugin.service.track(player, ItemStack(material), stat)
            }
        }
        for (stat in listOf(Stat.PLAYER_KILLS, Stat.MOB_KILLS, Stat.BLOCKS_BROKEN)) {
            player.addAttachment(plugin, "itemsignature.track.${stat.id}", true)
            plugin.service.track(player, ItemStack(Material.STONE), stat)
        }
    }
}
