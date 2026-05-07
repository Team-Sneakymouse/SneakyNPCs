package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.menus.CustomMenu
import com.danidipp.sneakynpcs.menus.ExternalMenu
import com.danidipp.sneakynpcs.menus.NPCMenu
import com.danidipp.sneakynpcs.menus.NPCQuest
import com.danidipp.sneakynpcs.menus.NPCQuestHint
import com.danidipp.sneakynpcs.menus.NPCQuestItem
import com.danidipp.sneakynpcs.menus.NPCQuestItemReward
import com.danidipp.sneakynpcs.menus.NPCQuestReputationReward
import com.danidipp.sneakynpcs.menus.NPCQuestReward
import com.danidipp.sneakynpcs.menus.NPCQuestVariableOperation
import com.danidipp.sneakynpcs.menus.NPCQuestVariableReward
import com.danidipp.sneakynpcs.menus.QuestMenu
import com.danidipp.sneakynpcs.menus.SelectionMenu
import com.danidipp.sneakynpcs.menus.ShopMenu
import com.danidipp.sneakynpcs.menus.ShopMenuItem
import com.danidipp.sneakynpcs.menus.TailorButtonConfig
import com.danidipp.sneakynpcs.menus.TailorMenu
import com.danidipp.sneakynpcs.menus.TaxesMenu
import com.danidipp.sneakynpcs.menus.TaxesMenuConfig
import com.danidipp.sneakynpcs.menus.TaxesPaymentConfig
import com.danidipp.sneakynpcs.menus.TaxesRewardConfig
import com.danidipp.sneakynpcs.menus.calculateTaxesCurrencyUnits
import com.danidipp.sneakynpcs.shop.CurrencyLookup
import com.danidipp.sneakynpcs.shop.ShopPrice
import com.danidipp.sneakynpcs.shop.ShopRequirement
import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.util.magicitems.MagicItem
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

private fun describeConfigValue(value: Any?): String = when (value) {
    null -> "null"
    is ConfigurationSection -> "ConfigurationSection(path='${value.currentPath}', keys=[${value.getKeys(false).sorted().joinToString(", ")}])"
    is Map<*, *> -> "Map(keys=[${value.keys.filterIsInstance<String>().sorted().joinToString(", ")}])"
    is List<*> -> "List(size=${value.size})"
    is String -> "String('$value')"
    else -> "${value::class.simpleName}($value)"
}

internal fun parseBankDisplayConfig(
    rawBankDisplay: Any?,
    path: String,
    currencyLookup: CurrencyLookup,
): Pair<List<String>, List<Component>> {
    val errors = ValidationErrors()
    val bankDisplay = when (rawBankDisplay) {
        null -> emptyList()
        is List<*> -> buildList {
            for ((index, entry) in rawBankDisplay.withIndex()) {
                when (entry) {
                    !is String -> errors.add("$path.bankDisplay[$index]", "Invalid field. Expected string, got ${describeConfigValue(entry)}")
                    else -> when {
                        entry.isBlank() -> errors.add("$path.bankDisplay[$index]", "Cannot be blank")
                        !currencyLookup.hasCurrency(entry) -> errors.add("$path.bankDisplay[$index]", "Unknown bank display currency '$entry'")
                        currencyLookup.getCurrency(entry)?.variableId == null ->
                            errors.add("$path.bankDisplay[$index]", "Currency '$entry' does not define a bank variable")
                        else -> add(entry)
                    }
                }
            }
        }
        else -> {
            errors.add("$path.bankDisplay", "Invalid field. Expected list, got ${describeConfigValue(rawBankDisplay)}")
            emptyList()
        }
    }
    return Pair(bankDisplay, errors.toList())
}

internal fun parseExternalMenuConfig(
    menuYaml: Map<*, *>,
    path: String,
    spellLookup: (String) -> Any?,
): Pair<String?, List<Component>> {
    val errors = ValidationErrors()
    val allowedKeys = setOf("type", "magicspell")
    val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
    if (unknownKeys.isNotEmpty()) {
        errors.add(path, "Unknown external menu keys ${unknownKeys.joinToString(", ")}")
    }

    val magicSpellId = menuYaml["magicspell"] as? String ?: run {
        errors.add("$path.magicspell", "Missing or invalid field")
        return Pair(null, errors.toList())
    }
    if (magicSpellId.isBlank()) {
        errors.add("$path.magicspell", "Cannot be blank")
        return Pair(null, errors.toList())
    }
    if (spellLookup(magicSpellId) == null) {
        errors.add("$path.magicspell", "Unknown MagicSpell internal spell id '$magicSpellId'")
        return Pair(null, errors.toList())
    }

    return Pair(magicSpellId, errors.toList())
}

