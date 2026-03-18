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
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.persistence.PersistentDataType
import java.io.File
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.CompletableFuture

data class ConfigErrorInfo(
    val sourceId: String,
    val title: Component,
    val errors: List<Component>,
    val exception: Exception? = null,
) {
    fun asComponent(): Component {
        val lines = mutableListOf(title)
        lines.addAll(errors)
        return Component.join(JoinConfiguration.newlines(), lines)
    }
}

class ConfigValidationException(
    val validationErrors: List<ConfigErrorInfo>,
    message: String,
) : IllegalStateException(message)

internal fun unwrapAsyncThrowable(throwable: Throwable?): Throwable? {
    var current = throwable ?: return null
    while (current is CompletionException || current is ExecutionException) {
        current = current.cause ?: break
    }
    return current
}

private fun validationTitle(subject: String, location: String? = null): Component {
    val parts = mutableListOf<Component>(
        Component.text("[Config] ", NamedTextColor.DARK_GRAY),
        Component.text(subject, NamedTextColor.GOLD)
    )
    if (location != null) {
        parts.add(Component.text(" @ ", NamedTextColor.DARK_GRAY))
        parts.add(Component.text(location, NamedTextColor.YELLOW))
    }
    parts.add(Component.text(" failed", NamedTextColor.RED))
    return Component.join(JoinConfiguration.noSeparators(), parts)
}

private fun validationDetail(path: String, message: String): Component {
    val parts = mutableListOf<Component>(
        Component.text(" - ", NamedTextColor.RED)
    )
    if (path.isNotBlank()) {
        parts.add(Component.text(path, NamedTextColor.YELLOW))
        parts.add(Component.text(": ", NamedTextColor.DARK_GRAY))
    }
    parts.add(Component.text(message, NamedTextColor.GRAY))
    return Component.join(JoinConfiguration.noSeparators(), parts)
}

