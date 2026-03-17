package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.CustomMenu
import com.danidipp.sneakynpcs.menus.NPCMenu
import com.danidipp.sneakynpcs.menus.NPCQuest
import com.danidipp.sneakynpcs.menus.NPCQuestItem
import com.danidipp.sneakynpcs.menus.QuestMenu
import com.danidipp.sneakynpcs.menus.ShopMenu
import com.danidipp.sneakynpcs.menus.ShopMenuItem
import com.danidipp.sneakynpcs.shop.ShopPrice
import com.danidipp.sneakynpcs.shop.ShopRequirement
import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.util.magicitems.MagicItems
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.persistence.PersistentDataType
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

data class ConfigErrorInfo(
    val npcId: String,
    val errors: List<String>,
    val exception: Exception? = null,
)

class ConfigManager(private val plugin: SneakyNPCs) {
    val configs: MutableMap<String, NPC> = mutableMapOf()

    private val storeCurrencyKey = NamespacedKey(MagicSpells.getInstance(), "magicspellpermanentdata_store_value_currency")
    private val storeAmountKey = NamespacedKey(MagicSpells.getInstance(), "magicspellpermanentdata_store_value_amount")

    fun loadConfigs(): CompletableFuture<Pair<MutableMap<String, NPC>, List<ConfigErrorInfo>>> {
        configs.clear()
        return CompletableFuture.supplyAsync {
            plugin.logger.info("Loading currencies and NPC configs...")

            ensureDataFolders()
            val currenciesFile = ensureResourceConfig("currencies.yml", plugin.dataFolder)
            val currencyErrors = plugin.currencyGraphService.loadCurrencies(currenciesFile)
            if (currencyErrors.isNotEmpty()) {
                currencyErrors.forEach { plugin.logger.severe(" - $it") }
                throw IllegalStateException("Currency config validation failed in ${currenciesFile.absolutePath}")
            }

            val npcFolder = plugin.dataFolder.resolve("npc")
            if (!npcFolder.exists() && !npcFolder.mkdirs()) {
                throw IllegalStateException("Failed to create npc directory at ${npcFolder.absolutePath}")
            }
            val files = (npcFolder.listFiles { file ->
                file.isFile && listOf("yml", "yaml").any { extension -> file.extension.equals(extension, true) }
            } ?: emptyArray()).toMutableList()

            if (files.isEmpty()) {
                plugin.logger.warning("No NPC YAML files found in ${npcFolder.absolutePath}")
                val exampleFile = ensureResourceConfig("ExampleNPC.yml", npcFolder)
                files.add(exampleFile)
                plugin.logger.info("Created example NPC file at ${exampleFile.absolutePath}")
            }

            plugin.logger.info("Processing ${files.size} NPC config files")
            val loadedConfigs = mutableMapOf<String, NPC>()
            val allErrors = mutableListOf<ConfigErrorInfo>()

            for (file in files) {
                try {
                    val npcYaml = YamlConfiguration.loadConfiguration(file)
                    val npcId = file.nameWithoutExtension
                    val (config, errors) = parseNPC(npcId, npcYaml)
                    if (errors.isNotEmpty()) {
                        plugin.logger.severe("Config validation failed for npc '$npcId' in '${file.name}':")
                        errors.forEach { plugin.logger.severe(" - $it") }
                        allErrors.add(ConfigErrorInfo(npcId, errors))
                        continue
                    }
                    loadedConfigs[npcId] = config
                } catch (e: Exception) {
                    plugin.logger.severe("Failed to load config file: ${file.absolutePath}")
                    plugin.logger.log(Level.SEVERE, e.message, e)
                    allErrors.add(ConfigErrorInfo(file.nameWithoutExtension, listOf("Exception: ${e.message}"), e))
                }
            }

            plugin.logger.info("Config loading complete: ${loadedConfigs.size} successful, ${allErrors.size} errors")
            configs.clear()
            configs.putAll(loadedConfigs)
            Pair(loadedConfigs, allErrors)
        }
    }

    private fun ensureDataFolders() {
        if (plugin.dataFolder.exists()) return
        plugin.logger.warning("Data folder missing. Creating ${plugin.dataFolder.absolutePath}")
        if (!plugin.dataFolder.mkdirs()) {
            throw IllegalStateException("Failed to create data folder ${plugin.dataFolder.absolutePath}")
        }
    }

