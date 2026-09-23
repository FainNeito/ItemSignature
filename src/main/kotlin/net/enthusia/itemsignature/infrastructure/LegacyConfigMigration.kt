package net.enthusia.itemsignature.infrastructure

import java.nio.file.Files
import java.nio.file.Path

/** Preserve an existing installation's settings when Bukkit changes the data folder name. */
internal object LegacyConfigMigration {
    fun copyIfMissing(legacyConfig: Path, currentConfig: Path) {
        if (!Files.exists(legacyConfig) || Files.exists(currentConfig)) return
        Files.createDirectories(currentConfig.parent)
        Files.copy(legacyConfig, currentConfig)
    }
}
