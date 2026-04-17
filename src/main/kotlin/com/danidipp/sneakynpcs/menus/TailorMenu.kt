package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.shop.ShopMessageFormatter
import com.danidipp.sneakynpcs.shop.ShopPrice
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.permissions.PermissionAttachment

data class TailorButtonConfig(
    val title: String,
    val lore: List<String>,
    val price: ShopPrice,
)

internal fun buildTailorCommand(slot: Int): String? = when (slot) {
    14 -> "sneakymannequins:mannequin item shirt"
    15 -> "sneakymannequins:mannequin item pants"
    16 -> "sneakymannequins:mannequin item shirt pants"
    else -> null
}

internal fun requiredTailorPermissions(): Set<String> = setOf(
    "sneakymannequins.command.mannequin",
    "sneakymannequins.command.item",
)

class TailorMenu(
    private val shirt: TailorButtonConfig,
    private val pants: TailorButtonConfig,
    private val both: TailorButtonConfig,
) : NPCMenu(MenuType.TAILOR) {
    private val miniMessage = MiniMessage.miniMessage()

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {
        val inv = gui.inventory
        val npc = gui.npc
        val hideTooltip = shouldHideTooltip(player)
        inv.clear()
        inv.setItem(0, makeItem(npc.guiModelKey, "alt", hideTooltip))
        inv.setItem(1, makeItem(npc.guiModelKey, "tailor", hideTooltip))
        inv.setItem(14, buildButtonItem(shirt))
        inv.setItem(15, buildButtonItem(pants))
        inv.setItem(16, buildButtonItem(both))
    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val command = buildTailorCommand(event.slot) ?: return
        val buttonConfig = getButtonConfig(event.slot) ?: return
        if (!gui.plugin.server.pluginManager.isPluginEnabled("SneakyMannequins")) {
            player.sendMessage(
                gui.plugin.prefix.append(
                    Component.text("SneakyMannequins not available. Please tell Dani.", NamedTextColor.RED)
                )
            )
            return
        }

        when (val chargeResult = gui.plugin.shopTransactionService.charge(player, buttonConfig.price)) {
            is com.danidipp.sneakynpcs.shop.ShopTransactionService.PurchaseResult.Failure -> {
                player.sendMessage(
                    gui.plugin.prefix.append(
                        Component.text(chargeResult.message, NamedTextColor.RED)
                    )
                )
                return
            }
            is com.danidipp.sneakynpcs.shop.ShopTransactionService.PurchaseResult.Success -> {
                ShopMessageFormatter.buildSpentTotalsMessage(gui.plugin.currencyGraphService, chargeResult.spent)?.let(player::sendMessage)
            }
        }

        gui.closeAllLevels()
        gui.plugin.server.scheduler.runTaskLater(gui.plugin, Runnable {
            if (!player.isOnline) {
                gui.plugin.logger.warning("Player '${player.name}' went offline before tailor command '$command' could be dispatched")
                return@Runnable
            }

            val missingPermissions = requiredTailorPermissions().filterNot(player::hasPermission)
            val attachment = createTemporaryAttachment(gui, player, missingPermissions)
            try {
                val dispatched = Bukkit.dispatchCommand(player, command)
                if (dispatched) return@Runnable

                gui.plugin.logger.warning("Failed to dispatch tailor command '$command' for player '${player.name}'")
                player.sendMessage(
                    gui.plugin.prefix.append(
                        Component.text("Failed to open tailor menu. Please tell Dani.", NamedTextColor.RED)
                    )
                )
            } finally {
                attachment?.remove()
            }
        }, 1L)
    }

    private fun buildButtonItem(buttonConfig: TailorButtonConfig) = makeItem("lom:invisible").apply {
        editMeta { meta ->
            meta.displayName(formatTooltipText(buttonConfig.title))
            meta.lore(buttonConfig.lore.map(::formatTooltipText))
        }
    }

    private fun formatTooltipText(input: String): Component {
        return Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(miniMessage.deserialize(input))
    }

    private fun getButtonConfig(slot: Int): TailorButtonConfig? = when (slot) {
        14 -> shirt
        15 -> pants
        16 -> both
        else -> null
    }

    private fun createTemporaryAttachment(
        gui: NPCGui,
        player: Player,
        missingPermissions: List<String>,
    ): PermissionAttachment? {
        if (missingPermissions.isEmpty()) return null
        val attachment = player.addAttachment(gui.plugin)
        for (permission in missingPermissions) {
            attachment.setPermission(permission, true)
        }
        return attachment
    }
}
