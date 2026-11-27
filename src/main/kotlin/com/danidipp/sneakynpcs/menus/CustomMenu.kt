package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/** A placeholder row ("locked") in an NPC's options. NPC Screens 3 leaves these out of the hub. */
class CustomMenu(val guiId: String) : NPCMenu(MenuType.CUSTOM) {
    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {}
    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {}
}
