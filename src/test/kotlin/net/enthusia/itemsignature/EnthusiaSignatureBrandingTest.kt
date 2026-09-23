package net.enthusia.itemsignature

import net.enthusia.itemsignature.infrastructure.ItemSignaturePlugin
import net.enthusia.itemsignature.infrastructure.LegacyConfigMigration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class EnthusiaSignatureBrandingTest {
    @TempDir lateinit var temp: Path

    @Test fun `public name and both admin commands are registered`() {
        val server = MockBukkit.mock()
        try {
            val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
            assertEquals("EnthusiaSignature", plugin.description.name)
            assertNotNull(server.getPluginCommand("itemsignature"))
            assertNotNull(server.getPluginCommand("enthusiasignature"))
            assertTrue(plugin.dataFolder.name.startsWith("EnthusiaSignature"))
        } finally {
            MockBukkit.unmock()
        }
    }

    @Test fun `legacy config is copied only once and old copy remains untouched`() {
        val legacy = temp.resolve("ItemSignature/config.yml")
        val current = temp.resolve("EnthusiaSignature/config.yml")
        Files.createDirectories(legacy.parent)
        Files.writeString(legacy, "marker: legacy\n")

        LegacyConfigMigration.copyIfMissing(legacy, current)
        assertEquals("marker: legacy\n", Files.readString(current))
        assertEquals("marker: legacy\n", Files.readString(legacy))

        Files.writeString(current, "marker: current\n")
        LegacyConfigMigration.copyIfMissing(legacy, current)
        assertEquals("marker: current\n", Files.readString(current))
        assertEquals("marker: legacy\n", Files.readString(legacy))
    }

    @Test fun `missing legacy config does not create an empty new config`() {
        val current = temp.resolve("EnthusiaSignature/config.yml")
        LegacyConfigMigration.copyIfMissing(temp.resolve("ItemSignature/config.yml"), current)
        assertFalse(Files.exists(current))
    }

    @Test fun `migration failure is reported instead of silently using defaults`() {
        val legacy = temp.resolve("ItemSignature/config.yml")
        Files.createDirectories(legacy.parent)
        Files.writeString(legacy, "marker: legacy\n")
        val blocked = temp.resolve("blocked")
        Files.writeString(blocked, "not a directory")
        assertThrows(IOException::class.java) {
            LegacyConfigMigration.copyIfMissing(legacy, blocked.resolve("config.yml"))
        }
        assertTrue(Files.exists(legacy))
    }

    @Test fun `uninspectable legacy path is not mistaken for a missing config`() {
        val parentFile = temp.resolve("not-a-directory")
        Files.writeString(parentFile, "blocked")
        val current = temp.resolve("EnthusiaSignature/config.yml")
        assertThrows(IOException::class.java) {
            LegacyConfigMigration.copyIfMissing(parentFile.resolve("config.yml"), current)
        }
        assertFalse(Files.exists(current))
    }

    @Test fun `plugin startup loads the legacy settings before generating defaults`() {
        val server = MockBukkit.mock()
        try {
            val pluginRoot = server.pluginManager.parentTemporaryDirectory.toPath()
            val legacy = pluginRoot.resolve("ItemSignature/config.yml")
            Files.createDirectories(legacy.parent)
            Files.writeString(legacy, "settings:\n  signing:\n    max-text-length: 73\n")
            val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
            assertEquals(73, plugin.service.settings.maxLength)
            assertEquals(Files.readString(legacy), Files.readString(plugin.dataFolder.toPath().resolve("config.yml")))
        } finally {
            MockBukkit.unmock()
        }
    }
}
