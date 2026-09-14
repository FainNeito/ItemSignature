package net.enthusia.itemsignature.infrastructure

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method

/** Optional linkage to the documented Nexo FontManager/Glyph API (1.8).
 * No Kotlin types cross this reflection boundary, allowing an isolated bundled runtime.
 */
class NexoBridge(private val plugin: JavaPlugin) : GlyphResolver {
    private var warned = false
    private val methods = mutableMapOf<Pair<Class<*>, String>, Method>()
    override fun containsGlyphCharacters(text: String): Boolean {
        val nexo = plugin.server.pluginManager.getPlugin("Nexo")?.takeIf { it.isEnabled } ?: return false
        return try {
            val manager = method(nexo.javaClass, "fontManager").invoke(nexo)
            val map = method(manager.javaClass, "getUnicodeGlyphMap").invoke(manager) as Map<*, *>
            text.any { map.containsKey(it) }
        } catch (ex: ReflectiveOperationException) {
            warn(ex)
            // Fail closed for player text when Nexo is present but its glyph map cannot be checked.
            throw InputFailure("glyph-unavailable")
        } catch (ex: LinkageError) { warn(ex); throw InputFailure("glyph-unavailable") }
    }
    override fun resolve(id: String, player: Player?): Component? {
        val nexo = plugin.server.pluginManager.getPlugin("Nexo")?.takeIf { it.isEnabled } ?: return null
        return try {
            val manager = method(nexo.javaClass, "fontManager").invoke(nexo)
            val glyph = method(manager.javaClass, "glyphFromID", String::class.java).invoke(manager, id) ?: return null
            // Nexo may return its 'required' fallback for an unknown ID; never silently accept it.
            if (method(glyph.javaClass, "getId").invoke(glyph) != id) return null
            if (player != null && method(glyph.javaClass, "hasPermission", Player::class.java).invoke(glyph, player) != true) return null
            method(glyph.javaClass, "glyphComponent").invoke(glyph) as? Component
        } catch (ex: ReflectiveOperationException) {
            warn(ex); null
        } catch (ex: LinkageError) {
            warn(ex); null
        }
    }
    private fun method(type: Class<*>, name: String, vararg params: Class<*>) =
        methods.getOrPut(type to name) { type.getMethod(name, *params) }
    private fun warn(ex: Throwable) {
        if (!warned) {
            plugin.logger.warning("Nexo glyph API unavailable; using text icon fallbacks. ${ex.javaClass.simpleName}: ${ex.message}")
            warned = true
        }
    }
}
