package com.danidipp.sneakynpcs

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

class QuestDebugService : Listener {
    private val enabledPlayers = mutableSetOf<UUID>()

    fun toggle(player: Player): Boolean {
        val playerId = player.uniqueId
        if (enabledPlayers.add(playerId)) return true

        enabledPlayers.remove(playerId)
        return false
    }

    fun isEnabled(player: Player): Boolean = player.uniqueId in enabledPlayers

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        enabledPlayers.remove(event.player.uniqueId)
    }
}
