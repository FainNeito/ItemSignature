package net.enthusia.itemsignature

import net.enthusia.itemsignature.domain.Stat
import net.enthusia.itemsignature.infrastructure.ItemData
import net.enthusia.itemsignature.infrastructure.ItemSignaturePlugin
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockito.Mockito.*

class TrackerPerformanceTest {
    @Test fun `many signed tracked items retain independent exact counts and external lore`() {
        val server = MockBukkit.mock()
        try {
            val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
            val items = (0 until 32).map { index ->
                val player = server.addPlayer()
                player.addAttachment(plugin, "itemsignature.sign", true)
                player.addAttachment(plugin, "itemsignature.track.distance_walked", true)
                val item = ItemStack(Material.DIAMOND_BOOTS)
                val meta = item.itemMeta!!
                meta.lore(listOf(Component.text("External lore $index")))
                item.itemMeta = meta
                plugin.service.sign(player, item, emptyList())
                plugin.service.track(player, item, Stat.DISTANCE_WALKED)
                item
            }
            val signatures = items.map { ItemData.string(it.itemMeta!!, "signature") }
            repeat(40) { items.forEach { assertTrue(plugin.service.incrementDistance(it, Stat.DISTANCE_WALKED, 1.005)) } }
            items.forEachIndexed { index, item ->
                val meta = item.itemMeta!!
                assertEquals(4020L, ItemData.number(meta, "value"))
                assertEquals(signatures[index], ItemData.string(meta, "signature"))
                assertEquals(Component.text("External lore $index"), meta.lore()!!.first())
                assertEquals(4, meta.lore()!!.size)
            }
        } finally { MockBukkit.unmock() }
    }

    @Test fun `stable lore cache evicts old items and bypasses oversized inputs`() {
        MockBukkit.mock()
        try {
            val original = ItemStack(Material.STONE).itemMeta!!
            val prefix = java.util.UUID.randomUUID().toString()
            ItemData.setLines(original, "editable", listOf(Component.text(prefix)))
            val first = ItemData.lines(original, "editable")
            repeat(512) { index ->
                val meta = ItemStack(Material.STONE).itemMeta!!
                ItemData.setLines(meta, "editable", listOf(Component.text("$prefix-$index")))
                ItemData.lines(meta, "editable")
            }
            assertNotSame(first, ItemData.lines(original, "editable"))
            val beforeBudgetEviction = ItemData.lines(original, "editable")
            repeat(40) { index ->
                val meta = ItemStack(Material.STONE).itemMeta!!
                ItemData.setLines(meta, "editable", listOf(Component.text("$prefix-budget-$index" + "x".repeat(8_000))))
                ItemData.lines(meta, "editable")
            }
            // Forty entries are below the entry-count limit, so this exercises the character budget.
            assertNotSame(beforeBudgetEviction, ItemData.lines(original, "editable"))
            val large = listOf(Component.text("x".repeat(20_000)))
            ItemData.setLines(original, "editable", large)
            val decoded = ItemData.lines(original, "editable")
            assertEquals(large, decoded)
            assertNotSame(decoded, ItemData.lines(original, "editable"))
        } finally { MockBukkit.unmock() }
    }

    @Test fun `rotation only movement never reads equipment`() {
        val server = MockBukkit.mock()
        try {
            val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
            val player = spy(server.addPlayer())
            doReturn(true).`when`(player).isOnGround
            val world = server.addSimpleWorld("performance")
            val from = org.bukkit.Location(world, 0.0, 64.0, 0.0)
            val to = from.clone().apply { yaw = 90f }
            clearInvocations(player)
            net.enthusia.itemsignature.infrastructure.DistanceTrackingListener { plugin.service }
                .onMove(org.bukkit.event.player.PlayerMoveEvent(player, from, to))
            verify(player, never()).inventory
        } finally { MockBukkit.unmock() }
    }

    @Test fun `each counter update reads one metadata snapshot and keeps exact totals`() {
        val server = MockBukkit.mock()
        try {
            val plugin = MockBukkit.load(ItemSignaturePlugin::class.java)
            val player = server.addPlayer()
            player.addAttachment(plugin, "itemsignature.track.blocks_broken", true)
            player.addAttachment(plugin, "itemsignature.track.distance_walked", true)
            val pick = spy(ItemStack(Material.DIAMOND_PICKAXE))
            plugin.service.track(player, pick, Stat.BLOCKS_BROKEN)
            clearInvocations(pick)
            repeat(100) { assertTrue(plugin.service.increment(pick, Stat.BLOCKS_BROKEN)) }
            verify(pick, times(100)).itemMeta
            assertEquals(100L, ItemData.number(pick.itemMeta!!, "value"))
            val boots = spy(ItemStack(Material.DIAMOND_BOOTS))
            plugin.service.track(player, boots, Stat.DISTANCE_WALKED)
            clearInvocations(boots)
            repeat(100) { assertTrue(plugin.service.incrementDistance(boots, Stat.DISTANCE_WALKED, 0.005)) }
            verify(boots, times(100)).itemMeta
            assertEquals(50L, ItemData.number(boots.itemMeta!!, "value"))
        } finally { MockBukkit.unmock() }
    }

    @Test fun `repeated stable lore reads reuse immutable snapshots without stale data`() {
        MockBukkit.mock()
        try {
            val meta = ItemStack(Material.STONE).itemMeta!!
            ItemData.setLines(meta, "signature", listOf(Component.text("Original")))
            val first = ItemData.lines(meta, "signature")
            repeat(1000) { assertSame(first, ItemData.lines(meta, "signature")) }
            assertThrows(UnsupportedOperationException::class.java) { (first as MutableList<Component>).clear() }
            ItemData.setLines(meta, "signature", listOf(Component.text("Replacement")))
            assertEquals(listOf(Component.text("Replacement")), ItemData.lines(meta, "signature"))
            ItemData.set(meta, "signature", "not json")
            assertThrows(net.enthusia.itemsignature.infrastructure.InputFailure::class.java) { ItemData.lines(meta, "signature") }
        } finally { MockBukkit.unmock() }
    }
}
