package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.CustomMenu
import com.danidipp.sneakynpcs.menus.NPCMenu
import com.danidipp.sneakynpcs.menus.NPCQuest
import com.danidipp.sneakynpcs.menus.NPCQuestItem
import com.danidipp.sneakynpcs.menus.QuestMenu
import com.danidipp.sneakynpcs.menus.SelectionMenu
import com.danidipp.sneakynpcs.menus.ShopMenu
import com.danidipp.sneakynpcs.menus.ShopMenuItem
import com.danidipp.sneakynpcs.shop.ShopPrice
import com.danidipp.sneakynpcs.shop.ShopRequirement
import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.util.magicitems.MagicItems
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
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

        val style = npcYaml.getString("style")?.trim().orEmpty()
        if (style.isBlank()) {
            errors.add("$npcId: Missing or invalid 'style' field")
        } else if (style != style.lowercase()) {
            errors.add("$npcId: Invalid 'style' value '$style'. Expected lowercase.")
        }

        val friendship = npcYaml.getBoolean("friendship", false)
        val reputation = npcYaml.getString("reputation", "") ?: ""

        val maxGold = parseIntField(npcYaml, "maxGold", npcId, errors) { it > 0 }
        val restockInterval = parseIntField(npcYaml, "restockInterval", npcId, errors) { it >= 0 }
        val restockAmount = parseIntField(npcYaml, "restockAmount", npcId, errors) { it >= 0 }

        val rootMenuYaml = npcYaml.get("rootMenu")
        val rootMenuMap = asObjectMap(rootMenuYaml)
        val (rootMenu, menuErrors) = if (rootMenuMap != null) {
            parseMenuNode(rootMenuMap, npcId, "rootMenu")
        } else {
            Pair(
                null,
                listOf(
                    "$npcId: Missing or invalid 'rootMenu' section. " +
                        "Expected an object with at least a 'type' key, got ${describeValue(rootMenuYaml)}. " +
                        "Top-level keys: ${npcYaml.getKeys(false).sorted().joinToString(", ")}"
                )
            )
        }
        errors.addAll(menuErrors)

        return Pair(
            NPC(
                id = npcId,
                style = style.ifBlank { "invalid_style" },
                friendship = friendship,
                reputation = reputation,
                maxGold = maxGold ?: 1,
                restockInterval = restockInterval ?: 0,
                restockAmount = restockAmount ?: 0,
                rootMenu = rootMenu ?: CustomMenu("invalid_root_menu")
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

    private fun asObjectMap(value: Any?): Map<*, *>? = when (value) {
        is Map<*, *> -> value
        is ConfigurationSection -> value.getValues(false)
        else -> null
    }

    private fun describeValue(value: Any?): String = when (value) {
        null -> "null"
        is ConfigurationSection -> "ConfigurationSection(path='${value.currentPath}', keys=[${value.getKeys(false).sorted().joinToString(", ")}])"
        is Map<*, *> -> "Map(keys=[${value.keys.filterIsInstance<String>().sorted().joinToString(", ")}])"
        is List<*> -> "List(size=${value.size})"
        is String -> "String('$value')"
        else -> "${value::class.simpleName}($value)"
    }

    fun parseMenuNode(menuYaml: Map<*, *>, npcId: String, path: String): Pair<NPCMenu?, List<String>> {
        val rawType = menuYaml["type"]
        val type = rawType as? String ?: return Pair(
            null,
            listOf(
                "$npcId ($path): Missing or invalid 'type' field. " +
                    "Expected string, got ${describeValue(rawType)}. " +
                    "Available keys: ${menuYaml.keys.filterIsInstance<String>().sorted().joinToString(", ")}"
            )
        )
        if (type.isBlank()) {
            return Pair(
                null,
                listOf("$npcId ($path): Invalid 'type' field. Expected non-blank string, got empty string.")
            )
        }

        return when (type.lowercase()) {
            "selection" -> parseSelectionMenu(menuYaml, npcId, path)
            "quest" -> parseQuestMenu(menuYaml, npcId, path)
            "shop" -> parseShopMenu(menuYaml, npcId, path)
            "custom" -> parseCustomMenu(menuYaml, npcId, path)
            else -> Pair(null, listOf("$npcId ($path): Unknown menu type '$type'"))
        }
    }

    fun parseSelectionMenu(menuYaml: Map<*, *>, npcId: String, path: String): Pair<SelectionMenu?, List<String>> {
        val errors = mutableListOf<String>()
        val allowedKeys = setOf("type", "gui", "options")
        val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add("$npcId ($path): Unknown selection menu keys ${unknownKeys.joinToString(", ")}")
        }

        val guiId = menuYaml["gui"] as? String
        if (guiId.isNullOrBlank()) {
            errors.add("$npcId ($path): Missing or invalid 'gui' field")
        }

        val optionsYaml = menuYaml["options"] as? List<*>
        if (optionsYaml == null) {
            errors.add("$npcId ($path): Missing or invalid 'options' field")
            return Pair(null, errors)
        }
        if (optionsYaml.size != 4) {
            errors.add("$npcId ($path): 'options' must contain exactly 4 entries (found ${optionsYaml.size})")
        }

        val options = mutableListOf<NPCMenu>()
        for ((index, optionEntry) in optionsYaml.withIndex()) {
            val optionMap = asObjectMap(optionEntry)
            if (optionMap == null) {
                errors.add("$npcId ($path.options[$index]): Option must be a menu object, got ${describeValue(optionEntry)}")
                continue
            }
            val (optionMenu, optionErrors) = parseMenuNode(optionMap, npcId, "$path.options[$index]")
            errors.addAll(optionErrors)
            if (optionMenu != null) {
                options.add(optionMenu)
            }
        }

        if (errors.isNotEmpty()) {
            return Pair(null, errors)
        }

        return Pair(SelectionMenu(guiId = guiId!!, options = options), errors)
    }

    fun parseQuestMenu(menuYaml: Map<*, *>, npcId: String, path: String): Pair<QuestMenu?, List<String>> {
        val errors = mutableListOf<String>()
        val allowedKeys = setOf("type", "quests")
        val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add("$npcId ($path): Unknown quest menu keys ${unknownKeys.joinToString(", ")}")
        }

        val questsYaml = menuYaml["quests"] as? List<*> ?: run {
            errors.add("$npcId ($path): Missing or invalid 'quests' field")
            return Pair(null, errors)
        }
        if (questsYaml.isEmpty()) {
            errors.add("$npcId ($path): 'quests' list is empty")
            return Pair(null, errors)
        }

        val quests = mutableListOf<NPCQuest>()
        for ((questIndex, questEntry) in questsYaml.withIndex()) {
            val questMap = asObjectMap(questEntry)
            if (questMap == null) {
                errors.add("$npcId ($path.quests[$questIndex]): Quest must be an object, got ${describeValue(questEntry)}")
                continue
            }
            val (quest, questErrors) = parseQuestMenuQuest(questMap, npcId, "$path.quests[$questIndex]")
            if (questErrors.isNotEmpty()) {
                errors.addAll(questErrors)
                continue
            }
            if (quest != null) quests.add(quest)
        }
        if (quests.isEmpty()) {
            errors.add("$npcId ($path): No valid quests found in 'quests' list")
            return Pair(null, errors)
        }
        return Pair(QuestMenu(quests), errors)
    }

    fun parseQuestMenuQuest(questYaml: Map<*, *>, npcId: String, path: String): Pair<NPCQuest?, List<String>> {
        val errors = mutableListOf<String>()
        val allowedKeys = setOf("quest", "items")
        val unknownKeys = questYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add("$npcId ($path): Unknown quest keys ${unknownKeys.joinToString(", ")}")
        }

        val questId = "$npcId-" + (questYaml["quest"] as? String ?: run {
            errors.add("$npcId ($path): Missing or invalid 'quest' field")
            return Pair(null, errors)
        })

        val itemsYaml = questYaml["items"] as? List<*> ?: run {
            errors.add("$npcId ($path): Missing or invalid 'items' field")
            return Pair(null, errors)
        }

        val items = mutableListOf<NPCQuestItem>()
        for ((itemIndex, itemEntry) in itemsYaml.withIndex()) {
            val itemMap = asObjectMap(itemEntry)
            if (itemMap == null) {
                errors.add("$npcId ($path.items[$itemIndex]): Item must be an object, got ${describeValue(itemEntry)}")
                continue
            }
            val (item, itemErrors) = parseQuestMenuQuestItem(itemMap, npcId, "$path.items[$itemIndex]")
            if (itemErrors.isNotEmpty()) {
                errors.addAll(itemErrors)
                continue
            }
            if (item != null) items.add(item)
        }
        return Pair(NPCQuest(quest = questId, items = items), errors)
    }

    fun parseQuestMenuQuestItem(itemYaml: Map<*, *>, npcId: String, path: String): Pair<NPCQuestItem?, List<String>> {
        val errors = mutableListOf<String>()
        val allowedKeys = setOf("item", "amount")
        val unknownKeys = itemYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add("$npcId ($path): Unknown quest item keys ${unknownKeys.joinToString(", ")}")
        }

        val itemId = itemYaml["item"] as? String ?: run {
            errors.add("$npcId ($path): Missing or invalid 'item' field")
            return Pair(null, errors)
        }
        val magicItem = MagicItems.getMagicItemFromString(itemId) ?: run {
            errors.add("$npcId ($path): Magic item '$itemId' not found")
            return Pair(null, errors)
        }
        val amount = when (val amountValue = itemYaml["amount"]) {
            is Int -> amountValue
            is String -> amountValue.toIntOrNull()
            is Number -> amountValue.toInt()
            else -> null
        } ?: run {
            errors.add("$npcId ($path): Missing or invalid 'amount' field")
            return Pair(null, errors)
        }
        if (amount <= 0) {
            errors.add("$npcId ($path): 'amount' must be > 0")
            return Pair(null, errors)
        }

        return Pair(NPCQuestItem(magicItem = magicItem, amount = amount), errors)
    }

    fun parseShopMenu(menuYaml: Map<*, *>, npcId: String, path: String): Pair<ShopMenu?, List<String>> {
        val errors = mutableListOf<String>()
        val allowedKeys = setOf("type", "items", "currency")
        val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add("$npcId ($path): Unknown shop menu keys ${unknownKeys.joinToString(", ")}")
        }

        val currencyId = when (val rawCurrency = menuYaml["currency"]) {
            null -> null
            is String -> rawCurrency.takeIf { it.isNotBlank() } ?: run {
                errors.add("$npcId ($path): 'currency' cannot be blank")
                null
            }
            else -> {
                errors.add("$npcId ($path): Invalid 'currency' field. Expected string, got ${describeValue(rawCurrency)}")
                null
            }
        }
        if (currencyId != null && !plugin.currencyGraphService.hasCurrency(currencyId)) {
            errors.add("$npcId ($path): Unknown shop currency '$currencyId'")
        }

        val itemsYaml = menuYaml["items"] as? List<*> ?: run {
            errors.add("$npcId ($path): Missing or invalid 'items' field")
            return Pair(null, errors)
        }
        if (itemsYaml.isEmpty()) {
            errors.add("$npcId ($path): 'items' list is empty")
            return Pair(null, errors)
        }
        if (itemsYaml.size > 48) {
            errors.add("$npcId ($path): Shop supports up to 48 items (found ${itemsYaml.size})")
            return Pair(null, errors)
        }

        val items = mutableListOf<ShopMenuItem>()
        for ((itemIndex, itemEntry) in itemsYaml.withIndex()) {
            val itemMap = asObjectMap(itemEntry)
            if (itemMap == null) {
                errors.add("$npcId ($path.items[$itemIndex]): Item must be an object, got ${describeValue(itemEntry)}")
                continue
            }
            val (item, itemErrors) = parseShopMenuItem(itemMap, npcId, "$path.items[$itemIndex]")
            if (itemErrors.isNotEmpty()) {
                errors.addAll(itemErrors)
                continue
            }
            if (item != null) items.add(item)
        }

        if (items.isEmpty()) {
            errors.add("$npcId ($path): No valid shop items found")
            return Pair(null, errors)
        }

        val duplicateItemIds = items.groupingBy { it.itemId }.eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        if (duplicateItemIds.isNotEmpty()) {
            errors.add(
                "$npcId ($path): Shop may not sell the same MagicItem more than once. Duplicates: ${duplicateItemIds.joinToString(", ")}"
            )
            return Pair(null, errors)
        }

        return Pair(ShopMenu(items = items, currencyId = currencyId), errors)
    }

    fun parseShopMenuItem(itemYaml: Map<*, *>, npcId: String, path: String): Pair<ShopMenuItem?, List<String>> {
        val errors = mutableListOf<String>()
        val allowedKeys = setOf("item", "buyStacks", "requirements")
        val unknownKeys = itemYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add("$npcId ($path): Unknown item keys ${unknownKeys.joinToString(", ")}")
        }

        val itemId = itemYaml["item"] as? String ?: run {
            errors.add("$npcId ($path): Missing or invalid 'item' field")
            return Pair(null, errors)
        }

        val magicItem = MagicItems.getMagicItemFromString(itemId) ?: run {
            errors.add("$npcId ($path): Magic item '$itemId' not found")
            return Pair(null, errors)
        }

        val buyStacks = when (val raw = itemYaml["buyStacks"]) {
            null -> false
            is Boolean -> raw
            else -> {
                errors.add("$npcId ($path): 'buyStacks' must be a boolean")
                false
            }
        }

        val requirements = mutableListOf<ShopRequirement>()
        val requirementsYaml = itemYaml["requirements"]
        if (requirementsYaml != null) {
            if (requirementsYaml !is List<*>) {
                errors.add("$npcId ($path): 'requirements' must be a list")
            } else {
                for ((reqIndex, reqEntry) in requirementsYaml.withIndex()) {
                    val reqMap = asObjectMap(reqEntry)
                    if (reqMap == null) {
                        errors.add("$npcId ($path.requirements[$reqIndex]): Requirement must be an object, got ${describeValue(reqEntry)}")
                        continue
                    }
                    val reqAllowedKeys = setOf("variable", "min")
                    val reqUnknown = reqMap.keys.filterIsInstance<String>().filterNot { it in reqAllowedKeys }
                    if (reqUnknown.isNotEmpty()) {
                        errors.add("$npcId ($path.requirements[$reqIndex]): Unknown requirement keys ${reqUnknown.joinToString(", ")}")
                    }
                    val variableName = reqMap["variable"] as? String
                    if (variableName.isNullOrBlank()) {
                        errors.add("$npcId ($path.requirements[$reqIndex]): Missing or invalid 'variable'")
                        continue
                    }
                    val variable = MagicSpells.getVariableManager().getVariable(variableName) ?: run {
                        errors.add("$npcId ($path.requirements[$reqIndex]): Unknown MagicVariable '$variableName'")
                        continue
                    }
                    val min = when (val minRaw = reqMap["min"]) {
                        is Int -> minRaw
                        is String -> minRaw.toIntOrNull()
                        is Number -> minRaw.toInt()
                        else -> null
                    }
                    if (min == null) {
                        errors.add("$npcId ($path.requirements[$reqIndex]): Missing or invalid 'min'")
                        continue
                    }
                    requirements.add(ShopRequirement(variable, min))
                }
            }
        }

        val itemMeta = magicItem.itemStack.itemMeta ?: run {
            errors.add("$npcId ($path): Magic item '$itemId' has no item meta for pricing")
            return Pair(null, errors)
        }
        val pdc = itemMeta.persistentDataContainer
        val currencyId = pdc.get(storeCurrencyKey, PersistentDataType.STRING)?.takeIf { it.isNotBlank() } ?: run {
            errors.add("$npcId ($path): Missing price currency PDC '$storeCurrencyKey' on '$itemId'")
            return Pair(null, errors)
        }
        if (!plugin.currencyGraphService.hasCurrency(currencyId)) {
            errors.add("$npcId ($path): Unknown currency '$currencyId' in item '$itemId'")
            return Pair(null, errors)
        }

        val amountRaw = pdc.get(storeAmountKey, PersistentDataType.STRING) ?: run {
            errors.add("$npcId ($path): Missing price amount PDC '$storeAmountKey' on '$itemId'")
            return Pair(null, errors)
        }
        val amount = amountRaw.toIntOrNull() ?: run {
            errors.add("$npcId ($path): Invalid price amount '$amountRaw' on '$itemId' (expected integer string)")
            return Pair(null, errors)
        }
        if (amount <= 0) {
            errors.add("$npcId ($path): Price amount must be > 0 (got $amount)")
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

    fun parseCustomMenu(menuYaml: Map<*, *>, npcId: String, path: String): Pair<CustomMenu?, List<String>> {
        val errors = mutableListOf<String>()
        val allowedKeys = setOf("type", "gui")
        val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add("$npcId ($path): Unknown custom menu keys ${unknownKeys.joinToString(", ")}")
        }

        val guiId = menuYaml["gui"] as? String ?: run {
            errors.add("$npcId ($path): Missing or invalid 'gui' field")
            return Pair(null, errors)
        }
        if (guiId.isBlank()) {
            errors.add("$npcId ($path): 'gui' cannot be blank")
            return Pair(null, errors)
        }
        return Pair(CustomMenu(guiId), errors)
    }
}
