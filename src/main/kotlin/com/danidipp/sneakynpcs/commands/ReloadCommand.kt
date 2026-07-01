@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs.commands

import com.danidipp.sneakynpcs.SneakyNPCs
import com.danidipp.sneakynpcs.ShopOfferAvailabilityReport
import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.HoverEvent
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
                val availabilityReport = plugin.configManager.latestAvailabilityReport
                sender.sendMessage(Component.join(JoinConfiguration.noSeparators(),
                    plugin.prefix,
                    Component.text("Reloaded ${result.loadedCount} NPC configurations.", statusColor),
                    if (result.keptCount > 0) Component.join(JoinConfiguration.noSeparators(),
                        Component.text(" Kept ", NamedTextColor.GRAY),
                        Component.text("${result.keptCount}", NamedTextColor.GOLD),
                        Component.text(" existing NPCs that failed to reload.", NamedTextColor.GRAY)
                    ) else Component.empty(),
                    Component.space(),
                    buildAvailabilitySummary(availabilityReport)
                ))
            }
            Command.SINGLE_SUCCESS
        }
    }

    private fun buildAvailabilitySummary(report: ShopOfferAvailabilityReport): Component {
        return Component.text(
            "Availability for ${report.shopDate}: " +
                "${report.visibleOfferIds.size} visible, " +
                "${report.hiddenOfferIds.size} hidden, " +
                "${report.randomGroupCount} random groups.",
            NamedTextColor.GRAY,
        ).hoverEvent(HoverEvent.showText(buildAvailabilityHover(report)))
    }

    private fun buildAvailabilityHover(report: ShopOfferAvailabilityReport): Component {
        return Component.join(
            JoinConfiguration.newlines(),
            listOf(
                Component.text("Visible offers", NamedTextColor.GREEN),
                buildIdLines(report.visibleOfferIds),
                Component.text("Hidden offers", NamedTextColor.RED),
                buildIdLines(report.hiddenOfferIds),
                Component.text("Random groups", NamedTextColor.GOLD),
                buildIdLines(report.randomGroupSelections.map { "${it.groupId} -> ${it.selectedItemId}" }),
            )
        )
    }

    private fun buildIdLines(ids: List<String>): Component {
        if (ids.isEmpty()) return Component.text("(none)", NamedTextColor.DARK_GRAY)
        return Component.join(
            JoinConfiguration.newlines(),
            ids.map { Component.text(it, NamedTextColor.GRAY) },
        )
    }
}
