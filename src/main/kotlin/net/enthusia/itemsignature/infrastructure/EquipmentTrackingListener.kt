package net.enthusia.itemsignature.infrastructure

import net.enthusia.itemsignature.domain.Stat
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.entity.Sheep
import org.bukkit.entity.Trident
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerRiptideEvent
import org.bukkit.event.player.PlayerShearEntityEvent
import org.bukkit.event.world.PortalCreateEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import io.papermc.paper.event.player.PlayerShieldDisableEvent
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent

/** Successful action adapters; counter writes always target the recorded item identity. */
class EquipmentTrackingListener(private val plugin: JavaPlugin, private val service: () -> ItemService) : Listener {
    private val portalUses = mutableMapOf<java.util.UUID, ItemStack>()
    private val pendingTills = mutableSetOf<org.bukkit.block.Block>()
    private fun allowed(player: Player) = service().settings.countCreative || player.gameMode != GameMode.CREATIVE
    private fun held(player: Player, stat: Stat): ItemStack? {
        // Events without a hand cannot safely choose between two eligible held items.
        val choices = listOf(player.inventory.itemInMainHand, player.inventory.itemInOffHand).filter { stat.accepts(it.type.name) }
        return choices.singleOrNull()
    }
    private fun credit(player: Player, source: ItemStack?, stat: Stat) {
        if (!allowed(player) || source == null) return
        val id = source.itemMeta?.let { ItemData.string(it, "tracker_id") } ?: return
        val matches = player.inventory.contents.withIndex().filter { entry ->
            entry.value?.itemMeta?.let { ItemData.string(it, "tracker_id") == id } == true
        }
        if (matches.size != 1) return
        val match = matches.single()
        val item = match.value!!
        if (service().increment(item, stat)) player.inventory.setItem(match.index, item)
    }
    private inline fun safely(action: () -> Unit) { try { action() } catch (_: InputFailure) { } }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onArrow(event: EntityShootBowEvent) = safely {
        val player = event.entity as? Player ?: return@safely
        if (event.projectile is AbstractArrow && event.projectile !is Trident) credit(player, event.bow, Stat.ARROWS_SHOT)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) = safely {
        if (event.state == PlayerFishEvent.State.CAUGHT_FISH) {
            val source = event.hand?.let { event.player.inventory.getItem(it) } ?: held(event.player, Stat.TIMES_FISHED)
            credit(event.player, source, Stat.TIMES_FISHED)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShear(event: PlayerShearEntityEvent) = safely {
        if (event.entity is Sheep) credit(event.player, event.item, Stat.SHEEP_SHEARED)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShield(event: PlayerShieldDisableEvent) = safely {
        val attacker = event.damager as? Player ?: return@safely
        if (event.cooldown > 0) credit(attacker, attacker.inventory.itemInMainHand, Stat.SHIELDS_DISABLED)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRiptide(event: PlayerRiptideEvent) = safely { credit(event.player, event.item, Stat.TIMES_RIPTIDED) }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onThrow(event: PlayerLaunchProjectileEvent) = safely {
        val trident = event.projectile as? Trident ?: return@safely
        if (!allowed(event.player)) return@safely
        val meta = event.itemStack.itemMeta ?: return@safely
        if (ItemData.string(meta, "stat") != Stat.TIMES_THROWN.id || ItemData.protected(meta)) return@safely
        ItemData.validate(meta)
        val id = ItemData.string(meta, "tracker_id") ?: return@safely
        // Vanilla replaces the pickup item after the callback. Write to the actual projectile next tick.
        plugin.server.scheduler.runTask(plugin, Runnable {
            safely {
                if (!trident.isValid || trident.isDead) return@safely
                val pickup = trident.itemStack
                if (pickup.itemMeta?.let { ItemData.string(it, "tracker_id") } != id) return@safely
                if (service().increment(pickup, Stat.TIMES_THROWN)) trident.itemStack = pickup
            }
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onUse(event: PlayerInteractEvent) = safely {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.useItemInHand() == Event.Result.DENY ||
            event.useInteractedBlock() == Event.Result.DENY || !allowed(event.player)) return@safely
        val item = event.item?.clone() ?: return@safely
        if (item.type == Material.FLINT_AND_STEEL) {
            portalUses[event.player.uniqueId] = item
            plugin.server.scheduler.runTask(plugin, Runnable { portalUses.remove(event.player.uniqueId, item) })
        }
        val block = event.clickedBlock ?: return@safely
        if (!Stat.LAND_TILLED.accepts(item.type.name) || block.type !in setOf(Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH) ||
            event.blockFace == org.bukkit.block.BlockFace.DOWN || !block.getRelative(org.bukkit.block.BlockFace.UP).type.isAir) return@safely
        if (!pendingTills.add(block)) return@safely
        plugin.server.scheduler.runTask(plugin, Runnable {
            pendingTills.remove(block)
            if (block.type == Material.FARMLAND) safely { credit(event.player, item, Stat.LAND_TILLED) }
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPortal(event: PortalCreateEvent) = safely {
        val player = event.entity as? Player ?: return@safely
        if (event.reason != PortalCreateEvent.CreateReason.FIRE || event.blocks.none { it.type == Material.NETHER_PORTAL }) return@safely
        val source = portalUses.remove(player.uniqueId) ?: return@safely
        credit(player, source, Stat.PORTALS_IGNITED)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBrush(event: EntityChangeBlockEvent) = safely {
        val player = event.entity as? Player ?: return@safely
        val completed = (event.block.type == Material.SUSPICIOUS_SAND && event.to == Material.SAND) ||
            (event.block.type == Material.SUSPICIOUS_GRAVEL && event.to == Material.GRAVEL)
        if (completed) credit(player, held(player, Stat.TIMES_SIFTED), Stat.TIMES_SIFTED)
    }
}
