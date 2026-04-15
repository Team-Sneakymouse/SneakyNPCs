package com.danidipp.sneakynpcs.shop

import com.danidipp.sneakynpcs.SneakyNPCs
import com.nisovin.magicspells.util.magicitems.MagicItems
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.math.BigInteger
import java.util.ArrayDeque

interface CurrencyLookup {
    fun getCurrency(currencyId: String): CurrencyDefinition?
    fun hasCurrency(currencyId: String): Boolean
    fun getAtomicValue(currencyId: String): BigInteger?
    fun getConvertibleCurrencies(currencyId: String): Set<String>
    fun getWalletCurrencies(nativeCurrencyId: String): List<CurrencyDefinition>
    fun isConvertible(fromCurrencyId: String, toCurrencyId: String): Boolean
}

class CurrencyGraphService(private val plugin: SneakyNPCs) : CurrencyLookup {
    private var currencies: Map<String, CurrencyDefinition> = emptyMap()
    private var atomicValues: Map<String, BigInteger> = emptyMap()
    private var components: Map<String, Set<String>> = emptyMap()

    fun getCurrencies(): Map<String, CurrencyDefinition> = currencies
    override fun getCurrency(currencyId: String): CurrencyDefinition? = currencies[currencyId]
    override fun hasCurrency(currencyId: String): Boolean = currencies.containsKey(currencyId)
    override fun getAtomicValue(currencyId: String): BigInteger? = atomicValues[currencyId]
    override fun getConvertibleCurrencies(currencyId: String): Set<String> = components[currencyId] ?: emptySet()
    override fun getWalletCurrencies(nativeCurrencyId: String): List<CurrencyDefinition> {
        val native = currencies[nativeCurrencyId] ?: return emptyList()
        val component = components[nativeCurrencyId] ?: return listOf(native)
        val others = component.asSequence()
            .filter { it != nativeCurrencyId }
            .mapNotNull(currencies::get)
            .sortedWith(
                compareByDescending<CurrencyDefinition> { atomicValues[it.id] ?: BigInteger.ZERO }
                    .thenBy { it.id }
            )
            .toList()
        return buildList {
            add(native)
            addAll(others)
        }
    }

    override fun isConvertible(fromCurrencyId: String, toCurrencyId: String): Boolean =
        components[fromCurrencyId]?.contains(toCurrencyId) == true

    fun loadCurrencies(file: File): List<String> {
        val errors = mutableListOf<String>()
        val yaml = YamlConfiguration.loadConfiguration(file)
        val currenciesSection = yaml.getConfigurationSection("currencies")
        if (currenciesSection == null) {
            return listOf("Missing required 'currencies' section in ${file.name}")
        }

        val parsed = mutableMapOf<String, CurrencyDefinition>()
        for (currencyId in currenciesSection.getKeys(false)) {
            val section = currenciesSection.getConfigurationSection(currencyId)
            if (section == null) {
                errors.add("Currency '$currencyId' must be a configuration section")
                continue
            }

            val itemId = section.getString("item")?.takeIf { it.isNotBlank() }
            val variableId = section.getString("variable")?.takeIf { it.isNotBlank() }
            val exchangeTo = section.getString("exchangeTo")?.takeIf { it.isNotBlank() }
            val rawRate = section.get("rate")
            val rawSellable = section.get("sellable")
            val rate = when (rawRate) {
                null -> null
                is Int -> rawRate
                is String -> rawRate.toIntOrNull()
                is Number -> rawRate.toInt()
                else -> null
            }
            val sellable = when (rawSellable) {
                null -> false
                is Boolean -> rawSellable
                else -> {
                    errors.add("Currency '$currencyId' has invalid 'sellable' value '$rawSellable'. Expected boolean")
                    false
                }
            }

            if (itemId == null && variableId == null) {
                errors.add("Currency '$currencyId' must define at least one of 'item' or 'variable'")
                continue
            }

            val itemMagicItem = if (itemId != null) {
                MagicItems.getMagicItemByInternalName(itemId) ?: run {
                    errors.add("Currency '$currencyId' references unknown MagicItem '$itemId'")
                    null
                }
            } else null

            if ((exchangeTo == null) != (rate == null)) {
                errors.add("Currency '$currencyId' must define both 'exchangeTo' and 'rate' together")
                continue
            }
            if (rate != null && rate <= 0) {
                errors.add("Currency '$currencyId' has invalid rate '$rate'. Rate must be > 0")
                continue
            }
            if (exchangeTo != null && exchangeTo == currencyId) {
                errors.add("Currency '$currencyId' cannot exchange to itself")
                continue
            }

            parsed[currencyId] = CurrencyDefinition(
                id = currencyId,
                itemId = itemId,
                itemMagicItem = itemMagicItem,
                variableId = variableId,
                exchangeToCurrency = exchangeTo,
                exchangeRate = rate,
                sellable = sellable
            )
        }

        // Reference validation
        for ((currencyId, currency) in parsed) {
            val to = currency.exchangeToCurrency ?: continue
            if (!parsed.containsKey(to)) {
                errors.add("Currency '$currencyId' exchange target '$to' does not exist")
            }
        }

        if (errors.isNotEmpty()) return errors

        val graphErrors = mutableListOf<String>()
        val (newAtomicValues, newComponents) = buildGraphValues(parsed, graphErrors)
        if (graphErrors.isNotEmpty()) return graphErrors

        currencies = parsed
        atomicValues = newAtomicValues
        components = newComponents
        plugin.logger.info("Loaded ${currencies.size} currencies")
        return emptyList()
    }

