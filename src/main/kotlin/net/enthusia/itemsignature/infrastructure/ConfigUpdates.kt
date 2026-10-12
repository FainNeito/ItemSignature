package net.enthusia.itemsignature.infrastructure

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Add defaults without confusing an explicit false, empty list or custom parent with absence. */
internal object ConfigUpdates {
    fun merge(yaml: YamlConfiguration, defaults: YamlConfiguration): Boolean {
        var changed = false
        for (key in defaults.getKeys(true).filterNot(defaults::isConfigurationSection)) {
            val parents = key.split('.').dropLast(1).runningReduce { parent, part -> "$parent.$part" }
            if (!yaml.contains(key, true) && parents.none { yaml.contains(it, true) && !yaml.isConfigurationSection(it) }) {
                yaml.set(key, defaults.get(key))
                changed = true
            }
        }
        val obsolete = mapOf(
            "messages.invalid-stat" to setOf("<red>Choose player_kills, mob_kills, or blocks_broken."),
            "messages.help" to setOf("<yellow>/sign [message], /sign confirm, /sign cancel, /track <player_kills|mob_kills|blocks_broken>")
        )
        for ((key, stock) in obsolete) {
            if (yaml.getString(key) in stock && yaml.get(key) != defaults.get(key)) {
                yaml.set(key, defaults.get(key))
                changed = true
            }
        }
        yaml.setDefaults(defaults)
        return changed
    }

    /** Publish only a complete validated configuration; unsupported atomic moves fail closed. */
    fun save(file: File, yaml: YamlConfiguration) {
        val path = file.toPath()
        val staged = Files.createTempFile(path.parent, ".config-update-", ".yml")
        try {
            yaml.save(staged.toFile())
            Files.copy(path, path.resolveSibling("config.yml.bak"), StandardCopyOption.REPLACE_EXISTING)
            Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(staged) }
    }
}