internal fun parseTailorMenuConfig(
    menuYaml: Map<*, *>,
    path: String,
    currencyLookup: CurrencyLookup,
): Pair<TailorMenu?, List<Component>> {
    val errors = ValidationErrors()
    val allowedKeys = setOf("type", "shirt", "pants", "both")
    val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
    if (unknownKeys.isNotEmpty()) {
        errors.add(path, "Unknown tailor menu keys ${unknownKeys.joinToString(", ")}")
    }

    val (shirt, shirtErrors) = parseTailorButtonConfig(menuYaml["shirt"], "$path.shirt", currencyLookup)
    val (pants, pantsErrors) = parseTailorButtonConfig(menuYaml["pants"], "$path.pants", currencyLookup)
    val (both, bothErrors) = parseTailorButtonConfig(menuYaml["both"], "$path.both", currencyLookup)
    errors.addAll(shirtErrors)
    errors.addAll(pantsErrors)
    errors.addAll(bothErrors)

    if (errors.isNotEmpty()) {
        return Pair(null, errors.toList())
    }

    return Pair(TailorMenu(shirt = shirt!!, pants = pants!!, both = both!!), errors.toList())
}

internal fun parseTailorButtonConfig(
    rawButton: Any?,
    path: String,
    currencyLookup: CurrencyLookup,
): Pair<TailorButtonConfig?, List<Component>> {
    val errors = ValidationErrors()
    val buttonYaml = when (rawButton) {
        is Map<*, *> -> rawButton
        is ConfigurationSection -> rawButton.getValues(false)
        else -> null
    } ?: run {
        errors.add(path, "Missing or invalid section")
        return Pair(null, errors.toList())
    }

    val allowedKeys = setOf("title", "lore", "priceCurrency", "priceAmount")
    val unknownKeys = buttonYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
    if (unknownKeys.isNotEmpty()) {
        errors.add(path, "Unknown tailor button keys ${unknownKeys.joinToString(", ")}")
    }

    val title = buttonYaml["title"] as? String
    if (title.isNullOrBlank()) {
        errors.add("$path.title", "Missing or invalid field")
    }

    val lore = when (val rawLore = buttonYaml["lore"]) {
        is List<*> -> buildList {
            for ((index, entry) in rawLore.withIndex()) {
                when (entry) {
                    is String -> add(entry)
                    else -> errors.add("$path.lore[$index]", "Invalid field. Expected string, got ${describeConfigValue(entry)}")
                }
            }
        }
        null -> {
            errors.add("$path.lore", "Missing or invalid field")
            emptyList()
        }
        else -> {
            errors.add("$path.lore", "Invalid field. Expected list, got ${describeConfigValue(rawLore)}")
            emptyList()
        }
    }

    val priceCurrency = buttonYaml["priceCurrency"] as? String
    if (priceCurrency.isNullOrBlank()) {
        errors.add("$path.priceCurrency", "Missing or invalid field")
    } else if (!currencyLookup.hasCurrency(priceCurrency)) {
        errors.add("$path.priceCurrency", "Unknown currency '$priceCurrency'")
    }

    val priceAmount = when (val rawAmount = buttonYaml["priceAmount"]) {
        is Int -> rawAmount
        is Number -> rawAmount.toInt()
        else -> null
    }
    if (priceAmount == null) {
        errors.add("$path.priceAmount", "Missing or invalid field")
    } else if (priceAmount <= 0) {
        errors.add("$path.priceAmount", "Must be > 0")
    }

    if (errors.isNotEmpty()) {
        return Pair(null, errors.toList())
    }

    return Pair(
        TailorButtonConfig(
            title = title!!,
            lore = lore,
            price = ShopPrice(currencyId = priceCurrency!!, amount = priceAmount!!),
        ),
        errors.toList()
    )
}

