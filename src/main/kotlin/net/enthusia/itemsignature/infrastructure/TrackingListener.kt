package net.enthusia.itemsignature.infrastructure

import net.enthusia.itemsignature.domain.Stat

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.persistence.PersistentDataType as Type

class TrackingListener(private val service: () -> ItemService) : Listener {
    internal var trackingClock: java.time.Clock = java.time.Clock.systemUTC()
    private val recentKills = mutableMapOf<Pair<java.util.UUID, java.util.UUID>, Long>()
    private val weaponKey = ItemData.key("projectile_weapon")
    private val ownerKey = ItemData.key("projectile_owner")
    private fun allowed(player: Player) = service().settings.countCreative || player.gameMode != GameMode.CREATIVE

    private fun incrementHand(player: Player, stat: Stat) {
        val item = player.inventory.itemInMainHand
        if (service().increment(item, stat)) player.inventory.setItemInMainHand(item)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (allowed(event.player)) safely { incrementHand(event.player, Stat.BLOCKS_BROKEN) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShoot(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return
        if (!allowed(player)) return
        val meta = event.bow?.itemMeta ?: return
        if (ItemData.protected(meta)) return
        safely {
            ItemData.validate(meta)
            val id = ItemData.string(meta, "tracker_id") ?: return@safely
            event.projectile.persistentDataContainer.set(weaponKey, Type.STRING, id)
            event.projectile.persistentDataContainer.set(ownerKey, Type.STRING, player.uniqueId.toString())
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        // The death event owns the final blow. Entity caches can be absent or stale.
        val source = event.damageSource
        val direct = source.directEntity
        val player = source.causingEntity as? Player ?: (direct as? Player) ?: return
        if (!allowed(player)) return
        val stat = if (event.entity is Player) Stat.PLAYER_KILLS else Stat.MOB_KILLS
        safely {
            val settings = service().settings
            val now = trackingClock.millis()
            recentKills.entries.removeIf { now - it.value >= settings.killCooldownMillis }
            val pair = if (stat == Stat.PLAYER_KILLS && settings.preventKillFarming)
                player.uniqueId to event.entity.uniqueId else null
            if (pair != null && recentKills.containsKey(pair)) return@safely
            fun increment(item: org.bukkit.inventory.ItemStack): Boolean {
                if (!service().increment(item, stat)) return false
                if (pair != null) recentKills[pair] = now
                return true
            }
            when (val damager = direct) {
                is Player -> if (damager.uniqueId == player.uniqueId) {
                    val item = player.inventory.itemInMainHand
                    if (increment(item)) player.inventory.setItemInMainHand(item)
                }
                is Projectile -> {
                    val shooter = damager.shooter as? Player ?: return@safely
                    if (shooter.uniqueId != player.uniqueId) return@safely
                    val pdc = damager.persistentDataContainer
                    if (pdc.get(ownerKey, Type.STRING) != player.uniqueId.toString()) return@safely
                    val id = pdc.get(weaponKey, Type.STRING) ?: return@safely
                    // Retain the slot as well as the stack so copy-returning inventories persist updates.
                    val matches = player.inventory.contents.withIndex().filter { entry ->
                        val item = entry.value
                        item != null && item.hasItemMeta() && ItemData.string(item.itemMeta!!, "tracker_id") == id
                    }
                    if (matches.size == 1) {
                        val match = matches.single()
                        val item = match.value!!
                        if (increment(item)) player.inventory.setItem(match.index, item)
                    }
                }
            }
        }
    }
    // Malformed/unknown item data is left untouched. Commands explain it to the holder.
    private inline fun safely(action: () -> Unit) { try { action() } catch (_: InputFailure) { } }
}
