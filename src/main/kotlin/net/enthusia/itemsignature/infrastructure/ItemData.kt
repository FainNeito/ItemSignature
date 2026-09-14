package net.enthusia.itemsignature.infrastructure

import net.enthusia.itemsignature.domain.Stat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.NamespacedKey
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType as Type

/** Only our namespace is written; external item identity and all other metadata survive. */
object ItemData {
    fun key(name: String) = NamespacedKey("itemsignature", name)
    private val json = GsonComponentSerializer.gson()
    private val diaryKeys = listOf(NamespacedKey("diarykeeper", "is_diary"), NamespacedKey("diarykeeper", "diary_id"))
    fun protected(meta: ItemMeta) = diaryKeys.any { meta.persistentDataContainer.has(it) }

    fun string(meta: ItemMeta, name: String): String? = read { meta.persistentDataContainer.get(key(name), Type.STRING) }
    fun number(meta: ItemMeta, name: String): Long? = read { meta.persistentDataContainer.get(key(name), Type.LONG) }
    fun set(meta: ItemMeta, name: String, value: String) = meta.persistentDataContainer.set(key(name), Type.STRING, value)
    fun set(meta: ItemMeta, name: String, value: Long) = meta.persistentDataContainer.set(key(name), Type.LONG, value)
    fun lines(meta: ItemMeta, name: String): List<Component> = read {
        string(meta, name)?.takeIf { it.isNotEmpty() }?.split('\n')?.map(json::deserialize) ?: emptyList()
    }
    fun setLines(meta: ItemMeta, name: String, lines: List<Component>) = set(meta, name, lines.joinToString("\n", transform = json::serialize))

    fun validate(meta: ItemMeta) {
        val pdc = meta.persistentDataContainer
        val ours = pdc.keys.any { it.namespace == "itemsignature" }
        if (!ours) return
        if (number(meta, "version") !in listOf(1L, 2L)) throw InputFailure("data-error")
        val signed = listOf("signer_uuid", "signer_name", "signed_at", "signature").map { pdc.has(key(it)) }
        if (signed.any { it } && !signed.all { it }) throw InputFailure("data-error")
        if (signed.all { it }) {
            read { java.util.UUID.fromString(string(meta, "signer_uuid")) }
            number(meta, "signed_at") ?: throw InputFailure("data-error")
            if (lines(meta, "signature").isEmpty()) throw InputFailure("data-error")
        }
        val tracked = listOf("stat", "value", "tracker_id").map { pdc.has(key(it)) }
        if (tracked.any { it } && !tracked.all { it }) throw InputFailure("data-error")
        if (tracked.all { it }) {
            Stat.from(string(meta, "stat") ?: "") ?: throw InputFailure("data-error")
            if ((number(meta, "value") ?: -1) < 0) throw InputFailure("data-error")
            read { java.util.UUID.fromString(string(meta, "tracker_id")) }
        }
    }

    private val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
    private const val MANAGED = "itemsignature:managed"
    private fun text(line: Component) = plain.serialize(line)

    fun editable(meta: ItemMeta): MutableList<Component> {
        val current = meta.lore() ?: emptyList()
        val previous = lines(meta, "rendered")
        val knownManaged = (previous + lines(meta, "signature")).map(::text).toSet()
        // Reserve recorded editable occurrences first, including legitimate text that
        // happens to look exactly like a signature or stat. Never restore stale base lore.
        val reserved = lines(meta, "editable").map(::text).groupingBy { it }.eachCount().toMutableMap()
        val value = number(meta, "value")
        val tracker = if (string(meta, "stat") != null) previous.lastOrNull()?.let(::text) else null
        val shapes = if (value != null && tracker != null) {
            Regex("(?<![0-9])" + value + "(?![0-9])").findAll(tracker).map {
                tracker.substring(0, it.range.first) to tracker.substring(it.range.last + 1)
            }.toList()
        } else emptyList()
        fun staleCounter(line: String): Boolean = shapes.any { (prefix, suffix) ->
            if (!line.startsWith(prefix) || !line.endsWith(suffix) || line.length <= prefix.length + suffix.length) false
            else {
                val digits = line.substring(prefix.length, line.length - suffix.length)
                val count = if (digits.all { it in '0'..'9' }) digits.toLongOrNull() else null
                count != null && value != null && count in 0..value
            }
        }
        return current.filter { line ->
            val visible = text(line)
            val keep = reserved[visible] ?: 0
            if (keep > 0) {
                reserved[visible] = keep - 1
                true
            } else {
                line.insertion() != MANAGED && visible !in knownManaged && !staleCounter(visible)
            }
        }.toMutableList()
    }

    fun redraw(meta: ItemMeta, editable: List<Component>, renderer: TextRenderer) {
        val managed = lines(meta, "signature").toMutableList()
        string(meta, "stat")?.let { id ->
            val stat = Stat.from(id) ?: throw InputFailure("data-error")
            managed += renderer.statLine(stat, number(meta, "value") ?: throw InputFailure("data-error"))
        }
        val marked = managed.map { it.insertion(MANAGED) }
        meta.lore(editable + marked)
        set(meta, "version", 2L)
        setLines(meta, "rendered", marked)
        setLines(meta, "editable", editable)
    }
    private fun <T> read(action: () -> T): T = try { action() } catch (ex: RuntimeException) {
        throw InputFailure("data-error")
    }
}