internal fun parseTaxesMenuConfig(
    menuYaml: Map<*, *>,
    path: String,
    currencyLookup: CurrencyLookup,
    hasVariable: (String) -> Boolean,
    rewardItemLookup: (String) -> MagicItem?,
): Pair<TaxesMenu?, List<Component>> {
    val (taxesConfig, errors) = parseTaxesMenuSettings(
        menuYaml = menuYaml,
        path = path,
        currencyLookup = currencyLookup,
        hasVariable = hasVariable,
        hasRewardItem = { itemId -> rewardItemLookup(itemId) != null },
    )
    if (errors.isNotEmpty()) {
        return Pair(null, errors)
    }

    val rewardItem = rewardItemLookup(taxesConfig!!.reward.itemId) ?: return Pair(
        null,
        listOf(validationDetail("$path.reward.item", "Required MagicItem '${taxesConfig.reward.itemId}' is not configured"))
    )

    return Pair(TaxesMenu(rewardItem, taxesConfig), errors)
}

internal fun parseTaxesMenuSettings(
    menuYaml: Map<*, *>,
    path: String,
    currencyLookup: CurrencyLookup,
    hasVariable: (String) -> Boolean,
    hasRewardItem: (String) -> Boolean,
): Pair<TaxesMenuConfig?, List<Component>> {
    val errors = ValidationErrors()
    val allowedKeys = setOf("type", "payments", "reward")
    val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
    if (unknownKeys.isNotEmpty()) {
        errors.add(path, "Unknown taxes menu keys ${unknownKeys.joinToString(", ")}")
    }

    val payments = mutableMapOf<Int, TaxesPaymentConfig>()
    val paymentsYaml = asConfigMap(menuYaml["payments"])
    if (menuYaml.containsKey("payments") && paymentsYaml == null) {
        errors.add("$path.payments", "Invalid field. Expected object, got ${describeConfigValue(menuYaml["payments"])}")
    } else if (!menuYaml.containsKey("payments")) {
        errors.add("$path.payments", "Missing or invalid section")
    }

    if (paymentsYaml != null) {
        val allowedPaymentKeys = setOf("slot14", "slot16")
        val unknownPaymentKeys = paymentsYaml.keys.filterIsInstance<String>().filterNot { it in allowedPaymentKeys }
        if (unknownPaymentKeys.isNotEmpty()) {
            errors.add("$path.payments", "Unknown taxes payment keys ${unknownPaymentKeys.joinToString(", ")}")
        }

        parseTaxesPaymentConfig(paymentsYaml["slot14"], "$path.payments.slot14", currencyLookup, errors)
            ?.let { payments[14] = it }
        parseTaxesPaymentConfig(paymentsYaml["slot16"], "$path.payments.slot16", currencyLookup, errors)
            ?.let { payments[16] = it }
    }

    val reward = parseTaxesRewardConfig(menuYaml["reward"], "$path.reward", hasRewardItem, errors)

    if (!hasVariable("bankTax")) {
        errors.add("$path.bankTax", "Required MagicSpells variable 'bankTax' is not configured")
    }

    if (reward != null) {
        for ((slot, payment) in payments) {
            if (calculateTaxesCurrencyUnits(reward.taxValue, payment.taxValuePerUnit) == null) {
                errors.add(
                    "$path.payments.slot$slot.taxValue",
                    "Reward tax value ${reward.taxValue} must be divisible by currency tax value ${payment.taxValuePerUnit}"
                )
            }
        }
    }

    if (errors.isNotEmpty() || reward == null) {
        return Pair(null, errors.toList())
    }

    return Pair(TaxesMenuConfig(payments = payments.toMap(), reward = reward), errors.toList())
}

internal fun validateTaxesMenuConfig(
    menuYaml: Map<*, *>,
    path: String,
    currencyLookup: CurrencyLookup,
    hasVariable: (String) -> Boolean,
    hasRewardItem: (String) -> Boolean,
): List<Component> {
    return parseTaxesMenuSettings(
        menuYaml = menuYaml,
        path = path,
        currencyLookup = currencyLookup,
        hasVariable = hasVariable,
        hasRewardItem = hasRewardItem,
    ).second
}

