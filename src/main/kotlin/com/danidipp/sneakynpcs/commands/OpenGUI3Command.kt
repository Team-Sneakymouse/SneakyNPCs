@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs.commands

import com.danidipp.sneakynpcs.SneakyNPCs
import com.danidipp.sneakynpcs.screens3.Npc3Config
import com.danidipp.sneakynpcs.screens3.Npc3Screen
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

/**
 * /sneakynpcs opengui <npc> [player] (also opengui3): NPC Screens 3, in SneakyRoamin's style. Works for every loaded
 * NPC; the hub rows come from its npc/<id>.yml menus (see [Npc3Config]).
 */
object OpenGUI3Command {
    val plugin = SneakyNPCs.getInstance()

    fun createCommand(literal: String): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(literal).then(Commands
            .argument("npc", StringArgumentType.word())
            .suggests { _, builder ->
                plugin.npcs.keys.sorted()
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
        val npc = plugin.npcs[id] ?: plugin.npcs.entries.firstOrNull { it.key.equals(id, ignoreCase = true) }?.value ?: run {
            sender.sendMessage(plugin.prefix.append(Component.text("NPC with ID '$id' not found.", NamedTextColor.RED)))
            return
        }
        val config = Npc3Config.of(npc)
        config.warnings.forEach { plugin.logger.warning("NPC Screens 3 '${npc.id}': $it") }
        plugin.persistenceManager.getPlayerData(player.uniqueId).whenComplete { _, throwable ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                if (throwable != null || !plugin.persistenceManager.dataCache.containsKey(player.uniqueId)) {
                    plugin.logger.warning("Failed to load player data for ${player.name} before opening npc3 '${npc.id}': ${throwable?.message}")
                    player.sendMessage(plugin.prefix.append(Component.text("Failed to fetch your data. Please tell Dani.", NamedTextColor.RED)))
                    return@Runnable
                }
                Npc3Screen(plugin, config, npc, player).open()
            })
        }
    }
}
