package net.enthusia.itemsignature.infrastructure

import net.enthusia.itemsignature.domain.Stat

import org.bukkit.configuration.file.YamlConfiguration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class Settings(val yaml: YamlConfiguration) {
    val maxLength = (if (yaml.contains("settings.lore.max-text-length", true)) yaml.getInt("settings.lore.max-text-length")
        else yaml.getInt("settings.signing.max-text-length", 160)).also { require(it in 1..2000) }
    val blacklist = if (yaml.contains("settings.lore.blacklisted-words", true)) yaml.getStringList("settings.lore.blacklisted-words") else yaml.getStringList("settings.signing.blacklisted-words")
    val nameColor = yaml.getString("settings.signing.default-name-color", "<gray>")!!
    val dateFormat = DateTimeFormatter.ofPattern(yaml.getString("settings.signing.date-format", "yyyy-MM-dd")!!)
    val zone = ZoneId.of(yaml.getString("settings.signing.timezone", "UTC"))
    val nexo = yaml.getBoolean("settings.nexo-integration.enabled", true)
    val discord = yaml.getBoolean("settings.nexo-integration.use-discord-style-tags", true)
    val countCreative = yaml.getBoolean("settings.tracking.count-creative", false)
    init {
        listOf("signature.basic", "signature.custom", "signature.quote", "signature.date-stamp", "tracking.stat-line").forEach {
            require(yaml.isString("formats.$it")) { "Missing or invalid format: $it" }
        }
        dateFormat.format(java.time.Instant.EPOCH.atZone(zone))
        // A template must always remain a single lore line.
        yaml.getConfigurationSection("formats")?.getValues(true)?.values?.filterIsInstance<String>()?.forEach {
            require(!it.contains('\n') && !it.contains('\r')) { "Formats cannot contain newlines" }
        }
    }
    fun format(path: String) = yaml.getString("formats.$path") ?: error("Missing format: $path")
    fun statName(stat: Stat) = yaml.getString("formats.tracking.stat-names.${stat.id}.name", stat.label)!!
}

class InputFailure(val messageKey: String, val replacements: Map<String, String> = emptyMap()) : RuntimeException(messageKey)
