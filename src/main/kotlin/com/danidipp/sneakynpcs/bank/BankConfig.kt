package com.danidipp.sneakynpcs.bank

/**
 * A bank feature only players with [variable] set to something other than 0 may use.
 * [skillName] names the unlock in the failure message.
 */
data class BankGate(
    val variable: String,
    val skillName: String?,
)

/** A WorldGuard region, written `<world>:<region>` in config. */
data class BankRegion(
    val world: String,
    val region: String,
)

enum class BankFeature(val key: String, val defaultGate: BankGate) {
    NOTES("notes", BankGate("skillTradingTier6A", "Bank Voucher")),
    LOANS("loans", BankGate("skillTradingTier3A", "Loans License")),
    EXCHANGES("exchanges", BankGate("skillTradingTier9C", "Trade Mule")),
}

data class BankMenuConfig(
    val gates: Map<BankFeature, BankGate> = BankFeature.entries.associateWith { it.defaultGate },
    val noteDepositRegion: BankRegion? = DEFAULT_NOTE_DEPOSIT_REGION,
) {
    companion object {
        val DEFAULT_NOTE_DEPOSIT_REGION = BankRegion("world", "bank")
    }
}
