package net.enthusia.itemsignature

import net.enthusia.itemsignature.infrastructure.*
import net.enthusia.itemsignature.domain.Stat
import org.bukkit.*
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockito.Mockito.*

class DistanceTrackerTest {
    private lateinit var server: org.mockbukkit.mockbukkit.ServerMock
    private lateinit var plugin: ItemSignaturePlugin
    private lateinit var player: org.mockbukkit.mockbukkit.entity.PlayerMock
    private lateinit var world: World
    @BeforeEach fun setup() {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        player = spy(server.addPlayer())
        player.gameMode = GameMode.SURVIVAL
        doReturn(true).`when`(player).isOnGround
        world = server.addSimpleWorld("distance")
        listOf(Stat.DISTANCE_FLOWN, Stat.DISTANCE_WALKED).forEach { player.addAttachment(plugin, "itemsignature.track.${it.id}", true) }
    }
    @AfterEach fun close() { MockBukkit.unmock() }
    private fun equip(stat: Stat, material: Material, slot: EquipmentSlot) {
        val item = ItemStack(material)
        plugin.service.track(player, item, stat)
        player.inventory.setItem(slot, item)
    }
    private fun value(slot: EquipmentSlot) = ItemData.number(player.inventory.getItem(slot).itemMeta!!, "value")
    private fun move(x: Double, y: Double = 0.0, z: Double = 0.0, cancelled: Boolean = false) {
        server.pluginManager.callEvent(PlayerMoveEvent(player, Location(world, 0.0, 64.0, 0.0), Location(world, x, 64.0 + y, z)).apply { isCancelled = cancelled })
    }

    @Test fun `boots count horizontal ground distance and elytra counts three dimensional gliding distance`() {
        equip(Stat.DISTANCE_WALKED, Material.LEATHER_BOOTS, EquipmentSlot.FEET)
        equip(Stat.DISTANCE_FLOWN, Material.ELYTRA, EquipmentSlot.CHEST)
        move(3.0, 0.0, 4.0)
        assertEquals(500L, value(EquipmentSlot.FEET))
        assertEquals(0L, value(EquipmentSlot.CHEST))
        player.isGliding = true
        move(3.0, 4.0)
        assertEquals(500L, value(EquipmentSlot.CHEST))
        assertEquals(500L, value(EquipmentSlot.FEET))
        assertTrue(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(player.inventory.chestplate!!.itemMeta!!.lore()!!.last()).endsWith("5.00 m"))
    }

    @Test fun `distance ignores rotation teleports other worlds cancelled flight riding and creative moves`() {
        equip(Stat.DISTANCE_WALKED, Material.DIAMOND_BOOTS, EquipmentSlot.FEET)
        move(5.0, cancelled = true)
        move(0.0)
        server.pluginManager.callEvent(PlayerTeleportEvent(player, Location(world, 0.0, 64.0, 0.0), Location(world, 500.0, 64.0, 0.0)))
        server.pluginManager.callEvent(PlayerMoveEvent(player, Location(world, 0.0, 64.0, 0.0), Location(server.addSimpleWorld("other"), 5.0, 64.0, 0.0)))
        player.gameMode = GameMode.CREATIVE
        move(5.0)
        player.gameMode = GameMode.SURVIVAL
        doReturn(true).`when`(player).isInsideVehicle
        move(5.0)
        doReturn(false).`when`(player).isInsideVehicle
        doReturn(true).`when`(player).isFlying
        move(5.0)
        assertEquals(0L, value(EquipmentSlot.FEET))
    }

    @Test fun `subcentimeter distances accumulate on the item without duplicating lore`() {
        equip(Stat.DISTANCE_WALKED, Material.COPPER_BOOTS, EquipmentSlot.FEET)
        repeat(10) { move(0.004) }
        assertEquals(4L, value(EquipmentSlot.FEET))
        val item = player.inventory.boots!!
        assertEquals(1, item.itemMeta!!.lore()!!.size)
        val meta = item.itemMeta!!
        ItemData.set(meta, "value", Long.MAX_VALUE - 1)
        item.itemMeta = meta
        player.inventory.boots = item
        move(100.0)
        assertEquals(Long.MAX_VALUE, value(EquipmentSlot.FEET))
    }
}
