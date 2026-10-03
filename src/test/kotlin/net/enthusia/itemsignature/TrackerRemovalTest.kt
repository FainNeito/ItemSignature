package net.enthusia.itemsignature

import net.enthusia.itemsignature.infrastructure.*
import net.enthusia.itemsignature.domain.Stat
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit

class TrackerRemovalTest {
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    @Test fun `removal defaults disabled then only placer can remove without losing signature or custom lore`() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        val owner = server.addPlayer()
        val other = server.addPlayer()
        owner.addAttachment(plugin, "itemsignature.track.mob_kills", true)
        owner.addAttachment(plugin, "itemsignature.track.remove", true)
        other.addAttachment(plugin, "itemsignature.track.remove", true)
        val item = ItemStack(Material.DIAMOND_SWORD)
        item.itemMeta = item.itemMeta!!.also { it.lore(listOf(Component.text("Original lore"))) }
        plugin.service.sign(owner, item, emptyList())
        plugin.service.track(owner, item, Stat.MOB_KILLS)
        val before = item.clone()
        owner.inventory.setItemInMainHand(item)
        server.dispatchCommand(owner, "track remove")
        assertEquals(before, owner.inventory.itemInMainHand)
        plugin.service.settings.yaml.set("settings.tracking.allow-removal", true)
        plugin.service.settings.yaml.save(java.io.File(plugin.dataFolder, "config.yml"))
        server.dispatchCommand(server.consoleSender, "enthusiasignature reload")
        other.inventory.setItemInMainHand(item)
        server.dispatchCommand(other, "track remove")
        assertEquals(before, other.inventory.itemInMainHand)
        server.dispatchCommand(owner, "track remove")
        val removed = owner.inventory.itemInMainHand
        assertNull(ItemData.string(removed.itemMeta!!, "stat"))
        assertNull(ItemData.string(removed.itemMeta!!, "tracker_id"))
        assertNull(ItemData.string(removed.itemMeta!!, "tracker_owner"))
        assertNull(ItemData.number(removed.itemMeta!!, "value"))
        assertEquals(owner.uniqueId.toString(), ItemData.string(removed.itemMeta!!, "signer_uuid"))
        assertEquals(3, removed.itemMeta!!.lore()!!.size)
        assertEquals(Component.text("Original lore"), removed.itemMeta!!.lore()!!.first())
        plugin.service.track(owner, removed, Stat.MOB_KILLS)
        assertEquals(0L, ItemData.number(removed.itemMeta!!, "value"))
    }

    @Test fun `removal rejects missing owner legacy trackers diary items and missing permission`() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        val player = server.addPlayer()
        player.addAttachment(plugin, "itemsignature.track.mob_kills", true)
        player.addAttachment(plugin, "itemsignature.track.remove", false)
        plugin.service.settings.yaml.set("settings.tracking.allow-removal", true)
        plugin.service.settings.yaml.save(java.io.File(plugin.dataFolder, "config.yml"))
        server.dispatchCommand(server.consoleSender, "enthusiasignature reload")
        val item = ItemStack(Material.DIAMOND_SWORD)
        plugin.service.track(player, item, Stat.MOB_KILLS)
        player.inventory.setItemInMainHand(item)
        server.dispatchCommand(player, "track remove")
        assertEquals(item, player.inventory.itemInMainHand)
        player.addAttachment(plugin, "itemsignature.track.remove", true)
        item.itemMeta = item.itemMeta!!.also { it.persistentDataContainer.remove(ItemData.key("tracker_owner")) }
        player.inventory.setItemInMainHand(item)
        server.dispatchCommand(player, "track remove")
        assertEquals(item, player.inventory.itemInMainHand)
        val diary = ItemStack(Material.DIAMOND_SWORD)
        plugin.service.track(player, diary, Stat.MOB_KILLS)
        diary.itemMeta = diary.itemMeta!!.also {
            it.persistentDataContainer.set(org.bukkit.NamespacedKey("diarykeeper", "is_diary"), org.bukkit.persistence.PersistentDataType.BOOLEAN, true)
        }
        player.inventory.setItemInMainHand(diary)
        server.dispatchCommand(player, "track remove")
        assertEquals(diary, player.inventory.itemInMainHand)
    }
}
