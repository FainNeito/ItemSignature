package net.enthusia.itemsignature

import net.enthusia.itemsignature.infrastructure.*
import net.enthusia.itemsignature.domain.Stat
import org.bukkit.*
import org.bukkit.block.BlockFace
import org.bukkit.entity.*
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.player.*
import org.bukkit.event.world.PortalCreateEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import io.papermc.paper.event.player.PlayerShieldDisableEvent
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockito.Mockito.*

class EquipmentActionTest {
    private lateinit var server: org.mockbukkit.mockbukkit.ServerMock
    private lateinit var plugin: ItemSignaturePlugin
    private lateinit var player: org.mockbukkit.mockbukkit.entity.PlayerMock
    @BeforeEach fun setup() {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
        player = server.addPlayer()
        player.gameMode = GameMode.SURVIVAL
        Stat.entries.forEach { player.addAttachment(plugin, "itemsignature.track.${it.id}", true) }
    }
    @AfterEach fun close() { MockBukkit.unmock() }
    private fun tool(stat: Stat, material: Material, slot: EquipmentSlot = EquipmentSlot.HAND): ItemStack =
        ItemStack(material).also { plugin.service.track(player, it, stat); player.inventory.setItem(slot, it) }
    private fun count(slot: EquipmentSlot = EquipmentSlot.HAND) = ItemData.number(player.inventory.getItem(slot).itemMeta!!, "value")
    private fun call(event: org.bukkit.event.Event) { server.pluginManager.callEvent(event) }

    @Test fun `arrows count projectiles on the firing bow and exclude cancelled and firework shots`() {
        val bow = tool(Stat.ARROWS_SHOT, Material.CROSSBOW, EquipmentSlot.OFF_HAND)
        fun shot(entity: Entity): EntityShootBowEvent {
            `when`(entity.persistentDataContainer).thenReturn(ItemStack(Material.STONE).itemMeta!!.persistentDataContainer)
            return EntityShootBowEvent(player, bow, null, entity, EquipmentSlot.OFF_HAND, 1f, true)
        }
        call(shot(mock(Arrow::class.java)).apply { isCancelled = true })
        call(shot(mock(Firework::class.java)))
        assertEquals(0L, count(EquipmentSlot.OFF_HAND))
        repeat(3) { call(shot(mock(Arrow::class.java))) }
        assertEquals(3L, count(EquipmentSlot.OFF_HAND))
    }

    @Test fun `fishing counts caught items not casts entities bites or cancelled catches`() {
        tool(Stat.TIMES_FISHED, Material.FISHING_ROD, EquipmentSlot.OFF_HAND)
        val hook = mock(FishHook::class.java)
        fun fish(state: PlayerFishEvent.State) = PlayerFishEvent(player, mock(Item::class.java), hook, EquipmentSlot.OFF_HAND, state)
        call(fish(PlayerFishEvent.State.FISHING))
        call(fish(PlayerFishEvent.State.BITE))
        call(fish(PlayerFishEvent.State.CAUGHT_ENTITY))
        call(fish(PlayerFishEvent.State.CAUGHT_FISH).apply { isCancelled = true })
        assertEquals(0L, count(EquipmentSlot.OFF_HAND))
        call(fish(PlayerFishEvent.State.CAUGHT_FISH))
        assertEquals(1L, count(EquipmentSlot.OFF_HAND))
    }

    @Test fun `shearing counts sheep only using the event hand`() {
        val shears = tool(Stat.SHEEP_SHEARED, Material.SHEARS, EquipmentSlot.OFF_HAND)
        fun shear(entity: Entity) = PlayerShearEntityEvent(player, entity, shears, EquipmentSlot.OFF_HAND, emptyList())
        call(shear(mock(MushroomCow::class.java)))
        call(shear(mock(Sheep::class.java)).apply { isCancelled = true })
        call(shear(mock(Sheep::class.java)))
        assertEquals(1L, count(EquipmentSlot.OFF_HAND))
    }

    @Test fun `shield disables credit the attacking axe only for enabled cooldowns`() {
        tool(Stat.SHIELDS_DISABLED, Material.IRON_AXE)
        val victim = server.addPlayer()
        call(PlayerShieldDisableEvent(victim, player, 100).apply { isCancelled = true })
        call(PlayerShieldDisableEvent(victim, player, 0))
        call(PlayerShieldDisableEvent(victim, player, 100))
        assertEquals(1L, count())
    }