private fun parseTaxesPaymentConfig(
    rawPayment: Any?,
    path: String,
    currencyLookup: CurrencyLookup,
    errors: ValidationErrors,
): TaxesPaymentConfig? {
    val paymentYaml = when (rawPayment) {
        null -> null
        else -> asConfigMap(rawPayment)
    } ?: run {
        errors.add(path, "Missing or invalid section")
        return null
    }

    val allowedKeys = setOf("currency", "taxValue")
    val unknownKeys = paymentYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
    if (unknownKeys.isNotEmpty()) {
        errors.add(path, "Unknown taxes payment config keys ${unknownKeys.joinToString(", ")}")
    }

    val rawCurrencyId = paymentYaml["currency"]
    val currencyId = (rawCurrencyId as? String)?.takeIf { it.isNotBlank() }
    if (currencyId == null) {
        errors.add("$path.currency", "Missing or invalid field")
    } else {
        validateTaxesCurrency(currencyId, "$path.currency", currencyLookup, errors)
    }

    val taxValue = parseLongValue(paymentYaml["taxValue"])
    if (taxValue == null) {
        errors.add("$path.taxValue", "Missing or invalid field")
    } else if (taxValue <= 0L) {
        errors.add("$path.taxValue", "Must be > 0")
    }

    if (currencyId == null || taxValue == null || taxValue <= 0L) return null
    return TaxesPaymentConfig(currencyId = currencyId, taxValuePerUnit = taxValue)
}

private fun parseTaxesRewardConfig(
    rawReward: Any?,
    path: String,
    hasRewardItem: (String) -> Boolean,
    errors: ValidationErrors,
): TaxesRewardConfig? {
    val rewardYaml = when (rawReward) {
        null -> null
        else -> asConfigMap(rawReward)
    } ?: run {
        errors.add(path, "Missing or invalid section")
        return null
    }

    val allowedKeys = setOf("item", "taxValue")
    val unknownKeys = rewardYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
    if (unknownKeys.isNotEmpty()) {
        errors.add(path, "Unknown taxes reward config keys ${unknownKeys.joinToString(", ")}")
    }

    val rawItemId = rewardYaml["item"]
    val itemId = (rawItemId as? String)?.takeIf { it.isNotBlank() }
    if (itemId == null) {
        errors.add("$path.item", "Missing or invalid field")
    } else if (!hasRewardItem(itemId)) {
        errors.add("$path.item", "Required MagicItem '$itemId' is not configured")
    }

    val taxValue = parseLongValue(rewardYaml["taxValue"])
    if (taxValue == null) {
        errors.add("$path.taxValue", "Missing or invalid field")
    } else if (taxValue <= 0L) {
        errors.add("$path.taxValue", "Must be > 0")
    }

    if (itemId == null || taxValue == null || taxValue <= 0L) return null
    return TaxesRewardConfig(itemId = itemId, taxValue = taxValue)
}

private fun asConfigMap(value: Any?): Map<*, *>? {
    return when (value) {
        is Map<*, *> -> value
        is ConfigurationSection -> value.getValues(false)
        else -> null
    }
}

private fun validateTaxesCurrency(
    currencyId: String,
    path: String,
    currencyLookup: CurrencyLookup,
    errors: ValidationErrors,
) {
    val currency = currencyLookup.getCurrency(currencyId) ?: run {
        errors.add(path, "Required currency '$currencyId' is not configured")
        return
    }
    if (currency.itemMagicItem == null && currency.variableId == null) {
        errors.add(path, "Required currency '$currencyId' must define an item or bank variable")
    }
}

private fun parseLongValue(raw: Any?): Long? = when (raw) {
    is Int -> raw.toLong()
    is Long -> raw
    is String -> raw.toLongOrNull()
    is Number -> raw.toLong()
    else -> null
}

private fun parseIntValue(raw: Any?): Int? = when (raw) {
    is Int -> raw
    is Long -> raw.toInt()
    is String -> raw.toIntOrNull()
    is Number -> raw.toInt()
    else -> null
}

private fun parseDoubleValue(raw: Any?): Double? = when (raw) {
    is Double -> raw
    is Float -> raw.toDouble()
    is Int -> raw.toDouble()
    is Long -> raw.toDouble()
    is String -> raw.toDoubleOrNull()
    is Number -> raw.toDouble()
    else -> null
}

