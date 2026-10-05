package net.enthusia.itemsignature

import net.enthusia.itemsignature.infrastructure.*
import net.enthusia.itemsignature.domain.Stat
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockito.Mockito.*
import io.papermc.paper.event.entity.EntityLungeEvent

class LungeTrackerTest {
    @AfterEach fun close() { MockBukkit.unmock() }
    @Test fun `native spear lunge credits the used spear only and ignores cancellation and zero power`() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        val player = spy(server.addPlayer())
        player.gameMode = GameMode.SURVIVAL
        player.addAttachment(plugin, "itemsignature.track.times_lunged", true)
        val spear = ItemStack(Material.IRON_SPEAR)
        plugin.service.track(player, spear, Stat.TIMES_LUNGED)
        player.inventory.setItemInOffHand(spear)
        doReturn(spear).`when`(player).activeItem
        doReturn(EquipmentSlot.OFF_HAND).`when`(player).activeItemHand
        server.pluginManager.callEvent(EntityLungeEvent(player, 1).apply { isCancelled = true })
        server.pluginManager.callEvent(EntityLungeEvent(player, 0))
        server.pluginManager.callEvent(EntityLungeEvent(player, 1))
        assertEquals(1L, ItemData.number(player.inventory.itemInOffHand.itemMeta!!, "value"))
        player.gameMode = GameMode.CREATIVE
        server.pluginManager.callEvent(EntityLungeEvent(player, 1))
        assertEquals(1L, ItemData.number(player.inventory.itemInOffHand.itemMeta!!, "value"))
    }

    @Test fun `discovery returns unavailable when native event is absent`() {
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == "io.papermc.paper.event.entity.EntityLungeEvent") throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        assertNull(LungeTrackingBridge.discover(loader))
        assertNotNull(LungeTrackingBridge.discover(javaClass.classLoader))
    }

    @Test fun `unsupported runtime refuses command attachment and hides lunge completion`() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        val player = server.addPlayer()
        player.addAttachment(plugin, "itemsignature.track.times_lunged", true)
        val spear = ItemStack(Material.WOODEN_SPEAR)
        player.inventory.setItemInMainHand(spear)
        // Simulate the discovery result separately from the native-event fixture.
        val bridge = ItemSignaturePlugin::class.java.getDeclaredField("lunge").also { it.isAccessible = true }.get(plugin)
        LungeTrackingBridge::class.java.getDeclaredField("eventType").also { it.isAccessible = true }.set(bridge, null)
        server.dispatchCommand(player, "track times_lunged")
        assertEquals(spear, player.inventory.itemInMainHand)
        assertFalse(plugin.onTabComplete(player, plugin.getCommand("track")!!, "track", arrayOf("times_")).contains("times_lunged"))
    }
}
