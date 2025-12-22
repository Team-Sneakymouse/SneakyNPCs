package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.CustomMenu
import com.danidipp.sneakynpcs.menus.NPCMenu
import com.danidipp.sneakynpcs.menus.NPCQuest
import com.danidipp.sneakynpcs.menus.NPCQuestItem
import com.danidipp.sneakynpcs.menus.QuestMenu
import com.danidipp.sneakynpcs.menus.ShopMenu
import com.danidipp.sneakynpcs.menus.ShopMenuItem
import com.nisovin.magicspells.util.magicitems.MagicItems
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

data class ConfigErrorInfo(
    val npcId: String,
    val errors: List<String>,
    val exception: Exception? = null,
)
class ConfigManager(val plugin: SneakyNPCs) {
    val miniMessage =  MiniMessage.miniMessage()
    val configs: MutableMap<String, NPC> = mutableMapOf()

    fun loadConfigs(): CompletableFuture<Pair<MutableMap<String, NPC>, List<ConfigErrorInfo>>> {
        configs.clear()
        return CompletableFuture.supplyAsync {
            plugin.logger.info("Loading NPC configs...")
            configs.clear()

            // Data folder
            if (!plugin.dataFolder.exists()) {
                plugin.logger.warning("Data folder does not exist, creating it now")
                if (!plugin.dataFolder.mkdirs())
                    throw Exception("Failed to create data folder")
            }

            // Files
            val files = (plugin.dataFolder.listFiles { file ->
                file.isFile && listOf("yml", "yaml").any{it -> file.extension.equals(it, true)}
            } ?: emptyArray()).toMutableList()

            if (files.isEmpty()) {
                plugin.logger.warning("No YAML configuration files found in data folder: ${plugin.dataFolder.absolutePath}")
                plugin.logger.warning("Create .yml files in this directory to add NPCs")
                val exampleFile = File(plugin.dataFolder, "ExampleNPC.yml")
                if (!exampleFile.exists()) try {
                    val resourceStream = plugin.getResource("ExampleNPC.yml")
                    if (resourceStream == null) throw Exception("Couldn't find ExampleNPC.yml in plugin jar")
                    resourceStream.use { input ->
                        exampleFile.outputStream().use { input.copyTo(it) }
                    }
                    plugin.logger.info("Created example NPC file: ${exampleFile.absolutePath}")
                    plugin.logger.info("Edit this file to create your first NPC, then reload the plugin")
                } catch (e: Exception) {
                    plugin.logger.severe("Failed to create example NPC file: ${e.message}")
                    e.printStackTrace()
                }
                if (exampleFile.exists()) files.add(exampleFile)
            }

            plugin.logger.info("Processing ${files.size} config files")
            val loadedConfigs = mutableMapOf<String, NPC>()
            val allErrors = mutableListOf<ConfigErrorInfo>()
            for (file in files) try {
                val npcYaml = YamlConfiguration.loadConfiguration(file)
                val npcId = file.nameWithoutExtension
                plugin.logger.info("Processing config file: ${file.name}")
                val (config, errors) = parseNPC(npcId, npcYaml)
                if (errors.isNotEmpty()) {
                    plugin.logger.severe("Config validation failed for shop '$npcId' in file '${file.name}':")
                    errors.forEach { plugin.logger.severe(" - $it") }
                    plugin.logger.severe("Shop '$npcId' will be skipped due to errors")
                    allErrors.add(ConfigErrorInfo(npcId, errors))
                    continue
                }

                loadedConfigs[npcId] = config
            } catch (e: Exception) {
                plugin.logger.severe("Failed to load config file: ${file.absolutePath}")
                plugin.logger.log(Level.SEVERE, e.message, e)
                e.printStackTrace()
                allErrors.add(ConfigErrorInfo(file.nameWithoutExtension, listOf("Exception: ${e.message}"), e))
            }
            // Summary
            plugin.logger.info("Config loading complete: ${configs.size} successful, ${allErrors.size} errors")
            if (allErrors.isNotEmpty()) {
                plugin.logger.warning("${allErrors.size} config files had errors and were skipped")
            }
            configs.putAll(loadedConfigs)
            return@supplyAsync Pair(loadedConfigs, allErrors)
        }
    }
    fun parseNPC(npcId: String, npcYaml: YamlConfiguration): Pair<NPC, List<String>> {
        val errors = mutableListOf<String>()

        val friendship = npcYaml.getBoolean("friendship", false)

        val reputation = npcYaml.getString("reputation", "") ?: ""

        val menusYaml: List<*> = npcYaml.getList("menus") ?: emptyList<Any>().also {
            errors.add("$npcId: Missing or invalid 'menus' section")
        }
        if (menusYaml.isEmpty() || menusYaml.size != 4)
            errors.add("$npcId: 'menus' section must contain exactly 4 entries (found ${menusYaml.size})")
        val (menus, menuErrors) = parseMenus(menusYaml, npcId)
        errors.addAll(menuErrors)

        return Pair(NPC(
            id = npcId,
            friendship = friendship,
            reputation = reputation,
            menus = menus
        ), errors)
    }
    fun parseMenus(menusYaml: List<*>, npcId: String): Pair<List<NPCMenu>, List<String>> {
        val menus = mutableListOf<NPCMenu>()
        val errors = mutableListOf<String>()

        for ((index, menuEntry) in menusYaml.withIndex()) {
            if (menuEntry !is Map<*, *>) {
                errors.add("$npcId (menu $index): Menu is not a valid map/object")
                continue
            }
            val type = menuEntry["type"] as? String ?: run {
                errors.add("$npcId (menu $index): Missing or invalid 'type' field")
                continue
            }
            when (type.lowercase()) {
                "quest" -> {
                    val (questMenu, questErrors) = parseQuestMenu(menuEntry, npcId, index)
                    if (questErrors.isNotEmpty()) errors.addAll(questErrors)
                    else if (questMenu == null) errors.add("$npcId (menu $index): Failed to parse quest menu")
                    else menus.add(questMenu)
                }
                "shop" -> {
                    val (shopMenu, shopErrors) = parseShopMenu(menuEntry, npcId, index)
                    if (shopErrors.isNotEmpty()) errors.addAll(shopErrors)
                    else if (shopMenu == null) errors.add("$npcId (menu $index): Failed to parse shop menu")
                    else menus.add(shopMenu)
                }
                "custom" -> {
                    val (customMenu, customErrors) = parseCustomMenu(menuEntry, npcId, index)
                    if (customErrors.isNotEmpty()) errors.addAll(customErrors)
                    else if (customMenu == null) errors.add("$npcId (menu $index): Failed to parse custom menu")
                    else menus.add(customMenu)
                }
                else -> {
                    errors.add("$npcId (menu $index): Unknown menu type '$type'")
                }
            }
        }
        return Pair(menus, errors)
    }
    fun parseQuestMenu(menuYaml: Map<*, *>, npcId: String, menuIndex: Int): Pair<QuestMenu?, List<String>> {
        val errors = mutableListOf<String>()

        val questsYaml = menuYaml["quests"] as? List<*> ?: run {
            errors.add("$npcId (menu $menuIndex): Missing or invalid 'quests' field (got ${menuYaml["quests"]})")
            return Pair(null, errors)
        }
        if (questsYaml.isEmpty()) {
            errors.add("$npcId (menu $menuIndex): 'quests' list is empty")
            return Pair(null, errors)
        }
        val quests = mutableListOf<NPCQuest>()
        for ((questIndex, questEntry) in questsYaml.withIndex()) {
            if (questEntry !is Map<*, *>) {
                errors.add("$npcId (menu $menuIndex, quest $questIndex): Quest is not a valid map/object (got ${questEntry?.javaClass?.simpleName})")
                continue
            }
            val (quest, questErrors) = parseQuestMenuQuest(questEntry, npcId, menuIndex, questIndex)
            if (questErrors.isNotEmpty()) {
                errors.addAll(questErrors)
                continue
            }
            if (quest != null) quests.add(quest)
        }
        if (quests.isEmpty()) {
            errors.add("$npcId (menu $menuIndex): No valid quests found in 'quests' list")
            return Pair(null, errors)
        }
        return Pair(QuestMenu(quests = quests), errors)
    }
    fun parseQuestMenuQuest(questYaml: Map<*, *>, npcId: String, menuIndex: Int, questIndex: Int): Pair<NPCQuest?, List<String>> {
        val errors = mutableListOf<String>()
        val questId = "$npcId-" + (questYaml["quest"] as? String ?: run {
            errors.add("$npcId (menu $menuIndex, quest $questIndex): Missing or invalid 'quest' field (got ${questYaml["quest"]})")
            return Pair(null, errors)
        })
        val itemsYaml = questYaml["items"] as? List<*> ?: run {
            errors.add("$npcId (menu $menuIndex, quest $questIndex): Missing or invalid 'items' field (should be a list)")
            return Pair(null, errors)
        }
        val items = mutableListOf<NPCQuestItem>()
        for ((itemIndex, itemEntry) in itemsYaml.withIndex()) {
            if (itemEntry !is Map<*, *>) {
                errors.add("$npcId (menu $menuIndex, quest $questIndex, item $itemIndex): Item is not a valid map/object (got ${itemEntry?.javaClass?.simpleName})")
                continue
            }
            val (item, itemErrors) = parseQuestMenuQuestItem(itemEntry, npcId, menuIndex, questIndex, itemIndex)
            if (itemErrors.isNotEmpty()) {
                errors.addAll(itemErrors)
                continue
            }
            if (item != null) items.add(item)
        }
        return Pair(NPCQuest(quest = questId, items = items), errors)
    }
    fun parseQuestMenuQuestItem(itemYaml: Map<*, *>, npcId: String, menuIndex: Int, questIndex: Int, itemIndex: Int): Pair<NPCQuestItem?, List<String>> {
        val errors = mutableListOf<String>()
        val itemId = itemYaml["item"] as? String ?: run {
            errors.add("$npcId (menu $menuIndex, quest $questIndex, item $itemIndex): Missing or invalid 'item' field (got ${itemYaml["item"]})")
            return Pair(null, errors)
        }
        val magicItem = MagicItems.getMagicItemFromString(itemId) ?: run {
            errors.add("$npcId (menu $menuIndex, quest $questIndex, item $itemIndex): Magic item '$itemId' not found in MagicSpells")
            return Pair(null, errors)
        }
        val amount = when (val amountValue = itemYaml["amount"]) {
            is Int -> amountValue
            is String -> amountValue.toIntOrNull()
            else -> null
        } ?: run {
            errors.add("$npcId (menu $menuIndex, quest $questIndex, item $itemIndex): Missing or invalid 'amount' field (got ${itemYaml["amount"]})")
            return Pair(null, errors)
        }
        if (amount <= 0) {
            errors.add("$npcId (menu $menuIndex, quest $questIndex, item $itemIndex): 'amount' must be greater than 0 (got $amount)")
            return Pair(null, errors)
        }
        return Pair(NPCQuestItem(magicItem = magicItem, amount = amount), errors)
    }
    fun parseShopMenu(menuYaml: Map<*, *>, npcId: String, menuIndex: Int): Pair<ShopMenu?, List<String>> {
        val errors = mutableListOf<String>()
        val maxGold = when (val maxGoldValue = menuYaml["max_gold"]) {
            is Int -> maxGoldValue
            is String -> maxGoldValue.toIntOrNull()
            else -> null
        } ?: run {
            errors.add("$npcId (menu $menuIndex): Missing or invalid 'max_gold' field (got ${menuYaml["max_gold"]})")
            return Pair(null, errors)
        }
        if (maxGold <= 0) {
            errors.add("$npcId (menu $menuIndex): 'max_gold' must be greater than 0 (got $maxGold)")
            return Pair(null, errors)
        }
        val restockTime = when (val restockTimeValue = menuYaml["restock_time"]) {
            is Int -> restockTimeValue
            is String -> restockTimeValue.toIntOrNull()
            else -> null
        } ?: run {
            errors.add("$npcId (menu $menuIndex): Missing or invalid 'restock_time' field (got ${menuYaml["restock_time"]})")
            return Pair(null, errors)
        }
        if (restockTime < 0) {
            errors.add("$npcId (menu $menuIndex): 'restock_time' cannot be negative (got $restockTime)")
            return Pair(null, errors)
        }
        val itemsYaml = menuYaml["items"] as? List<*> ?: run {
            errors.add("$npcId (menu $menuIndex): Missing or invalid 'items' field (should be a list)")
            return Pair(null, errors)
        }
        if (itemsYaml.isEmpty()) {
            errors.add("$npcId (menu $menuIndex): 'items' list is empty")
            return Pair(null, errors)
        }
        val items = mutableListOf<ShopMenuItem>()
        for ((itemIndex, itemEntry) in itemsYaml.withIndex()) {
            if (itemEntry !is Map<*, *>) {
                errors.add("$npcId (menu $menuIndex, item $itemIndex): Item is not a valid map/object (got ${itemEntry?.javaClass?.simpleName})")
                continue
            }
            val (item, itemErrors) = parseShopMenuItem(itemEntry, npcId, menuIndex, itemIndex)
            if (itemErrors.isNotEmpty()) {
                errors.addAll(itemErrors)
                continue
            }
            if (item != null) items.add(item)
        }
        if (items.isEmpty()) {
            errors.add("$npcId (menu $menuIndex): No valid items found in 'items' list")
            return Pair(null, errors)
        }
        return Pair(
            ShopMenu(
                maxGold = maxGold,
                restockTime = restockTime,
                items = items
            ), errors
        )
    }
    fun parseShopMenuItem(itemYaml: Map<*, *>, npcId: String, menuIndex: Int, itemIndex: Int): Pair<ShopMenuItem?, List<String>> {
        val errors = mutableListOf<String>()
        val itemId = itemYaml["item"] as? String ?: run {
            errors.add("$npcId (menu $menuIndex, item $itemIndex): Missing or invalid 'item' field (got ${itemYaml["item"]})")
            return Pair(null, errors)
        }
        val magicItem = MagicItems.getMagicItemFromString(itemId) ?: run {
            errors.add("$npcId (menu $menuIndex, item $itemIndex): Magic item '$itemId' not found in MagicSpells")
            return Pair(null, errors)
        }
        val buyStacks = when (val buyStacksValue = itemYaml["buy_stacks"]) {
            is Boolean -> buyStacksValue
            is String -> buyStacksValue.equals("true", true) || buyStacksValue == "1"
            null -> false // Default to false if not specified
            else -> {
                errors.add("$npcId (menu $menuIndex, item $itemIndex): Invalid 'buy_stacks' field (got ${itemYaml["buy_stacks"]})")
                return Pair(null, errors)
            }
        }
        return Pair(ShopMenuItem(magicItem = magicItem, buyStacks = buyStacks), errors)
    }
    fun parseCustomMenu(menuYaml: Map<*, *>, npcId: String, menuIndex: Int): Pair<CustomMenu?, List<String>> {
        val errors = mutableListOf<String>()
        val guiId = menuYaml["gui"] as? String ?: run {
            errors.add("$npcId (menu $menuIndex): Missing or invalid 'gui' field (got ${menuYaml["gui"]})")
            return Pair(null, errors)
        }
        if (guiId.isBlank()) {
            errors.add("$npcId (menu $menuIndex): 'gui' cannot be blank")
            return Pair(null, errors)
        }
        return Pair(CustomMenu(guiId = guiId), errors)
    }
}

