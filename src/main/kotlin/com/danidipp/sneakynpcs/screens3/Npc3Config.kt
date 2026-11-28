package com.danidipp.sneakynpcs.screens3

import com.danidipp.sneakynpcs.NPC
import com.danidipp.sneakynpcs.menus.CustomMenu
import com.danidipp.sneakynpcs.menus.ExternalMenu
import com.danidipp.sneakynpcs.menus.ExternalMenuAction
import com.danidipp.sneakynpcs.menus.MenuType
import com.danidipp.sneakynpcs.menus.NPCMenu
import com.danidipp.sneakynpcs.menus.SelectionMenu
import com.danidipp.sneakynpcs.menus.ShopMenu
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStream
import java.io.InputStreamReader

/** What a hub row does when it's clicked. */
internal sealed interface Npc3Action {
    /** opens the Screens 3 shop on this shop menu */
    data class Shop(val menu: ShopMenu) : Npc3Action
    /** opens the shop on the first shop, where the sell box is */
    data object Sell : Npc3Action
    data object Leave : Npc3Action
    /** quest, tailor, taxes, bank and external menus: the old chest menu, which returns to the hub on Esc */
    data class Legacy(val menu: NPCMenu) : Npc3Action
}

/** [iconModel]: an item_model id (e.g. lom:district_crest_moonbay) shown on a PAPER icon instead of [icon]. */
internal data class Npc3Option(val label: String, val hint: String, val icon: Material, val action: Npc3Action, val iconModel: String? = null)

/**
 * One NPC's Screens 3 window. Every loaded NPC has one: the hub rows come from the NPC's own root menu (npc/<id>.yml),
 * and the name, title, portrait, row labels and row icons from the table bundled in the jar (npc3/screens.yml), so no NPC needs
 * a file on the server.
 */
internal data class Npc3Config(
    val npcId: String,
    val name: String,
    val title: String,
    val portraitName: String,
    val options: List<Npc3Option>,
    val warnings: List<String>,
) {
    companion object {
        const val MAX_OPTIONS = 4

        private var table: YamlConfiguration? = null

        /** Reads npc3/screens.yml from the jar once. */
        fun loadTable(stream: InputStream?) {
            table = stream?.use { YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8)) }
        }

        fun of(npc: NPC): Npc3Config = build(npc, table?.getConfigurationSection(tableKey(npc.id)))

        private fun tableKey(id: String) = table?.getKeys(false)?.firstOrNull { it.equals(id, ignoreCase = true) } ?: id

        fun build(npc: NPC, entry: ConfigurationSection?): Npc3Config {
            val warnings = mutableListOf<String>()
            val labels = entry?.getConfigurationSection("labels")
            val icons = entry?.getConfigurationSection("icons")
            val roots = (npc.rootMenu as? SelectionMenu)?.childMenus() ?: listOf(npc.rootMenu)

            val options = mutableListOf<Npc3Option>()
            val lastShop = roots.lastOrNull { it is ShopMenu }
            var shopIndex = 0
            val typeCounts = mutableMapOf<MenuType, Int>()
            for (menu in roots) {
                if (menu is CustomMenu || menu is SelectionMenu) continue
                val n = typeCounts.merge(menu.type, 1, Int::plus)!!
                val key = menu.type.name.lowercase() + if (n > 1) n else ""
                val custom = labels?.getString(key)
                options += when (menu) {
                    is ShopMenu -> {
                        shopIndex++
                        Npc3Option(custom ?: if (roots.count { it is ShopMenu } > 1) "Wares $shopIndex" else "Browse wares", "See what's for sale", Material.CHEST, Npc3Action.Shop(menu), icons?.getString(key))
                    }
                    is ExternalMenu -> Npc3Option(custom ?: externalLabel(menu), labels?.getString("${key}_hint") ?: "Step this way", Material.ENDER_EYE, Npc3Action.Legacy(menu))
                    else -> legacy(menu, custom)
                }
                // the sell row follows the last shop
                if (menu === lastShop) {
                    options += Npc3Option(labels?.getString("sell") ?: "Sell goods", "Trade goods for coin", Material.GOLD_NUGGET, Npc3Action.Sell)
                }
            }
            if (options.size > MAX_OPTIONS) {
                warnings += "has ${options.size} options; showing the first $MAX_OPTIONS: ${options.drop(MAX_OPTIONS).joinToString { it.label }} left out"
            }
            val shown = options.take(MAX_OPTIONS).toMutableList()
            if (shown.size < MAX_OPTIONS) shown += Npc3Option("Farewell", "Be on your way", Material.OAK_DOOR, Npc3Action.Leave)

            return Npc3Config(
                npcId = npc.id,
                name = entry?.getString("name") ?: spaced(npc.id),
                title = entry?.getString("title") ?: "",
                portraitName = entry?.getString("portrait")?.lowercase() ?: "default",
                options = shown,
                warnings = warnings,
            )
        }

        private fun legacy(menu: NPCMenu, custom: String?): Npc3Option {
            val (label, hint, icon) = when (menu.type) {
                MenuType.QUEST -> Triple("Work", "Any jobs going?", Material.WRITABLE_BOOK)
                MenuType.TAILOR -> Triple("Tailoring", "Pack up an outfit", Material.LEATHER_CHESTPLATE)
                MenuType.TAXES -> Triple("Pay taxes", "Settle up with the city", Material.PAPER)
                MenuType.BANK -> Triple("Banking", "Your account and notes", Material.GOLD_INGOT)
                else -> Triple(spaced(menu.type.name.lowercase()), "", Material.BOOK)
            }
            return Npc3Option(custom ?: label, hint, icon, Npc3Action.Legacy(menu))
        }

        /** "npc-carlthekobold-gamblingMenu" -> "Gambling"; a command falls back to "More" */
        private fun externalLabel(menu: ExternalMenu): String {
            val spell = (menu.action as? ExternalMenuAction.MagicSpell)?.spellId ?: return "More"
            val word = spell.substringAfterLast('-').removeSuffix("Menu").removeSuffix("menu")
            return spaced(word).ifBlank { "More" }
        }

        /** "CarlTheKobold" -> "Carl The Kobold" */
        fun spaced(id: String): String =
            id.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").replaceFirstChar { it.uppercase() }
    }
}
