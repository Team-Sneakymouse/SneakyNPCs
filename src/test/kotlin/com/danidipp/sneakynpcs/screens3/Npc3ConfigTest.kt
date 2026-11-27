package com.danidipp.sneakynpcs.screens3

import com.danidipp.sneakynpcs.NPC
import com.danidipp.sneakynpcs.NpcWalletConfig
import com.danidipp.sneakynpcs.menus.CustomMenu
import com.danidipp.sneakynpcs.menus.ExternalMenu
import com.danidipp.sneakynpcs.menus.ExternalMenuAction
import com.danidipp.sneakynpcs.menus.NPCMenu
import com.danidipp.sneakynpcs.menus.SelectionMenu
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class Npc3ConfigTest {
    private fun npc(id: String, root: NPCMenu) = NPC(id, id.lowercase(), false, NpcWalletConfig("gold", 10L, 60L, 1L), root)

    @Test
    fun `rows come from the root menu, custom rows are skipped and Farewell fills the rest`() {
        val root = SelectionMenu("main", listOf(
            CustomMenu("alt"),
            ExternalMenu(ExternalMenuAction.ConsoleCommand("nadiyaslots open %player_name%")),
            ExternalMenu(ExternalMenuAction.MagicSpell("npc-carlthekobold-gamblingMenu")),
        ))
        val config = Npc3Config.build(npc("JackTimbers", root), null)

        assertEquals("Jack Timbers", config.name)
        assertEquals(listOf("More", "Gambling", "Farewell"), config.options.map { it.label })
        assertIs<Npc3Action.Legacy>(config.options[0].action)
        assertIs<Npc3Action.Leave>(config.options[2].action)
    }

    @Test
    fun `the table sets name, title and the second external row's label`() {
        val root = SelectionMenu("main", listOf(
            ExternalMenu(ExternalMenuAction.MagicSpell("npc-nadiya-slots")),
            ExternalMenu(ExternalMenuAction.MagicSpell("npc-nadiya-fenceMenu")),
        ))
        val entry = YamlConfiguration().apply {
            set("name", "Nadiya Azveltara")
            set("title", "Fence")
            set("labels.external2", "Fence")
            set("labels.external2_hint", "No questions asked")
        }
        val config = Npc3Config.build(npc("NadiyaAzveltara", root), entry)

        assertEquals("Nadiya Azveltara", config.name)
        assertEquals("Fence", config.title)
        assertEquals(listOf("Slots", "Fence", "Farewell"), config.options.map { it.label })
        assertEquals("No questions asked", config.options[1].hint)
    }
}
