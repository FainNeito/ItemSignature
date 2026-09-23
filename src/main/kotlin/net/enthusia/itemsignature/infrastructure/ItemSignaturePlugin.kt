package net.enthusia.itemsignature.infrastructure

import net.enthusia.itemsignature.domain.Stat

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

open class ItemSignaturePlugin : JavaPlugin() {

    internal var confirmationClock: java.time.Clock = java.time.Clock.systemUTC()
    private data class Pending(val item: org.bukkit.inventory.ItemStack, val slot: Int, val args: List<String>, val expires: Long)
    private val pending = mutableMapOf<java.util.UUID, Pending>()

    private fun signing(player: Player, item: org.bukkit.inventory.ItemStack, args: List<String>) {
        val now = confirmationClock.millis()
        pending.entries.removeIf { it.value.expires <= now }
        when (args.singleOrNull()?.lowercase()) {
            "cancel" -> { pending.remove(player.uniqueId); message(player, "sign-cancelled") }
            "confirm" -> {
                val request = pending.remove(player.uniqueId) ?: throw InputFailure("sign-no-pending")
                if (request.slot != player.inventory.heldItemSlot || request.item != item) throw InputFailure("sign-item-changed")
                // Revalidate permissions and item data at confirmation time.
                service.sign(player, item, request.args)
                message(player, "signed-success")
            }
            else -> {
                pending.remove(player.uniqueId)
                val preview = item.clone()
                service.sign(player, preview, args)
                pending[player.uniqueId] = Pending(item.clone(), player.inventory.heldItemSlot, args.toList(), now + 30_000)
                message(player, "sign-warning")
                ItemData.lines(preview.itemMeta!!, "signature").forEach { player.sendMessage(it) }
            }
        }
    }

    lateinit var service: ItemService
        private set

    override fun onEnable() {
        try {
            LegacyConfigMigration.copyIfMissing(
                File(dataFolder.parentFile, "ItemSignature/config.yml").toPath(),
                File(dataFolder, "config.yml").toPath()
            )
            saveDefaultConfig()
            loadSettings()
        } catch (ex: Exception) {
            logger.severe("Unable to load EnthusiaSignature configuration: ${ex.message}")
            server.pluginManager.disablePlugin(this)
            return
        }
        listOf("sign", "track", "itemsignature", "enthusiasignature").forEach { getCommand(it)!!.setExecutor(this) }
        server.pluginManager.registerEvents(TrackingListener { service }, this)
    }

    private fun loadSettings() {
        val yaml = YamlConfiguration()
        yaml.load(File(dataFolder, "config.yml"))
        val defaults = getResource("config.yml")!!.use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use(YamlConfiguration::loadConfiguration)
        }
        yaml.setDefaults(defaults)
        // Old generated help advertised the removed command; other customized messages stay intact.
        if (yaml.getString("messages.help", "")!!.contains("/lore")) yaml.set("messages.help", defaults.getString("messages.help"))
        if (yaml.getString("messages.sign-usage", "")!!.contains("&a")) yaml.set("messages.sign-usage", defaults.getString("messages.sign-usage"))
        val settings = Settings(yaml)
        service = ItemService(settings, TextRenderer(settings, NexoBridge(this)))
        pending.clear()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        try {
            if (command.name == "itemsignature" || command.name == "enthusiasignature") {
                if (args.size == 1 && args[0].equals("reload", true)) {
                    if (!sender.hasPermission("itemsignature.reload")) throw InputFailure("no-permission")
                    try { loadSettings() } catch (ex: Exception) {
                        logger.warning("Configuration reload rejected: ${ex.message}")
                        throw InputFailure("reload-failed")
                    }
                    message(sender, "reloaded")
                } else message(sender, "help")
                return true
            }
            val player = sender as? Player ?: throw InputFailure("players-only")
            val item = player.inventory.itemInMainHand
            when (command.name) {
                "sign" -> signing(player, item, args.toList())
                "track" -> {
                    val stat = if (args.size == 1) Stat.from(args[0]) else null
                    if (stat == null) throw InputFailure("invalid-stat")
                    service.track(player, item, stat)
                    message(player, "tracker-applied", mapOf("stat_name" to service.settings.statName(stat)))
                }
            }
            player.inventory.setItemInMainHand(item)
        } catch (ex: InputFailure) { message(sender, ex.messageKey, ex.replacements) }
        return true
    }

    private fun message(sender: CommandSender, key: String, values: Map<String, String> = emptyMap()) {
        val yaml = service.settings.yaml
        val raw = yaml.getString("settings.prefix", "")!! + yaml.getString("messages.$key", key)!!
        sender.sendMessage(service.renderer.template(raw, values.mapValues { Component.text(it.value) }))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val choices = when {
            args.size == 1 && command.name == "track" -> Stat.entries.filter { sender.hasPermission("itemsignature.track.${it.id}") }.map { it.id }
            args.size == 1 && command.name == "sign" -> listOf("confirm", "cancel", "--color")
            args.size == 1 && command.name in listOf("itemsignature", "enthusiasignature") && sender.hasPermission("itemsignature.reload") -> listOf("reload")
            else -> emptyList()
        }
        return choices.filter { it.startsWith(args.lastOrNull() ?: "", true) }
    }
}
