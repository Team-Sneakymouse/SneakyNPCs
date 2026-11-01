@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs.commands

import com.danidipp.sneakynpcs.SneakyNPCs
import com.danidipp.sneakynpcs.screens2.Npc2Config
import com.danidipp.sneakynpcs.screens2.Npc2Screen
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** /sneakynpcs opengui2 <npc> [player]: the NPC Screens 2 version of opengui, from npc2/<npc>.yml. */
object OpenGUI2Command {
    val plugin = SneakyNPCs.getInstance()

    fun createCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("opengui2").then(Commands
            .argument("npc", StringArgumentType.word())
            .suggests { _, builder ->
                Npc2Config.ids(plugin.dataFolder)
                    .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                    .forEach { builder.suggest(it) }
                builder.buildFuture()
            }
            .then(Commands
                .argument("player", ArgumentTypes.player())
                .executes { ctx ->
                    val resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                    open(ctx.source.sender, ctx.getArgument("npc", String::class.java), resolver.resolve(ctx.source).first())
                    Command.SINGLE_SUCCESS
                }
            )
            .executes { ctx ->
                val player = ctx.source.sender as? Player ?: run {
                    ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("Only players can use this command for themselves", NamedTextColor.RED)))
                    return@executes Command.SINGLE_SUCCESS
                }
                open(player, ctx.getArgument("npc", String::class.java), player)
                Command.SINGLE_SUCCESS
            }
        )
    }

    private fun open(sender: CommandSender, id: String, player: Player) {
        val config = Npc2Config.load(plugin.dataFolder, id) ?: run {
            sender.sendMessage(plugin.prefix.append(Component.text("No npc2/$id.yml found.", NamedTextColor.RED)))
            return
        }
        config.warnings.forEach { plugin.logger.warning("npc2/${config.id}.yml: $it") }
        val npc = plugin.npcs[config.npcId] ?: run {
            sender.sendMessage(plugin.prefix.append(Component.text("npc2/${config.id}.yml names NPC '${config.npcId}', which isn't loaded.", NamedTextColor.RED)))
            return
        }
        plugin.persistenceManager.getPlayerData(player.uniqueId).whenComplete { _, throwable ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                if (throwable != null || !plugin.persistenceManager.dataCache.containsKey(player.uniqueId)) {
                    plugin.logger.warning("Failed to load player data for ${player.name} before opening npc2 '${config.id}': ${throwable?.message}")
                    player.sendMessage(plugin.prefix.append(Component.text("Failed to fetch your data. Please tell Dani.", NamedTextColor.RED)))
                    return@Runnable
                }
                Npc2Screen(plugin, config, npc, player).open()
            })
        }
    }
}