private class ValidationErrors(
    private val entries: MutableList<Component> = mutableListOf()
) : List<Component> by entries {

    fun add(path: String, message: String) {
        entries.add(validationDetail(path, message))
    }

    fun add(component: Component) {
        entries.add(component)
    }

    fun addAll(components: Iterable<Component>) {
        entries.addAll(components)
    }

    fun isNotEmpty(): Boolean = entries.isNotEmpty()
}

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
                val errorInfo = ConfigErrorInfo(
                    sourceId = "currencies",
                    title = validationTitle("Currency config", currenciesFile.absolutePath),
                    errors = currencyErrors.map { validationDetail("currencies", it) }
                )
                logValidationError(errorInfo)
                throw ConfigValidationException(
                    validationErrors = listOf(errorInfo),
                    message = "Currency config validation failed in ${currenciesFile.absolutePath}"
                )
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
                        val errorInfo = ConfigErrorInfo(
                            sourceId = npcId,
                            title = validationTitle("NPC '$npcId'", file.name),
                            errors = errors
                        )
                        logValidationError(errorInfo)
                        allErrors.add(errorInfo)
                        continue
                    }
                    loadedConfigs[npcId] = config
                } catch (e: Exception) {
                    val errorInfo = ConfigErrorInfo(
                        sourceId = file.nameWithoutExtension,
                        title = validationTitle("Config file '${file.name}'", file.absolutePath),
                        errors = listOf(validationDetail("load", "Exception: ${e.message ?: "Unknown error"}")),
                        exception = e
                    )
                    logValidationError(errorInfo)
                    allErrors.add(errorInfo)
                }
            }

            plugin.logger.info("Config loading complete: ${loadedConfigs.size} successful, ${allErrors.size} errors")
            configs.clear()
            configs.putAll(loadedConfigs)
            Pair(loadedConfigs, allErrors)
        }
    }

    private fun logValidationError(errorInfo: ConfigErrorInfo) {
        plugin.componentLogger.error(errorInfo.title)
        errorInfo.errors.forEach(plugin.componentLogger::error)
        errorInfo.exception?.let {
            plugin.componentLogger.error(Component.text("Stack trace attached.", NamedTextColor.DARK_GRAY), it)
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

    fun parseNPC(npcId: String, npcYaml: YamlConfiguration): Pair<NPC, List<Component>> {
        val errors = ValidationErrors()

        val style = npcYaml.getString("style")?.trim().orEmpty()
        if (style.isBlank()) {
            errors.add("style", "Missing or invalid field")
        } else if (style != style.lowercase()) {
            errors.add("style", "Invalid value '$style'. Expected lowercase.")
        }

        val friendship = npcYaml.getBoolean("friendship", false)
        val reputation = npcYaml.getString("reputation", "") ?: ""
        val wallet = parseWalletConfig(npcYaml.get("wallet"), npcId, errors)

        val rootMenuYaml = npcYaml.get("rootMenu")
        val rootMenuMap = asObjectMap(rootMenuYaml)
        val (rootMenu, menuErrors) = if (rootMenuMap != null) {
            parseMenuNode(rootMenuMap, npcId, "rootMenu")
        } else {
            Pair(
                null,
                listOf(
                    validationDetail(
                        "rootMenu",
                        "Missing or invalid section. " +
                            "Expected an object with at least a 'type' key, got ${describeValue(rootMenuYaml)}. " +
                            "Top-level keys: ${npcYaml.getKeys(false).sorted().joinToString(", ")}"
                    )
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
                wallet = wallet ?: NpcWalletConfig(currencyId = "invalid_wallet_currency", max = 1L, restockIntervalSeconds = 0L, restockAmount = 0L),
                rootMenu = rootMenu ?: CustomMenu("invalid_root_menu")
            ),
            errors.toList()
        )
    }

    private fun parseWalletConfig(rawWallet: Any?, npcId: String, errors: ValidationErrors): NpcWalletConfig? {
        val walletYaml = asObjectMap(rawWallet) ?: run {
            errors.add("wallet", "Missing or invalid section")
            return null
        }

        val allowedKeys = setOf("currency", "max", "restockInterval", "restockAmount")
        val unknownKeys = walletYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add("wallet", "Unknown wallet keys ${unknownKeys.joinToString(", ")}")
        }

        val currencyId = (walletYaml["currency"] as? String)?.takeIf { it.isNotBlank() }
        if (currencyId == null) {
            errors.add("wallet.currency", "Missing or invalid field")
        } else if (!plugin.currencyGraphService.hasCurrency(currencyId)) {
            errors.add("wallet.currency", "Unknown wallet currency '$currencyId'")
        }

        val max = parseLongValue(walletYaml["max"])
        if (max == null) {
            errors.add("wallet.max", "Missing or invalid field")
        } else if (max <= 0L) {
            errors.add("wallet.max", "Must be > 0")
        }

        val restockInterval = parseLongValue(walletYaml["restockInterval"])
        if (restockInterval == null) {
            errors.add("wallet.restockInterval", "Missing or invalid field")
        } else if (restockInterval < 0L) {
            errors.add("wallet.restockInterval", "Must be >= 0")
        }

        val restockAmount = parseLongValue(walletYaml["restockAmount"])
        if (restockAmount == null) {
            errors.add("wallet.restockAmount", "Missing or invalid field")
        } else if (restockAmount < 0L) {
            errors.add("wallet.restockAmount", "Must be >= 0")
        }

        if (restockAmount != null && restockAmount > 0L && (restockInterval == null || restockInterval <= 0L)) {
            errors.add("wallet.restockInterval", "Must be > 0 when 'restockAmount' is > 0")
        }

        if (currencyId == null || max == null || max <= 0L || restockInterval == null || restockInterval < 0L || restockAmount == null || restockAmount < 0L) {
            return null
        }

        return NpcWalletConfig(
            currencyId = currencyId,
            max = max,
            restockIntervalSeconds = restockInterval,
            restockAmount = restockAmount
        )
    }

    private fun parseLongValue(raw: Any?): Long? = when (raw) {
        is Int -> raw.toLong()
        is Long -> raw
        is String -> raw.toLongOrNull()
        is Number -> raw.toLong()
        else -> null
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

    fun parseMenuNode(menuYaml: Map<*, *>, npcId: String, path: String): Pair<NPCMenu?, List<Component>> {
        val rawType = menuYaml["type"]
        val type = rawType as? String ?: return Pair(
            null,
            listOf(
                validationDetail(
                    "$path.type",
                    "Missing or invalid field. " +
                        "Expected string, got ${describeValue(rawType)}. " +
                        "Available keys: ${menuYaml.keys.filterIsInstance<String>().sorted().joinToString(", ")}"
                )
            )
        )
        if (type.isBlank()) {
            return Pair(
                null,
                listOf(validationDetail("$path.type", "Invalid value. Expected non-blank string, got empty string."))
            )
        }

        return when (type.lowercase()) {
            "selection" -> parseSelectionMenu(menuYaml, npcId, path)
            "quest" -> parseQuestMenu(menuYaml, npcId, path)
            "shop" -> parseShopMenu(menuYaml, npcId, path)
            "custom" -> parseCustomMenu(menuYaml, npcId, path)
            else -> Pair(null, listOf(validationDetail("$path.type", "Unknown menu type '$type'")))
        }
    }

    fun parseSelectionMenu(menuYaml: Map<*, *>, npcId: String, path: String): Pair<SelectionMenu?, List<Component>> {
        val errors = ValidationErrors()
        val allowedKeys = setOf("type", "gui", "options")
        val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add(path, "Unknown selection menu keys ${unknownKeys.joinToString(", ")}")
        }

        val guiId = menuYaml["gui"] as? String
        if (guiId.isNullOrBlank()) {
            errors.add("$path.gui", "Missing or invalid field")
        }

        val optionsYaml = menuYaml["options"] as? List<*>
        if (optionsYaml == null) {
            errors.add("$path.options", "Missing or invalid field")
            return Pair(null, errors)
        }
        if (optionsYaml.size != 4) {
            errors.add("$path.options", "Must contain exactly 4 entries (found ${optionsYaml.size})")
        }

        val options = mutableListOf<NPCMenu>()
        for ((index, optionEntry) in optionsYaml.withIndex()) {
            val optionMap = asObjectMap(optionEntry)
            if (optionMap == null) {
                errors.add("$path.options[$index]", "Option must be a menu object, got ${describeValue(optionEntry)}")
                continue
            }
            val (optionMenu, optionErrors) = parseMenuNode(optionMap, npcId, "$path.options[$index]")
            errors.addAll(optionErrors)
            if (optionMenu != null) {
                options.add(optionMenu)
            }
        }

        if (errors.isNotEmpty()) {
            return Pair(null, errors.toList())
        }

        return Pair(SelectionMenu(guiId = guiId!!, options = options), errors.toList())
    }

    fun parseQuestMenu(menuYaml: Map<*, *>, npcId: String, path: String): Pair<QuestMenu?, List<Component>> {
        val errors = ValidationErrors()
        val allowedKeys = setOf("type", "quests")
        val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add(path, "Unknown quest menu keys ${unknownKeys.joinToString(", ")}")
        }

        val questsYaml = menuYaml["quests"] as? List<*> ?: run {
            errors.add("$path.quests", "Missing or invalid field")
            return Pair(null, errors)
        }
        if (questsYaml.isEmpty()) {
            errors.add("$path.quests", "List is empty")
            return Pair(null, errors)
        }

        val quests = mutableListOf<NPCQuest>()
        for ((questIndex, questEntry) in questsYaml.withIndex()) {
            val questMap = asObjectMap(questEntry)
            if (questMap == null) {
                errors.add("$path.quests[$questIndex]", "Quest must be an object, got ${describeValue(questEntry)}")
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
            errors.add("$path.quests", "No valid quests found in list")
            return Pair(null, errors)
        }
        return Pair(QuestMenu(quests), errors.toList())
    }

    fun parseQuestMenuQuest(questYaml: Map<*, *>, npcId: String, path: String): Pair<NPCQuest?, List<Component>> {
        val errors = ValidationErrors()
        val allowedKeys = setOf("quest", "items")
        val unknownKeys = questYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add(path, "Unknown quest keys ${unknownKeys.joinToString(", ")}")
        }

        val questId = "$npcId-" + (questYaml["quest"] as? String ?: run {
            errors.add("$path.quest", "Missing or invalid field")
            return Pair(null, errors)
        })

        val itemsYaml = questYaml["items"] as? List<*> ?: run {
            errors.add("$path.items", "Missing or invalid field")
            return Pair(null, errors)
        }

        val items = mutableListOf<NPCQuestItem>()
        for ((itemIndex, itemEntry) in itemsYaml.withIndex()) {
            val itemMap = asObjectMap(itemEntry)
            if (itemMap == null) {
                errors.add("$path.items[$itemIndex]", "Item must be an object, got ${describeValue(itemEntry)}")
                continue
            }
            val (item, itemErrors) = parseQuestMenuQuestItem(itemMap, npcId, "$path.items[$itemIndex]")
            if (itemErrors.isNotEmpty()) {
                errors.addAll(itemErrors)
                continue
            }
            if (item != null) items.add(item)
        }
        return Pair(NPCQuest(quest = questId, items = items), errors.toList())
    }

    fun parseQuestMenuQuestItem(itemYaml: Map<*, *>, npcId: String, path: String): Pair<NPCQuestItem?, List<Component>> {
        val errors = ValidationErrors()
        val allowedKeys = setOf("item", "amount")
        val unknownKeys = itemYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add(path, "Unknown quest item keys ${unknownKeys.joinToString(", ")}")
        }

        val itemId = itemYaml["item"] as? String ?: run {
            errors.add("$path.item", "Missing or invalid field")
            return Pair(null, errors)
        }
        val magicItem = MagicItems.getMagicItemFromString(itemId) ?: run {
            errors.add("$path.item", "Magic item '$itemId' not found")
            return Pair(null, errors)
        }
        val amount = when (val amountValue = itemYaml["amount"]) {
            is Int -> amountValue
            is String -> amountValue.toIntOrNull()
            is Number -> amountValue.toInt()
            else -> null
        } ?: run {
            errors.add("$path.amount", "Missing or invalid field")
            return Pair(null, errors)
        }
        if (amount <= 0) {
            errors.add("$path.amount", "Must be > 0")
            return Pair(null, errors)
        }

        return Pair(NPCQuestItem(magicItem = magicItem, amount = amount), errors.toList())
    }

    fun parseShopMenu(menuYaml: Map<*, *>, npcId: String, path: String): Pair<ShopMenu?, List<Component>> {
        val errors = ValidationErrors()
        val allowedKeys = setOf("type", "items", "currency")
        val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add(path, "Unknown shop menu keys ${unknownKeys.joinToString(", ")}")
        }

        val currencyId = when (val rawCurrency = menuYaml["currency"]) {
            null -> null
            is String -> rawCurrency.takeIf { it.isNotBlank() } ?: run {
                errors.add("$path.currency", "Cannot be blank")
                null
            }
            else -> {
                errors.add("$path.currency", "Invalid field. Expected string, got ${describeValue(rawCurrency)}")
                null
            }
        }
        if (currencyId != null && !plugin.currencyGraphService.hasCurrency(currencyId)) {
            errors.add("$path.currency", "Unknown shop currency '$currencyId'")
        }

        val itemsYaml = menuYaml["items"] as? List<*> ?: run {
            errors.add("$path.items", "Missing or invalid field")
            return Pair(null, errors)
        }
        if (itemsYaml.isEmpty()) {
            errors.add("$path.items", "List is empty")
            return Pair(null, errors)
        }
        if (itemsYaml.size > 48) {
            errors.add("$path.items", "Shop supports up to 48 items (found ${itemsYaml.size})")
            return Pair(null, errors)
        }

        val items = mutableListOf<ShopMenuItem>()
        for ((itemIndex, itemEntry) in itemsYaml.withIndex()) {
            val itemMap = asObjectMap(itemEntry)
            if (itemMap == null) {
                errors.add("$path.items[$itemIndex]", "Item must be an object, got ${describeValue(itemEntry)}")
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
            errors.add("$path.items", "No valid shop items found")
            return Pair(null, errors)
        }

        val duplicateItemIds = items.groupingBy { it.itemId }.eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        if (duplicateItemIds.isNotEmpty()) {
            errors.add(
                "$path.items",
                "Shop may not sell the same MagicItem more than once. Duplicates: ${duplicateItemIds.joinToString(", ")}"
            )
            return Pair(null, errors)
        }

        return Pair(ShopMenu(items = items, currencyId = currencyId), errors.toList())
    }

    fun parseShopMenuItem(itemYaml: Map<*, *>, npcId: String, path: String): Pair<ShopMenuItem?, List<Component>> {
        val errors = ValidationErrors()
        val allowedKeys = setOf("item", "buyStacks", "requirements")
        val unknownKeys = itemYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add(path, "Unknown item keys ${unknownKeys.joinToString(", ")}")
        }

        val itemId = itemYaml["item"] as? String ?: run {
            errors.add("$path.item", "Missing or invalid field")
            return Pair(null, errors)
        }

        val magicItem = MagicItems.getMagicItemFromString(itemId) ?: run {
            errors.add("$path.item", "Magic item '$itemId' not found")
            return Pair(null, errors)
        }

        val buyStacks = when (val raw = itemYaml["buyStacks"]) {
            null -> false
            is Boolean -> raw
            else -> {
                errors.add("$path.buyStacks", "Must be a boolean")
                false
            }
        }

        val requirements = mutableListOf<ShopRequirement>()
        val requirementsYaml = itemYaml["requirements"]
        if (requirementsYaml != null) {
            if (requirementsYaml !is List<*>) {
                errors.add("$path.requirements", "Must be a list")
            } else {
                for ((reqIndex, reqEntry) in requirementsYaml.withIndex()) {
                    val reqMap = asObjectMap(reqEntry)
                    if (reqMap == null) {
                        errors.add("$path.requirements[$reqIndex]", "Requirement must be an object, got ${describeValue(reqEntry)}")
                        continue
                    }
                    val reqAllowedKeys = setOf("variable", "min")
                    val reqUnknown = reqMap.keys.filterIsInstance<String>().filterNot { it in reqAllowedKeys }
                    if (reqUnknown.isNotEmpty()) {
                        errors.add("$path.requirements[$reqIndex]", "Unknown requirement keys ${reqUnknown.joinToString(", ")}")
                    }
                    val variableName = reqMap["variable"] as? String
                    if (variableName.isNullOrBlank()) {
                        errors.add("$path.requirements[$reqIndex].variable", "Missing or invalid field")
                        continue
                    }
                    val variable = MagicSpells.getVariableManager().getVariable(variableName) ?: run {
                        errors.add("$path.requirements[$reqIndex].variable", "Unknown MagicVariable '$variableName'")
                        continue
                    }
                    val min = when (val minRaw = reqMap["min"]) {
                        is Int -> minRaw
                        is String -> minRaw.toIntOrNull()
                        is Number -> minRaw.toInt()
                        else -> null
                    }
                    if (min == null) {
                        errors.add("$path.requirements[$reqIndex].min", "Missing or invalid field")
                        continue
                    }
                    requirements.add(ShopRequirement(variable, min))
                }
            }
        }

        val itemMeta = magicItem.itemStack.itemMeta ?: run {
            errors.add(path, "Magic item '$itemId' has no item meta for pricing")
            return Pair(null, errors)
        }
        val pdc = itemMeta.persistentDataContainer
        val currencyId = pdc.get(storeCurrencyKey, PersistentDataType.STRING)?.takeIf { it.isNotBlank() } ?: run {
            val msKey = storeCurrencyKey.toString().substringAfter("_")
            errors.add("$path.price.currency", "Missing price currency PDC '$msKey' on '$itemId'")
            return Pair(null, errors)
        }
        if (!plugin.currencyGraphService.hasCurrency(currencyId)) {
            errors.add("$path.price.currency", "Unknown currency '$currencyId' in item '$itemId'")
            return Pair(null, errors)
        }

        val amountRaw = pdc.get(storeAmountKey, PersistentDataType.STRING) ?: run {
            val msKey = storeAmountKey.toString().substringAfter("_")
            errors.add("$path.price.amount", "Missing price amount PDC '$msKey' on '$itemId'")
            return Pair(null, errors)
        }
        val amount = amountRaw.toIntOrNull() ?: run {
            errors.add("$path.price.amount", "Invalid price amount '$amountRaw' on '$itemId' (expected integer string)")
            return Pair(null, errors)
        }
        if (amount <= 0) {
            errors.add("$path.price.amount", "Must be > 0 (got $amount)")
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
            errors.toList()
        )
    }

    fun parseCustomMenu(menuYaml: Map<*, *>, npcId: String, path: String): Pair<CustomMenu?, List<Component>> {
        val errors = ValidationErrors()
        val allowedKeys = setOf("type", "gui")
        val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add(path, "Unknown custom menu keys ${unknownKeys.joinToString(", ")}")
        }

        val guiId = menuYaml["gui"] as? String ?: run {
            errors.add("$path.gui", "Missing or invalid field")
            return Pair(null, errors)
        }
        if (guiId.isBlank()) {
            errors.add("$path.gui", "Cannot be blank")
            return Pair(null, errors)
        }
        return Pair(CustomMenu(guiId), errors.toList())
    }
}
