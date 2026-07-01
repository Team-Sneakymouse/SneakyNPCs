package com.danidipp.sneakynpcs

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

enum class ShopWeekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
}

data class ShopOfferAvailabilityConfig(
    val weekdays: Set<ShopWeekday> = emptySet(),
    val daysOfMonth: Set<Int> = emptySet(),
    val everyNthDayOfYear: Int? = null,
    val randomGroup: String? = null,
    val chance: Double? = null,
)

data class ConfiguredShopOfferAvailability(
    val offerId: String,
    val itemId: String,
    val availability: ShopOfferAvailabilityConfig?,
)

data class ShopOfferAvailabilitySeedContext(
    val npcId: String,
    val shopMenuPath: String,
)

data class ShopOfferAvailabilityResult(
    val visibleOfferIds: List<String>,
    val hiddenOfferIds: List<String>,
    val randomGroupSelections: List<ShopOfferRandomGroupSelection> = emptyList(),
)

data class ShopOfferRandomGroupSelection(
    val groupId: String,
    val groupName: String,
    val selectedOfferId: String,
    val selectedItemId: String,
)

data class ShopOfferAvailabilityReport(
    val shopDate: LocalDate,
    val visibleOfferIds: List<String> = emptyList(),
    val hiddenOfferIds: List<String> = emptyList(),
    val randomGroupSelections: List<ShopOfferRandomGroupSelection> = emptyList(),
) {
    val randomGroupCount: Int
        get() = randomGroupSelections.size
}

class ShopOfferAvailabilityReportAccumulator(
    private val shopDate: LocalDate,
) {
    private val visibleOfferIds = mutableListOf<String>()
    private val hiddenOfferIds = mutableListOf<String>()
    private val randomGroupSelections = mutableListOf<ShopOfferRandomGroupSelection>()

    fun record(result: ShopOfferAvailabilityResult) {
        visibleOfferIds.addAll(result.visibleOfferIds)
        hiddenOfferIds.addAll(result.hiddenOfferIds)
        randomGroupSelections.addAll(result.randomGroupSelections)
    }

    fun merge(other: ShopOfferAvailabilityReportAccumulator) {
        val report = other.toReport()
        visibleOfferIds.addAll(report.visibleOfferIds)
        hiddenOfferIds.addAll(report.hiddenOfferIds)
        randomGroupSelections.addAll(report.randomGroupSelections)
    }

    fun toReport(): ShopOfferAvailabilityReport = ShopOfferAvailabilityReport(
        shopDate = shopDate,
        visibleOfferIds = visibleOfferIds.toList(),
        hiddenOfferIds = hiddenOfferIds.toList(),
        randomGroupSelections = randomGroupSelections.toList(),
    )
}

internal fun evaluateShopOfferAvailability(
    offers: List<ConfiguredShopOfferAvailability>,
    shopDate: LocalDate,
    seedContext: ShopOfferAvailabilitySeedContext,
): ShopOfferAvailabilityResult {
    val selectedRandomGroupOfferIds = mutableSetOf<String>()
    val randomGroupSelections = mutableListOf<ShopOfferRandomGroupSelection>()
    val groupedOffers = offers
        .filter { it.availability?.randomGroup != null }
        .groupBy { it.availability!!.randomGroup!! }

    for ((groupName, groupOffers) in groupedOffers) {
        val selected = groupOffers[Random(seed(seedContext, shopDate, "randomGroup", groupName)).nextInt(groupOffers.size)]
        selectedRandomGroupOfferIds.add(selected.offerId)
        randomGroupSelections.add(
            ShopOfferRandomGroupSelection(
                groupId = "${seedContext.npcId}:${seedContext.shopMenuPath}:$groupName",
                groupName = groupName,
                selectedOfferId = selected.offerId,
                selectedItemId = selected.itemId,
            )
        )
    }

    val visibleOfferIds = mutableListOf<String>()
    val hiddenOfferIds = mutableListOf<String>()

    for (offer in offers) {
        if (isShopOfferAvailable(offer, shopDate, seedContext, selectedRandomGroupOfferIds)) {
            visibleOfferIds.add(offer.offerId)
        } else {
            hiddenOfferIds.add(offer.offerId)
        }
    }

    return ShopOfferAvailabilityResult(
        visibleOfferIds = visibleOfferIds,
        hiddenOfferIds = hiddenOfferIds,
        randomGroupSelections = randomGroupSelections,
    )
}

private fun isShopOfferAvailable(
    offer: ConfiguredShopOfferAvailability,
    shopDate: LocalDate,
    seedContext: ShopOfferAvailabilitySeedContext,
    selectedRandomGroupOfferIds: Set<String>,
): Boolean {
    val availability = offer.availability
    if (availability == null) return true
    return availability.weekdays.contains(shopDate.dayOfWeek.toShopWeekday()) ||
        availability.daysOfMonth.contains(shopDate.dayOfMonth) ||
        availability.everyNthDayOfYear?.let { shopDate.dayOfYear % it == 0 } == true ||
        selectedRandomGroupOfferIds.contains(offer.offerId) ||
        availability.chance?.let {
            Random(seed(seedContext, shopDate, "chance", offer.offerId)).nextDouble() < it
        } == true
}

private fun seed(
    seedContext: ShopOfferAvailabilitySeedContext,
    shopDate: LocalDate,
    kind: String,
    id: String,
): Long {
    val key = listOf(seedContext.npcId, seedContext.shopMenuPath, shopDate.toString(), kind, id)
        .joinToString("|")
    return key.fold(1125899906842597L) { acc, char -> 31L * acc + char.code.toLong() }
}

private fun DayOfWeek.toShopWeekday(): ShopWeekday = when (this) {
    DayOfWeek.MONDAY -> ShopWeekday.MONDAY
    DayOfWeek.TUESDAY -> ShopWeekday.TUESDAY
    DayOfWeek.WEDNESDAY -> ShopWeekday.WEDNESDAY
    DayOfWeek.THURSDAY -> ShopWeekday.THURSDAY
    DayOfWeek.FRIDAY -> ShopWeekday.FRIDAY
    DayOfWeek.SATURDAY -> ShopWeekday.SATURDAY
    DayOfWeek.SUNDAY -> ShopWeekday.SUNDAY
}
