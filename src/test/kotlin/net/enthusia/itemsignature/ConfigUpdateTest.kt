package net.enthusia.itemsignature

import net.enthusia.itemsignature.infrastructure.ItemSignaturePlugin
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.io.File

class ConfigUpdateTest {
    @Test fun `reload persists missing defaults and refreshes only known stock help`() {
        val server = MockBukkit.mock()
        try {
            val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
            val file = File(plugin.dataFolder, "config.yml")
            val yaml = YamlConfiguration.loadConfiguration(file)
            yaml.set("settings.prefix", "<gold>My custom prefix")
            yaml.set("settings.signing.blacklisted-words", emptyList<String>())
            yaml.set("settings.tracking.count-creative", true)
            yaml.set("settings.tracking.allow-removal", null)
            yaml.set("messages.help", "<yellow>/sign [message], /sign confirm, /sign cancel, /track <player_kills|mob_kills|blocks_broken>")
            yaml.set("messages.invalid-stat", "<red>Choose player_kills, mob_kills, or blocks_broken.")
            yaml.set("messages.sign-usage", "<yellow>Use /sign with &a to add your custom shop message")
            yaml.save(file)
            val original = file.readBytes()
            assertTrue(server.dispatchCommand(server.consoleSender, "esign reload"))
            assertArrayEquals(original, File(plugin.dataFolder, "config.yml.bak").readBytes())
            val updated = YamlConfiguration.loadConfiguration(file)
            assertTrue(updated.contains("settings.tracking.allow-removal", true))
            assertFalse(updated.getBoolean("settings.tracking.allow-removal"))
            assertTrue(updated.getBoolean("settings.tracking.count-creative"))
            assertEquals(emptyList<String>(), updated.getStringList("settings.signing.blacklisted-words"))
            assertEquals("<gold>My custom prefix", updated.getString("settings.prefix"))
            assertTrue(updated.getString("messages.help")!!.contains("tab"))
            assertTrue(updated.getString("messages.invalid-stat")!!.contains("tab"))
            assertEquals("<yellow>Use /sign with &a to add your custom shop message", updated.getString("messages.sign-usage"))
            val bytes = file.readBytes()
            server.dispatchCommand(server.consoleSender, "esign reload")
            assertArrayEquals(bytes, file.readBytes())
        } finally { MockBukkit.unmock() }
    }

    @Test fun `custom messages stay intact and invalid reload never rewrites config`() {
        val server = MockBukkit.mock()
        try {
            val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
            val file = File(plugin.dataFolder, "config.yml")
            val yaml = YamlConfiguration.loadConfiguration(file)
            yaml.set("messages.help", "<yellow>My custom /lore and /track guide")
            yaml.set("messages.invalid-stat", "<red>Ask our staff for tracker help")
            yaml.save(file)
            server.dispatchCommand(server.consoleSender, "esign reload")
            assertEquals("<yellow>My custom /lore and /track guide", plugin.service.settings.yaml.getString("messages.help"))
            assertEquals("<red>Ask our staff for tracker help", plugin.service.settings.yaml.getString("messages.invalid-stat"))
            val previous = plugin.service
            yaml.set("settings.signing.timezone", "Not/AZone")
            yaml.set("settings.tracking.allow-removal", null)
            yaml.save(file)
            val invalidBytes = file.readBytes()
            server.dispatchCommand(server.consoleSender, "esign reload")
            assertSame(previous, plugin.service)
            assertArrayEquals(invalidBytes, file.readBytes())
        } finally { MockBukkit.unmock() }
    }
}
