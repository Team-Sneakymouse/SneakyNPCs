package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

class SelectionMenu(
    private val guiId: String,
    private val options: List<NPCMenu>,
) : NPCMenu(MenuType.SELECTION) {

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {
        val inv = gui.inventory
        val npc = gui.npc
        inv.clear()
        inv.setItem(0, makeItem(npc.guiModelKey, guiId))
        if (npc.friendship) inv.setItem(1, makeItem("lom:npcs/friendship", 0))
        val rep = 0 // TODO: Get player reputation for npc's guild
        if (rep >= 10) inv.setItem(51, makeItem("lom:npcs/progressbar-reputation", rep))
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
