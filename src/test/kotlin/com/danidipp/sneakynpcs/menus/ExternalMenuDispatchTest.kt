package com.danidipp.sneakynpcs.menus

import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalMenuDispatchTest {
    private fun resolve(
        action: ExternalMenuAction,
        knownSpells: Set<String> = emptySet(),
        placeholdersAvailable: Boolean = true,
    ) = resolveExternalMenuDispatch(
        action = action,
        playerName = "Steve",
        spellExists = { it in knownSpells },
        placeholdersAvailable = placeholdersAvailable,
        setPlaceholders = { it.replace("%player_name%", "Steve") },
    )

    @Test
    fun `known spell is cast as the player`() {
        assertEquals(
            ExternalMenuDispatch.Run("ms cast as Steve wizard_menu"),
            resolve(ExternalMenuAction.MagicSpell("wizard_menu"), knownSpells = setOf("wizard_menu")),
        )
    }

    @Test
    fun `unknown spell is unavailable at click time`() {
        assertEquals(
            ExternalMenuDispatch.SpellUnavailable,
            resolve(ExternalMenuAction.MagicSpell("wizard_menu")),
        )
    }

    @Test
    fun `command placeholders are expanded`() {
        assertEquals(
            ExternalMenuDispatch.Run("nadiyaslots open Steve"),
            resolve(ExternalMenuAction.ConsoleCommand("nadiyaslots open %player_name%")),
        )
    }

    @Test
    fun `command with placeholders is not run without PlaceholderAPI`() {
        assertEquals(
            ExternalMenuDispatch.PlaceholdersUnavailable,
            resolve(ExternalMenuAction.ConsoleCommand("nadiyaslots open %player_name%"), placeholdersAvailable = false),
        )
    }

    @Test
    fun `command without placeholders runs without PlaceholderAPI`() {
        assertEquals(
            ExternalMenuDispatch.Run("broadcast hello"),
            resolve(ExternalMenuAction.ConsoleCommand("broadcast hello"), placeholdersAvailable = false),
        )
    }
}
