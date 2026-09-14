package net.enthusia.itemsignature

import net.enthusia.itemsignature.infrastructure.*
import net.enthusia.itemsignature.domain.Stat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit

class LoreRepairTest {
    private lateinit var plugin: ItemSignaturePlugin
    private lateinit var item: ItemStack
    private lateinit var player: org.mockbukkit.mockbukkit.entity.PlayerMock
    private val service get() = plugin.service
    private fun plain(c: Component) = PlainTextComponentSerializer.plainText().serialize(c)
    private fun seed(text: String) { val meta = item.itemMeta!!; meta.lore(listOf(Component.text(text))); item.itemMeta = meta }
    private fun visible() = item.itemMeta!!.lore()!!.map(::plain)
    private fun serverRoundtrip() {
        val meta = item.itemMeta!!
        meta.lore(meta.lore()!!.map { it.compact() })
        item.itemMeta = meta
    }
    @BeforeEach fun setup() {
        val server = MockBukkit.mock()
        plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        player = server.addPlayer("Alice")
        player.addAttachment(plugin, "itemsignature.quote", true)
        Stat.entries.forEach { player.addAttachment(plugin, "itemsignature.track.${it.id}", true) }
        item = ItemStack(Material.DIAMOND_SWORD)
    }
    @AfterEach fun close() { MockBukkit.unmock() }

    @Test fun `normalized item components never duplicate signature or tracker on update`() {
        for (stat in Stat.entries) {
            item = ItemStack(Material.DIAMOND_SWORD)
            seed("Old lore")
            service.sign(player, item, listOf("A keepsake"))
            service.track(player, item, stat)
            repeat(6) {
                serverRoundtrip()
                service.increment(item, stat)
                assertEquals(5, visible().size)
            }
            assertEquals(6L, ItemData.number(item.itemMeta!!, "value"))
        }
    }

    @Test fun `legacy repeated blocks and stale counters repair without changing ownership or custom lore`() {
        seed("Glory!")
        service.sign(player, item, listOf("a"))
        service.track(player, item, Stat.MOB_KILLS)
        val meta = item.itemMeta!!
        val signature = ItemData.lines(meta, "signature")
        val signer = ItemData.string(meta, "signer_uuid")
        val date = ItemData.number(meta, "signed_at")
        val duplicateLines = mutableListOf<Component>(Component.text("Glory!"))
        repeat(3) { n -> duplicateLines += signature; duplicateLines += service.renderer.statLine(Stat.MOB_KILLS, n.toLong()) }
        meta.lore(duplicateLines.map { it.compact() })
        ItemData.set(meta, "version", 1L)
        meta.persistentDataContainer.remove(ItemData.key("editable"))
        ItemData.set(meta, "value", 2L)
        ItemData.setLines(meta, "rendered", signature + service.renderer.statLine(Stat.MOB_KILLS, 2))
        item.itemMeta = meta
        service.increment(item, Stat.MOB_KILLS)
        assertEquals(5, visible().size)
        assertEquals("Glory!", visible().first())
        assertTrue(visible().last().endsWith("Mob Kills: 3"))
        assertEquals(signer, ItemData.string(item.itemMeta!!, "signer_uuid"))
        assertEquals(date, ItemData.number(item.itemMeta!!, "signed_at"))
        serverRoundtrip()
        service.increment(item, Stat.MOB_KILLS)
        assertEquals(5, visible().size)
        assertEquals("Glory!", visible().first())
    }

    @Test fun `known editable text that looks like a managed line is retained`() {
        service.sign(player, item, emptyList())
        val meta = item.itemMeta!!
        val signatureLine = ItemData.lines(meta, "signature").first()
        ItemData.redraw(meta, listOf(signatureLine, Component.text("Mob Kills: 99999")), service.renderer)
        item.itemMeta = meta
        service.track(player, item, Stat.MOB_KILLS)
        repeat(3) { serverRoundtrip(); service.increment(item, Stat.MOB_KILLS) }
        assertEquals(5, visible().size)
        assertEquals(2, visible().count { it.contains("Signed by Alice") })
        assertTrue(visible().contains("Mob Kills: 99999"))
    }
}
