package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.SneakyNPCs
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

class SelectionMenu(
    private val guiId: String,
    private val options: List<NPCMenu>,
) : NPCMenu(MenuType.SELECTION) {

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {
        val inv = gui.inventory
        val npc = gui.npc
        val hideTooltip = shouldHideTooltip(player)
        inv.clear()
        inv.setItem(0, makeItem(npc.guiModelKey, guiId, hideTooltip))
        if (npc.friendship) inv.setItem(1, makeItem("lom:npcs/friendship", 0, hideTooltip))
        val rep = playerData?.getReputation(npc.id)
            ?: SneakyNPCs.getInstance().persistenceManager.dataCache[player.uniqueId]?.getReputation(npc.id)
            ?: 0.0
        if (rep >= 10.0) inv.setItem(51, makeItem("lom:npcs/progressbar-reputation", rep, hideTooltip))
    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {
        val optionIndex = when (event.slot) {
            14, 15, 16 -> 0
            23, 24, 25 -> 1
            32, 33, 34 -> 2
            41, 42, 43 -> 3
            else -> null
        } ?: return

        val child = options.getOrNull(optionIndex) ?: return
        gui.pushMenu(child)
    }

    override fun childMenus(): List<NPCMenu> = options
}
