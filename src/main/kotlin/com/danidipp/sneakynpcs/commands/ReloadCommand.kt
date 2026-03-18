@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs.commands

import com.danidipp.sneakynpcs.SneakyNPCs
import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor

object ReloadCommand {
    fun createCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        val plugin = SneakyNPCs.getInstance()
        return Commands.literal("reload").executes { command ->
            val sender = command.source.sender

            sender.sendMessage(
                plugin.prefix.append(Component.text("Reloading configurations...", NamedTextColor.GRAY))
            )

            plugin.reloadNpcConfigs("SneakyNPCsReloadCommand") { result ->
                if (result.throwable != null) {
                    sender.sendMessage(Component.join(JoinConfiguration.noSeparators(),
                        plugin.prefix,
                        Component.text("Failed to load NPC configurations", NamedTextColor.RED),
                        Component.newline(),
                        Component.text(result.throwable.message ?: "Unknown error", NamedTextColor.GRAY)
                    ))
                    return@reloadNpcConfigs
                }

                if (result.errors.isNotEmpty()) {
                    val summaryText = when {
                        result.loadedCount == 0 && result.keptCount > 0 ->
                            "Reload aborted due to validation errors. Kept ${result.keptCount} existing NPC configurations."
                        result.loadedCount == 0 ->
                            "Reload found ${result.errors.size} validation error groups."
                        else ->
                            "Reload completed with ${result.errors.size} validation error groups."
                    }

                    sender.sendMessage(Component.join(JoinConfiguration.noSeparators(),
                        plugin.prefix,
                        Component.text(summaryText, NamedTextColor.RED),
                        Component.newline(),
                        Component.join(JoinConfiguration.newlines(), result.errors.map { it.asComponent() })
                    ))
                }

                val statusColor = if (result.errors.isEmpty()) NamedTextColor.GREEN else NamedTextColor.YELLOW
                sender.sendMessage(Component.join(JoinConfiguration.noSeparators(),
                    plugin.prefix,
                    Component.text("Reloaded ${result.loadedCount} NPC configurations.", statusColor),
                    if (result.keptCount > 0) Component.join(JoinConfiguration.noSeparators(),
                        Component.text(" Kept ", NamedTextColor.GRAY),
                        Component.text("${result.keptCount}", NamedTextColor.GOLD),
                        Component.text(" existing NPCs that failed to reload.", NamedTextColor.GRAY)
                    ) else Component.empty()
                ))
            }
            Command.SINGLE_SUCCESS
        }
    }
}