internal fun parseQuestMenuQuestRewardsConfig(
    rawRewards: Any?,
    path: String,
    itemLookup: (String) -> MagicItem?,
    hasVariable: (String) -> Boolean,
): Pair<List<NPCQuestReward>, List<Component>> {
    val errors = ValidationErrors()
    val rewardsYaml = rawRewards as? List<*> ?: run {
        errors.add(path, "Missing or invalid field")
        return Pair(emptyList(), errors.toList())
    }
    if (rewardsYaml.isEmpty()) {
        errors.add(path, "List is empty")
        return Pair(emptyList(), errors.toList())
    }

    val rewards = mutableListOf<NPCQuestReward>()
    for ((rewardIndex, rewardEntry) in rewardsYaml.withIndex()) {
        val rewardMap = asConfigMap(rewardEntry)
        if (rewardMap == null) {
            errors.add("$path[$rewardIndex]", "Reward must be an object, got ${describeConfigValue(rewardEntry)}")
            continue
        }
        val (reward, rewardErrors) = parseQuestMenuQuestRewardConfig(
            rewardYaml = rewardMap,
            path = "$path[$rewardIndex]",
            itemLookup = itemLookup,
            hasVariable = hasVariable,
        )
        errors.addAll(rewardErrors)
        if (reward != null) rewards.add(reward)
    }

    return Pair(rewards, errors.toList())
}

internal fun parseQuestMenuQuestRewardConfig(
    rewardYaml: Map<*, *>,
    path: String,
    itemLookup: (String) -> MagicItem?,
    hasVariable: (String) -> Boolean,
): Pair<NPCQuestReward?, List<Component>> {
    val errors = ValidationErrors()
    val hasItem = rewardYaml["item"] != null
    val hasVariableKey = rewardYaml["variable"] != null
    val hasReputation = rewardYaml["reputation"] != null

    if (listOf(hasItem, hasVariableKey, hasReputation).count { it } != 1) {
        errors.add(path, "Reward must define exactly one of 'item', 'variable', or 'reputation'")
        return Pair(null, errors.toList())
    }

    return if (hasItem) {
        parseQuestMenuQuestItemRewardConfig(rewardYaml, path, itemLookup)
    } else if (hasVariableKey) {
        parseQuestMenuQuestVariableRewardConfig(rewardYaml, path, hasVariable)
    } else {
        parseQuestMenuQuestReputationRewardConfig(rewardYaml, path)
    }
}

private fun parseQuestMenuQuestItemRewardConfig(
    rewardYaml: Map<*, *>,
    path: String,
    itemLookup: (String) -> MagicItem?,
): Pair<NPCQuestReward?, List<Component>> {
    val errors = ValidationErrors()
    val allowedKeys = setOf("item", "amount")
    val unknownKeys = rewardYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
    if (unknownKeys.isNotEmpty()) {
        errors.add(path, "Unknown quest reward item keys ${unknownKeys.joinToString(", ")}")
    }

    val itemId = rewardYaml["item"] as? String
    if (itemId.isNullOrBlank()) {
        errors.add("$path.item", "Missing or invalid field")
    }
    val magicItem = itemId?.takeIf { it.isNotBlank() }?.let { id ->
        itemLookup(id) ?: run {
            errors.add("$path.item", "Magic item '$id' not found")
            null
        }
    }

    val amount = parseIntValue(rewardYaml["amount"])
    if (amount == null) {
        errors.add("$path.amount", "Missing or invalid field")
    } else if (amount <= 0) {
        errors.add("$path.amount", "Must be > 0")
    }

    if (errors.isNotEmpty() || magicItem == null || amount == null || amount <= 0) {
        return Pair(null, errors.toList())
    }
    return Pair(NPCQuestItemReward(magicItem = magicItem, amount = amount), errors.toList())
}

