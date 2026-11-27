package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.ShopItemLimitConfig
import com.danidipp.sneakynpcs.shop.ShopPrice
import com.danidipp.sneakynpcs.shop.ShopItemRequirement
import com.sneakyitems.util.magicitems.MagicItem
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.Locale

data class ShopMenuItem(
    val itemId: String,
    val magicItem: MagicItem,
    val stockEntryId: String,
    val buyStacks: Boolean,
    val requirements: List<ShopItemRequirement>,
    val price: ShopPrice,
    /** The price comes from the NPC config, so the item's own lore doesn't show it: the shop adds a cost line. */
    val priceFromConfig: Boolean = false,
    val limits: ShopItemLimitConfig?,
)

internal fun formatBankDisplayAmount(amount: Long): String = "%,d".format(Locale.US, amount)

internal data class ShopOfferPage<T>(
    val index: Int,
    val offers: List<T>,
)

internal fun <T> shopOfferPage(offers: List<T>, requestedPage: Int, pageSize: Int): ShopOfferPage<T> {
    require(pageSize > 0) { "pageSize must be positive" }
    val maxPage = if (offers.isEmpty()) 0 else (offers.size - 1) / pageSize
    val page = requestedPage.coerceIn(0, maxPage)
    return ShopOfferPage(page, offers.drop(page * pageSize).take(pageSize))
}

/**
 * A shop's offers. NPC Screens 3 ([com.danidipp.sneakynpcs.screens3.Npc3Screen]) shows and sells them; this menu never
 * opens as a chest menu any more.
 */
class ShopMenu(
    internal val items: List<ShopMenuItem>,
    internal val bankDisplayCurrencyIds: List<String>,
) : NPCMenu(MenuType.SHOP) {
    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {}
    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {}
}
