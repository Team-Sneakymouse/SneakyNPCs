@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs.commands

import com.danidipp.sneakynpcs.SneakyNPCs
import com.danidipp.sneakynpcs.menus.QuestMenu
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

object QuestCommand {
    private val plugin = SneakyNPCs.getInstance()

    fun createCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("quest")
            .then(Commands.literal("list")
                .then(Commands.argument("player", ArgumentTypes.player())
                    .then(Commands.argument("npc", StringArgumentType.word())
                        .suggests { ctx, builder ->
                            plugin.npcs.keys
                                .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                                .forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val npcId = ctx.getArgument("npc", String::class.java)
                            val npc = plugin.npcs[npcId] ?: run {
                                ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("NPC with ID '$npcId' not found.", NamedTextColor.RED)))
                                return@executes Command.SINGLE_SUCCESS
                            }

                            val playerResolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                            val player: Player = playerResolver.resolve(ctx.source).firstOrNull() ?: run {
                                ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("Player not found.", NamedTextColor.RED)))
                                return@executes Command.SINGLE_SUCCESS
                            }

                            plugin.persistenceManager.getPlayerData(player.uniqueId).thenAccept { playerData ->
                                val completedQuests = playerData.getCompletedQuests(npc.id)

                                val header = Component.text("Quests for ", NamedTextColor.GRAY)
                                    .append(Component.text(npc.id, NamedTextColor.GOLD))
                                    .append(Component.text(" for player ", NamedTextColor.GRAY))
                                    .append(Component.text(player.name, NamedTextColor.GOLD))
                                    .append(Component.text(":", NamedTextColor.GRAY))

                                val questMenus = npc.allMenus.filterIsInstance<QuestMenu>()
                                if (questMenus.isEmpty()) {
                                    ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("This NPC does not have any quests.", NamedTextColor.RED)))
                                    return@thenAccept
                                }

                                val questLines = questMenus.map { menu ->
                                    val entries = menu.quests.map { quest ->
                                        val questName = quest.quest.removePrefix("${npc.id}-")
                                        val isCompleted = completedQuests.contains(quest.quest)
                                        val color = if (isCompleted) NamedTextColor.GREEN else NamedTextColor.RED
                                        Component.text(questName, color)
                                    }
                                    Component.join(JoinConfiguration.separator(Component.space()), entries)
                                }

                                val questList = Component.join(JoinConfiguration.newlines(), questLines)
                                
                                ctx.source.sender.sendMessage(plugin.prefix
                                    .append(header)
                                    .append(Component.newline())
                                    .append(questList)
                                )
                            }

                            Command.SINGLE_SUCCESS
                        }
                    )
                )
            )
            .then(Commands.literal("complete")
                .then(Commands.argument("player", ArgumentTypes.player())
                    .then(Commands.argument("npc", StringArgumentType.word())
                        .suggests { ctx, builder ->
                            plugin.npcs.keys
                                .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                                .forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .then(Commands.argument("quest", StringArgumentType.word())
                            .suggests { ctx, builder ->
                                val npcId = try { ctx.getArgument("npc", String::class.java) } catch (e: Exception) { null }
                                val npc = npcId?.let { plugin.npcs[it] }
                                npc?.allMenus?.filterIsInstance<QuestMenu>()?.forEach { menu ->
                                    menu.quests.map { it.quest.removePrefix("${npc.id}-") }
                                        .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                                        .forEach { builder.suggest(it) }
                                }
                                builder.buildFuture()
                            }
                            .executes { ctx ->
                                executeQuestAction(ctx, true)
                            }
                        )
                    )
                )
            )
            .then(Commands.literal("reset")
                .then(Commands.argument("player", ArgumentTypes.player())
                    .then(Commands.argument("npc", StringArgumentType.word())
                        .suggests { ctx, builder ->
                            plugin.npcs.keys
                                .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                                .forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .then(Commands.argument("quest", StringArgumentType.word())
                            .suggests { ctx, builder ->
                                val npcId = try { ctx.getArgument("npc", String::class.java) } catch (e: Exception) { null }
                                val npc = npcId?.let { plugin.npcs[it] }
                                npc?.allMenus?.filterIsInstance<QuestMenu>()?.forEach { menu ->
                                    menu.quests.map { it.quest.removePrefix("${npc.id}-") }
                                        .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                                        .forEach { builder.suggest(it) }
                                }
                                builder.buildFuture()
                            }
                            .executes { ctx ->
                                executeQuestAction(ctx, false)
                            }
                        )
                    )
                )
            )
    }

    private fun executeQuestAction(ctx: com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack>, complete: Boolean): Int {
        val npcId = ctx.getArgument("npc", String::class.java)
        val questShortId = ctx.getArgument("quest", String::class.java)
        val fullQuestId = "$npcId-$questShortId"

        val npc = plugin.npcs[npcId] ?: run {
            ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("NPC with ID '$npcId' not found.", NamedTextColor.RED)))
            return Command.SINGLE_SUCCESS
        }

        // Verify quest exists
        val questExists = npc.allMenus.filterIsInstance<QuestMenu>().any { menu ->
            menu.quests.any { it.quest == fullQuestId }
        }

        if (!questExists) {
            ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("Quest '$questShortId' not found for NPC '$npcId'.", NamedTextColor.RED)))
            return Command.SINGLE_SUCCESS
        }

        val playerResolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
        val player: Player = playerResolver.resolve(ctx.source).firstOrNull() ?: run {
            ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("Player not found.", NamedTextColor.RED)))
            return Command.SINGLE_SUCCESS
        }

        plugin.persistenceManager.getPlayerData(player.uniqueId).thenAccept { playerData ->
            if (complete) {
                playerData.completeQuest(fullQuestId)
                ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("Set quest ", NamedTextColor.GRAY)
                    .append(Component.text(questShortId, NamedTextColor.GOLD))
                    .append(Component.text(" for ", NamedTextColor.GRAY))
                    .append(Component.text(player.name, NamedTextColor.GOLD))
                    .append(Component.text(" to ", NamedTextColor.GRAY))
                    .append(Component.text("completed", NamedTextColor.GREEN))
                    .append(Component.text(".", NamedTextColor.GRAY))
                ))
            } else {
                playerData.removeQuest(fullQuestId)
                ctx.source.sender.sendMessage(plugin.prefix.append(Component.text("Set quest ", NamedTextColor.GRAY)
                    .append(Component.text(questShortId, NamedTextColor.GOLD))
                    .append(Component.text(" for ", NamedTextColor.GRAY))
                    .append(Component.text(player.name, NamedTextColor.GOLD))
                    .append(Component.text(" to ", NamedTextColor.GRAY))
                    .append(Component.text("incomplete", NamedTextColor.RED))
                    .append(Component.text(".", NamedTextColor.GRAY))
                ))
            }
        }

        return Command.SINGLE_SUCCESS
    }
}
