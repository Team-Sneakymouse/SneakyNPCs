package com.danidipp.sneakynpcs

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

interface InventoryTransactionLogger {
    fun log(player: Player, removedItems: List<ItemStack> = emptyList(), addedItems: List<ItemStack> = emptyList())
}

object NoOpInventoryTransactionLogger : InventoryTransactionLogger {
    override fun log(player: Player, removedItems: List<ItemStack>, addedItems: List<ItemStack>) = Unit
}
