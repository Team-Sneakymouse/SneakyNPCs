@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.SneakyNPCs
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

enum class MenuType { SELECTION, QUEST, SHOP, CUSTOM}
sealed class NPCMenu(val type: MenuType) {

    abstract fun open(gui: NPCGui, player: Player, playerData: PlayerData?)
    abstract fun onClick(gui: NPCGui, event: InventoryClickEvent)
    open fun childMenus(): List<NPCMenu> = emptyList()
    protected fun shouldHideTooltip(player: Player): Boolean = player.gameMode == GameMode.SURVIVAL

    fun makeItem(key: String, data: Any? = null, hideTooltip: Boolean = false) = ItemStack(Material.BRICKS).apply {
        if (hideTooltip) setData(DataComponentTypes.HIDE_TOOLTIP)
        setData(DataComponentTypes.ITEM_MODEL, NamespacedKey(key.split(":").first(), key.split(":").last()))
        if (data != null) setData(DataComponentTypes.CUSTOM_MODEL_DATA, when (data) {
            is Int, is Double, is Number -> CustomModelData.customModelData().addFloat(data.toFloat())
            is String -> CustomModelData.customModelData().addString(data)
            else -> {
                SneakyNPCs.getInstance().logger.warning("Unsupported custom model data in GUIHandler#makeItem type: ${data::class.java}")
                CustomModelData.customModelData().addString(data.toString())
            }
        })
    }
}
