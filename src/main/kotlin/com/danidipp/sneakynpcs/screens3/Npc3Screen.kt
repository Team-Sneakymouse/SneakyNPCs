@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs.screens3

import com.danidipp.sneakynpcs.NPC
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.SneakyNPCs
import com.danidipp.sneakynpcs.menus.ShopMenu
import com.danidipp.sneakynpcs.menus.ShopMenuItem
import com.danidipp.sneakynpcs.menus.formatBankDisplayAmount
import com.danidipp.sneakynpcs.menus.shopOfferPage
import com.danidipp.sneakynpcs.screens2.Npc2Config
import com.danidipp.sneakynpcs.screens2.Npc2Memory
import com.danidipp.sneakynpcs.screens2.Npc2Option
import com.danidipp.sneakynpcs.screens3.Npc3Layout.CREAM
import com.danidipp.sneakynpcs.screens3.Npc3Layout.GOLD
import com.danidipp.sneakynpcs.screens3.Npc3Layout.MUTED
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
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.Locale

/**
 * NPC Screens 3 (/sneakynpcs opengui3): the hub and shop redrawn from the ground up in SneakyRoamin's style. The
 * whole window is art (tools/npc3_art.py): on the hub the player's inventory is hidden under a ledger of purse, till
 * and what you last bought ([Npc3Mask]); on the shop the player's items show in drawn sockets so they can sell.
 * Reads the same npc2/<id>.yml as Screens 2. Esc or a click outside the window from the shop goes back to the hub.
 */
