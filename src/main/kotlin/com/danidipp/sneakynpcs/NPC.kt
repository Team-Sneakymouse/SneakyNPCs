package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.NPCMenu

data class NPC (
    val id: String,
    val friendship: Boolean,
    val reputation: String,
    val maxGold: Int,
    val restockInterval: Int,
    val restockAmount: Int,
    val rootMenu: NPCMenu,
) {
    val allMenus: List<NPCMenu> = flattenMenuTree(rootMenu)

    private fun flattenMenuTree(root: NPCMenu): List<NPCMenu> {
        val flattened = mutableListOf<NPCMenu>()

        fun visit(menu: NPCMenu) {
            flattened.add(menu)
            menu.childMenus().forEach(::visit)
        }

        visit(root)
        return flattened
    }
}
