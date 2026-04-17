package com.danidipp.sneakynpcs.shop

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import java.math.BigInteger

object ShopMessageFormatter {
    fun buildSessionTotalsMessage(currencyLookup: CurrencyLookup, ledger: ShopSessionLedger): Component {
        return Component.join(
            JoinConfiguration.separator(Component.newline()),
            *buildList {
                if (ledger.spentTotals().isNotEmpty()) {
                    add(buildTotalsLine(currencyLookup, "Total Spent: ", ledger.spentTotals()))
                }
                if (ledger.earnedTotals().isNotEmpty()) {
                    add(buildTotalsLine(currencyLookup, "Total Earned: ", ledger.earnedTotals()))
                }
            }.toTypedArray(),
        )
    }

    fun buildSpentTotalsMessage(currencyLookup: CurrencyLookup, spent: Iterable<CurrencyUnits>): Component? {
        val ledger = ShopSessionLedger().apply { recordSpent(spent) }
        if (ledger.spentTotals().isEmpty()) return null
        return buildTotalsLine(currencyLookup, "Total Spent: ", ledger.spentTotals())
    }

    fun formatCurrencyId(currencyId: String): String {
        return currencyId.split('_', '-', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
    }

    private fun buildTotalsLine(
        currencyLookup: CurrencyLookup,
        label: String,
        totals: Map<String, Long>,
    ): Component {
        val line = Component.text(label, NamedTextColor.YELLOW)
        val formattedValues = totals.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> {
                currencyLookup.getAtomicValue(it.key) ?: BigInteger.ZERO
            })
            .map { (currencyId, amount) ->
                Component.text("$amount ${formatCurrencyId(currencyId)}", NamedTextColor.GOLD)
            }
        return line.append(
            Component.join(
                JoinConfiguration.separator(Component.text(", ", NamedTextColor.YELLOW)),
                formattedValues,
            )
        )
    }

}
