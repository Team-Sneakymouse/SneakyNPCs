@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.NPCMenu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class NPCGui(val plugin: SneakyNPCs, val npc: NPC, val player: Player) : InventoryHolder {
    private val inventory: Inventory = Bukkit.createInventory(this, 9 * 6, Component.text(npc.id))
    override fun getInventory() = inventory
    private val menuStack = ArrayDeque<NPCMenu>()
    private var menu: NPCMenu = npc.rootMenu

    init {
        menuStack.addLast(npc.rootMenu)
        openCurrentMenu()
    }

    fun openRoot() {
        menuStack.clear()
        menuStack.addLast(npc.rootMenu)
        openCurrentMenu()
    }

    fun pushMenu(child: NPCMenu) {
        menuStack.addLast(child)
        openCurrentMenu()
    }

    fun goBackOneLevel(): Boolean {
        if (menuStack.size <= 1) return false
        menuStack.removeLast()
        openCurrentMenu()
        return true
    }

    private fun openCurrentMenu() {
        menu = menuStack.last()
        val playerData = plugin.persistenceManager.dataCache[player.uniqueId]
        if (playerData == null) {
            plugin.logger.warning("No player data found for ${player.name} (${player.uniqueId}) when opening GUI for npc '${npc.id}' menu '${menu.type}'")
            player.sendMessage(plugin.prefix.append(Component.text("Failed to fetch your data. Please tell Dani.", NamedTextColor.RED)))
        }
        menu.open(this, player, playerData)
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

            // Double-click collect-to-cursor interactions can emit extra click events.
            if (event.click == ClickType.DOUBLE_CLICK || event.action == InventoryAction.COLLECT_TO_CURSOR) {
                return
            }

            if (event.clickedInventory == null) {
                gui.goBackOneLevel()
                return
            }

            if (event.clickedInventory != event.view.topInventory) return
            gui.menu.onClick(gui, event)
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            if (event.inventory.holder !is NPCGui) return
            val gui = event.inventory.holder as NPCGui
            if (gui.goBackOneLevel()) {
                Bukkit.getScheduler().runTask(SneakyNPCs.getInstance()) { _ ->
                    event.player.openInventory(gui.inventory)
                }
            }
        }
    }
}