private fun parseQuestMenuQuestVariableRewardConfig(
    rewardYaml: Map<*, *>,
    path: String,
    hasVariable: (String) -> Boolean,
): Pair<NPCQuestReward?, List<Component>> {
    val errors = ValidationErrors()
    val allowedKeys = setOf("variable", "operation", "amount")
    val unknownKeys = rewardYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
    if (unknownKeys.isNotEmpty()) {
        errors.add(path, "Unknown quest reward variable keys ${unknownKeys.joinToString(", ")}")
    }

    val variableName = rewardYaml["variable"] as? String
    if (variableName.isNullOrBlank()) {
        errors.add("$path.variable", "Missing or invalid field")
    } else if (!hasVariable(variableName)) {
        errors.add("$path.variable", "Unknown MagicVariable '$variableName'")
    }

    val operation = when (val rawOperation = rewardYaml["operation"]) {
        "set" -> NPCQuestVariableOperation.SET
        "add" -> NPCQuestVariableOperation.ADD
        "subtract" -> NPCQuestVariableOperation.SUBTRACT
        else -> {
            errors.add("$path.operation", "Unknown variable operation '$rawOperation'. Expected one of: set, add, subtract")
            null
        }
    }

    val amount = parseDoubleValue(rewardYaml["amount"])
    if (amount == null) {
        errors.add("$path.amount", "Missing or invalid field")
    }

    if (errors.isNotEmpty() || variableName.isNullOrBlank() || operation == null || amount == null) {
        return Pair(null, errors.toList())
    }
    return Pair(
        NPCQuestVariableReward(variableName = variableName, operation = operation, amount = amount),
        errors.toList(),
    )
}

private fun parseQuestMenuQuestReputationRewardConfig(
    rewardYaml: Map<*, *>,
    path: String,
): Pair<NPCQuestReward?, List<Component>> {
    val errors = ValidationErrors()
    val allowedKeys = setOf("reputation")
    val unknownKeys = rewardYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
    if (unknownKeys.isNotEmpty()) {
        errors.add(path, "Unknown quest reward reputation keys ${unknownKeys.joinToString(", ")}")
    }

    val amount = parseDoubleValue(rewardYaml["reputation"])
    if (amount == null) {
        errors.add("$path.reputation", "Missing or invalid field")
    } else if (amount <= 0.0) {
        errors.add("$path.reputation", "Must be > 0")
    }

    if (errors.isNotEmpty() || amount == null || amount <= 0.0) {
        return Pair(null, errors.toList())
    }
    return Pair(NPCQuestReputationReward(amount = amount), errors.toList())
}

internal fun validateShopItemIdForStockPersistence(itemId: String): String? {
    if (itemId.contains('.')) {
        return "Magic item id may not contain '.'"
    }
    return null
}

internal fun buildShopStockEntryId(path: String, itemId: String): String {
    val normalizedPath = path
        .replace(".", "/")
        .replace(Regex("\\[(\\d+)]"), "/$1")
    val lastSeparatorIndex = normalizedPath.lastIndexOf('/')
    if (lastSeparatorIndex == -1) {
        return itemId
    }
    return normalizedPath.substring(0, lastSeparatorIndex + 1) + itemId
}

