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
import org.bukkit.Bukkit
import java.util.logging.Level

object ReloadCommand {
    fun createCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        val plugin = SneakyNPCs.getInstance()
        return Commands.literal("reload").executes { command ->
            command.source.sender.sendMessage("Configuration reloaded.")
            plugin.configManager.loadConfigs().handle { (configs, errors), throwable ->
                if(throwable != null) {
                    command.source.sender.sendMessage(Component.join(JoinConfiguration.noSeparators(),
                        plugin.prefix,
                        Component.text("Failed to load NPC configurations", NamedTextColor.RED),
                        Component.newline(),
                        Component.text(throwable.message ?: "Unknown error", NamedTextColor.GRAY)
                    ))
                    return@handle
                }
                if (errors.isNotEmpty()) {
                    command.source.sender.sendMessage(Component.join(JoinConfiguration.noSeparators(),
                        plugin.prefix,
                        Component.text("Skipped loading ${errors.size} npc config files due to validation errors", NamedTextColor.RED),
                        Component.newline(),
                        *errors.map { Component.join(JoinConfiguration.newlines(),
                            Component.text("${it.npcId}:", NamedTextColor.WHITE),
                            *it.errors.map { Component.text("- $it", NamedTextColor.GRAY) }.toTypedArray()
                        ) }.toTypedArray()
                    ))
                }
                // Keep existing npcs if they failed to load
                val loadedCount = configs.size
                for (error in errors) {
                    configs[error.npcId] = plugin.npcs[error.npcId] ?: continue
                }
                plugin.npcs.clear()
                plugin.npcs.putAll(configs)
                plugin.logger.info("Loaded $loadedCount NPC configurations.${if(loadedCount != plugin.npcs.size) " Kept ${plugin.npcs.size - loadedCount} existing NPCs that failed to reload." else ""}")
                command.source.sender.sendMessage(Component.join(JoinConfiguration.noSeparators(),
                    plugin.prefix,
                    Component.text("Reloaded $loadedCount NPC configurations.", NamedTextColor.GREEN),
                    if(loadedCount != plugin.npcs.size) Component.join(JoinConfiguration.noSeparators(),
                        Component.text(" Kept ", NamedTextColor.GRAY),
                        Component.text("${plugin.npcs.size - loadedCount}", NamedTextColor.GOLD),
                        Component.text(" existing NPCs that failed to reload.", NamedTextColor.GRAY)
                    ) else Component.empty()
                ))
            }
            Command.SINGLE_SUCCESS
        }
    }
}