internal class Npc3Screen(
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
        inventory = fresh
        switching = true
        when (next) {
            Page.HUB -> {
                fillHub(fresh)
                mask.opening(player, 54)
                player.openInventory(fresh)
                mask.opened(player)
                mask.show(player, hubLedgerItems())
                player.updateInventory()
            }
            Page.SHOP -> {
                fillShop(fresh)
                player.openInventory(fresh)
            }
        }
        switching = false
    }

    private fun header(art: Char) = Npc3Text().art(art).name(Npc3Layout.NAME_X, config.name, GOLD)

    private fun firstName() = config.name.substringBefore(' ')

    // --- the hub: portrait, standing, up to four option plaques, reputation; the ledger over the hidden inventory

    private fun hubTitle(): Component {
        val t = header(Npc3Layout.HUB_TOP)
        val nameEnd = Npc3Layout.NAME_X + Npc3Text.nameWidth(config.name)
        if (config.title.isNotBlank() && nameEnd + 8 + Npc3Text.width(config.title) <= Npc3Layout.RIGHT) {
            t.right(Npc3Layout.RIGHT, 6, config.title, MUTED)
        }
        t.art(Npc3Layout.HUB_BOTTOM, 0)
        t.art(Npc3Layout.portrait(config.portraitName), Npc3Layout.PORTRAIT_X)
        val rep = reputation()
        t.center(32, 77, standing(rep), GOLD)

        config.options.forEachIndexed { i, option ->
            val y = Npc3Layout.rowY(i)
            t.text(Npc3Layout.OPTION_TEXT_X, y, option.label, CREAM)
            t.text(Npc3Layout.OPTION_TEXT_X, y + 8, hint(option), MUTED)
        }

        val next = nextStanding(rep)
        t.text(Npc3Layout.PANEL_LEFT, 94, "Reputation", GOLD)
        t.right(Npc3Layout.PANEL_RIGHT, 94, "${rep.toInt()} / $MAX_REP", CREAM)
        bar(t, Npc3Layout.PANEL_LEFT, 104, Npc3Layout.BAR_SEGMENTS, rep / MAX_REP, GOLD)
        t.text(Npc3Layout.PANEL_LEFT, 114, if (next != null) "${next.second - rep.toInt()} more to become ${next.first}" else "${firstName()} counts you a confidant", MUTED)

        t.text(Npc3Layout.PANEL_LEFT, 141, "Your Wallet", GOLD)
        purse().take(3).forEachIndexed { i, (id, line) ->
            t.text(Npc3Layout.PANEL_LEFT, 151 + 9 * i, Npc3Text.fit(line, 74), COIN_COLOURS[id] ?: CREAM)
        }
        // the NPC's wallet is a bar that fills back up over time; the numbers are on its tooltip (Rob, 2026-11-02)
        t.text(Npc3Layout.WALLET_X, 141, Npc3Text.fit("${firstName()}'s Wallet", 74), GOLD)
        bar(t, Npc3Layout.WALLET_X, Npc3Layout.WALLET_BAR_Y, Npc3Layout.WALLET_SEGMENTS, walletFill().first, Npc3Layout.WALLET_GREEN)
        val last = Npc2Memory.lastPurchase(player, npc.id)
        t.center(88, 181, Npc3Text.fit(if (last != null) "You last bought $last here" else "Your first time at this counter", 154), MUTED)
        return t.build()
    }

    /** segments of [Npc3Text.BAR] from x: the first fraction of them in colour, the rest empty */
    private fun bar(t: Npc3Text, x: Int, y: Int, segments: Int, fraction: Double, colour: Int) {
        val filled = (fraction.coerceIn(0.0, 1.0) * segments).toInt().let { if (fraction > 0 && it == 0) 1 else it }
        val bar = Npc3Text.BAR.toString()
        if (filled > 0) t.text(x, y, bar.repeat(filled), colour)
        if (filled < segments) t.text(x + filled * Npc3Text.width(bar), y, bar.repeat(segments - filled), Npc3Layout.BAR_EMPTY)
    }

    private fun walletFill(): Pair<Double, Long?> {
        val playerData = plugin.persistenceManager.dataCache[player.uniqueId] ?: return 0.0 to null
        return plugin.npcWalletService.fill(playerData, npc)
    }

    private fun walletLore(): List<String> {
        val (fraction, fullAt) = walletFill()
        val refill = when {
            fraction >= 1.0 -> "Full"
            fullAt == null -> "Doesn't refill"
            else -> "Full again in ${duration(fullAt - System.currentTimeMillis())}"
        }
        return tillLines().ifEmpty { listOf("Empty") } + listOfNotNull(tillPerHour(), refill)
    }

    private fun hint(option: Npc2Option) = when (option) {
        Npc2Option.SHOP -> "See what's for sale"
        Npc2Option.SELL -> "Trade goods for coin"
        Npc2Option.LEAVE -> "Be on your way"
    }

    private fun icon(option: Npc2Option) = when (option) {
        Npc2Option.SHOP -> Material.CHEST
        Npc2Option.SELL -> Material.GOLD_NUGGET
        Npc2Option.LEAVE -> Material.OAK_DOOR
    }

    private fun reputation() = plugin.persistenceManager.dataCache[player.uniqueId]?.getReputation(npc.id) ?: 0.0

    private fun fillHub(inv: Inventory) {
        val portrait = button(config.name, listOfNotNull(config.title.takeIf { it.isNotBlank() }))
        for (row in 0..3) for (col in 0..2) inv.setItem(Npc3Layout.slot(col, row), portrait)
        config.options.forEachIndexed { i, option ->
            inv.setItem(Npc3Layout.slot(3, i), item(icon(option), option.label, listOf(hint(option))))
            val label = button(option.label, listOf(hint(option)))
            for (col in 4..8) inv.setItem(Npc3Layout.slot(col, i), label)
        }
        val rep = reputation()
        val next = nextStanding(rep)
        val repItem = button("Reputation: ${rep.toInt()}", listOfNotNull(standing(rep), next?.let { "${it.second - rep.toInt()} more to ${it.first}" }))
        for (slot in 36 until 54) inv.setItem(slot, repItem)
    }

    /** The 36 items shown over the hidden inventory: purse and till tooltips, and the close button. */
    private fun hubLedgerItems(): Array<ItemStack?> {
        val items = arrayOfNulls<ItemStack>(36)
        val purse = button("Your wallet", purseLines().ifEmpty { listOf("Coins on hand pay first") })
        val till = button("${firstName()}'s wallet", walletLore())
        for (row in 0..2) {
            for (col in 0..3) items[row * 9 + col] = purse
            for (col in 5..8) items[row * 9 + col] = till
        }
        items[HOTBAR + 4] = button("Close")
        return items
    }

    /** The player's bank balance of [id], or null if that currency doesn't exist. */
    private fun bankBalance(id: String): Long? {
        val currency = plugin.currencyGraphService.getCurrency(id) ?: return null
        return plugin.balanceService.getBankCurrencyUnits(player, currency)
    }

    /** (currency id, "Name: amount") for each purse currency that exists */
    private fun purse(): List<Pair<String, String>> = PURSE_CURRENCIES.mapNotNull { id ->
        bankBalance(id)?.let { id to "${currencyName(id)}: ${formatBankDisplayAmount(it)}" }
    }

    private fun purseLines() = purse().map { it.second }

    private fun tillLines(): List<String> {
        val playerData = plugin.persistenceManager.dataCache[player.uniqueId] ?: return emptyList()
        val wallet = plugin.npcWalletService.getOrCreateRestockedWallet(playerData, npc)
        return wallet.balances.toSortedMap().map { (id, amount) -> "${currencyName(id)}: ${formatBankDisplayAmount(amount)}" }
    }

    /** "Gold per Hour: 2", from the NPC's wallet restock (restockAmount every restockInterval seconds) */
    private fun tillPerHour(): String? {
        val wallet = npc.wallet
        if (wallet.restockAmount <= 0L || wallet.restockIntervalSeconds <= 0L) return null
        val perHour = wallet.restockAmount * 3600.0 / wallet.restockIntervalSeconds
        val amount = if (perHour % 1.0 == 0.0) formatBankDisplayAmount(perHour.toLong()) else "%,.1f".format(Locale.US, perHour)
        return "${currencyName(wallet.currencyId)} per Hour: $amount"
    }

    // --- the shop: offers on the shelf, purse and till, back, pages and the sell box; the player's own items below

    private fun visibleOffers(): List<ShopMenuItem> {
        val shop = shop ?: return emptyList()
        val evaluation = ShopRequirementEvaluation(player, reputation())
        return shop.items.filter { plugin.requirementService.offerVisible(it.requirements, evaluation) }
    }

    private fun pageCount(offers: Int) = if (offers == 0) 1 else (offers - 1) / PRODUCT_SLOTS.size + 1

    private fun shopTitle(): Component {
        val offers = visibleOffers()
        shopPage = shopOfferPage(offers, shopPage, PRODUCT_SLOTS.size).index
        val t = header(Npc3Layout.SHOP_TOP)
        t.right(Npc3Layout.RIGHT, 6, if (offers.isEmpty()) "Nothing for sale" else "Wares", MUTED)
        t.art(Npc3Layout.SHOP_BOTTOM, 0)

        val purse = bankBalance(PURSE_DEFAULT)?.let { "${formatBankDisplayAmount(it)} ${currencyName(PURSE_DEFAULT)}" } ?: "-"
        val till = tillLines().firstOrNull()?.let(::amountFirst) ?: "-"
        val left = Npc3Layout.PANEL_LEFT
        t.text(left, 94, "Wallet", GOLD)
        val purseX = left + Npc3Text.width("Wallet") + 4
        val half = (Npc3Layout.PANEL_RIGHT - left) / 2
        val tillText = Npc3Text.fit(till, half - Npc3Text.width("Till") - 8)
        val tillX = Npc3Layout.PANEL_RIGHT - Npc3Text.width(tillText) - 4 - Npc3Text.width("Till")
        // the purse may run all the way up to the till's label
        t.text(purseX, 94, Npc3Text.fit(purse, tillX - 6 - purseX), CREAM)
        t.right(Npc3Layout.PANEL_RIGHT, 94, tillText, CREAM)
        t.text(tillX, 94, "Till", GOLD)

        t.center(70, 112, "${shopPage + 1} / ${pageCount(offers.size)}", CREAM)
        t.right(147, 112, "Sell", GOLD)
        return t.build()
    }

    /** "Gold Coins: 125" -> "125 Gold Coins" */
    private fun amountFirst(line: String): String {
        val name = line.substringBefore(": ")
        val amount = line.substringAfter(": ", "")
        return if (amount.isEmpty()) line else "$amount $name"
    }

    private fun fillShop(inv: Inventory) {
        val offers = visibleOffers()
        val shown = shopOfferPage(offers, shopPage, PRODUCT_SLOTS.size).offers
        shownOffers = shown
        val playerData = plugin.persistenceManager.dataCache[player.uniqueId]
        shown.forEachIndexed { i, offer -> inv.setItem(PRODUCT_SLOTS[i], displayItem(offer, playerData)) }
        val purse = button("Your purse", purseLines().ifEmpty { listOf("Coins on hand pay first") })
        val till = button("${firstName()}'s till", tillLines().ifEmpty { listOf("Empty") } + listOfNotNull(tillPerHour()))
        for (col in 0..4) inv.setItem(Npc3Layout.slot(col, 4), purse)
        for (col in 5..8) inv.setItem(Npc3Layout.slot(col, 4), till)
        inv.setItem(BACK_SLOT, button("Back"))
        if (pageCount(offers.size) > 1) {
            inv.setItem(PREV_SLOT, button("Previous page"))
            inv.setItem(NEXT_SLOT, button("Next page"))
        }
        inv.setItem(SELL_SLOT, button("Sell", listOf("Drop an item here to sell it", "Right-click drops one", "Or shift-click it in your inventory")))
    }

    private fun displayItem(offer: ShopMenuItem, playerData: PlayerData?): ItemStack {
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

    private fun buy(index: Int, click: ClickType) {
        val offer = shownOffers.getOrNull(index) ?: return
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
        val raw = event.rawSlot
        if (event.clickedInventory == null) {
            if (page == Page.SHOP) navigate(Page.HUB)
            return
        }
        when (page) {
            Page.HUB -> {
                if (raw == 54 + HOTBAR + 4) return player.closeInventory()
                if (raw >= 54 || raw % 9 < 3) return
                val option = config.options.getOrNull(raw / 9) ?: return
                when (option) {
                    Npc2Option.SHOP -> if (shop != null) navigate(Page.SHOP) else fail("${config.name} has nothing to sell.")
                    Npc2Option.SELL -> if (shop != null) navigate(Page.SHOP) else fail("${config.name} isn't buying.")
                    Npc2Option.LEAVE -> player.closeInventory()
                }
            }
            Page.SHOP -> {
                if (raw >= 54) {
                    if (event.isShiftClick) {
                        val offered = event.currentItem?.clone() ?: return
                        if (offered.type.isAir) return
                        sell(offered) { event.clickedInventory?.setItem(event.slot, null) }
                    }
                    return
                }
                when (raw) {
                    BACK_SLOT -> navigate(Page.HUB)
                    PREV_SLOT, NEXT_SLOT -> {
                        val pages = pageCount(visibleOffers().size)
                        if (pages <= 1) return
                        shopPage = (shopPage + (if (raw == NEXT_SLOT) 1 else -1) + pages) % pages
                        navigate(Page.SHOP)
                    }
                    SELL_SLOT -> sellFromCursor(event)
                    in PRODUCT_SLOTS -> if (event.click == ClickType.LEFT || event.click == ClickType.SHIFT_LEFT) buy(PRODUCT_SLOTS.indexOf(raw), event.click)
                }
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

    /** Sells the whole stack on the cursor (after it was dragged onto the sell box). */
    private fun sellWholeCursor() {
        val cursor = player.itemOnCursor
        if (cursor.type.isAir || cursor.amount <= 0) return
        sell(cursor.clone()) { player.setItemOnCursor(null) }
    }

    companion object Listener : org.bukkit.event.Listener {
        private const val HOTBAR = 27
        /** the hub purse, in this order (Rob, 2026-11-02); the shop's purse line shows [PURSE_DEFAULT] */
        private val PURSE_CURRENCIES = listOf("silver", "gold", "dragoncoins")
        private const val PURSE_DEFAULT = "gold"
        private val CURRENCY_NAMES = mapOf("dragoncoins" to "Dragon Coins")

        private fun currencyName(id: String) = CURRENCY_NAMES[id] ?: ShopMessageFormatter.formatCurrencyId(id)
        private val PRODUCT_SLOTS = (0 until 36).toList()
        private val BACK_SLOT = Npc3Layout.slot(0, 5)
        private val PREV_SLOT = Npc3Layout.slot(2, 5)
        private val NEXT_SLOT = Npc3Layout.slot(4, 5)
        private val SELL_SLOT = Npc3Layout.slot(8, 5)

        private val mask by lazy { Npc3Mask(SneakyNPCs.getInstance()) }

        private fun button(name: String, lore: List<String> = emptyList()) = item(Material.PAPER, name, lore).apply {
            setData(DataComponentTypes.ITEM_MODEL, NamespacedKey("lom", "invisible"))
        }

        private fun item(material: Material, name: String, lore: List<String> = emptyList()) = ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                meta.lore(lore.map { Component.text(it, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false) })
            }
        }

        private fun itemName(stack: ItemStack): String =
            PlainTextComponentSerializer.plainText().serialize(stack.effectiveName())

        private const val MAX_REP = 100
        private val COIN_COLOURS = mapOf("silver" to 0xD2D6DA, "gold" to 0xFFC93C, "dragoncoins" to 0xFF8A80)

        /** "3h 20m", "45m", "under a minute" */
        private fun duration(millis: Long): String {
            val minutes = (millis.coerceAtLeast(0L) + 59_999L) / 60_000L
            return when {
                minutes < 1 -> "under a minute"
                minutes < 60 -> "${minutes}m"
                else -> "${minutes / 60}h" + if (minutes % 60 > 0) " ${minutes % 60}m" else ""
            }
        }

        private val STANDINGS = listOf("Stranger" to 0, "Familiar" to 10, "Friend" to 50, "Confidant" to 100)

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
            val screen = event.inventory.holder as? Npc3Screen ?: return
            // on the shop the player's own inventory works as normal (pick up, place, swap), so they can carry an item
            // to the sell box; shift-click sells and double-click could collect shop items, so those stay ours
            if (screen.page == Page.SHOP && event.clickedInventory != null && event.rawSlot >= event.view.topInventory.size &&
                !event.isShiftClick && event.click != ClickType.DOUBLE_CLICK && event.action != InventoryAction.COLLECT_TO_CURSOR
            ) return
            event.isCancelled = true
            if (event.click == ClickType.DOUBLE_CLICK || event.action == InventoryAction.COLLECT_TO_CURSOR) return
            screen.onClick(event)
        }

        @EventHandler
        fun onDrag(event: InventoryDragEvent) {
            val screen = event.inventory.holder as? Npc3Screen ?: return
            if (screen.page == Page.HUB || event.rawSlots.any { it < event.view.topInventory.size }) event.isCancelled = true
            // a drag that crosses the sell box sells the whole stack
            if (screen.page == Page.SHOP && SELL_SLOT in event.rawSlots) {
                Bukkit.getScheduler().runTask(screen.plugin, Runnable {
                    if (screen.player.openInventory.topInventory.holder === screen) screen.sellWholeCursor()
                })
            }
        }

        @EventHandler
        fun onClose(event: InventoryCloseEvent) {
            val screen = event.inventory.holder as? Npc3Screen ?: return
            val player = screen.player
            if (mask.closed(player)) Bukkit.getScheduler().runTask(screen.plugin, Runnable { if (player.isOnline) player.updateInventory() })
            if (screen.switching || screen.page == Page.HUB) return
            Bukkit.getScheduler().runTask(screen.plugin, Runnable {
                if (player.isOnline && player.openInventory.topInventory.type == InventoryType.CRAFTING) screen.navigate(Page.HUB)
            })
        }

        @EventHandler
        fun onQuit(event: PlayerQuitEvent) = mask.forget(event.player.uniqueId)
    }
}
