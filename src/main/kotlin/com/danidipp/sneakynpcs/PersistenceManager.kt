package com.danidipp.sneakynpcs

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class PlayerData(
    val uuid: UUID,
    private val completedQuests: MutableSet<String>,
    private val reputation: MutableMap<String, Int>,
    private val npcWallets: MutableMap<String, NpcWalletState>,
    private val shopItemStocks: MutableMap<String, MutableMap<String, ShopItemStockState>>,
) {
    @Volatile
    var isDirty = false

    @Synchronized
    fun getCompletedQuests(prefix: String?): Set<String> {
        if (prefix == null) return completedQuests.toSet()
        return completedQuests.filter{ it.startsWith(prefix) }.toSet()
    }
//    @Synchronized
//    fun hasCompletedQuest(questId: String): Boolean {
//        return completedQuests.contains(questId)
//    }
    @Synchronized
    fun completeQuest(questId: String) {
        if (completedQuests.add(questId))
            isDirty = true
    }
    @Synchronized
    fun removeQuest(questId: String) {
        if (completedQuests.remove(questId))
            isDirty = true
    }

    @Synchronized
    fun getReputation(guildId: String): Int {
        return reputation.getOrDefault(guildId, 0)
    }
    @Synchronized
    fun setReputation(guildId: String, amount: Int) {
        if (reputation[guildId] != amount) {
            reputation[guildId] = amount
            isDirty = true
        }
    }

    @Synchronized
    fun getReputationEntries(): Map<String, Int> = reputation.toMap()

    @Synchronized
    fun getNpcWalletState(npcId: String): NpcWalletState? {
        return npcWallets[npcId]?.deepCopy()
    }

    @Synchronized
    fun setNpcWalletState(npcId: String, state: NpcWalletState) {
        val normalized = state.deepCopy()
        if (npcWallets[npcId] != normalized) {
            npcWallets[npcId] = normalized
            isDirty = true
        }
    }

    @Synchronized
    fun getNpcWalletCount(): Int = npcWallets.size

    @Synchronized
    fun getShopItemStockState(npcId: String, stockEntryId: String): ShopItemStockState? {
        return shopItemStocks[npcId]?.get(stockEntryId)?.deepCopy()
    }

    @Synchronized
    fun setShopItemStockState(npcId: String, stockEntryId: String, state: ShopItemStockState) {
        val normalized = state.deepCopy()
        val npcStocks = shopItemStocks.getOrPut(npcId) { mutableMapOf() }
        if (npcStocks[stockEntryId] != normalized) {
            npcStocks[stockEntryId] = normalized
            isDirty = true
        }
    }

    @Synchronized
    fun getShopItemStockCount(): Int = shopItemStocks.values.sumOf { it.size }

    @Synchronized
    fun toYaml(): YamlConfiguration {
        val config = YamlConfiguration()
        config.set("completedQuests", completedQuests.toList())
        for ((guildId, amount) in reputation) {
            config.set("reputation.$guildId", amount)
        }
        for ((npcId, walletState) in npcWallets) {
            config.set("npcWallets.$npcId.nativeCurrency", walletState.nativeCurrencyId)
            config.set("npcWallets.$npcId.lastRestockAt", walletState.lastRestockAtEpochMillis)
            for ((currencyId, amount) in walletState.balances) {
                config.set("npcWallets.$npcId.balances.$currencyId", amount)
            }
        }
        for ((npcId, stockStates) in shopItemStocks) {
            for ((stockEntryId, stockState) in stockStates) {
                config.set("shopItemStocks.$npcId.$stockEntryId.remainingQuantity", stockState.remainingQuantity)
                config.set("shopItemStocks.$npcId.$stockEntryId.lastRestockAt", stockState.lastRestockAtEpochMillis)
            }
        }
        return config
    }
}

internal fun loadPlayerDataFromConfig(uuid: UUID, config: YamlConfiguration): PlayerData {
    val completedQuests = config.getStringList("completedQuests").toMutableSet()

    val reputation = mutableMapOf<String, Int>()
    config.getConfigurationSection("reputation")?.let { section ->
        for (key in section.getKeys(false)) {
            reputation[key] = section.getInt(key)
        }
    }

    val npcWallets = mutableMapOf<String, NpcWalletState>()
    config.getConfigurationSection("npcWallets")?.let { walletSection ->
        for (npcId in walletSection.getKeys(false)) {
            val npcSection = walletSection.getConfigurationSection(npcId) ?: continue
            val nativeCurrencyId = npcSection.getString("nativeCurrency")?.takeIf { it.isNotBlank() } ?: continue
            val balances = mutableMapOf<String, Long>()
            npcSection.getConfigurationSection("balances")?.let { balanceSection ->
                for (currencyId in balanceSection.getKeys(false)) {
                    val amount = balanceSection.getLong(currencyId)
                    if (amount > 0L) balances[currencyId] = amount
                }
            }
            npcWallets[npcId] = NpcWalletState(
                nativeCurrencyId = nativeCurrencyId,
                lastRestockAtEpochMillis = npcSection.getLong("lastRestockAt"),
                balances = balances
            )
        }
    }

    val shopItemStocks = mutableMapOf<String, MutableMap<String, ShopItemStockState>>()
    config.getConfigurationSection("shopItemStocks")?.let { stockSection ->
        for (npcId in stockSection.getKeys(false)) {
            val npcSection = stockSection.getConfigurationSection(npcId) ?: continue
            val npcStocks = mutableMapOf<String, ShopItemStockState>()
            for (stockEntryId in npcSection.getKeys(false)) {
                val stockEntrySection = npcSection.getConfigurationSection(stockEntryId) ?: continue
                npcStocks[stockEntryId] = ShopItemStockState(
                    remainingQuantity = stockEntrySection.getInt("remainingQuantity"),
                    lastRestockAtEpochMillis = stockEntrySection.getLong("lastRestockAt")
                )
            }
            if (npcStocks.isNotEmpty()) {
                shopItemStocks[npcId] = npcStocks
            }
        }
    }

    return PlayerData(
        uuid = uuid,
        completedQuests = completedQuests,
        reputation = reputation,
        npcWallets = npcWallets,
        shopItemStocks = shopItemStocks,
    )
}

