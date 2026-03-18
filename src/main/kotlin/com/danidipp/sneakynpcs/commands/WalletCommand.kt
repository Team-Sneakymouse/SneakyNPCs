@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs.commands

import com.danidipp.sneakynpcs.SneakyNPCs
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

object WalletCommand {
    private val plugin = SneakyNPCs.getInstance()

    fun createCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("wallet")
            .then(Commands.literal("set")
                .then(Commands.argument("player", ArgumentTypes.player())
                    .then(Commands.argument("npc", StringArgumentType.word())
                        .suggests { _, builder ->
                            plugin.npcs.keys
                                .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                                .forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                            .executes { ctx ->
                                val npcId = ctx.getArgument("npc", String::class.java)
                                val npc = plugin.npcs[npcId] ?: run {
                                    ctx.source.sender.sendMessage(
                                        plugin.prefix.append(Component.text("NPC with ID '$npcId' not found.", NamedTextColor.RED))
                                    )
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                val playerResolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                                val player: Player = playerResolver.resolve(ctx.source).firstOrNull() ?: run {
                                    ctx.source.sender.sendMessage(
                                        plugin.prefix.append(Component.text("Player not found.", NamedTextColor.RED))
                                    )
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                val requestedAmount = LongArgumentType.getLong(ctx, "amount")
                                plugin.persistenceManager.getPlayerData(player.uniqueId).thenAccept { playerData ->
                                    val state = plugin.npcWalletService.setNativeWalletAmount(playerData, npc, requestedAmount)
                                    ctx.source.sender.sendMessage(
                                        plugin.prefix.append(
                                            Component.text("Set wallet for ", NamedTextColor.GRAY)
                                                .append(Component.text(player.name, NamedTextColor.GOLD))
                                                .append(Component.text(" with NPC ", NamedTextColor.GRAY))
                                                .append(Component.text(npc.id, NamedTextColor.GOLD))
                                                .append(Component.text(" to ", NamedTextColor.GRAY))
                                                .append(Component.text(state.balances[npc.wallet.currencyId]?.toString() ?: "0", NamedTextColor.YELLOW))
                                                .append(Component.text(" ", NamedTextColor.GRAY))
                                                .append(Component.text(npc.wallet.currencyId, NamedTextColor.GOLD))
                                                .append(Component.text(".", NamedTextColor.GRAY))
                                        )
                                    )
                                }

                                Command.SINGLE_SUCCESS
                            }
                        )
                    )
                )
            )
    }
}
