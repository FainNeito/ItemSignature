package net.enthusia.itemsignature

import net.enthusia.itemsignature.infrastructure.*
import net.enthusia.itemsignature.domain.Stat

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Entity
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.mockito.Mockito.*

class TrackingListenerTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: ItemSignaturePlugin
    private lateinit var player: PlayerMock
    private lateinit var listener: TrackingListener

    @BeforeEach fun setup() {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        player = server.addPlayer()
        player.gameMode = GameMode.SURVIVAL
        Stat.entries.forEach { player.addAttachment(plugin, "itemsignature.track.${it.id}", true) }
        listener = TrackingListener { plugin.service }
    }
    @AfterEach fun teardown() { MockBukkit.unmock() }
    private fun tracked(stat: Stat, material: Material = Material.DIAMOND_SWORD) = ItemStack(material).also { plugin.service.track(player, it, stat) }
    private fun value(item: ItemStack) = ItemData.number(item.itemMeta!!, "value")

    @Test fun `registered block listener ignores cancelled breaks and creative mode`() {
        player.inventory.setItemInMainHand(tracked(Stat.BLOCKS_BROKEN, Material.DIAMOND_PICKAXE))
        val block = server.addSimpleWorld("mines").getBlockAt(0, 64, 0)
        block.type = Material.STONE
        server.pluginManager.callEvent(BlockBreakEvent(block, player).apply { isCancelled = true })
        assertEquals(0L, value(player.inventory.itemInMainHand))
        server.pluginManager.callEvent(BlockBreakEvent(block, player))
        assertEquals(1L, value(player.inventory.itemInMainHand))
        player.gameMode = GameMode.CREATIVE
        server.pluginManager.callEvent(BlockBreakEvent(block, player))
        assertEquals(1L, value(player.inventory.itemInMainHand))
    }

    private fun death(damager: Entity, pvp: Boolean = false): EntityDeathEvent {
        val victim = if (pvp) mock(Player::class.java) else mock(LivingEntity::class.java)
        val source = mock(org.bukkit.damage.DamageSource::class.java)
        `when`(source.directEntity).thenReturn(damager)
        `when`(source.causingEntity).thenReturn(if (damager is Projectile) player else damager)
        // No legacy lastDamageCause/killer cache: the event owns its final damage source.
        return EntityDeathEvent(victim, source, mutableListOf())
    }

    @Test fun `death listener distinguishes mobs from players`() {
        player.inventory.setItemInMainHand(tracked(Stat.MOB_KILLS))
        listener.onDeath(death(player, true))
        assertEquals(0L, value(player.inventory.itemInMainHand))
        listener.onDeath(death(player))
        assertEquals(1L, value(player.inventory.itemInMainHand))
        player.inventory.setItemInMainHand(tracked(Stat.PLAYER_KILLS))
        listener.onDeath(death(player, true))
        assertEquals(1L, value(player.inventory.itemInMainHand))
    }

    @Test fun `arrow kill credits original bow after switching slots`() {
        val bow = tracked(Stat.MOB_KILLS, Material.BOW)
        val sword = tracked(Stat.MOB_KILLS)
        val projectile = mock(Projectile::class.java)
        val projectilePdc = ItemStack(Material.STONE).itemMeta!!.persistentDataContainer
        `when`(projectile.persistentDataContainer).thenReturn(projectilePdc)
        `when`(projectile.shooter).thenReturn(player)
        val shoot = mock(EntityShootBowEvent::class.java)
        `when`(shoot.entity).thenReturn(player)
        `when`(shoot.bow).thenReturn(bow)
        `when`(shoot.projectile).thenReturn(projectile)
        listener.onShoot(shoot)
        player.inventory.setItem(4, bow)
        player.inventory.setItemInMainHand(sword)
        listener.onDeath(death(projectile))
        assertEquals(1L, value(player.inventory.getItem(4)!!))
        assertEquals(0L, value(player.inventory.itemInMainHand))
        player.inventory.setItem(4, null)
        listener.onDeath(death(projectile))
        assertEquals(0L, value(player.inventory.itemInMainHand))
    }

    @Test fun `diary carrying old tracker tags is never redrawn by events`() {
        val item = tracked(Stat.BLOCKS_BROKEN)
        val meta = item.itemMeta!!
        meta.persistentDataContainer.set(org.bukkit.NamespacedKey("diarykeeper", "is_diary"), org.bukkit.persistence.PersistentDataType.BOOLEAN, true)
        item.itemMeta = meta
        player.inventory.setItemInMainHand(item)
        val block = server.addSimpleWorld("diaries").getBlockAt(0, 64, 0)
        listener.onBreak(BlockBreakEvent(block, player))
        assertEquals(item, player.inventory.itemInMainHand)
    }

    @Test fun `kill updates are committed even when inventory reads return copies`() {
        for (stat in listOf(Stat.MOB_KILLS, Stat.PLAYER_KILLS)) {
            var stored = tracked(stat)
            val actor = mock(Player::class.java)
            val inventory = mock(org.bukkit.inventory.PlayerInventory::class.java)
            `when`(actor.inventory).thenReturn(inventory)
            `when`(actor.gameMode).thenReturn(GameMode.SURVIVAL)
            `when`(actor.uniqueId).thenReturn(java.util.UUID.randomUUID())
            `when`(inventory.itemInMainHand).thenAnswer { stored.clone() }
            doAnswer { call -> stored = call.getArgument(0); null }.`when`(inventory).setItemInMainHand(any(ItemStack::class.java))
            val event = death(actor, stat == Stat.PLAYER_KILLS)
            // Populate legacy caches here to isolate the missing inventory write-back bug.
            val damage = mock(EntityDamageByEntityEvent::class.java)
            `when`(damage.damager).thenReturn(actor)
            `when`(event.entity.lastDamageCause).thenReturn(damage)
            `when`(event.entity.killer).thenReturn(actor)
            listener.onDeath(event)
            assertEquals(1L, value(stored))
        }
    }

    @Test fun `unrelated final damage never credits a stale player damage cache`() {
        player.inventory.setItemInMainHand(tracked(Stat.MOB_KILLS))
        val victim = mock(LivingEntity::class.java)
        val stale = mock(EntityDamageByEntityEvent::class.java)
        `when`(stale.damager).thenReturn(player)
        `when`(victim.lastDamageCause).thenReturn(stale)
        `when`(victim.killer).thenReturn(player)
        val source = mock(org.bukkit.damage.DamageSource::class.java)
        listener.onDeath(EntityDeathEvent(victim, source, mutableListOf()))
        assertEquals(0L, value(player.inventory.itemInMainHand))
    }

    @Test fun `registered death handler honors cancellation and creative exclusion`() {
        player.inventory.setItemInMainHand(tracked(Stat.MOB_KILLS))
        server.pluginManager.callEvent(death(player).apply { isCancelled = true })
        assertEquals(0L, value(player.inventory.itemInMainHand))
        player.gameMode = GameMode.CREATIVE
        server.pluginManager.callEvent(death(player))
        assertEquals(0L, value(player.inventory.itemInMainHand))
        player.gameMode = GameMode.SURVIVAL
        server.pluginManager.callEvent(death(player))
        assertEquals(1L, value(player.inventory.itemInMainHand))
    }
}