class PersistenceManager(val plugin: SneakyNPCs): Listener {
    val dataCache: ConcurrentMap<UUID, PlayerData> = ConcurrentHashMap()
    val saveTask: BukkitTask;

    init {
        val dataFolder = plugin.dataFolder.resolve("players")
        if (!dataFolder.exists()) dataFolder.mkdirs()

        // Warm cache with currently online players
        for (player in Bukkit.getOnlinePlayers())
            getPlayerData(player.uniqueId)

        val saveFrequency = 20L * 60L * 5L // every 5 minutes
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            for (data in dataCache.values.filter { it.isDirty }.toList()) { // iterate over snapshot
                savePlayerData(data)
            }

            // evict players who haven't been online since the last check from cache if it's clean
            for ((uuid, data) in dataCache.entries.toList()) {
                val offlinePlayer = Bukkit.getOfflinePlayer(uuid)

                if (data.isDirty) continue // data either got dirty again just now or there is a saving problem. do nothing for now.

                if (offlinePlayer.lastSeen < System.currentTimeMillis() - saveFrequency)
                    dataCache.remove(uuid)
            }
        }, saveFrequency, saveFrequency)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent){
        getPlayerData(event.player.uniqueId)
    }
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent){
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val uuid = event.player.uniqueId
            val data = dataCache[uuid] ?: return@Runnable
            if (data.isDirty) savePlayerData(data)
        })
    }

    // Called on main thread when the plugin is disabled
    fun onDisable() {
        saveTask.cancel()
        for (data in dataCache.values) {
            if(!data.isDirty) continue
            savePlayerData(data) // Don't switch to async. Data integrity > performance on shutdown
        }
    }

    val loadingCache = ConcurrentHashMap<UUID, CompletableFuture<PlayerData>>()
    fun getPlayerData(uuid: UUID): CompletableFuture<PlayerData> {
        dataCache[uuid]?.let {
            plugin.logger.warning("Cache hit for player data $uuid")
            return CompletableFuture.completedFuture(it)
        }

        loadingCache.computeIfAbsent(uuid) {
            val future = CompletableFuture.supplyAsync {
                val playerFile = plugin.dataFolder.resolve("players").resolve("$uuid.yaml")

                if (!playerFile.exists()) {
                    plugin.logger.warning("Creating new player data for $uuid")
                    return@supplyAsync PlayerData(
                        uuid = uuid,
                        completedQuests = mutableSetOf(),
                        reputation = mutableMapOf(),
                        npcWallets = mutableMapOf(),
                        shopItemStocks = mutableMapOf(),
                    ).also { dataCache[uuid] = it }
                }

                plugin.logger.warning("Loading player data for $uuid")
                val config = YamlConfiguration()
                try {
                    playerFile.reader().use { reader -> config.load(reader) }
                } catch (e: Exception) {
                    when (e) {
                        is IOException, is InvalidConfigurationException -> {
                            plugin.logger.severe("CORRUPT DATA for $uuid (${e.message})! Backing up and creating new data.")
                            Bukkit.getPlayer(uuid)?.sendMessage(plugin.prefix.append(
                                Component.text("Failed to load player data. Please tell Dani so we can check what can be recovered!", NamedTextColor.RED)
                            ))
                            try {
                                val backupFile = plugin.dataFolder.resolve("players")
                                    .resolve("$uuid.corrupt-${System.currentTimeMillis()}.yaml")
                                moveFile(playerFile, backupFile)
                            } catch (backupException: IOException) {
                                plugin.logger.severe("Failed to backup corrupt file for $uuid: ${backupException.message}")
                                plugin.logger.warning(playerFile.readText())
                            }
                        }

                        else -> throw e
                    }
                }

                val playerData = loadPlayerDataFromConfig(uuid, config)
                plugin.logger.warning(
                    "Loaded player data for $uuid: ${playerData.getCompletedQuests(null).size} completed quests, " +
                        "${playerData.getReputationEntries().size} reputation entries, ${playerData.getNpcWalletCount()} npc wallets, " +
                        "${playerData.getShopItemStockCount()} shop item stocks"
                )
                return@supplyAsync playerData.also { dataCache[uuid] = it }
            }
            future.whenComplete { _, _ -> loadingCache.remove(uuid, future) }
            future
        }

        return loadingCache[uuid]!!
    }

    private fun savePlayerData(data: PlayerData) {
        var config: YamlConfiguration
        synchronized(data) {
            if (!data.isDirty) return // lost race condition
            data.isDirty = false
            config = data.toYaml()
        }

        val dataFolder = plugin.dataFolder.resolve("players")
        val playerFile = dataFolder.resolve("${data.uuid}.yaml")
        val tempFile = dataFolder.resolve("${data.uuid}.tmp.yaml")

        try {
            config.save(tempFile)
            moveFile(tempFile, playerFile)
        } catch (e: IOException){
            synchronized(data) { // re-set isDirty since saving failed
                if (!data.isDirty) data.isDirty = true
            }
            val player = Bukkit.getOfflinePlayer(data.uuid)
            plugin.logger.severe("Failed to save data for player ${player.name} (${player.uniqueId}): ${e.message}")
        }
    }

    private fun moveFile(from: File, to: File){
        try {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
