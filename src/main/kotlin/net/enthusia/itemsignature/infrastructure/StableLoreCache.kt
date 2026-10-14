package net.enthusia.itemsignature.infrastructure

import net.kyori.adventure.text.Component
import java.util.Collections
import java.util.LinkedHashMap

/** Pure immutable text snapshots only: never retains players, items, counters or permissions. */
internal class StableLoreCache(private val decode: (String) -> List<Component>) {
    private val entries = LinkedHashMap<String, List<Component>>(256, 0.75f, true)
    private var encodedChars = 0

    @Synchronized
    fun read(encoded: String): List<Component> {
        entries[encoded]?.let { return it }
        val decoded = Collections.unmodifiableList(ArrayList(decode(encoded)))
        // Large/custom legacy lore is still decoded normally, but cannot crowd out hot entries.
        if (encoded.length > 16_384) return decoded
        while (entries.size >= 256 || encodedChars + encoded.length > 262_144) {
            val oldest = entries.entries.iterator()
            encodedChars -= oldest.next().key.length
            oldest.remove()
        }
        entries[encoded] = decoded
        encodedChars += encoded.length
        return decoded
    }
}
