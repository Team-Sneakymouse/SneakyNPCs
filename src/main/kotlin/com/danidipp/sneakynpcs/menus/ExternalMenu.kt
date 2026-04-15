package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

internal fun buildExternalMagicSpellCommand(playerName: String, magicSpellId: String): String {
    return "ms cast as $playerName $magicSpellId"
}

class ExternalMenu(val magicSpellId: String) : NPCMenu(MenuType.EXTERNAL) {
    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {
        val playerName = player.name
        val command = buildExternalMagicSpellCommand(playerName, magicSpellId)
        gui.closeAllLevels()
        gui.plugin.server.scheduler.runTaskLater(gui.plugin, Runnable {
            if (!player.isOnline) {
                gui.plugin.logger.warning("Player '$playerName' went offline before external menu spell '$magicSpellId' could be cast")
                return@Runnable
            }

            val dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            if (dispatched) return@Runnable

            gui.plugin.logger.warning("Failed to dispatch external menu command '$command' for player '$playerName'")
            player.sendMessage(gui.plugin.prefix.append(Component.text("Failed to open external menu. Please tell Dani.", NamedTextColor.RED)))
        }, 1L)
    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) = Unit
}
