package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import com.nisovin.magicspells.util.magicitems.MagicItem
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

data class ShopMenuItem(
    val magicItem: MagicItem,
    val buyStacks: Boolean,
)

class ShopMenu(val maxGold: Int, val restockTime: Int, val items: List<ShopMenuItem>) : NPCMenu(MenuType.SHOP) {

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData) {
        val inv = gui.inventory
        val npc = gui.npc
        inv.clear()
        inv.setItem(0, makeItem("lom:npcs/gui-${npc.id.lowercase()}", "alt"))
        inv.setItem(53, makeItem("lom:npcs/tradewindow"))
    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {
        // TODO("Not yet implemented")
    }
}