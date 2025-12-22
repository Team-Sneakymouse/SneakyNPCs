package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.HomeMenu
import com.danidipp.sneakynpcs.menus.NPCMenu

data class NPC (
    val id: String,
    val friendship: Boolean,
    val reputation: String,
    val menus: List<NPCMenu>,
) {
    val homeMenu = HomeMenu()
}
