package net.enthusia.itemsignature.infrastructure

import net.enthusia.itemsignature.domain.Stat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.Tag
import org.bukkit.entity.Player
import java.text.Normalizer
import java.util.Locale

fun interface GlyphResolver {
    fun resolve(id: String, player: Player?): Component?
    fun containsGlyphCharacters(text: String): Boolean = false
}

/** Restricted MiniMessage: no events, arbitrary fonts, substitutions or multiline tags. */
class TextRenderer(private val settings: Settings, private val glyphs: GlyphResolver) {
    private val mini = MiniMessage.builder().tags(TagResolver.resolver(
        StandardTags.color(), StandardTags.decorations(TextDecoration.BOLD),
        StandardTags.decorations(TextDecoration.ITALIC), StandardTags.decorations(TextDecoration.UNDERLINED),
        StandardTags.decorations(TextDecoration.STRIKETHROUGH), StandardTags.reset()
    )).build()
    private val glyphToken = Regex("(?i)%nexo_([a-z0-9_]+)%|<glyph:([a-z0-9_]+)>|:([a-z0-9_]+):")
    private val legacy = Regex("(?i)&#[0-9a-f]{6}|&[0-9a-fk-or]")
    private val tag = Regex("<(/?)([^<>]+)>")
    private val decorations = setOf("bold", "b", "italic", "i", "em", "underlined", "u", "strikethrough", "st", "reset")

    fun user(raw: String, player: Player): Component {
        if (raw.isBlank() || raw.length > settings.maxLength || raw.codePoints().anyMatch {
            Character.isISOControl(it) || Character.getType(it) in setOf(Character.FORMAT.toInt(), Character.PRIVATE_USE.toInt(), Character.SURROGATE.toInt())
        } || raw.contains('§') || raw.contains('\\') || legacy.containsMatchIn(raw) ||
            (settings.nexo && glyphs.containsGlyphCharacters(raw))) fail("invalid-text")
        val withoutGlyphs = glyphToken.replace(raw, "")
        if (tag.replace(withoutGlyphs, "").any { it == '<' || it == '>' }) fail("invalid-text")
        for (match in tag.findAll(withoutGlyphs)) {
            val name = match.groupValues[2].lowercase(Locale.ROOT)
            when {
                name.startsWith("#") && Regex("#[0-9a-f]{6}").matches(name) -> permission(player, "hex")
                NamedTextColor.NAMES.value(name) != null || name in decorations -> permission(player, "color")
                else -> fail("invalid-text")
            }
        }
        checkBlacklist(tag.replace(withoutGlyphs, ""))
        val component = render(raw, player, emptyMap())
        checkBlacklist(PlainTextComponentSerializer.plainText().serialize(component))
        return component
    }

    private fun checkBlacklist(text: String) {
        fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        val normalized = normalize(text)
        if (settings.blacklist.map(::normalize).any { it.isNotEmpty() && normalized.contains(it) }) fail("text-blacklisted")
    }

    fun nameColor(raw: String, player: Player): TextColor {
        val name = raw.removeSurrounding("<", ">").lowercase(Locale.ROOT)
        val color = if (Regex("#[0-9a-f]{6}").matches(name)) {
            permission(player, "hex")
            TextColor.fromHexString(name)
        } else {
            permission(player, "color")
            NamedTextColor.NAMES.value(name)
        }
        return color ?: fail("sign-usage")
    }

    fun template(raw: String, values: Map<String, Component> = emptyMap()): Component =
        render(upgradeLegacy(raw), null, values)

    // Compatibility only for server-owned configuration from 1.0.x; never used on player input.
    private fun upgradeLegacy(raw: String): String {
        val names = listOf("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white")
        return legacy.replace(raw) {
            val code = it.value[1].lowercaseChar()
            when {
                code == '#' -> "<reset><" + it.value.substring(1) + ">"
                code in "0123456789abcdef" -> "<reset><" + names[code.digitToInt(16)] + ">"
                code == 'r' -> "<reset>"
                code == 'l' -> "<bold>"
                code == 'm' -> "<strikethrough>"
                code == 'n' -> "<underlined>"
                code == 'o' -> "<italic>"
                else -> ""
            }
        }
    }

    private fun render(raw: String, player: Player?, values: Map<String, Component>): Component {
        val resolvers = mutableListOf<TagResolver>()
        var count = 0
        fun insert(component: Component): String {
            val name = "is_value_" + count++
            resolvers += TagResolver.resolver(name, Tag.selfClosingInserting(component))
            return "<$name>"
        }
        var text = Regex("%[a-z_]+%").replace(raw) { match ->
            values[match.value.removeSurrounding("%")]?.let(::insert) ?: match.value
        }
        text = glyphToken.replace(text) { match ->
            if (match.value.startsWith(':') && !settings.discord) {
                if (player != null) fail("glyph-unavailable")
                return@replace ""
            }
            if (player != null) permission(player, "glyph")
            val id = match.groupValues.drop(1).first { it.isNotEmpty() }
            val glyph = if (settings.nexo) glyphs.resolve(id, player) else null
            if (glyph == null && player != null) fail("glyph-unavailable")
            insert(glyph ?: Component.empty())
        }
        return Component.empty().decoration(TextDecoration.ITALIC, false)
            .append(mini.deserialize(text, TagResolver.resolver(resolvers)))
    }

    fun statLine(stat: Stat, value: Long): Component {
        val path = "formats.tracking.stat-names.${stat.id}"
        val icon = template(settings.yaml.getString("$path.icon", "")!!)
        val effectiveIcon = if (PlainTextComponentSerializer.plainText().serialize(icon).isBlank())
            Component.text(settings.yaml.getString("$path.fallback-icon", "")!!) else icon
        return template(settings.format("tracking.stat-line"), mapOf(
            "nexo_stat_icon" to effectiveIcon, "stat_name" to Component.text(settings.statName(stat)),
            "stat_value" to Component.text(value.toString())))
    }

    private fun permission(player: Player, node: String) {
        if (!player.hasPermission("itemsignature.$node")) fail("no-permission")
    }
    private fun fail(key: String): Nothing = throw InputFailure(key, mapOf("max" to settings.maxLength.toString()))
}
