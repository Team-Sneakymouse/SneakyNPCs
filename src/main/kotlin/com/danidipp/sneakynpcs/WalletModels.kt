package com.danidipp.sneakynpcs

data class NpcWalletConfig(
    val currencyId: String,
    val max: Long,
    val restockIntervalSeconds: Long,
    val restockAmount: Long,
)

data class NpcWalletState(
    val nativeCurrencyId: String,
    val lastRestockAtEpochMillis: Long,
    val balances: MutableMap<String, Long>,
) {
    fun deepCopy(): NpcWalletState = copy(balances = balances.toMutableMap())
}
