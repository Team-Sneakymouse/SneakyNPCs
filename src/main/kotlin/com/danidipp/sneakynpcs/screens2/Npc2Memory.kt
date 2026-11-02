package com.danidipp.sneakynpcs.screens2

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

/**
 * What an NPC remembers of a character: the last thing they bought from them, kept on the player (one entry per
 * character and NPC).
 */
internal object Npc2Memory {
    private const val UUID_PLACEHOLDER = "%sneakycharacters_character_uuid%"

    private fun characterId(player: Player): String =
        (placeholder(player, UUID_PLACEHOLDER) ?: player.uniqueId.toString()).lowercase().filter { it.isLetterOrDigit() || it == '-' }

    private fun placeholder(player: Player, placeholder: String): String? {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return null
        val value = PlaceholderAPI.setPlaceholders(player, placeholder)
        return value.takeIf { it.isNotBlank() && it != placeholder }
    }

    private fun key(player: Player, npcId: String) =
        NamespacedKey("sneakynpcs", "npc2/${npcId.lowercase().filter { it.isLetterOrDigit() || it == '_' }}/${characterId(player)}")

    fun lastPurchase(player: Player, npcId: String): String? =
        player.persistentDataContainer.get(key(player, npcId), PersistentDataType.STRING)

    fun rememberPurchase(player: Player, npcId: String, itemName: String) {
        player.persistentDataContainer.set(key(player, npcId), PersistentDataType.STRING, itemName)
    }
}
