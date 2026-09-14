package net.enthusia.itemsignature

import net.enthusia.itemsignature.infrastructure.*
import net.enthusia.itemsignature.domain.Stat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType as Type
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ItemSignatureTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: ItemSignaturePlugin
    private lateinit var player: PlayerMock
    private lateinit var item: ItemStack
    private val service get() = plugin.service
    private fun plain(component: Component) = PlainTextComponentSerializer.plainText().serialize(component)
    private fun seed(text: String) { val meta = item.itemMeta!!; meta.lore(listOf(Component.text(text))); item.itemMeta = meta }
    private fun lore() = item.itemMeta!!.lore()!!.map(::plain)
    private fun grant(vararg nodes: String) { nodes.forEach { player.addAttachment(plugin, "itemsignature.$it", true) } }
    private fun failure(key: String, action: () -> Unit) {
        assertEquals(key, assertThrows(InputFailure::class.java, action).messageKey)
    }

    @BeforeEach fun setup() {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        player = server.addPlayer("Alice")
        player.isOp = false
        item = ItemStack(Material.DIAMOND_SWORD)
    }
    @AfterEach fun teardown() { MockBukkit.unmock() }

    @Test fun `basic players sign and preserve existing lore without extra grants`() {
        seed("A precious keepsake")
        service.sign(player, item, emptyList())
        assertEquals("A precious keepsake", lore().first())
        assertEquals(player.uniqueId.toString(), ItemData.string(item.itemMeta!!, "signer_uuid"))
        assertTrue(lore().any { it.contains("Signed by Alice") })
    }

    @ParameterizedTest @ValueSource(strings = ["is_diary", "diary_id"])
    fun `all mutations reject a DiaryKeeper marker even without DiaryKeeper installed`(marker: String) {
        item = ItemStack(Material.WRITABLE_BOOK)
        val meta = item.itemMeta!!
        meta.persistentDataContainer.set(NamespacedKey("diarykeeper", marker), Type.STRING, "legacy-or-current")
        meta.lore(listOf(Component.text("Diary protected lore")))
        item.itemMeta = meta
        val before = item.clone()
        grant("track.blocks_broken")
        failure("protected-item") { service.sign(player, item, emptyList()) }
        failure("protected-item") { service.track(player, item, Stat.BLOCKS_BROKEN) }
        assertFalse(service.increment(item, Stat.BLOCKS_BROKEN))
        assertEquals(before, item)
    }

    @Test fun `EnthusiaLoreItems identity and custom metadata survive edits signing and stats`() {
        grant("track.mob_kills")
        val meta = item.itemMeta!!
        val id = NamespacedKey("enthusialoreitems", "instance_id")
        val bytes = ByteArray(16) { it.toByte() }
        meta.persistentDataContainer.set(id, Type.BYTE_ARRAY, bytes)
        meta.displayName(Component.text("Relic", NamedTextColor.GOLD))
        meta.lore(listOf(Component.text("Original relic lore")))
        item.itemMeta = meta
        service.sign(player, item, emptyList())
        service.track(player, item, Stat.MOB_KILLS)
        service.increment(item, Stat.MOB_KILLS)
        assertArrayEquals(bytes, item.itemMeta!!.persistentDataContainer.get(id, Type.BYTE_ARRAY))
        assertEquals(meta.displayName(), item.itemMeta!!.displayName())
        assertEquals("Original relic lore", lore().first())
    }


    @Test fun `signature cannot be replaced after trading and data stays on cloned item`() {
        val fixed = ItemService(service.settings, service.renderer, Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneOffset.UTC))
        fixed.sign(player, item, emptyList())
        item = item.clone()
        val buyer = server.addPlayer("Bob")
        failure("already-signed") { service.sign(buyer, item, listOf("replacement")) }
        assertEquals("Alice", ItemData.string(item.itemMeta!!, "signer_name"))
        assertEquals(1789311600000L, ItemData.number(item.itemMeta!!, "signed_at"))
        assertTrue(lore().any { it.contains("2026-09-13") })
    }

    @Test fun `item metadata roundtrips through Bukkit streams including counters and signer`() {
        grant("track.blocks_broken")
        service.sign(player, item, emptyList())
        service.track(player, item, Stat.BLOCKS_BROKEN)
        repeat(3) { service.increment(item, Stat.BLOCKS_BROKEN) }
        // MockBukkit's YAML adapter narrows small Long values to Integer. Use Bukkit's
        // object stream to exercise a disk-equivalent roundtrip with intact PDC types.
        val bytes = java.io.ByteArrayOutputStream()
        org.bukkit.util.io.BukkitObjectOutputStream(bytes).use { it.writeObject(item) }
        val restored = org.bukkit.util.io.BukkitObjectInputStream(java.io.ByteArrayInputStream(bytes.toByteArray())).use {
            it.readObject() as ItemStack
        }
        assertEquals(item.type, restored.type)
        assertEquals(item.amount, restored.amount)
        assertEquals(item.itemMeta!!.serialize(), restored.itemMeta!!.serialize())
        // Recreate the service to ensure nothing depends on an in-memory cache.
        ItemService(service.settings, service.renderer).increment(restored, Stat.BLOCKS_BROKEN)
        assertEquals(4L, ItemData.number(restored.itemMeta!!, "value"))
        assertEquals(player.uniqueId.toString(), ItemData.string(restored.itemMeta!!, "signer_uuid"))
    }

    @ParameterizedTest @ValueSource(strings = ["Admin", "fOuNdEr", "A<red>dm</red>in", "A d m i n", "Ａｄｍｉｎ"])
    fun `blacklist rejects case formatting spacing and compatibility unicode`(text: String) {
        grant("color")
        val before = item.itemMeta!!.serialize()
        failure("text-blacklisted") { service.renderer.user(text, player) }
        assertEquals(before, item.itemMeta!!.serialize())
    }

    @ParameterizedTest @ValueSource(strings = ["a\nb", "a\rb", "A\u200bdmin", "\ue000", "§aColor", "<click:run_command:'/op Alice'>x</click>", "<newline>"])
    fun `unsafe formatting never becomes an item component`(text: String) {
        failure("invalid-text") { service.renderer.user(text, player) }
    }

    @Test fun `rank permissions independently gate colors hex quotes and trackers`() {
        failure("no-permission") { service.renderer.user("<green>Green", player) }
        failure("no-permission") { service.sign(player, item, listOf("A quote")) }
        failure("no-permission") { service.track(player, item, Stat.PLAYER_KILLS) }
        grant("color")
        service.renderer.user("<green>Green", player)
        failure("no-permission") { service.renderer.user("<#FF0000>Red", player) }
        grant("hex", "quote")
        service.sign(player, item, listOf("--color", "#FF0000", "Remember me"))
        assertTrue(lore().any { it.contains("Remember me") })
    }

    @Test fun `glyph syntax routes only permitted IDs to resolver`() {
        val requested = mutableListOf<String>()
        val renderer = TextRenderer(service.settings) { id, _ -> requested += id; if (id == "sword") Component.text("SWORD") else null }
        failure("no-permission") { renderer.user(":sword:", player) }
        assertTrue(requested.isEmpty())
        grant("glyph")
        for (syntax in listOf(":sword:", "%nexo_sword%", "<glyph:sword>")) assertEquals("SWORD", plain(renderer.user(syntax, player)))
        failure("glyph-unavailable") { renderer.user("<glyph:private>", player) }
        assertEquals("%some_other_expansion%", plain(renderer.user("%some_other_expansion%", player)))
    }

    @Test fun `missing Nexo uses a fallback icon and rejects user glyphs`() {
        grant("glyph", "track.mob_kills")
        failure("glyph-unavailable") { service.renderer.user(":sword:", player) }
        service.track(player, item, Stat.MOB_KILLS)
        assertTrue(lore().last().startsWith("☠"))
        assertFalse(lore().last().contains("%nexo"))
    }


    @Test fun `stat values use PDC not visible lore and saturate rather than overflow`() {
        grant("track.mob_kills")
        service.track(player, item, Stat.MOB_KILLS)
        var meta = item.itemMeta!!
        meta.lore(listOf(Component.text("Mob Kills: 99999")))
        item.itemMeta = meta
        service.increment(item, Stat.MOB_KILLS)
        assertEquals(1L, ItemData.number(item.itemMeta!!, "value"))
        assertEquals("Mob Kills: 99999", lore().first())
        meta = item.itemMeta!!
        ItemData.set(meta, "value", Long.MAX_VALUE)
        item.itemMeta = meta
        assertFalse(service.increment(item, Stat.MOB_KILLS))
        assertEquals(Long.MAX_VALUE, ItemData.number(item.itemMeta!!, "value"))
    }

    @Test fun `wrong stat duplicate tracker air stacks and corrupt data are rejected`() {
        grant("track.mob_kills", "track.blocks_broken")
        service.track(player, item, Stat.MOB_KILLS)
        failure("tracker-already-exists") { service.track(player, item, Stat.BLOCKS_BROKEN) }
        assertFalse(service.increment(item, Stat.BLOCKS_BROKEN))
        failure("invalid-item") { service.sign(player, ItemStack(Material.AIR), emptyList()) }
        failure("stacked-item") { service.sign(player, ItemStack(Material.STONE, 2), emptyList()) }
        val meta = item.itemMeta!!
        ItemData.set(meta, "value", "not-a-long")
        item.itemMeta = meta
        failure("data-error") { service.sign(player, item, emptyList()) }
    }

    @Test fun `lore updates from another plugin are retained on tracker redraw`() {
        grant("track.mob_kills")
        seed("Original")
        service.track(player, item, Stat.MOB_KILLS)
        val meta = item.itemMeta!!
        meta.lore(listOf(Component.text("Changed externally")) + meta.lore()!!.drop(1))
        item.itemMeta = meta
        service.increment(item, Stat.MOB_KILLS)
        assertEquals("Changed externally", lore().first())
        assertEquals(2, lore().size)
    }

    @Test fun `commands mutate actual main hand item and console receives a clear response`() {
        player.inventory.setItemInMainHand(item)
        assertTrue(server.dispatchCommand(player, "sign"))
        assertTrue(server.dispatchCommand(player, "sign confirm"))
        assertNotNull(ItemData.string(player.inventory.itemInMainHand.itemMeta!!, "signer_uuid"))
        assertTrue(server.dispatchCommand(server.consoleSender, "sign"))
    }

    @Test fun `invalid reload preserves working settings and valid reload replaces them`() {
        val file = java.io.File(plugin.dataFolder, "config.yml")
        val original = file.readText()
        val previous = service
        file.writeText(original.replace("max-text-length: 160", "max-text-length: -1"))
        server.dispatchCommand(server.consoleSender, "itemsignature reload")
        assertSame(previous, service)
        file.writeText(original.replace("max-text-length: 160", "max-text-length: 170"))
        server.dispatchCommand(server.consoleSender, "itemsignature reload")
        assertNotSame(previous, service)
        assertEquals(170, service.settings.maxLength)
    }

    @Test fun `old resign configuration cannot replace a permanent signature`() {
        grant("track.mob_kills")
        seed("Keepsake")
        service.sign(player, item, emptyList())
        service.track(player, item, Stat.MOB_KILLS)
        service.increment(item, Stat.MOB_KILLS)
        val yaml = YamlConfiguration().apply { loadFromString(service.settings.yaml.saveToString()); setDefaults(service.settings.yaml); set("settings.signing.one-time-sign", false) }
        val settings = Settings(yaml)
        val replacement = ItemService(settings, TextRenderer(settings) { _, _ -> null })
        failure("already-signed") { replacement.sign(server.addPlayer("Bob"), item, emptyList()) }
        assertEquals("Alice", ItemData.string(item.itemMeta!!, "signer_name"))
        assertEquals(1L, ItemData.number(item.itemMeta!!, "value"))
        assertEquals(4, lore().size)
        assertEquals("Keepsake", lore().first())
    }
}
