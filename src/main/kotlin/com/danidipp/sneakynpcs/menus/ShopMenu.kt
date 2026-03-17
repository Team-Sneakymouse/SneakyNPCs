package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.SneakyNPCs
import com.danidipp.sneakynpcs.shop.ShopPrice
import com.danidipp.sneakynpcs.shop.ShopRequirement
import com.danidipp.sneakynpcs.shop.ShopTransactionService
import com.nisovin.magicspells.util.magicitems.MagicItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ShopMenuItem(
    val itemId: String,
    val magicItem: MagicItem,
    val buyStacks: Boolean,
    val requirements: List<ShopRequirement>,
    val price: ShopPrice,
)

class ShopMenu(private val items: List<ShopMenuItem>) : NPCMenu(MenuType.SHOP) {
    private val plugin = SneakyNPCs.getInstance()
    private val pageState = ConcurrentHashMap<UUID, Int>()

    private val productSlots = listOf(
        3, 4, 5, 6, 7, 8,
        12, 13, 14, 15, 16, 17,
        21, 22, 23, 24, 25, 26,
        30, 31, 32, 33, 34, 35
    )
    private val pageToggleSlot = 40

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {
        val page = pageState[player.uniqueId] ?: 0
        render(gui, player, page.coerceIn(0, getMaxPage()))
    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val currentPage = pageState[player.uniqueId] ?: 0

        if (event.slot == pageToggleSlot && items.size > 24) {
            val nextPage = if (currentPage == 0) 1 else 0
            render(gui, player, nextPage)
            return
        }

        val slotIndex = productSlots.indexOf(event.slot)
        if (slotIndex == -1) return

        val itemIndex = currentPage * 24 + slotIndex
        val shopItem = items.getOrNull(itemIndex) ?: return
        val quantity = getClickQuantity(event, shopItem)

        when (val result = plugin.shopTransactionService.purchase(player, shopItem, quantity)) {
            is ShopTransactionService.PurchaseResult.Success -> {
                player.playSound(player.location, "lom:buy", 1f, 1f)
            }
            is ShopTransactionService.PurchaseResult.Failure -> {
                player.playSound(player.location, "lom:fail_wrong", 1f, 1f)
                player.sendMessage(
                    plugin.prefix.append(
                        Component.text(result.message, NamedTextColor.RED)
                    )
                )
            }
        }
    }

    private fun render(gui: NPCGui, player: Player, requestedPage: Int) {
        val page = requestedPage.coerceIn(0, getMaxPage())
        pageState[player.uniqueId] = page

        val inv = gui.inventory
        val npc = gui.npc
        inv.clear()
        inv.setItem(0, makeItem("lom:npcs/gui-${npc.id.lowercase()}", "alt"))
        inv.setItem(53, makeItem("lom:npcs/tradewindow"))

        val pageStart = page * 24
        val pageItems = items.drop(pageStart).take(24)
        for ((index, shopItem) in pageItems.withIndex()) {
            val slot = productSlots[index]
            inv.setItem(slot, shopItem.magicItem.itemStack.clone())
        }

        if (items.size > 24) {
            inv.setItem(pageToggleSlot, makeItem("lom:npcs/tradewindow", "page-${page + 1}"))
        }
    }

    private fun getClickQuantity(event: InventoryClickEvent, shopItem: ShopMenuItem): Int {
        if (!shopItem.buyStacks) return 1
        if (!event.isShiftClick) return 1
        return shopItem.magicItem.itemStack.maxStackSize.coerceAtLeast(1)
    }

    private fun getMaxPage(): Int = if (items.size > 24) 1 else 0
}
