@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs.screens2

import com.danidipp.sneakynpcs.NPC
import com.danidipp.sneakynpcs.SneakyNPCs
import com.danidipp.sneakynpcs.menus.ShopMenu
import com.danidipp.sneakynpcs.menus.ShopMenuItem
import com.danidipp.sneakynpcs.menus.formatBankDisplayAmount
import com.danidipp.sneakynpcs.menus.shopOfferPage
import com.danidipp.sneakynpcs.shop.ShopMessageFormatter
import com.danidipp.sneakynpcs.shop.ShopRequirementEvaluation
import com.danidipp.sneakynpcs.shop.ShopTransactionService
import com.sneakyutils.util.DisplayOnlyInventoryHolder
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * NPC Screens 2 (/sneakynpcs opengui2): an NPC's hub and shop drawn as a district frame with live text, beside the
 * old NPCGui menus. Each screen is its own inventory whose title draws it (Npc2Title); going between screens opens
 * the next one. Esc or a click outside the window from the shop goes back to the hub.
 */
internal class Npc2Screen(
    private val plugin: SneakyNPCs,
    private val config: Npc2Config,
    private val npc: NPC,
    private val player: Player,
) : DisplayOnlyInventoryHolder {
    enum class Page { HUB, SHOP }

    private var page = Page.HUB
    private var shopPage = 0
    private var inventory: Inventory = Bukkit.createInventory(this, 54, Component.empty())
    /** set while one screen replaces another, so the old one's close isn't taken as Esc */
    private var switching = false
    private val shop: ShopMenu? = npc.allMenus.filterIsInstance<ShopMenu>().firstOrNull()
    private var shownOffers: List<ShopMenuItem> = emptyList()

    override fun getInventory() = inventory

    fun open() = show(Page.HUB)

    private fun show(next: Page) {
        page = next
        val title = when (next) {
            Page.HUB -> hubTitle()
            Page.SHOP -> shopTitle()
        }
        val fresh = Bukkit.createInventory(this, 54, title)
        when (next) {
            Page.HUB -> fillHub(fresh)
            Page.SHOP -> fillShop(fresh)
        }
        inventory = fresh
        switching = true
        player.openInventory(fresh)
        switching = false
    }

    // --- the hub: portrait, title, standing, reputation bar and up to four options

    private fun frame(char: Char) = Npc2Title()
        .glyph(Npc2Layout.FRAME_X, char, Npc2Layout.FRAME_W)
        .glyph(Npc2Layout.PORTRAIT_X, config.portrait, Npc2Layout.PORTRAIT_W)
        .centered(88, 3, config.name, Npc2Layout.CREAM, Npc2Font.NAME)

    private fun hubTitle(): Component {
        val title = frame(config.district.hub)
        if (config.title.isNotBlank()) title.centered(115, 22, Npc2Text.wrap(config.title, 100, 1).first(), Npc2Layout.INK)
        config.options.forEachIndexed { i, option -> title.centered(115, Npc2Layout.cellY(1 + i) + 5, option.label, Npc2Layout.CREAM) }
        val rep = reputation()
        title.text(66, 112, "Rep")
        val filled = repSegments(rep)
        for (i in 0 until Npc2Layout.REP_SEGMENTS) {
            title.glyph(Npc2Layout.REP_BAR_X + 5 * i, if (i < filled) Npc2Layout.REP_FULL else Npc2Layout.REP_EMPTY, 4)
        }
        title.centered(34, 75, "Standing")
        title.centered(34, 92, standing(rep))
        title.centered(34, 106, "${rep.toInt()}")
        return title.build()
    }

    private fun reputation() = plugin.persistenceManager.dataCache[player.uniqueId]?.getReputation(npc.id) ?: 0.0

    private fun fillHub(inv: Inventory) {
        config.options.forEachIndexed { i, option ->
            val label = button(option.label)
            for (column in 3..8) inv.setItem(Npc2Layout.slot(column, 1 + i), label)
        }
        val rep = reputation()
        val next = nextStanding(rep)
        val repItem = button("Reputation: ${rep.toInt()}", listOfNotNull(standing(rep), next?.let { "${it.second - rep.toInt()} more to ${it.first}" }))
        for (column in 3..8) inv.setItem(Npc2Layout.slot(column, 5), repItem)
    }

    // --- the shop: the NPC's offers (the old shop's, through the same services), purse, till, sell box and pages

    private fun visibleOffers(): List<ShopMenuItem> {
        val shop = shop ?: return emptyList()
        val rep = plugin.persistenceManager.dataCache[player.uniqueId]?.getReputation(npc.id) ?: 0.0
        val evaluation = ShopRequirementEvaluation(player, rep)
        return shop.items.filter { plugin.requirementService.offerVisible(it.requirements, evaluation) }
    }

    private fun pageCount(offers: Int) = if (offers == 0) 1 else (offers - 1) / PRODUCT_SLOTS.size + 1

    private fun shopTitle(): Component {
        val offers = visibleOffers()
        shopPage = shopOfferPage(offers, shopPage, PRODUCT_SLOTS.size).index
        val title = frame(config.district.shop)
        title.text(9, Npc2Layout.cellY(3) + 5, "Purse")
        title.text(9, Npc2Layout.cellY(4) + 5, "Till")
        title.centered(Npc2Layout.cellX(0) + 9, Npc2Layout.cellY(5) + 5, "<")
        title.centered(115, Npc2Layout.cellY(5) + 5, "Page ${shopPage + 1}/${pageCount(offers.size)}", Npc2Layout.CREAM)
        if (pageCount(offers.size) > 1) {
            title.centered(Npc2Layout.cellX(3) + 9, Npc2Layout.cellY(5) + 5, "<")
            title.centered(Npc2Layout.cellX(8) + 9, Npc2Layout.cellY(5) + 5, ">")
        }
        return title.build()
    }

    private fun fillShop(inv: Inventory) {
        val offers = visibleOffers()
        val shown = shopOfferPage(offers, shopPage, PRODUCT_SLOTS.size).offers
        shownOffers = shown
        val playerData = plugin.persistenceManager.dataCache[player.uniqueId]
        shown.forEachIndexed { i, offer -> inv.setItem(PRODUCT_SLOTS[i], displayItem(offer, playerData)) }
        inv.setItem(BACK_SLOT, button("Back"))
        inv.setItem(PURSE_SLOT, purseItem())
        inv.setItem(TILL_SLOT, tillItem())
        if (pageCount(offers.size) > 1) {
            inv.setItem(PREV_SLOT, button("Previous page"))
            inv.setItem(NEXT_SLOT, button("Next page"))
        }
    }

    private fun displayItem(offer: ShopMenuItem, playerData: com.danidipp.sneakynpcs.PlayerData?): ItemStack {
        val stack = offer.magicItem.itemStack.clone()
        val limits = offer.limits
        if (limits != null && playerData != null) {
            val left = plugin.shopItemStockService.getRemainingQuantity(playerData, npc.id, offer.stockEntryId, limits)
            if (left <= 0) {
                stack.setData(DataComponentTypes.ITEM_MODEL, NamespacedKey.minecraft("barrier"))
                stack.amount = 1
            } else {
                stack.amount = minOf(left, stack.maxStackSize.coerceAtLeast(1))
            }
        }
        stack.editMeta { meta ->
            val lore = meta.lore()?.toMutableList() ?: mutableListOf()
            lore.add(Component.empty())
            lore.add(Component.text("Price: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("${offer.price.amount} ${ShopMessageFormatter.formatCurrencyId(offer.price.currencyId)}", NamedTextColor.GOLD)))
            if (offer.buyStacks) lore.add(Component.text("Shift-click to buy a stack", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
            meta.lore(lore)
        }
        return stack
    }

    private fun purseItem(): ItemStack {
        val lines = shop?.bankDisplayCurrencyIds.orEmpty().mapNotNull { id ->
            val currency = plugin.currencyGraphService.getCurrency(id) ?: return@mapNotNull null
            "${ShopMessageFormatter.formatCurrencyId(currency.id)}: ${formatBankDisplayAmount(plugin.balanceService.getBankCurrencyUnits(player, currency))}"
        }
        return button("Your purse", lines.ifEmpty { listOf("Coins on hand pay first") })
    }

    private fun tillItem(): ItemStack {
        val playerData = plugin.persistenceManager.dataCache[player.uniqueId] ?: return button("${config.name}'s till")
        val wallet = plugin.npcWalletService.getOrCreateRestockedWallet(playerData, npc)
        val lines = wallet.balances.toSortedMap().map { (id, amount) -> "${ShopMessageFormatter.formatCurrencyId(id)}: $amount" }
        return button("${config.name}'s till", lines.ifEmpty { listOf("Empty") } + "Drop items in the gold box to sell")
    }

    private fun buy(slotIndex: Int, click: ClickType) {
        val offer = shownOffers.getOrNull(slotIndex) ?: return
        val playerData = plugin.persistenceManager.dataCache[player.uniqueId] ?: return fail("Failed to fetch your data. Please tell Dani.")
        val quantity = if (offer.buyStacks && click == ClickType.SHIFT_LEFT) offer.magicItem.itemStack.maxStackSize.coerceAtLeast(1) else 1
        when (val result = plugin.shopTransactionService.purchase(player, playerData, npc, offer, quantity)) {
            is ShopTransactionService.PurchaseResult.Success -> {
                val name = itemName(offer.magicItem.itemStack)
                Npc2Memory.rememberPurchase(player, npc.id, name)
                player.playSound(player.location, "lom:buy", 1f, 1f)
                val total = offer.price.amount.toLong() * result.deliveredQuantity
                player.sendMessage(Component.text("You bought ${result.deliveredQuantity}x $name for $total ${ShopMessageFormatter.formatCurrencyId(offer.price.currencyId)}", NamedTextColor.GRAY))
                show(Page.SHOP)
            }
            is ShopTransactionService.PurchaseResult.Failure -> fail(result.message)
        }
    }

    private fun sell(offered: ItemStack, onSuccess: () -> Unit) {
        val playerData = plugin.persistenceManager.dataCache[player.uniqueId] ?: return fail("Failed to fetch your data. Please tell Dani.")
        when (val result = plugin.shopTransactionService.sell(player, playerData, npc, offered)) {
            is ShopTransactionService.SellResult.Success -> {
                onSuccess()
                plugin.inventoryTransactionLogger.log(player = player, removedItems = listOf(offered.clone()))
                player.playSound(player.location, "lom:buy", 1f, 1f)
                player.sendMessage(Component.text("You sold ${offered.amount}x ${itemName(offered)} for ${result.payoutAmount} ${ShopMessageFormatter.formatCurrencyId(result.currencyId)}", NamedTextColor.GRAY))
                show(Page.SHOP)
            }
            is ShopTransactionService.SellResult.Failure -> fail(result.message)
        }
    }

    private fun fail(message: String) {
        player.playSound(player.location, "lom:fail_wrong", 1f, 1f)
        player.sendMessage(plugin.prefix.append(Component.text(message, NamedTextColor.RED)))
    }

    private fun navigate(next: Page) {
        player.playSound(player.location, "lom:ui.button.click", 1f, 1f)
        show(next)
    }

    private fun onClick(event: InventoryClickEvent) {
        if (event.clickedInventory == null) {
            if (page == Page.SHOP) navigate(Page.HUB)
            return
        }
        if (event.clickedInventory != event.view.topInventory) {
            if (page == Page.SHOP && event.isShiftClick) {
                val offered = event.currentItem?.clone() ?: return
                if (offered.type.isAir) return
                sell(offered) { event.clickedInventory?.setItem(event.slot, null) }
            }
            return
        }
        when (page) {
            Page.HUB -> {
                val option = config.options.getOrNull(event.slot / 9 - 1).takeIf { event.slot % 9 >= 3 } ?: return
                when (option) {
                    Npc2Option.SHOP -> if (shop != null) navigate(Page.SHOP) else fail("${config.name} has nothing to sell.")
                    Npc2Option.SELL -> if (shop != null) navigate(Page.SHOP) else fail("${config.name} isn't buying.")
                    Npc2Option.LEAVE -> player.closeInventory()
                }
            }
            Page.SHOP -> when (event.slot) {
                BACK_SLOT -> navigate(Page.HUB)
                PREV_SLOT, NEXT_SLOT -> {
                    val pages = pageCount(visibleOffers().size)
                    if (pages <= 1) return
                    shopPage = (shopPage + (if (event.slot == NEXT_SLOT) 1 else -1) + pages) % pages
                    navigate(Page.SHOP)
                }
                SELL_SLOT -> sellFromCursor(event)
                in PRODUCT_SLOTS -> if (event.click == ClickType.LEFT || event.click == ClickType.SHIFT_LEFT) buy(PRODUCT_SLOTS.indexOf(event.slot), event.click)
            }
        }
    }

    private fun sellFromCursor(event: InventoryClickEvent) {
        val cursor = event.cursor
        if (cursor.type.isAir || cursor.amount <= 0) return
        val one = event.action == InventoryAction.PLACE_ONE || event.click == ClickType.RIGHT
        val offered = cursor.clone().apply { amount = if (one) 1 else cursor.amount }
        sell(offered) {
            val left = cursor.amount - offered.amount
            event.view.setCursor(if (left > 0) cursor.clone().apply { amount = left } else null)
        }
    }

    companion object Listener : org.bukkit.event.Listener {
        private val PRODUCT_SLOTS = (0..3).flatMap { r -> (3..8).map { c -> Npc2Layout.slot(c, r) } }
        private val BACK_SLOT = Npc2Layout.slot(0, 5)
        private val PREV_SLOT = Npc2Layout.slot(3, 5)
        private val NEXT_SLOT = Npc2Layout.slot(8, 5)
        private val SELL_SLOT = Npc2Layout.slot(7, 4)
        private val PURSE_SLOT = Npc2Layout.slot(2, 3)
        private val TILL_SLOT = Npc2Layout.slot(2, 4)

        private fun button(name: String, lore: List<String> = emptyList()) = ItemStack(Material.PAPER).apply {
            setData(DataComponentTypes.ITEM_MODEL, NamespacedKey("lom", "invisible"))
            editMeta { meta ->
                meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                meta.lore(lore.map { Component.text(it, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false) })
            }
        }

        private fun itemName(stack: ItemStack): String =
            PlainTextComponentSerializer.plainText().serialize(stack.effectiveName())

        private val STANDINGS = listOf("Stranger" to 0, "Familiar" to 10, "Friend" to 50, "Confidant" to 100)

        /** the bar fills over 0..100 (Confidant), at least one cell once there's any reputation */
        private fun repSegments(rep: Double): Int {
            if (rep <= 0) return 0
            return (rep / 100.0 * Npc2Layout.REP_SEGMENTS).toInt().coerceIn(1, Npc2Layout.REP_SEGMENTS)
        }

        private fun nextStanding(rep: Double) = STANDINGS.firstOrNull { it.second > rep }

        private fun standing(rep: Double) = when {
            rep < 0 -> "Wary"
            rep < 10 -> "Stranger"
            rep < 50 -> "Familiar"
            rep < 100 -> "Friend"
            else -> "Confidant"
        }

        @EventHandler
        fun onClick(event: InventoryClickEvent) {
            val screen = event.inventory.holder as? Npc2Screen ?: return
            event.isCancelled = true
            if (event.click == ClickType.DOUBLE_CLICK || event.action == InventoryAction.COLLECT_TO_CURSOR) return
            screen.onClick(event)
        }

        @EventHandler
        fun onDrag(event: InventoryDragEvent) {
            if (event.inventory.holder !is Npc2Screen) return
            if (event.rawSlots.any { it < event.view.topInventory.size }) event.isCancelled = true
        }

        @EventHandler
        fun onClose(event: InventoryCloseEvent) {
            val screen = event.inventory.holder as? Npc2Screen ?: return
            if (screen.switching || screen.page != Page.HUB) {
                if (!screen.switching) Bukkit.getScheduler().runTask(screen.plugin, Runnable {
                    if (screen.player.isOnline && screen.player.openInventory.topInventory.type == InventoryType.CRAFTING) screen.navigate(Page.HUB)
                })
            }
        }
    }
}
