package com.danidipp.sneakynpcs.shop

import com.danidipp.sneakynpcs.Variables
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class BalanceService {
    private val magicItemKey = NamespacedKey("magicspells", "magicitem")

    private fun matchesCurrency(stack: ItemStack?, itemId: String): Boolean {
        return stack?.itemMeta?.persistentDataContainer
            ?.get(magicItemKey, PersistentDataType.STRING) == itemId
    }

    fun getInventoryCurrencyUnits(player: Player, currency: CurrencyDefinition): Long {
        val itemId = currency.itemId ?: return 0L
        return countItems(player, itemId)
    }

    /** How many items with MagicItem id [itemId] the player carries. */
    fun countItems(player: Player, itemId: String): Long {
        var total = 0L
        for (stack in player.inventory.contents) {
            if (stack == null || !matchesCurrency(stack, itemId)) continue
            total += stack.amount.toLong()
        }
        return total
    }

    fun getBankCurrencyUnits(player: Player, currency: CurrencyDefinition): Long {
        val variableId = currency.variableId ?: return 0L
        val value = Variables.getNumber(player, variableId)
        return value.toLong().coerceAtLeast(0L)
    }

    fun setBankCurrencyUnits(player: Player, currency: CurrencyDefinition, amount: Long) {
        val variableId = currency.variableId ?: return
        Variables.setNumber(player, variableId, amount.toDouble())
    }

    fun addBankCurrencyUnits(player: Player, currency: CurrencyDefinition, amount: Long) {
        if (amount == 0L) return
        val current = getBankCurrencyUnits(player, currency)
        setBankCurrencyUnits(player, currency, current + amount)
    }

    fun removeBankCurrencyUnits(player: Player, currency: CurrencyDefinition, amount: Long): Boolean {
        if (amount <= 0L) return true
        val current = getBankCurrencyUnits(player, currency)
        if (current < amount) return false
        setBankCurrencyUnits(player, currency, current - amount)
        return true
    }

    fun removeInventoryCurrencyUnits(player: Player, currency: CurrencyDefinition, amount: Long): Boolean {
        if (amount <= 0L) return true
        val itemId = currency.itemId ?: return false
        return removeItems(player, itemId, amount)
    }

    /** Removes [amount] items with MagicItem id [itemId], or nothing if the player has fewer. */
    fun removeItems(player: Player, itemId: String, amount: Long): Boolean {
        if (amount <= 0L) return true
        val inventory = player.inventory
        val removable = mutableListOf<Pair<Int, Int>>()
        var remaining = amount

        for (slot in 0 until inventory.size) {
            val stack = inventory.getItem(slot) ?: continue
            if (!matchesCurrency(stack, itemId)) continue
            val take = minOf(stack.amount.toLong(), remaining).toInt()
            if (take <= 0) continue
            removable.add(slot to take)
            remaining -= take.toLong()
            if (remaining <= 0L) break
        }

        if (remaining > 0L) return false

        for ((slot, take) in removable) {
            val stack = inventory.getItem(slot) ?: continue
            if (stack.amount <= take) inventory.setItem(slot, null)
            else {
                val updated = stack.clone()
                updated.amount = stack.amount - take
                inventory.setItem(slot, updated)
            }
        }
        return true
    }

    fun addInventoryCurrencyUnits(player: Player, currency: CurrencyDefinition, amount: Long): Boolean {
        val template = currency.itemMagicItem?.itemStack ?: return amount <= 0L
        return addItems(player, template, amount)
    }

    /** Gives [amount] copies of [template], or nothing if they don't all fit. */
    fun addItems(player: Player, template: ItemStack, amount: Long): Boolean {
        if (amount <= 0L) return true
        if (amount > Int.MAX_VALUE) return false
        if (!canFitItem(player.inventory, template, amount.toInt())) return false

        var remaining = amount.toInt()
        while (remaining > 0) {
            val nextAmount = minOf(template.maxStackSize.coerceAtLeast(1), remaining)
            val stack = template.clone().apply { this.amount = nextAmount }
            val leftovers = player.inventory.addItem(stack)
            if (leftovers.isNotEmpty()) return false
            remaining -= nextAmount
        }
        return true
    }

    /** How many copies of [item], up to [limit], fit in the inventory's storage slots. */
    fun fitCount(inventory: Inventory, item: ItemStack, limit: Long): Long {
        if (limit <= 0L) return 0L
        val maxStack = item.maxStackSize.coerceAtLeast(1).toLong()
        var space = 0L
        for (stack in inventory.storageContents) {
            space += when {
                stack == null || stack.type.isAir -> maxStack
                stack.isSimilar(item) -> (stack.maxStackSize - stack.amount).coerceAtLeast(0).toLong()
                else -> 0L
            }
            if (space >= limit) return limit
        }
        return space
    }

    fun canFitItem(inventory: Inventory, item: ItemStack, amount: Int): Boolean {
        if (amount <= 0) return true

        var remaining = amount
        for (stack in inventory.storageContents) {
            if (stack == null || stack.type.isAir) {
                remaining -= item.maxStackSize
            } else if (stack.isSimilar(item)) {
                remaining -= (stack.maxStackSize - stack.amount)
            }
            if (remaining <= 0) return true
        }
        return false
    }
}
