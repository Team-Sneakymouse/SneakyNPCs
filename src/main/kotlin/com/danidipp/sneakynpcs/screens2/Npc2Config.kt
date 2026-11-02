package com.danidipp.sneakynpcs.screens2

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/** What a screen's option row does. */
internal enum class Npc2Option(val label: String) {
    SHOP("Browse wares"),
    SELL("Sell goods"),
    LEAVE("Farewell");

    companion object {
        fun of(name: String): Npc2Option? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * An NPC's Screens 2 settings: npc2/<id>.yml, kept apart from npc/ (the old menus). The shop, wallet and reputation
 * are the npc/<npc>.yml NPC's.
 */
internal data class Npc2Config(
    val id: String,
    val npcId: String,
    val name: String,
    /** the line under the name, e.g. "Goat Town Merchant" (none if blank) */
    val title: String,
    val district: Npc2District,
    val portrait: Char,
    val options: List<Npc2Option>,
    val warnings: List<String>,
) {
    companion object {
        fun folder(dataFolder: File) = File(dataFolder, "npc2")

        fun ids(dataFolder: File): List<String> = folder(dataFolder).listFiles { f -> f.extension == "yml" }
            ?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()

        /** Reads npc2/<id>.yml (read on each open, so edits need no reload); null if there's none. */
        fun load(dataFolder: File, id: String): Npc2Config? {
            val file = ids(dataFolder).firstOrNull { it.equals(id, ignoreCase = true) }
                ?.let { File(folder(dataFolder), "$it.yml") } ?: return null
            return parse(file.nameWithoutExtension, YamlConfiguration.loadConfiguration(file))
        }

        fun parse(id: String, yaml: ConfigurationSection): Npc2Config {
            val warnings = mutableListOf<String>()
            val districtName = yaml.getString("district")
            val district = Npc2District.of(districtName) ?: Npc2District.GOATTOWN.also {
                if (districtName != null) warnings.add("Unknown district '$districtName', using goattown")
            }
            val options = (yaml.getStringList("options").ifEmpty { listOf("shop", "sell", "leave") }).mapNotNull { name ->
                Npc2Option.of(name) ?: null.also { warnings.add("Unknown option '$name'") }
            }.take(4)
            return Npc2Config(
                id = id,
                npcId = yaml.getString("npc") ?: id,
                name = yaml.getString("name") ?: id,
                title = yaml.getString("title") ?: "",
                district = district,
                portrait = Npc2Portraits.of(yaml.getString("portrait")),
                options = options,
                warnings = warnings,
            )
        }
    }
}
