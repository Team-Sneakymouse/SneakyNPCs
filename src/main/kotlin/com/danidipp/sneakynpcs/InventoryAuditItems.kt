package com.danidipp.sneakynpcs

import org.bukkit.inventory.ItemStack

object InventoryAuditItems {
    fun split(template: ItemStack, amount: Long): List<ItemStack> {
        if (amount <= 0L || template.type.isAir) return emptyList()

        val maxStackSize = template.maxStackSize.coerceAtLeast(1)
        val stacks = mutableListOf<ItemStack>()
        var remaining = amount
        while (remaining > 0L) {
            val stackAmount = minOf(maxStackSize.toLong(), remaining).toInt()
            stacks += template.clone().apply { this.amount = stackAmount }
            remaining -= stackAmount.toLong()
        }
        return stacks
    }
}
