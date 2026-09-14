package net.enthusia.itemsignature

import net.enthusia.itemsignature.infrastructure.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit

class SigningFlowTest {
    private lateinit var server: org.mockbukkit.mockbukkit.ServerMock
    private lateinit var plugin: ItemSignaturePlugin
    private lateinit var player: org.mockbukkit.mockbukkit.entity.PlayerMock
    @BeforeEach fun setup() {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        player = server.addPlayer("Alice")
        player.inventory.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
        player.addAttachment(plugin, "itemsignature.quote", true)
    }
    @AfterEach fun close() { MockBukkit.unmock() }
    private fun command(text: String) { server.dispatchCommand(player, text) }
    private fun signed() = ItemData.string(player.inventory.itemInMainHand.itemMeta!!, "signer_uuid")
    @Test fun `confirmation expires and permissions are rechecked`() {
        plugin.confirmationClock = java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        command("sign Keepsake")
        plugin.confirmationClock = java.time.Clock.fixed(java.time.Instant.EPOCH.plusSeconds(30), java.time.ZoneOffset.UTC)
        command("sign confirm")
        assertNull(signed())
        command("sign Keepsake")
        player.addAttachment(plugin, "itemsignature.quote", false)
        command("sign confirm")
        assertNull(signed())
    }
    @Test fun `template values cannot introduce formatting and legacy configuration still renders`() {
        val rendered = plugin.service.renderer.template("&7%text%", mapOf("text" to Component.text("<red>Literal</red>")))
        assertEquals("<red>Literal</red>", PlainTextComponentSerializer.plainText().serialize(rendered))
    }
    @Test fun `old configuration text limits and blacklist remain effective`() {
        val yaml = org.bukkit.configuration.file.YamlConfiguration()
        yaml.setDefaults(plugin.service.settings.yaml)
        yaml.set("settings.lore.max-text-length", 12)
        yaml.set("settings.lore.blacklisted-words", listOf("Owner"))
        val renderer = TextRenderer(Settings(yaml)) { _, _ -> null }
        assertThrows(InputFailure::class.java) { renderer.user("1234567890123", player) }
        assertThrows(InputFailure::class.java) { renderer.user("Owner", player) }
    }
    @Test fun `lore command and permission are absent`() {
        assertNull(plugin.getCommand("lore"))
        assertFalse(plugin.description.permissions.any { it.name == "itemsignature.lore" })
    }
    @Test fun `sign previews warns then confirms exactly once`() {
        val before = player.inventory.itemInMainHand.clone()
        command("sign A keepsake")
        assertEquals(before, player.inventory.itemInMainHand)
        assertTrue(player.nextMessage()!!.contains("cannot be changed"))
        command("sign confirm")
        assertNotNull(signed())
        val after = player.inventory.itemInMainHand.clone()
        command("sign Replacement")
        command("sign confirm")
        assertEquals(after, player.inventory.itemInMainHand)
    }
    @Test fun `changed item slot cancellation and bare confirmation never sign`() {
        command("sign confirm")
        assertNull(signed())
        command("sign First")
        player.inventory.setItemInMainHand(ItemStack(Material.STONE_SWORD))
        command("sign confirm")
        assertNull(signed())
        command("sign Second")
        player.inventory.setItem(1, player.inventory.itemInMainHand.clone())
        player.inventory.heldItemSlot = 1
        command("sign confirm")
        assertNull(signed())
        command("sign Third")
        command("sign cancel")
        command("sign confirm")
        assertNull(signed())
    }
    @Test fun `MiniMessage colors require ranks and legacy codes are rejected`() {
        val r = plugin.service.renderer
        assertThrows(InputFailure::class.java) { r.user("<red>Hello</red>", player) }
        player.addAttachment(plugin, "itemsignature.color", true)
        assertEquals(Component.text("Hello", NamedTextColor.RED).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false).compact(), r.user("<red>Hello</red>", player).compact())
        assertThrows(InputFailure::class.java) { r.user("<#FF0000>Hello", player) }
        player.addAttachment(plugin, "itemsignature.hex", true)
        assertEquals("Hello", PlainTextComponentSerializer.plainText().serialize(r.user("<#FF0000>Hello", player)))
        for (raw in listOf("&aHello", "&#FF0000Hello", "<click:run_command:'/op Alice'>X</click>", "<font:minecraft:default>X", "<obfuscated>X", "<newline>", "A<red>dm</red>in")) {
            assertThrows(InputFailure::class.java, { r.user(raw, player) }, raw)
        }
    }
}
