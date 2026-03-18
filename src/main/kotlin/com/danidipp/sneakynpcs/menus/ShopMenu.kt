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
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
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
    private val sellSlot = 44
    private val walletSlot = 2

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {
        val page = pageState[player.uniqueId] ?: 0
        render(gui, player, page.coerceIn(0, getMaxPage()))
    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val currentPage = pageState[player.uniqueId] ?: 0
        if (event.slot == sellSlot) {
            handleSellSlotClick(gui, player, event)
            return
        }

        if (!isAllowedBuyClick(event.click)) return

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
        buildWalletStatusItem(player, npc)?.let { inv.setItem(walletSlot, it) }
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

    fun onPlayerInventoryClick(gui: NPCGui, event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!event.isShiftClick) return
        val offeredStack = event.currentItem?.clone() ?: return
        if (offeredStack.type.isAir) return

        handleSellResult(
            gui = gui,
            player = player,
            offeredStack = offeredStack,
            onSuccess = {
                event.clickedInventory?.setItem(event.slot, null)
            }
        )
    }

    fun onDrag(gui: NPCGui, event: InventoryDragEvent) {
        if (event.rawSlots != setOf(sellSlot)) return

        val player = event.whoClicked as? Player ?: return
        val offeredStack = event.newItems[sellSlot]?.clone() ?: return
        if (offeredStack.type.isAir || offeredStack.amount <= 0) return

        handleSellResult(
            gui = gui,
            player = player,
            offeredStack = offeredStack,
            onSuccess = {
                val remainingCursor = (event.oldCursor ?: return@handleSellResult).clone()
                val newAmount = remainingCursor.amount - offeredStack.amount
                event.view.setCursor(if (newAmount > 0) {
                    remainingCursor.apply { amount = newAmount }
                } else {
                    null
                })
            }
        )
    }

    private fun isAllowedBuyClick(click: ClickType): Boolean {
        return click == ClickType.LEFT || click == ClickType.SHIFT_LEFT
    }

    private fun handleSellSlotClick(gui: NPCGui, player: Player, event: InventoryClickEvent) {
        val action = event.action
        if (action != InventoryAction.PLACE_ALL &&
            action != InventoryAction.PLACE_SOME &&
            action != InventoryAction.PLACE_ONE &&
            action != InventoryAction.SWAP_WITH_CURSOR
        ) {
            return
        }

        val cursor = event.cursor ?: return
        if (cursor.type.isAir || cursor.amount <= 0) return
        val offeredAmount = if (action == InventoryAction.PLACE_ONE) 1 else cursor.amount
        val offeredStack = cursor.clone().apply { amount = offeredAmount }

        handleSellResult(
            gui = gui,
            player = player,
            offeredStack = offeredStack,
            onSuccess = {
                if (action == InventoryAction.PLACE_ONE) {
                    val updatedCursor = cursor.clone()
                    updatedCursor.amount = updatedCursor.amount - 1
                    event.view.setCursor(updatedCursor.takeIf { it.amount > 0 })
                } else {
                    event.view.setCursor(null)
                }
            }
        )
    }

    private fun handleSellResult(
        gui: NPCGui,
        player: Player,
        offeredStack: ItemStack,
        onSuccess: () -> Unit,
    ) {
        val playerData = plugin.persistenceManager.dataCache[player.uniqueId]
        if (playerData == null) {
            player.playSound(player.location, "lom:fail_wrong", 1f, 1f)
            player.sendMessage(plugin.prefix.append(Component.text("Failed to fetch your data. Please tell Dani.", NamedTextColor.RED)))
            return
        }

        when (val result = plugin.shopTransactionService.sell(player, playerData, gui.npc, offeredStack)) {
            is ShopTransactionService.SellResult.Success -> {
                onSuccess()
                player.playSound(player.location, "lom:buy", 1f, 1f)
                render(gui, player, pageState[player.uniqueId] ?: 0)
            }
            is ShopTransactionService.SellResult.Failure -> {
                player.playSound(player.location, "lom:fail_wrong", 1f, 1f)
                player.sendMessage(plugin.prefix.append(Component.text(result.message, NamedTextColor.RED)))
            }
        }
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

    private fun buildWalletStatusItem(player: Player, npc: com.danidipp.sneakynpcs.NPC): ItemStack? {
        val playerData = plugin.persistenceManager.dataCache[player.uniqueId] ?: return null
        val walletState = plugin.npcWalletService.getOrCreateRestockedWallet(playerData, npc)
        val nativeCurrencyId = npc.wallet.currencyId
        val nativeAmount = walletState.balances[nativeCurrencyId] ?: 0L
        val item = if (nativeAmount <= 0L) {
            makeItem("lom:invisible")
        } else {
            val fullness = ((nativeAmount.toDouble() / npc.wallet.max.toDouble()) * 25.0)
                .toInt()
                .coerceIn(1, 25)
            makeItem("lom:npcs/progressbar-gold", fullness)
        }

        val lore = buildList {
            if (walletState.balances.isEmpty()) {
                add(
                    Component.text("Empty", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                )
            } else {
                for ((currencyId, amount) in walletState.balances.toSortedMap()) {
                    add(
                        Component.text()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text(formatCurrencyId(currencyId), NamedTextColor.GOLD))
                            .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(amount.toString(), NamedTextColor.YELLOW))
                            .build()
                    )
                }
            }
        }

        item.editMeta { meta ->
            meta.displayName(
                Component.text("NPC Wallet", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)
            )
            meta.lore(lore)
        }
        return item
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
