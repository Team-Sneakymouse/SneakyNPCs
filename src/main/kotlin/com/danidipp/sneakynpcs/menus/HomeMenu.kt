package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

class HomeMenu() : NPCMenu(MenuType.HOME) {

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData) { return open(gui, player, null) }
    fun open(gui: NPCGui, player: Player, playerData: Nothing?) {
        val inv = gui.inventory
        val npc = gui.npc
        inv.clear()
        inv.setItem(0, makeItem("lom:npcs/gui-${npc.id.lowercase()}", "main"))
        if(npc.friendship) inv.setItem(1, makeItem("lom:npcs/friendship", 0))
        val rep = 0 // TODO: Get player reputation for npc's guild
        if (rep >= 10) inv.setItem(51, makeItem("lom:npcs/progressbar-reputation", rep))
    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {
        when (event.slot) {
            14, 15, 16 -> gui.openMenu(0)
            23, 24, 25 -> gui.openMenu(1)
            32, 33, 34 -> gui.openMenu(2)
            41, 42, 43 -> gui.openMenu(3)
        }
    }
}