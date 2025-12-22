@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs.commands

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.SneakyNPCs
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player


object OpenGUICommand {
    val plugin = SneakyNPCs.getInstance()
    fun createCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("opengui").then(Commands
            .argument("npc", StringArgumentType.word())
            .suggests { ctx, builder ->
                plugin.npcs.keys
                    .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                    .forEach { builder.suggest(it) }
                builder.buildFuture()
            }
            .then(Commands
                .argument("player", ArgumentTypes.player())
                .executes { ctx ->
                    val npcId = ctx.getArgument("npc", String::class.java)
                    val npc = plugin.npcs[npcId] ?: run {
                        ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("NPC with ID '$npcId' not found.", NamedTextColor.RED)))
                        return@executes Command.SINGLE_SUCCESS
                    }
                    val playerResolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                    val player: Player = playerResolver.resolve(ctx.getSource()).first()

                    val gui = NPCGui(plugin, npc, player)
                    player.openInventory(gui.inventory)
                    Command.SINGLE_SUCCESS
                }
            )
            .executes { ctx ->
                val npcId = ctx.getArgument("npc", String::class.java)
                val npc = plugin.npcs[npcId] ?: run {
                    ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("NPC with ID '$npcId' not found.", NamedTextColor.RED)))
                    return@executes Command.SINGLE_SUCCESS
                }
                val player = ctx.source.sender as? Player ?: run {
                    ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("Only players can use this command for themselves", NamedTextColor.RED)))
                    return@executes Command.SINGLE_SUCCESS
                }

                val gui = NPCGui(plugin, npc, player)
                player.openInventory(gui.inventory)
                Command.SINGLE_SUCCESS
            }
        )

    }
}