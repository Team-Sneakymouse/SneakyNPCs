@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.CustomMenu
import com.danidipp.sneakynpcs.menus.HomeMenu
import com.danidipp.sneakynpcs.menus.MenuType
import com.danidipp.sneakynpcs.menus.NPCMenu
import com.danidipp.sneakynpcs.menus.QuestMenu
import com.danidipp.sneakynpcs.menus.ShopMenu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class NPCGui(val plugin: SneakyNPCs, val npc: NPC, val player: Player) : InventoryHolder {
    private val inventory: Inventory = Bukkit.createInventory(this, 9*6, Component.text(npc.id))
    override fun getInventory() = inventory
    private var menu: NPCMenu = npc.homeMenu.also { it.open(this, player, null) }

    fun openMenu(index: Int?) {
        if (index == null) {
            menu = npc.homeMenu
        } else {
            menu = npc.menus.getOrNull(index) ?: npc.homeMenu
        }

        val playerData = plugin.persistenceManager.dataCache[player.uniqueId]
        if (playerData != null) {
            menu.open(this, player, playerData)
        } else {
            plugin.logger.warning("No player data found for ${player.name} (${player.uniqueId}) when opening GUI page ${npc.id}-$index")
            player.sendMessage(plugin.prefix.append(Component.text("Failed to fetch your data. Please tell Dani.", NamedTextColor.RED)))
            menu = npc.homeMenu
            npc.homeMenu.open(this, player, null)
        }
    }

    fun close() {

        player.closeInventory()
    }

    companion object GuiListener : Listener {
        @EventHandler
        fun onInventoryDrag(event: InventoryDragEvent){
            if (event.inventory.holder !is NPCGui) return
            event.isCancelled = true
        }

        @EventHandler
        fun onInventoryClick(event: InventoryClickEvent) {
            SneakyNPCs.getInstance().logger.info("Clicked GUI ${event.inventory.holder?.javaClass?.simpleName} for ${event.whoClicked.name} at slot ${event.slot}")
            if (event.inventory.holder !is NPCGui) return
            val gui = event.inventory.holder as NPCGui
            event.isCancelled = true

            if (event.clickedInventory == null && gui.menu.type != MenuType.HOME){
                gui.openMenu(null)
                return
            }

            if (event.clickedInventory != event.view.topInventory) return
            gui.menu.onClick(gui, event)
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            if (event.inventory.holder !is NPCGui) return
            val gui = event.inventory.holder as NPCGui
            if (gui.menu.type != MenuType.HOME) {
                gui.openMenu(null)
                Bukkit.getScheduler().runTask(SneakyNPCs.getInstance()) { _ ->
                    event.player.openInventory(gui.inventory)
                }
            }
        }
    }
}

