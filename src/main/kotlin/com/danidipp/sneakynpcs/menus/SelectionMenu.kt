package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/** An NPC's root list of options. NPC Screens 3 draws them as its hub rows ([com.danidipp.sneakynpcs.screens3.Npc3Config]). */
class SelectionMenu(
    @Suppress("unused") private val guiId: String,
    private val options: List<NPCMenu>,
) : NPCMenu(MenuType.SELECTION) {
    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {}
    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {}
    override fun childMenus(): List<NPCMenu> = options
}
