package net.enthusia.itemsignature.infrastructure

import net.enthusia.itemsignature.domain.Stat
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin

/** Optional native API: no linkage to a class missing from Paper 1.21.11. */
class LungeTrackingBridge(private val plugin: JavaPlugin, private val service: () -> ItemService) : Listener {
    private val eventType = discover(plugin.javaClass.classLoader)
    val available get() = eventType != null
    fun register() {
        val type = eventType ?: return
        val power = type.getMethod("getLungePower")
        plugin.server.pluginManager.registerEvent(type, this, EventPriority.MONITOR, { _, raw ->
            if ((raw as Cancellable).isCancelled || (power.invoke(raw) as Int) <= 0) return@registerEvent
            val player = (raw as EntityEvent).entity as? Player ?: return@registerEvent
            if (!service().settings.countCreative && player.gameMode == GameMode.CREATIVE) return@registerEvent
            val active = player.activeItem
            val slot = if (Stat.TIMES_LUNGED.accepts(active.type.name)) player.activeItemHand else EquipmentSlot.HAND
            val item = player.inventory.getItem(slot)
            try {
                if (Stat.TIMES_LUNGED.accepts(active.type.name) && active.itemMeta?.let { ItemData.string(it, "tracker_id") } !=
                    item.itemMeta?.let { ItemData.string(it, "tracker_id") }) return@registerEvent
                if (service().increment(item, Stat.TIMES_LUNGED)) player.inventory.setItem(slot, item)
            } catch (_: InputFailure) { }
        }, plugin, true)
    }

    companion object {
        fun discover(loader: ClassLoader): Class<out EntityEvent>? = try {
            val type = Class.forName("io.papermc.paper.event.entity.EntityLungeEvent", false, loader)
            if (!Cancellable::class.java.isAssignableFrom(type) || type.getMethod("getLungePower").returnType != Integer.TYPE) null
            else type.asSubclass(EntityEvent::class.java)
        } catch (_: ReflectiveOperationException) { null }
    }
}
