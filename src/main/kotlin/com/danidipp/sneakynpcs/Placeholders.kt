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
        // %sneakynpcs_current_questid_JackTimbers%
        if (params.startsWith("current_questid_")) {
            val npcName = params.removePrefix("current_questid_")
            if (player == null) return "Player not online"
            val npc = resolveNpc(npcName) ?: return "NPC not found"
            val completedQuests = plugin.persistenceManager
                .dataCache[player.uniqueId]?.getCompletedQuests(npc.id) ?: return "No data"
            val questMenu = npc.allMenus.firstOrNull { it is QuestMenu } as? QuestMenu ?: return "No quest menu"
            val questIndex = questMenu.quests.indexOfFirst { !completedQuests.contains(it.quest) }
            if (questIndex == -1) return ""
            return questIndex.toString()
        }

        // %sneakynpcs_current_quest_JackTimbers%
        if (params.startsWith("current_quest_")) {
            val npcName = params.removePrefix("current_quest_")
            if (player == null) return "Player not online"
            val npc = resolveNpc(npcName) ?: return "NPC not found"
            val completedQuests = plugin.persistenceManager
                .dataCache[player.uniqueId]?.getCompletedQuests(npc.id) ?: return "No data"
            val questMenu = npc.allMenus.firstOrNull { it is QuestMenu } as? QuestMenu ?: return "No quest menu"
            val quest = questMenu.quests.firstOrNull { !completedQuests.contains(it.quest) } ?: return "All quests completed"
            return quest.quest.removePrefix("${npc.id}-")
        }

        return null
    }

    private fun resolveNpc(npcName: String): NPC? {
        return plugin.configManager.configs[npcName]
            ?: plugin.configManager.configs.entries.firstOrNull { (id, _) -> id.equals(npcName, ignoreCase = true) }?.value
    }
}
