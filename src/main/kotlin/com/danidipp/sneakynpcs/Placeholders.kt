package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.QuestMenu
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

class Placeholders : PlaceholderExpansion() {
    val plugin = SneakyNPCs.getInstance()
    override fun getIdentifier() = SneakyNPCs.IDENTIFIER
    override fun getAuthor() = SneakyNPCs.AUTHORS
    override fun getVersion() = SneakyNPCs.VERSION
    override fun persist() = true // This expansion will not be deleted when PlaceholderAPI is reloaded

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        // %sneakynpcs_current_quest_JackTimbers%
        if (params.startsWith("current_quest")) {
            val npcName = params.removePrefix("current_quest_")
            if (player == null) return ""
            val completedQuests = plugin.persistenceManager.dataCache[player.uniqueId]?.getCompletedQuests(npcName)
            if (completedQuests == null) return "No data"
            val npc = plugin.configManager.configs[npcName] ?: return "NPC not found"
            val questMenu = npc.menus.firstOrNull { it is QuestMenu } as? QuestMenu ?: return "No quest menu"
            val quest = questMenu.quests.firstOrNull { !completedQuests.contains(it.quest) } ?: return "All quests completed"
            return quest.quest.removePrefix("$npcName-")
        }

        return null
    }
}