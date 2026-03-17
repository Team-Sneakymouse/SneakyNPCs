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
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ShopMenuItem(
    val itemId: String,
    val magicItem: MagicItem,
    val buyStacks: Boolean,
    val requirements: List<ShopRequirement>,
    val price: ShopPrice,
)

class ShopMenu(
    private val items: List<ShopMenuItem>,
    private val currencyId: String?,
) : NPCMenu(MenuType.SHOP) {
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
        if (!isAllowedClick(event.click)) return

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
                render(gui, player, currentPage)
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
        inv.setItem(0, makeItem(npc.guiModelKey, "alt"))
        inv.setItem(53, makeItem("lom:npcs/tradewindow"))
        buildCurrencyTooltipItem(player)?.let { inv.setItem(39, it) }

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

    private fun isAllowedClick(click: ClickType): Boolean {
        return click == ClickType.LEFT || click == ClickType.SHIFT_LEFT
    }

    private fun buildCurrencyTooltipItem(player: Player): ItemStack? {
        val selectedCurrencyId = currencyId ?: return null
        val relatedCurrencies = plugin.currencyGraphService.getRelatedBankCurrencies(selectedCurrencyId)
        if (relatedCurrencies.isEmpty()) return null

        val lore = relatedCurrencies.map { currency ->
            val amount = plugin.balanceService.getBankCurrencyUnits(player, currency)
            Component.text()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(formatCurrencyId(currency.id), NamedTextColor.GOLD))
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(Component.text(amount.toString(), NamedTextColor.YELLOW))
                .build()
        }

        return makeItem("lom:invisible").apply {
            editMeta { meta ->
                meta.displayName(
                    Component.text("Bank Balance", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
                )
                meta.lore(lore)
            }
        }
    }

    private fun formatCurrencyId(currencyId: String): String {
        return currencyId.split('_', '-', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
    }

    private fun getMaxPage(): Int = if (items.size > 24) 1 else 0
}
