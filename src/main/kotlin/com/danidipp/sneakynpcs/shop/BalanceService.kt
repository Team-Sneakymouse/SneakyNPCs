package com.danidipp.sneakynpcs.shop

import com.nisovin.magicspells.MagicSpells
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class BalanceService {
    fun getInventoryCurrencyUnits(player: Player, currency: CurrencyDefinition): Long {
        val template = currency.itemMagicItem?.itemStack ?: return 0L
        var total = 0L
        for (stack in player.inventory.contents) {
            if (stack == null || !stack.isSimilar(template)) continue
            total += stack.amount.toLong()
        }
        return total
    }

    fun getBankCurrencyUnits(player: Player, currency: CurrencyDefinition): Long {
        val variableId = currency.variableId ?: return 0L
        val value = MagicSpells.getVariableManager().getValue(variableId, player)
        return value.toLong().coerceAtLeast(0L)
    }

    fun setBankCurrencyUnits(player: Player, currency: CurrencyDefinition, amount: Long) {
        val variableId = currency.variableId ?: return
        MagicSpells.getVariableManager().set(variableId, player, amount.toDouble())
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
        val template = currency.itemMagicItem?.itemStack ?: return false
        val inventory = player.inventory
        val removable = mutableListOf<Pair<Int, Int>>()
        var remaining = amount

        for (slot in 0 until inventory.size) {
            val stack = inventory.getItem(slot) ?: continue
            if (!stack.isSimilar(template)) continue
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
