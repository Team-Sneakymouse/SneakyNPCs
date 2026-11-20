package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import com.nisovin.magicspells.MagicSpells
import com.sneakyutils.util.PlaceholderUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/** What an External Menu does when the player picks it: exactly one of `magicspell` or `command` in the config. */
sealed interface ExternalMenuAction {
    /** Casts the MagicSpell as the player. The spell is looked up on every click, not only at config load. */
    data class MagicSpell(val spellId: String) : ExternalMenuAction

    /** Runs the command from the console after expanding PlaceholderAPI placeholders such as `%player_name%`. */
    data class ConsoleCommand(val command: String) : ExternalMenuAction
}

internal sealed interface ExternalMenuDispatch {
    data class Run(val command: String) : ExternalMenuDispatch
    data object SpellUnavailable : ExternalMenuDispatch
    data object PlaceholdersUnavailable : ExternalMenuDispatch
}

private val externalCommandPlaceholderPattern = Regex("%[^\\s]+%")

internal fun buildExternalMagicSpellCommand(playerName: String, magicSpellId: String): String {
    return "ms cast as $playerName $magicSpellId"
}

internal fun resolveExternalMenuDispatch(
    action: ExternalMenuAction,
    playerName: String,
    spellExists: (String) -> Boolean,
    placeholdersAvailable: Boolean,
    setPlaceholders: (String) -> String,
): ExternalMenuDispatch = when (action) {
    is ExternalMenuAction.MagicSpell ->
        if (spellExists(action.spellId)) {
            ExternalMenuDispatch.Run(buildExternalMagicSpellCommand(playerName, action.spellId))
        } else {
            ExternalMenuDispatch.SpellUnavailable
        }
    is ExternalMenuAction.ConsoleCommand ->
        if (!placeholdersAvailable && externalCommandPlaceholderPattern.containsMatchIn(action.command)) {
            ExternalMenuDispatch.PlaceholdersUnavailable
        } else {
            ExternalMenuDispatch.Run(setPlaceholders(action.command))
        }
}

class ExternalMenu(val action: ExternalMenuAction) : NPCMenu(MenuType.EXTERNAL) {
    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {
        val plugin = gui.plugin
        val playerName = player.name
        val dispatch = resolveExternalMenuDispatch(
            action = action,
            playerName = playerName,
            spellExists = { spellId -> MagicSpells.getSpellByInternalName(spellId) != null },
            placeholdersAvailable = PlaceholderUtil.isAvailable(),
            setPlaceholders = { text -> PlaceholderUtil.setPlaceholders(player, text) ?: text },
        )
        val command = when (dispatch) {
            is ExternalMenuDispatch.Run -> dispatch.command
            ExternalMenuDispatch.SpellUnavailable -> {
                // Reported as a config warning at reload; the option stays in the menu but does nothing.
                if (!gui.goBackOneLevel()) gui.closeAllLevels()
                player.sendMessage(plugin.prefix.append(Component.text("This option is unavailable right now.", NamedTextColor.RED)))
                return
            }
            ExternalMenuDispatch.PlaceholdersUnavailable -> {
                plugin.logger.warning(
                    "PlaceholderAPI is unavailable; not running external menu command " +
                        "'${(action as ExternalMenuAction.ConsoleCommand).command}' for player '$playerName'"
                )
                if (!gui.goBackOneLevel()) gui.closeAllLevels()
                player.sendMessage(plugin.prefix.append(Component.text("Failed to open external menu. Please tell Dani.", NamedTextColor.RED)))
                return
            }
        }

        gui.closeAllLevels()
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!player.isOnline) {
                plugin.logger.warning("Player '$playerName' went offline before external menu command '$command' could run")
                return@Runnable
            }

            val dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            if (dispatched) return@Runnable

            plugin.logger.warning("Failed to dispatch external menu command '$command' for player '$playerName'")
            player.sendMessage(plugin.prefix.append(Component.text("Failed to open external menu. Please tell Dani.", NamedTextColor.RED)))
        }, 1L)
    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) = Unit
}