    private fun ensureResourceConfig(resourceName: String, outputDir: File): File {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IllegalStateException("Failed to create directory ${outputDir.absolutePath}")
        }

        val outFile = outputDir.resolve(resourceName)
        if (outFile.exists()) return outFile

        val resourceStream = plugin.getResource(resourceName)
            ?: throw IllegalStateException("Resource '$resourceName' not found inside plugin jar")

        resourceStream.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outFile
    }

    fun parseNPC(npcId: String, npcYaml: YamlConfiguration): Pair<NPC, List<String>> {
        val errors = mutableListOf<String>()

        val friendship = npcYaml.getBoolean("friendship", false)
        val reputation = npcYaml.getString("reputation", "") ?: ""

        val maxGold = parseIntField(npcYaml, "maxGold", npcId, errors) { it > 0 }
        val restockInterval = parseIntField(npcYaml, "restockInterval", npcId, errors) { it >= 0 }
        val restockAmount = parseIntField(npcYaml, "restockAmount", npcId, errors) { it >= 0 }

        val menusYaml = npcYaml.getList("menus") ?: run {
            errors.add("$npcId: Missing or invalid 'menus' section")
            emptyList<Any>()
        }
        if (menusYaml.size != 4) {
            errors.add("$npcId: 'menus' section must contain exactly 4 entries (found ${menusYaml.size})")
        }
        val (menus, menuErrors) = parseMenus(menusYaml, npcId)
        errors.addAll(menuErrors)

        return Pair(
            NPC(
                id = npcId,
                friendship = friendship,
                reputation = reputation,
                maxGold = maxGold ?: 1,
                restockInterval = restockInterval ?: 0,
                restockAmount = restockAmount ?: 0,
                menus = menus
            ),
            errors
        )
    }

    private fun parseIntField(
        yaml: YamlConfiguration,
        key: String,
        npcId: String,
        errors: MutableList<String>,
        validator: (Int) -> Boolean,
    ): Int? {
        val value = when (val raw = yaml.get(key)) {
            is Int -> raw
            is String -> raw.toIntOrNull()
            is Number -> raw.toInt()
            else -> null
        } ?: run {
            errors.add("$npcId: Missing or invalid '$key' field (got ${yaml.get(key)})")
            return null
        }

        if (!validator(value)) {
            errors.add("$npcId: Invalid '$key' value $value")
            return null
        }
        return value
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
                    else if (questMenu != null) menus.add(questMenu)
                }
                "shop" -> {
                    val (shopMenu, shopErrors) = parseShopMenu(menuEntry, npcId, index)
                    if (shopErrors.isNotEmpty()) errors.addAll(shopErrors)
                    else if (shopMenu != null) menus.add(shopMenu)
                }
                "custom" -> {
                    val (customMenu, customErrors) = parseCustomMenu(menuEntry, npcId, index)
                    if (customErrors.isNotEmpty()) errors.addAll(customErrors)
                    else if (customMenu != null) menus.add(customMenu)
                }
                else -> errors.add("$npcId (menu $index): Unknown menu type '$type'")
            }
        }

        return Pair(menus, errors)
    }

    fun parseQuestMenu(menuYaml: Map<*, *>, npcId: String, menuIndex: Int): Pair<QuestMenu?, List<String>> {
        val errors = mutableListOf<String>()
        val questsYaml = menuYaml["quests"] as? List<*> ?: run {
            errors.add("$npcId (menu $menuIndex): Missing or invalid 'quests' field")
            return Pair(null, errors)
        }
        if (questsYaml.isEmpty()) {
            errors.add("$npcId (menu $menuIndex): 'quests' list is empty")
            return Pair(null, errors)
        }

        val quests = mutableListOf<NPCQuest>()
        for ((questIndex, questEntry) in questsYaml.withIndex()) {
            if (questEntry !is Map<*, *>) {
                errors.add("$npcId (menu $menuIndex, quest $questIndex): Quest is not a valid map/object")
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
        return Pair(QuestMenu(quests), errors)
    }

    fun parseQuestMenuQuest(questYaml: Map<*, *>, npcId: String, menuIndex: Int, questIndex: Int): Pair<NPCQuest?, List<String>> {
        val errors = mutableListOf<String>()
        val questId = "$npcId-" + (questYaml["quest"] as? String ?: run {
            errors.add("$npcId (menu $menuIndex, quest $questIndex): Missing or invalid 'quest' field")
            return Pair(null, errors)
        })

        val itemsYaml = questYaml["items"] as? List<*> ?: run {
            errors.add("$npcId (menu $menuIndex, quest $questIndex): Missing or invalid 'items' field")
            return Pair(null, errors)
        }

        val items = mutableListOf<NPCQuestItem>()
        for ((itemIndex, itemEntry) in itemsYaml.withIndex()) {
            if (itemEntry !is Map<*, *>) {
                errors.add("$npcId (menu $menuIndex, quest $questIndex, item $itemIndex): Item is not a valid map/object")
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
            errors.add("$npcId (menu $menuIndex, quest $questIndex, item $itemIndex): Missing or invalid 'item' field")
            return Pair(null, errors)
        }
        val magicItem = MagicItems.getMagicItemFromString(itemId) ?: run {
            errors.add("$npcId (menu $menuIndex, quest $questIndex, item $itemIndex): Magic item '$itemId' not found")
            return Pair(null, errors)
        }
        val amount = when (val amountValue = itemYaml["amount"]) {
            is Int -> amountValue
            is String -> amountValue.toIntOrNull()
            is Number -> amountValue.toInt()
            else -> null
        } ?: run {
            errors.add("$npcId (menu $menuIndex, quest $questIndex, item $itemIndex): Missing or invalid 'amount' field")
            return Pair(null, errors)
        }
        if (amount <= 0) {
            errors.add("$npcId (menu $menuIndex, quest $questIndex, item $itemIndex): 'amount' must be > 0")
            return Pair(null, errors)
        }

        return Pair(NPCQuestItem(magicItem = magicItem, amount = amount), errors)
    }

    fun parseShopMenu(menuYaml: Map<*, *>, npcId: String, menuIndex: Int): Pair<ShopMenu?, List<String>> {
        val errors = mutableListOf<String>()
        val allowedKeys = setOf("type", "items")
        val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add("$npcId (menu $menuIndex): Unknown shop menu keys ${unknownKeys.joinToString(", ")}")
        }

        val itemsYaml = menuYaml["items"] as? List<*> ?: run {
            errors.add("$npcId (menu $menuIndex): Missing or invalid 'items' field")
            return Pair(null, errors)
        }
        if (itemsYaml.isEmpty()) {
            errors.add("$npcId (menu $menuIndex): 'items' list is empty")
            return Pair(null, errors)
        }
        if (itemsYaml.size > 48) {
            errors.add("$npcId (menu $menuIndex): Shop supports up to 48 items (found ${itemsYaml.size})")
            return Pair(null, errors)
        }

        val items = mutableListOf<ShopMenuItem>()
        for ((itemIndex, itemEntry) in itemsYaml.withIndex()) {
            if (itemEntry !is Map<*, *>) {
                errors.add("$npcId (menu $menuIndex, item $itemIndex): Item is not a valid map/object")
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
            errors.add("$npcId (menu $menuIndex): No valid shop items found")
            return Pair(null, errors)
        }

        return Pair(ShopMenu(items = items), errors)
    }

    fun parseShopMenuItem(itemYaml: Map<*, *>, npcId: String, menuIndex: Int, itemIndex: Int): Pair<ShopMenuItem?, List<String>> {
        val errors = mutableListOf<String>()
        val allowedKeys = setOf("item", "buyStacks", "requirements")
        val unknownKeys = itemYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add("$npcId (menu $menuIndex, item $itemIndex): Unknown item keys ${unknownKeys.joinToString(", ")}")
        }

        val itemId = itemYaml["item"] as? String ?: run {
            errors.add("$npcId (menu $menuIndex, item $itemIndex): Missing or invalid 'item' field")
            return Pair(null, errors)
        }

        val magicItem = MagicItems.getMagicItemFromString(itemId) ?: run {
            errors.add("$npcId (menu $menuIndex, item $itemIndex): Magic item '$itemId' not found")
            return Pair(null, errors)
        }

        val buyStacks = when (val raw = itemYaml["buyStacks"]) {
            null -> false
            is Boolean -> raw
            else -> {
                errors.add("$npcId (menu $menuIndex, item $itemIndex): 'buyStacks' must be a boolean")
                false
            }
        }

        val requirements = mutableListOf<ShopRequirement>()
        val requirementsYaml = itemYaml["requirements"]
        if (requirementsYaml != null) {
            if (requirementsYaml !is List<*>) {
                errors.add("$npcId (menu $menuIndex, item $itemIndex): 'requirements' must be a list")
            } else {
                for ((reqIndex, reqEntry) in requirementsYaml.withIndex()) {
                    if (reqEntry !is Map<*, *>) {
                        errors.add("$npcId (menu $menuIndex, item $itemIndex, requirement $reqIndex): Requirement must be a map")
                        continue
                    }
                    val reqAllowedKeys = setOf("variable", "min")
                    val reqUnknown = reqEntry.keys.filterIsInstance<String>().filterNot { it in reqAllowedKeys }
                    if (reqUnknown.isNotEmpty()) {
                        errors.add("$npcId (menu $menuIndex, item $itemIndex, requirement $reqIndex): Unknown requirement keys ${reqUnknown.joinToString(", ")}")
                    }
                    val variableName = reqEntry["variable"] as? String
                    if (variableName.isNullOrBlank()) {
                        errors.add("$npcId (menu $menuIndex, item $itemIndex, requirement $reqIndex): Missing or invalid 'variable'")
                        continue
                    }
                    val variable = MagicSpells.getVariableManager().getVariable(variableName) ?: run {
                        errors.add("$npcId (menu $menuIndex, item $itemIndex, requirement $reqIndex): Unknown MagicVariable '$variableName'")
                        continue
                    }
                    val min = when (val minRaw = reqEntry["min"]) {
                        is Int -> minRaw
                        is String -> minRaw.toIntOrNull()
                        is Number -> minRaw.toInt()
                        else -> null
                    }
                    if (min == null) {
                        errors.add("$npcId (menu $menuIndex, item $itemIndex, requirement $reqIndex): Missing or invalid 'min'")
                        continue
                    }
                    requirements.add(ShopRequirement(variable, min))
                }
            }
        }

        val itemMeta = magicItem.itemStack.itemMeta ?: run {
            errors.add("$npcId (menu $menuIndex, item $itemIndex): Magic item '$itemId' has no item meta for pricing")
            return Pair(null, errors)
        }
        val pdc = itemMeta.persistentDataContainer
        val currencyId = pdc.get(storeCurrencyKey, PersistentDataType.STRING)?.takeIf { it.isNotBlank() } ?: run {
            errors.add("$npcId (menu $menuIndex, item $itemIndex): Missing price currency PDC '$storeCurrencyKey' on '$itemId'")
            return Pair(null, errors)
        }
        if (!plugin.currencyGraphService.hasCurrency(currencyId)) {
            errors.add("$npcId (menu $menuIndex, item $itemIndex): Unknown currency '$currencyId' in item '$itemId'")
            return Pair(null, errors)
        }

        val amountRaw = pdc.get(storeAmountKey, PersistentDataType.STRING) ?: run {
            errors.add("$npcId (menu $menuIndex, item $itemIndex): Missing price amount PDC '$storeAmountKey' on '$itemId'")
            return Pair(null, errors)
        }
        val amount = amountRaw.toIntOrNull() ?: run {
            errors.add("$npcId (menu $menuIndex, item $itemIndex): Invalid price amount '$amountRaw' on '$itemId' (expected integer string)")
            return Pair(null, errors)
        }
        if (amount <= 0) {
            errors.add("$npcId (menu $menuIndex, item $itemIndex): Price amount must be > 0 (got $amount)")
            return Pair(null, errors)
        }

        return Pair(
            ShopMenuItem(
                itemId = itemId,
                magicItem = magicItem,
                buyStacks = buyStacks,
                requirements = requirements,
                price = ShopPrice(currencyId = currencyId, amount = amount)
            ),
            errors
        )
    }

    fun parseCustomMenu(menuYaml: Map<*, *>, npcId: String, menuIndex: Int): Pair<CustomMenu?, List<String>> {
        val errors = mutableListOf<String>()
        val guiId = menuYaml["gui"] as? String ?: run {
            errors.add("$npcId (menu $menuIndex): Missing or invalid 'gui' field")
            return Pair(null, errors)
        }
        if (guiId.isBlank()) {
            errors.add("$npcId (menu $menuIndex): 'gui' cannot be blank")
            return Pair(null, errors)
        }
        return Pair(CustomMenu(guiId), errors)
    }
}
