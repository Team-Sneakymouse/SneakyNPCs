package com.danidipp.sneakynpcs

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
        val (magicSpellId, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "magicspell" to "wizard_menu"),
            path = "rootMenu",
            spellLookup = { spellId -> if (spellId == "wizard_menu") Any() else null },
        )

        assertEquals("wizard_menu", magicSpellId)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `external menu rejects missing magicspell`() {
        val (magicSpellId, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external"),
            path = "rootMenu",
            spellLookup = { Any() },
        )

        assertNull(magicSpellId)
        assertEquals(
            listOf(" - rootMenu.magicspell: Missing or invalid field"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `external menu rejects blank magicspell`() {
        val (magicSpellId, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "magicspell" to ""),
            path = "rootMenu",
            spellLookup = { Any() },
        )

        assertNull(magicSpellId)
        assertEquals(
            listOf(" - rootMenu.magicspell: Cannot be blank"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `external menu rejects unknown keys`() {
        val (magicSpellId, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "magicspell" to "wizard_menu", "gui" to "unused"),
            path = "rootMenu",
            spellLookup = { Any() },
        )

        assertEquals("wizard_menu", magicSpellId)
        assertEquals(
            listOf(" - rootMenu: Unknown external menu keys gui"),
            errors.map(serializer::serialize)
        )
    }

    @Test
    fun `external menu rejects unresolved spell ids`() {
        val (magicSpellId, errors) = parseExternalMenuConfig(
            menuYaml = mapOf("type" to "external", "magicspell" to "wizard_menu"),
            path = "rootMenu",
            spellLookup = { null },
        )

        assertNull(magicSpellId)
        assertEquals(
            listOf(" - rootMenu.magicspell: Unknown MagicSpell internal spell id 'wizard_menu'"),
            errors.map(serializer::serialize)
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