    @Test fun `riptide credits used trident once with cancellation and creative exclusions`() {
        val trident = tool(Stat.TIMES_RIPTIDED, Material.TRIDENT, EquipmentSlot.OFF_HAND)
        call(PlayerRiptideEvent(player, trident).apply { isCancelled = true })
        player.gameMode = GameMode.CREATIVE
        call(PlayerRiptideEvent(player, trident))
        player.gameMode = GameMode.SURVIVAL
        call(PlayerRiptideEvent(player, trident))
        assertEquals(1L, count(EquipmentSlot.OFF_HAND))
    }

    @Test fun `thrown trident counter follows its pickup stack after vanilla replaces that stack`() {
        val held = tool(Stat.TIMES_THROWN, Material.TRIDENT)
        val trident = mock(Trident::class.java)
        var pickup = held.clone()
        `when`(trident.isValid).thenReturn(true)
        `when`(trident.itemStack).thenAnswer { pickup.clone() }
        doAnswer { pickup = it.getArgument(0); null }.`when`(trident).setItemStack(any(ItemStack::class.java))
        call(PlayerLaunchProjectileEvent(player, held, trident).apply { isCancelled = true })
        server.scheduler.performOneTick()
        assertEquals(0L, ItemData.number(pickup.itemMeta!!, "value"))
        call(PlayerLaunchProjectileEvent(player, held, trident))
        player.inventory.setItemInMainHand(ItemStack(Material.AIR))
        pickup = held.clone() // vanilla installs this after the launch event
        server.scheduler.performOneTick()
        assertEquals(1L, ItemData.number(pickup.itemMeta!!, "value"))
    }

    @Test fun `tilling waits for farmland and retains attribution after slot changes`() {
        val hoe = tool(Stat.LAND_TILLED, Material.DIAMOND_HOE)
        val block = server.addSimpleWorld("farm").getBlockAt(0, 64, 0)
        block.type = Material.DIRT
        fun interact() = PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, hoe, block, BlockFace.UP, EquipmentSlot.HAND)
        call(interact())
        server.scheduler.performOneTick()
        assertEquals(0L, count())
        call(interact().apply { setUseItemInHand(org.bukkit.event.Event.Result.DENY) })
        block.type = Material.FARMLAND
        server.scheduler.performOneTick()
        assertEquals(0L, count())
        block.type = Material.DIRT
        call(interact())
        call(interact()) // two callbacks for one transition must not double credit
        block.type = Material.FARMLAND
        player.inventory.setItem(5, player.inventory.itemInMainHand)
        player.inventory.setItemInMainHand(ItemStack(Material.STONE))
        server.scheduler.performOneTick()
        assertEquals(1L, ItemData.number(player.inventory.getItem(5)!!.itemMeta!!, "value"))
    }

    @Test fun `brush counts completed suspicious blocks not intermediate dust stages`() {
        tool(Stat.TIMES_SIFTED, Material.BRUSH)
        val block = server.addSimpleWorld("archaeology").getBlockAt(0, 64, 0)
        block.type = Material.SUSPICIOUS_SAND
        call(EntityChangeBlockEvent(player, block, server.createBlockData(Material.SUSPICIOUS_SAND)))
        call(EntityChangeBlockEvent(player, block, server.createBlockData(Material.SAND)).apply { isCancelled = true })
        call(EntityChangeBlockEvent(player, block, server.createBlockData(Material.SAND)))
        assertEquals(1L, count())
    }

    @Test fun `portal creation counts one ignition not individual fire blocks or destination generation`() {
        val lighter = tool(Stat.PORTALS_IGNITED, Material.FLINT_AND_STEEL, EquipmentSlot.OFF_HAND)
        val world = server.addSimpleWorld("portals")
        val block = world.getBlockAt(0, 64, 0)
        block.type = Material.OBSIDIAN
        call(PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, lighter, block, BlockFace.UP, EquipmentSlot.OFF_HAND))
        val portal = world.getBlockAt(0, 65, 0)
        portal.type = Material.NETHER_PORTAL
        fun create(reason: PortalCreateEvent.CreateReason) = PortalCreateEvent(listOf(portal.state), world, player, reason)
        call(create(PortalCreateEvent.CreateReason.NETHER_PAIR))
        call(create(PortalCreateEvent.CreateReason.FIRE).apply { isCancelled = true })
        assertEquals(0L, count(EquipmentSlot.OFF_HAND))
        call(create(PortalCreateEvent.CreateReason.FIRE))
        assertEquals(1L, count(EquipmentSlot.OFF_HAND))
    }
}
