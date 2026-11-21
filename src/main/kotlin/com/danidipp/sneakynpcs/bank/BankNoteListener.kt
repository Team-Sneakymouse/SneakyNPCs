package com.danidipp.sneakynpcs.bank

import com.danidipp.sneakynpcs.SneakyNPCs
import com.danidipp.sneakynpcs.menus.BankMenu
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sneakyitems.events.MagicItemClickEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.EquipmentSlot

/**
 * Right-clicking a bank note inside the bank's note deposit region deposits it; sneaking deposits every note
 * of that kind. The region comes from the first loaded bank menu that sets one.
 */
class BankNoteListener(private val plugin: SneakyNPCs) : Listener {
    @EventHandler
    fun onMagicItemClick(event: MagicItemClickEvent) {
        val (coin, note) = BankRules.noteByItemId(event.itemKey) ?: return
        if (!event.isRightClick || event.hand != EquipmentSlot.HAND) return
        // Right-clicking an NPC or another entity keeps its normal interaction.
        if (event.trigger == MagicItemClickEvent.Trigger.ENTITY) return
        val region = plugin.npcs.values.asSequence()
            .flatMap { it.allMenus }
            .filterIsInstance<BankMenu>()
            .firstNotNullOfOrNull { it.noteDepositRegion } ?: return
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return
        val player = event.player
        if (!WorldGuardRegions.contains(region, player.location)) return

        event.isCancelled = true
        val limit = if (player.isSneaking) Long.MAX_VALUE else 1L
        plugin.bankService.feedback(player, plugin.bankService.depositNotes(player, coin, note, limit))
    }
}

/** Kept apart so WorldGuard classes only load when WorldGuard is enabled. */
private object WorldGuardRegions {
    fun contains(region: BankRegion, location: Location): Boolean {
        val world = location.world ?: return false
        if (world.name != region.world) return false
        val manager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world)) ?: return false
        return manager.getRegion(region.region)?.contains(location.blockX, location.blockY, location.blockZ) == true
    }
}
