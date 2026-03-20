package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.InventoryAuditItems
import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.SneakyNPCs
import com.nisovin.magicspells.util.magicitems.MagicItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

data class NPCQuest(
    val quest: String,
    val items: List<NPCQuestItem>
)
data class NPCQuestItem(
    val magicItem: MagicItem,
    val amount: Int
)

class QuestMenu(val quests: List<NPCQuest>) : NPCMenu(MenuType.QUEST) {
    val plugin = SneakyNPCs.getInstance()

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {
        val resolvedData = playerData ?: plugin.persistenceManager.dataCache[player.uniqueId]
        if (resolvedData == null) {
            plugin.logger.warning("No player data found for ${player.name} (${player.uniqueId}) when opening quest menu")
            player.sendMessage(plugin.prefix.append(Component.text("Failed to fetch your data. Please tell Dani.", NamedTextColor.RED)))
            return
        }

        val inv = gui.inventory
        val npc = gui.npc
        val hideTooltip = shouldHideTooltip(player)

        val completedQuests = resolvedData.getCompletedQuests(npc.id)
        val currentQuest = quests.firstOrNull{ !completedQuests.contains(it.quest) }
        if (currentQuest == null) {
            plugin.logger.severe("Failed to find active quest")
            return
        }

        inv.clear()
        inv.setItem(0, makeItem(npc.guiModelKey, "alt", hideTooltip))
        inv.setItem(1, makeItem(npc.questModelKey, currentQuest.quest, hideTooltip))
        inv.setItem(53, makeItem("lom:npcs/questbox", if (questCompletable(player, currentQuest)) "complete" else "incomplete", hideTooltip))

        val items = currentQuest.items.toMutableList()
        for (slot in 5 until 9) {
            if (items.isEmpty()) break
            val item = items.removeAt(0)
            inv.setItem(slot, item.magicItem.itemStack.clone().apply { amount = item.amount })
        }
        for (slot in 14 until 18) {
            if (items.isEmpty()) break
            val item = items.removeAt(0)
            inv.setItem(slot, item.magicItem.itemStack.clone().apply { amount = item.amount })
        }

    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {
        val playerData = plugin.persistenceManager.dataCache[event.whoClicked.uniqueId]
        if (playerData == null) {
            plugin.logger.warning("No player data found for ${event.whoClicked.name} (${event.whoClicked.uniqueId}) when clicking GUI page ${this.javaClass.simpleName}")
            event.whoClicked.sendMessage(plugin.prefix.append(Component.text("Failed to fetch your data. Please tell Dani.", NamedTextColor.RED)))
            return
        }

        val completedQuests = playerData.getCompletedQuests(gui.npc.id)
        val currentQuest = quests.firstOrNull{ !completedQuests.contains(it.quest) }
        if (currentQuest == null) {
            plugin.logger.severe("Failed to find active quest")
            return
        }

        val player = event.whoClicked as? Player ?: run {
            event.whoClicked.sendMessage(plugin.prefix.append(Component.text("Must be a player to click", NamedTextColor.RED)))
            return
        }

        if (event.slot in 23..26) {
            if (!questCompletable(player, currentQuest)) return
            completeQuest(player, playerData, currentQuest)
            gui.close()
        }
    }

    fun questCompletable(player: Player, quest: NPCQuest): Boolean {
        if (quest.items.isEmpty()) {
            player.sendMessage("Quest has no items - uncompletable")
            return false // End of quest chain
        }
        val itemCounts: MutableMap<MagicItem, Int> = mutableMapOf()
        for (item in quest.items) {
            itemCounts[item.magicItem] = (itemCounts[item.magicItem] ?: 0) + item.amount
        }

        for ((requiredMagicItem, requiredAmount) in itemCounts) {
            val ownedItems = player.inventory.filter { it?.isSimilar(requiredMagicItem.itemStack) == true }
            val ownedAmount = ownedItems.sumOf { it.amount }
            if (ownedAmount < requiredAmount) {
                val itemName = PlainTextComponentSerializer.plainText().serialize(requiredMagicItem.itemStack.displayName())
                plugin.logger.warning("Player ${player.name} doesn't have enough ${itemName}: $ownedAmount/$requiredAmount")
                return false
            }
        }
        return true
    }

    // Pre-check: quest is current quest, quest is completable
    private fun completeQuest(player: Player, playerData: PlayerData, quest: NPCQuest) {
        // Tally up amounts for each unique MagicItem
        val itemCounts: MutableMap<MagicItem, Int> = mutableMapOf()
        for (item in quest.items) {
            itemCounts[item.magicItem] = (itemCounts[item.magicItem] ?: 0) + item.amount
        }

        // Remove required amounts from player's inventory
        for ((requiredMagicItem, requiredAmount) in itemCounts) {
            var remaining = requiredAmount
            val inv = player.inventory

            for (slot in 0 until inv.size) {
                if (remaining <= 0) break
                val stack = inv.getItem(slot) ?: continue
                if (!stack.isSimilar(requiredMagicItem.itemStack)) continue

                val take = minOf(stack.amount, remaining)
                if (stack.amount > take) {
                    val newStack = stack.clone()
                    newStack.amount = stack.amount - take
                    inv.setItem(slot, newStack)
                } else {
                    inv.setItem(slot, null)
                }
                remaining -= take
            }
        }

        plugin.inventoryTransactionLogger.log(
            player = player,
            removedItems = itemCounts.flatMap { (requiredMagicItem, requiredAmount) ->
                InventoryAuditItems.split(requiredMagicItem.itemStack, requiredAmount.toLong())
            }
        )

        playerData.completeQuest(quest.quest)
        player.sendMessage(plugin.prefix.append(Component.text("Quest completed!", NamedTextColor.GREEN)))
    }

}