internal fun parseShopItemLimitConfig(
    rawLimits: Any?,
    path: String,
): Pair<ShopItemLimitConfig?, List<Component>> {
    val errors = ValidationErrors()
    val limitsYaml = when (rawLimits) {
        is Map<*, *> -> rawLimits
        is ConfigurationSection -> rawLimits.getValues(false)
        else -> null
    } ?: run {
        errors.add(path, "Missing or invalid section")
        return Pair(null, errors.toList())
    }

    val allowedKeys = setOf("maxQuantity", "restockInterval", "restockAmount")
    val unknownKeys = limitsYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
    if (unknownKeys.isNotEmpty()) {
        errors.add(path, "Unknown limits keys ${unknownKeys.joinToString(", ")}")
    }

    val maxQuantity = parseIntValue(limitsYaml["maxQuantity"])
    if (maxQuantity == null) {
        errors.add("$path.maxQuantity", "Missing or invalid field")
    } else if (maxQuantity <= 0) {
        errors.add("$path.maxQuantity", "Must be > 0")
    }

    val restockInterval = parseLongValue(limitsYaml["restockInterval"])
    if (restockInterval == null) {
        errors.add("$path.restockInterval", "Missing or invalid field")
    } else if (restockInterval < 0L) {
        errors.add("$path.restockInterval", "Must be >= 0")
    }

    val restockAmount = parseIntValue(limitsYaml["restockAmount"])
    if (restockAmount == null) {
        errors.add("$path.restockAmount", "Missing or invalid field")
    } else if (restockAmount < 0) {
        errors.add("$path.restockAmount", "Must be >= 0")
    }

    if (restockAmount != null && restockAmount > 0 && (restockInterval == null || restockInterval <= 0L)) {
        errors.add("$path.restockInterval", "Must be > 0 when 'restockAmount' is > 0")
    }

    if (errors.isNotEmpty()) {
        return Pair(null, errors.toList())
    }

    return Pair(
        ShopItemLimitConfig(
            maxQuantity = maxQuantity!!,
            restockIntervalSeconds = restockInterval!!,
            restockAmount = restockAmount!!
        ),
        errors.toList()
    )
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

    private fun describeValue(value: Any?): String = describeConfigValue(value)

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
            "external" -> parseExternalMenu(menuYaml, npcId, path)
            "tailor" -> parseTailorMenu(menuYaml, path)
            "taxes" -> parseTaxesMenu(menuYaml, path)
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
        val allowedKeys = setOf("quest", "dialogue", "hint", "completion", "items", "reward")
        val unknownKeys = questYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add(path, "Unknown quest keys ${unknownKeys.joinToString(", ")}")
        }

        val questId = "$npcId-" + (questYaml["quest"] as? String ?: run {
            errors.add("$path.quest", "Missing or invalid field")
            return Pair(null, errors)
        })
        val dialogueId = questYaml["dialogue"] as? String ?: run {
            errors.add("$path.dialogue", "Missing or invalid field")
            return Pair(null, errors)
        }
        if (dialogueId.isBlank()) {
            errors.add("$path.dialogue", "Cannot be blank")
            return Pair(null, errors)
        }
        val (hint, hintErrors) = parseQuestMenuQuestHint(questYaml["hint"], "$path.hint")
        if (hintErrors.isNotEmpty()) {
            errors.addAll(hintErrors)
            return Pair(null, errors)
        }
        val (completion, completionErrors) = parseQuestMenuQuestTooltip(questYaml["completion"], "$path.completion", "completion")
        if (completionErrors.isNotEmpty()) {
            errors.addAll(completionErrors)
            return Pair(null, errors)
        }

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

        val (rewards, rewardErrors) = parseQuestMenuQuestRewards(questYaml["reward"], "$path.reward")
        if (rewardErrors.isNotEmpty()) {
            errors.addAll(rewardErrors)
            return Pair(null, errors.toList())
        }

        if (errors.isNotEmpty()) return Pair(null, errors.toList())
        return Pair(NPCQuest(quest = questId, dialogue = dialogueId, hint = hint, completion = completion, items = items, rewards = rewards), errors.toList())
    }

    fun parseQuestMenuQuestHint(hintYaml: Any?, path: String): Pair<NPCQuestHint?, List<Component>> {
        return parseQuestMenuQuestTooltip(hintYaml, path, "hint")
    }

    fun parseQuestMenuQuestTooltip(hintYaml: Any?, path: String, configKey: String): Pair<NPCQuestHint?, List<Component>> {
        val errors = ValidationErrors()
        if (hintYaml == null) return Pair(null, errors)

        val hintMap = asObjectMap(hintYaml) ?: run {
            errors.add(path, "${configKey.replaceFirstChar { it.uppercase() }} must be an object, got ${describeValue(hintYaml)}")
            return Pair(null, errors)
        }
        val allowedKeys = setOf("title", "body")
        val unknownKeys = hintMap.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add(path, "Unknown quest $configKey keys ${unknownKeys.joinToString(", ")}")
        }

        val title = hintMap["title"] as? String ?: run {
            errors.add("$path.title", "Missing or invalid field")
            return Pair(null, errors)
        }
        if (title.isBlank()) {
            errors.add("$path.title", "Cannot be blank")
            return Pair(null, errors)
        }

        val bodyYaml = hintMap["body"] as? List<*> ?: run {
            errors.add("$path.body", "Missing or invalid field")
            return Pair(null, errors)
        }
        val body = mutableListOf<String>()
        for ((lineIndex, lineEntry) in bodyYaml.withIndex()) {
            val line = lineEntry as? String
            if (line == null) {
                errors.add("$path.body[$lineIndex]", "Expected string, got ${describeValue(lineEntry)}")
                continue
            }
            body.add(line)
        }
        if (errors.isNotEmpty()) return Pair(null, errors.toList())

        return Pair(NPCQuestHint(title = title, body = body), errors.toList())
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

    fun parseQuestMenuQuestRewards(rawRewards: Any?, path: String): Pair<List<NPCQuestReward>, List<Component>> {
        return parseQuestMenuQuestRewardsConfig(
            rawRewards = rawRewards,
            path = path,
            itemLookup = { itemId -> MagicItems.getMagicItemFromString(itemId) },
            hasVariable = { variableName -> MagicSpells.getVariableManager().getVariable(variableName) != null },
        )
    }

    fun parseQuestMenuQuestReward(rewardYaml: Map<*, *>, path: String): Pair<NPCQuestReward?, List<Component>> {
        return parseQuestMenuQuestRewardConfig(
            rewardYaml = rewardYaml,
            path = path,
            itemLookup = { itemId -> MagicItems.getMagicItemFromString(itemId) },
            hasVariable = { variableName -> MagicSpells.getVariableManager().getVariable(variableName) != null },
        )
    }

    fun parseShopMenu(menuYaml: Map<*, *>, npcId: String, path: String): Pair<ShopMenu?, List<Component>> {
        val errors = ValidationErrors()
        val allowedKeys = setOf("type", "items", "bankDisplay")
        val unknownKeys = menuYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add(path, "Unknown shop menu keys ${unknownKeys.joinToString(", ")}")
        }

        val (bankDisplayCurrencyIds, bankDisplayErrors) = parseBankDisplayConfig(
            rawBankDisplay = menuYaml["bankDisplay"],
            path = path,
            currencyLookup = plugin.currencyGraphService,
        )
        errors.addAll(bankDisplayErrors)

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

        return Pair(ShopMenu(items = items, bankDisplayCurrencyIds = bankDisplayCurrencyIds), errors.toList())
    }

    fun parseShopMenuItem(itemYaml: Map<*, *>, npcId: String, path: String): Pair<ShopMenuItem?, List<Component>> {
        val errors = ValidationErrors()
        val allowedKeys = setOf("item", "buyStacks", "requirements", "limits")
        val unknownKeys = itemYaml.keys.filterIsInstance<String>().filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            errors.add(path, "Unknown item keys ${unknownKeys.joinToString(", ")}")
        }

        val itemId = itemYaml["item"] as? String ?: run {
            errors.add("$path.item", "Missing or invalid field")
            return Pair(null, errors)
        }
        validateShopItemIdForStockPersistence(itemId)?.let { validationMessage ->
            errors.add("$path.item", validationMessage)
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

        val (limits, limitErrors) = when (val rawLimits = itemYaml["limits"]) {
            null -> Pair(null, emptyList())
            else -> parseShopItemLimitConfig(rawLimits, "$path.limits")
        }
        if (limitErrors.isNotEmpty()) {
            errors.addAll(limitErrors)
            return Pair(null, errors.toList())
        }

        return Pair(
            ShopMenuItem(
                itemId = itemId,
                magicItem = magicItem,
                stockEntryId = buildShopStockEntryId(path, itemId),
                buyStacks = buyStacks,
                requirements = requirements,
                price = ShopPrice(currencyId = currencyId, amount = amount),
                limits = limits,
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

    fun parseExternalMenu(menuYaml: Map<*, *>, npcId: String, path: String): Pair<ExternalMenu?, List<Component>> {
        val (magicSpellId, errors) = parseExternalMenuConfig(menuYaml, path) { spellId ->
            MagicSpells.getSpellByInternalName(spellId)
        }
        if (magicSpellId == null) {
            return Pair(null, errors)
        }
        return Pair(ExternalMenu(magicSpellId), errors)
    }

    fun parseTailorMenu(menuYaml: Map<*, *>, path: String): Pair<TailorMenu?, List<Component>> {
        return parseTailorMenuConfig(menuYaml, path, plugin.currencyGraphService)
    }

    fun parseTaxesMenu(menuYaml: Map<*, *>, path: String): Pair<TaxesMenu?, List<Component>> {
        return parseTaxesMenuConfig(
            menuYaml = menuYaml,
            path = path,
            currencyLookup = plugin.currencyGraphService,
            hasVariable = { variableId -> MagicSpells.getVariableManager().getVariable(variableId) != null },
            rewardItemLookup = { itemId -> MagicItems.getMagicItemByInternalName(itemId) },
        )
    }
}
