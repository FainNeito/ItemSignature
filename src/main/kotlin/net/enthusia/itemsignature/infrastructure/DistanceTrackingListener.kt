package net.enthusia.itemsignature.infrastructure

import net.enthusia.itemsignature.domain.Stat
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.EquipmentSlot

class DistanceTrackingListener(private val service: () -> ItemService) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (event is PlayerTeleportEvent) return
        val player = event.player
        if ((!service().settings.countCreative && player.gameMode == GameMode.CREATIVE) || player.isInsideVehicle) return
        val to = event.to ?: return
        val from = event.from
        if (from.world != to.world) return
        val dx = to.x - from.x
        val dz = to.z - from.z
        val stat: Stat
        val slot: EquipmentSlot
        val meters: Double
        if (player.isGliding) {
            stat = Stat.DISTANCE_FLOWN
            slot = EquipmentSlot.CHEST
            val dy = to.y - from.y
            meters = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        } else {
            if (player.isFlying || !player.isOnGround) return
            stat = Stat.DISTANCE_WALKED
            slot = EquipmentSlot.FEET
            meters = kotlin.math.sqrt(dx * dx + dz * dz)
        }
        try {
            val item = player.inventory.getItem(slot)
            if (service().incrementDistance(item, stat, meters)) player.inventory.setItem(slot, item)
        } catch (_: InputFailure) { }
    }
}
