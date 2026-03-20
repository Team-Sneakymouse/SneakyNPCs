package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

class CustomMenu(val guiId: String) : NPCMenu(MenuType.CUSTOM) {

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {
        val inv = gui.inventory
        val npc = gui.npc
        inv.clear()
        inv.setItem(0, makeItem(npc.guiModelKey, guiId, shouldHideTooltip(player)))
    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {
        // TODO("Not yet implemented")
    }
}
