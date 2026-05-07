@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs.commands

import com.danidipp.sneakynpcs.SneakyNPCs
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
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

object ReputationCommand {
    private val plugin = SneakyNPCs.getInstance()

    fun createCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("reputation")
            .then(Commands.literal("list")
                .then(Commands.argument("player", ArgumentTypes.player())
                    .executes { ctx ->
                        val player = resolvePlayer(ctx.source, ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java))
                            ?: return@executes Command.SINGLE_SUCCESS

                        plugin.persistenceManager.getPlayerData(player.uniqueId).thenAccept { playerData ->
                            val entries = playerData.getReputationEntries()
                                .filterKeys { it in plugin.npcs.keys }
                                .toSortedMap()

                            if (entries.isEmpty()) {
                                ctx.source.sender.sendMessage(plugin.prefix.append(
                                    Component.text(player.name, NamedTextColor.GOLD)
                                        .append(Component.text(" has no reputation entries.", NamedTextColor.GRAY))
                                ))
                                return@thenAccept
                            }

                            val header = Component.text("Reputation for ", NamedTextColor.GRAY)
                                .append(Component.text(player.name, NamedTextColor.GOLD))
                                .append(Component.text(":", NamedTextColor.GRAY))
                            val lines = entries.map { (npcId, amount) ->
                                Component.text(npcId, NamedTextColor.GOLD)
                                    .append(Component.text(": ", NamedTextColor.GRAY))
                                    .append(Component.text(formatReputation(amount), NamedTextColor.YELLOW))
                            }
                            ctx.source.sender.sendMessage(plugin.prefix
                                .append(header)
                                .append(Component.newline())
                                .append(Component.join(JoinConfiguration.newlines(), lines))
                            )
                        }

                        Command.SINGLE_SUCCESS
                    }
                    .then(Commands.argument("npc", StringArgumentType.word())
                        .suggests { _, builder ->
                            suggestNpcIds(builder)
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val npcId = ctx.getArgument("npc", String::class.java)
                            val npc = plugin.npcs[npcId] ?: run {
                                ctx.source.sender.sendMessage(
                                    plugin.prefix.append(Component.text("NPC with ID '$npcId' not found.", NamedTextColor.RED))
                                )
                                return@executes Command.SINGLE_SUCCESS
                            }

                            val player = resolvePlayer(ctx.source, ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java))
                                ?: return@executes Command.SINGLE_SUCCESS

                            plugin.persistenceManager.getPlayerData(player.uniqueId).thenAccept { playerData ->
                                val reputation = playerData.getReputation(npc.id)
                                ctx.source.sender.sendMessage(plugin.prefix.append(
                                    Component.text("Reputation for ", NamedTextColor.GRAY)
                                        .append(Component.text(player.name, NamedTextColor.GOLD))
                                        .append(Component.text(" with NPC ", NamedTextColor.GRAY))
                                        .append(Component.text(npc.id, NamedTextColor.GOLD))
                                        .append(Component.text(" is ", NamedTextColor.GRAY))
                                        .append(Component.text(formatReputation(reputation), NamedTextColor.YELLOW))
                                        .append(Component.text(".", NamedTextColor.GRAY))
                                ))
                            }

                            Command.SINGLE_SUCCESS
                        }
                    )
                )
            )
            .then(Commands.literal("set")
                .then(Commands.argument("player", ArgumentTypes.player())
                    .then(Commands.argument("npc", StringArgumentType.word())
                        .suggests { _, builder ->
                            suggestNpcIds(builder)
                            builder.buildFuture()
                        }
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                            .executes { ctx ->
                                val npcId = ctx.getArgument("npc", String::class.java)
                                val npc = plugin.npcs[npcId] ?: run {
                                    ctx.source.sender.sendMessage(
                                        plugin.prefix.append(Component.text("NPC with ID '$npcId' not found.", NamedTextColor.RED))
                                    )
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                val player = resolvePlayer(ctx.source, ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java))
                                    ?: return@executes Command.SINGLE_SUCCESS
                                val value = DoubleArgumentType.getDouble(ctx, "value")

                                plugin.persistenceManager.getPlayerData(player.uniqueId).thenAccept { playerData ->
                                    playerData.setReputation(npc.id, value)
                                    ctx.source.sender.sendMessage(plugin.prefix.append(
                                        Component.text("Set reputation for ", NamedTextColor.GRAY)
                                            .append(Component.text(player.name, NamedTextColor.GOLD))
                                            .append(Component.text(" with NPC ", NamedTextColor.GRAY))
                                            .append(Component.text(npc.id, NamedTextColor.GOLD))
                                            .append(Component.text(" to ", NamedTextColor.GRAY))
                                            .append(Component.text(formatReputation(value), NamedTextColor.YELLOW))
                                            .append(Component.text(".", NamedTextColor.GRAY))
                                    ))
                                }

                                Command.SINGLE_SUCCESS
                            }
                        )
                    )
                )
            )
    }

    private fun resolvePlayer(source: CommandSourceStack, resolver: PlayerSelectorArgumentResolver): Player? {
        return resolver.resolve(source).firstOrNull() ?: run {
            source.sender.sendMessage(plugin.prefix.append(Component.text("Player not found.", NamedTextColor.RED)))
            null
        }
    }

    private fun suggestNpcIds(builder: com.mojang.brigadier.suggestion.SuggestionsBuilder) {
        plugin.npcs.keys
            .filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .forEach { builder.suggest(it) }
    }

    private fun formatReputation(value: Double): String {
        return value.toBigDecimal().stripTrailingZeros().toPlainString()
    }
}
