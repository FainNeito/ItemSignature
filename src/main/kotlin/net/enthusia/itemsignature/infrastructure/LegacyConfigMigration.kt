package net.enthusia.itemsignature.infrastructure

import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException

/** Preserve an existing installation's settings when Bukkit changes the data folder name. */
internal object LegacyConfigMigration {
    fun copyIfMissing(legacyConfig: Path, currentConfig: Path) {
        if (Files.exists(currentConfig)) return
        if (!Files.notExists(currentConfig)) throw IOException("Cannot determine whether the current config exists: $currentConfig")
        val legacyFolder = try {
            Files.readAttributes(legacyConfig.parent, BasicFileAttributes::class.java)
        } catch (_: NoSuchFileException) {
            return
        }
        if (!legacyFolder.isDirectory) throw IOException("Legacy config parent is not a directory: ${legacyConfig.parent}")
        val legacy = try {
            Files.readAttributes(legacyConfig, BasicFileAttributes::class.java)
        } catch (_: NoSuchFileException) {
            return
        }
        if (!legacy.isRegularFile) throw IOException("Legacy config is not a regular file: $legacyConfig")
        Files.createDirectories(currentConfig.parent)
        val staged = Files.createTempFile(currentConfig.parent, ".enthusiasignature-", ".tmp")
        try {
            Files.copy(legacyConfig, staged, StandardCopyOption.REPLACE_EXISTING)
            publishWithoutReplacing(staged, currentConfig)
        } finally {
            Files.deleteIfExists(staged)
        }
    }

    /** A hard link publishes the complete staged file as one no-replace directory operation. */
    internal fun publishWithoutReplacing(staged: Path, currentConfig: Path) {
        try {
            Files.createLink(currentConfig, staged)
        } catch (_: FileAlreadyExistsException) {
            // Another writer's configuration is authoritative.
        }
    }
}
