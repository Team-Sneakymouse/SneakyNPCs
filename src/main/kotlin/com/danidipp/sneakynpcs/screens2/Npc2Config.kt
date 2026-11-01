package com.danidipp.sneakynpcs.screens2

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/** What a screen's option row does. */
internal enum class Npc2Option(val label: String) {
    SHOP("Browse wares"),
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
    val district: Npc2District,
    val portrait: Char,
    val options: List<Npc2Option>,
    /** greeting lines by mood (see Npc2Mood), "default" for any mood without its own */
    val greetings: Map<String, List<String>>,
    /** lines for someone who's bought here before: {name} is their character, {item} what they last bought */
    val returning: List<String>,
    val shopLines: List<String>,
    val warnings: List<String>,
) {
    fun greetingsFor(mood: Npc2Mood): List<String> = greetings[mood.key] ?: greetings["default"] ?: listOf("Well met, {name}.")

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
            val options = (yaml.getStringList("options").ifEmpty { listOf("shop", "leave") }).mapNotNull { name ->
                Npc2Option.of(name) ?: null.also { warnings.add("Unknown option '$name'") }
            }.take(3)
            val greetings = yaml.getConfigurationSection("greetings")?.let { section ->
                section.getKeys(false).associateWith { section.getStringList(it) }.filterValues { it.isNotEmpty() }
            } ?: emptyMap()
            return Npc2Config(
                id = id,
                npcId = yaml.getString("npc") ?: id,
                name = yaml.getString("name") ?: id,
                district = district,
                portrait = Npc2Portraits.of(yaml.getString("portrait")),
                options = options,
                greetings = greetings,
                returning = yaml.getStringList("returning"),
                shopLines = yaml.getStringList("shopLines"),
                warnings = warnings,
            )
        }
    }
}

/** How the NPC feels, from the world they're in: it picks their greeting. */
internal enum class Npc2Mood(val key: String, val label: String) {
    CHEERFUL("default", "cheerful"),
    SLEEPY("night", "sleepy"),
    GLOOMY("rain", "gloomy");

    companion object {
        fun of(worldTime: Long, storm: Boolean): Npc2Mood = when {
            storm -> GLOOMY
            worldTime % 24000 in 13000..23000 -> SLEEPY
            else -> CHEERFUL
        }
    }
}