    private fun buildGraphValues(
        parsed: Map<String, CurrencyDefinition>,
        errors: MutableList<String>,
    ): Pair<Map<String, BigInteger>, Map<String, Set<String>>> {
        val adjacency = mutableMapOf<String, MutableList<Pair<String, Fraction>>>()
        for (currencyId in parsed.keys) {
            adjacency[currencyId] = mutableListOf()
        }

        for ((currencyId, currency) in parsed) {
            val target = currency.exchangeToCurrency ?: continue
            val rate = currency.exchangeRate ?: continue
            val forwardFactor = Fraction(BigInteger.valueOf(rate.toLong()), BigInteger.ONE)
            val inverseFactor = Fraction(BigInteger.ONE, BigInteger.valueOf(rate.toLong()))

            adjacency[currencyId]!!.add(target to forwardFactor)
            adjacency[target]!!.add(currencyId to inverseFactor)
        }

        val visited = mutableSetOf<String>()
        val valueByCurrency = mutableMapOf<String, Fraction>()
        val componentByCurrency = mutableMapOf<String, MutableSet<String>>()

        for (start in parsed.keys.sorted()) {
            if (!visited.add(start)) continue

            val queue: ArrayDeque<String> = ArrayDeque()
            queue.add(start)
            valueByCurrency[start] = Fraction.ONE
            val componentMembers = mutableSetOf<String>()
            componentMembers.add(start)
            componentByCurrency[start] = componentMembers

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val currentValue = valueByCurrency[current] ?: Fraction.ONE

                for ((next, factor) in adjacency[current].orEmpty()) {
                    val expectedNextValue = currentValue.multiply(factor)
                    val existing = valueByCurrency[next]
                    if (existing == null) {
                        valueByCurrency[next] = expectedNextValue
                        componentMembers.add(next)
                        componentByCurrency[next] = componentMembers
                        if (visited.add(next)) queue.add(next)
                    } else if (existing != expectedNextValue) {
                        errors.add("Inconsistent exchange graph between '$current' and '$next'")
                    }
                }
            }
        }

        if (errors.isNotEmpty()) return Pair(emptyMap(), emptyMap())

        val atomicByCurrency = mutableMapOf<String, BigInteger>()
        val componentsByCurrency = mutableMapOf<String, Set<String>>()

        val grouped = componentByCurrency.entries.groupBy({ it.value }, { it.key })
        for ((_, members) in grouped) {
            var lcm = BigInteger.ONE
            for (currencyId in members) {
                val fraction = valueByCurrency[currencyId] ?: continue
                lcm = lcm(lcm, fraction.denominator)
            }

            val memberSet = members.toSet()
            for (currencyId in members) {
                val fraction = valueByCurrency[currencyId] ?: continue
                val scaled = fraction.multiply(Fraction(lcm, BigInteger.ONE))
                if (scaled.denominator != BigInteger.ONE) {
                    errors.add("Failed to normalize atomic value for currency '$currencyId'")
                    continue
                }
                if (scaled.numerator <= BigInteger.ZERO) {
                    errors.add("Currency '$currencyId' produced a non-positive atomic value")
                    continue
                }
                atomicByCurrency[currencyId] = scaled.numerator
                componentsByCurrency[currencyId] = memberSet
            }
        }

        return Pair(atomicByCurrency, componentsByCurrency)
    }

    private fun lcm(a: BigInteger, b: BigInteger): BigInteger {
        if (a == BigInteger.ZERO || b == BigInteger.ZERO) return BigInteger.ZERO
        return a.divide(a.gcd(b)).multiply(b).abs()
    }

    private data class Fraction(
        val numerator: BigInteger,
        val denominator: BigInteger,
    ) {
        init {
            require(denominator != BigInteger.ZERO) { "Denominator cannot be zero" }
        }

        fun multiply(other: Fraction): Fraction {
            val n = numerator.multiply(other.numerator)
            val d = denominator.multiply(other.denominator)
            return normalize(n, d)
        }

        companion object {
            val ONE = Fraction(BigInteger.ONE, BigInteger.ONE)

            private fun normalize(n: BigInteger, d: BigInteger): Fraction {
                if (n == BigInteger.ZERO) return Fraction(BigInteger.ZERO, BigInteger.ONE)
                val sign = if (d.signum() < 0) -1 else 1
                val absN = if (sign < 0) n.negate() else n
                val absD = if (sign < 0) d.negate() else d
                val gcd = absN.gcd(absD)
                return Fraction(absN.divide(gcd), absD.divide(gcd))
            }
        }
    }
}
