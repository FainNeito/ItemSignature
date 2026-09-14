package net.enthusia.itemsignature.infrastructure

import net.enthusia.itemsignature.domain.Stat
import net.enthusia.itemsignature.domain.ItemFacts
import net.enthusia.itemsignature.domain.MutationRejection
import net.enthusia.itemsignature.application.CustomizationPolicy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.time.Clock
import java.time.Instant
import java.util.UUID

class ItemService(val settings: Settings, val renderer: TextRenderer, private val clock: Clock = Clock.systemUTC()) {
    fun permission(player: Player, node: String) {
        if (!player.hasPermission("itemsignature.$node")) throw InputFailure("no-permission")
    }
    private fun meta(item: ItemStack): ItemMeta {
        if (item.type.isAir || !item.type.isItem) throw InputFailure("invalid-item")
        val meta = item.itemMeta ?: throw InputFailure("invalid-item")
        when (CustomizationPolicy.rejection(ItemFacts(ItemData.protected(meta), item.amount))) {
            MutationRejection.DIARY -> throw InputFailure("protected-item")
            MutationRejection.STACK -> throw InputFailure("stacked-item")
            null -> Unit
        }
        ItemData.validate(meta)
        return meta
    }

    fun sign(player: Player, item: ItemStack, args: List<String>) {
        permission(player, "sign")
        val meta = meta(item)
        val oldName = ItemData.string(meta, "signer_name")
        if (oldName != null) throw InputFailure("already-signed", mapOf("player" to oldName))
        var rest = args
        var color: TextColor? = null
        if (rest.firstOrNull() == "--color") {
            color = renderer.nameColor(rest.getOrNull(1) ?: throw InputFailure("sign-usage"), player)
            rest = rest.drop(2)
        }
        val quote = if (rest.isNotEmpty()) {
            permission(player, "quote")
            renderer.user(rest.joinToString(" "), player).decoration(TextDecoration.ITALIC, true)
        } else null
        val instant = Instant.now(clock)
        val name = if (color != null) Component.text(player.name, color)
            else renderer.template(settings.nameColor + player.name)
        val values = mapOf("player" to name, "date" to Component.text(settings.dateFormat.format(instant.atZone(settings.zone))),
            "text" to (quote ?: Component.empty()))
        val signature = mutableListOf(renderer.template(settings.format(if (quote != null) "signature.custom" else "signature.basic"), values))
        if (quote != null) signature += renderer.template(settings.format("signature.quote"), values)
        signature += renderer.template(settings.format("signature.date-stamp"), values)
        val editable = ItemData.editable(meta)
        ItemData.set(meta, "signer_uuid", player.uniqueId.toString())
        ItemData.set(meta, "signer_name", player.name)
        ItemData.set(meta, "signed_at", instant.toEpochMilli())
        ItemData.setLines(meta, "signature", signature)
        ItemData.redraw(meta, editable, renderer)
        item.itemMeta = meta
    }

    fun track(player: Player, item: ItemStack, stat: Stat) {
        permission(player, "track.${stat.id}")
        val meta = meta(item)
        if (ItemData.string(meta, "stat") != null) throw InputFailure("tracker-already-exists")
        val editable = ItemData.editable(meta)
        ItemData.set(meta, "stat", stat.id)
        ItemData.set(meta, "value", 0L)
        ItemData.set(meta, "tracker_id", UUID.randomUUID().toString())
        ItemData.redraw(meta, editable, renderer)
        item.itemMeta = meta
    }

    fun increment(item: ItemStack, stat: Stat): Boolean {
        if (item.type.isAir || !item.hasItemMeta()) return false
        val raw = item.itemMeta ?: return false
        if (ItemData.protected(raw) || item.amount != 1 || ItemData.string(raw, "stat") != stat.id) return false
        val meta = meta(item)
        val value = ItemData.number(meta, "value") ?: throw InputFailure("data-error")
        val nextValue = CustomizationPolicy.nextCounter(value)
        if (nextValue == value) return false
        val editable = ItemData.editable(meta)
        ItemData.set(meta, "value", nextValue)
        ItemData.redraw(meta, editable, renderer)
        item.itemMeta = meta
        return true
    }
}
