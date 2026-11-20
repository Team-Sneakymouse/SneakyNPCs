package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.ExternalMenuAction
import com.danidipp.sneakynpcs.menus.buildExternalMagicSpellCommand
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigManagerExternalMenuValidationTest {
    private val serializer = PlainTextComponentSerializer.plainText()

    @Test
    fun `external menu parses successfully with a valid magicspell`() {
        val (action, errors, warnings) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "magicspell" to "wizard_menu"),
            path = "rootMenu",
            spellLookup = { spellId -> if (spellId == "wizard_menu") Any() else null },
        )

        assertEquals(ExternalMenuAction.MagicSpell("wizard_menu"), action)
        assertTrue(errors.isEmpty())
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `external menu parses a console command`() {
        val (action, errors, warnings) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "command" to "nadiyaslots open %player_name%"),
            path = "rootMenu",
            spellLookup = { null },
        )

        assertEquals(ExternalMenuAction.ConsoleCommand("nadiyaslots open %player_name%"), action)
        assertTrue(errors.isEmpty())
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `external menu rejects missing magicspell and command`() {
        val (action, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external"),
            path = "rootMenu",
            spellLookup = { Any() },
        )

        assertNull(action)
        assertEquals(
            listOf(" - rootMenu: External menu must define exactly one of 'magicspell' or 'command'"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `external menu rejects both magicspell and command`() {
        val (action, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "magicspell" to "wizard_menu", "command" to "say hi"),
            path = "rootMenu",
            spellLookup = { Any() },
        )

        assertNull(action)
        assertEquals(
            listOf(" - rootMenu: External menu must define exactly one of 'magicspell' or 'command'"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `external menu rejects blank magicspell`() {
        val (action, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "magicspell" to ""),
            path = "rootMenu",
            spellLookup = { Any() },
        )

        assertNull(action)
        assertEquals(
            listOf(" - rootMenu.magicspell: Cannot be blank"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `external menu rejects non-string magicspell`() {
        val (action, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "magicspell" to 5),
            path = "rootMenu",
            spellLookup = { Any() },
        )

        assertNull(action)
        assertEquals(
            listOf(" - rootMenu.magicspell: Missing or invalid field"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `external menu rejects blank command`() {
        val (action, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "command" to " "),
            path = "rootMenu",
            spellLookup = { Any() },
        )

        assertNull(action)
        assertEquals(
            listOf(" - rootMenu.command: Cannot be blank"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `external menu rejects non-string command`() {
        val (action, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "command" to listOf("say hi")),
            path = "rootMenu",
            spellLookup = { Any() },
        )

        assertNull(action)
        assertEquals(
            listOf(" - rootMenu.command: Missing or invalid field"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `external menu rejects unknown keys`() {
        val (action, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "magicspell" to "wizard_menu", "gui" to "unused"),
            path = "rootMenu",
            spellLookup = { Any() },
        )

        assertEquals(ExternalMenuAction.MagicSpell("wizard_menu"), action)
        assertEquals(
            listOf(" - rootMenu: Unknown external menu keys gui"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `external menu rejects asConsole because commands always run from the console`() {
        val (_, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "command" to "say hi", "asConsole" to false),
            path = "rootMenu",
            spellLookup = { Any() },
        )

        assertEquals(
            listOf(" - rootMenu: Unknown external menu keys asConsole"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `external menu warns about unresolved spell ids but still loads`() {
        val (action, errors, warnings) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "magicspell" to "wizard_menu"),
            path = "rootMenu",
            spellLookup = { null },
        )

        assertEquals(ExternalMenuAction.MagicSpell("wizard_menu"), action)
        assertTrue(errors.isEmpty())
        assertEquals(
            listOf(
                " - rootMenu.magicspell: Unknown MagicSpell internal spell id 'wizard_menu'. " +
                    "The option does nothing until MagicSpells has this spell."
            ),
            warnings.map(serializer::serialize)
        )
    }

    @Test
    fun `external magic spell command matches expected format`() {
        assertEquals(
            "ms cast as Steve wizard_menu",
            buildExternalMagicSpellCommand("Steve", "wizard_menu")
        )
    }
}
