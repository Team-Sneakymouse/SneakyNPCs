package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.shop.BalanceService
import com.danidipp.sneakynpcs.shop.CurrencyGraphService
import com.danidipp.sneakynpcs.shop.NpcWalletService
import com.danidipp.sneakynpcs.shop.RequirementService
import com.danidipp.sneakynpcs.shop.ShopItemStockService
import com.danidipp.sneakynpcs.shop.ShopTransactionService
import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.events.MagicSpellsLoadedEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.atomic.AtomicBoolean

class SneakyNPCs : JavaPlugin() {
    var prefix = Component.join(JoinConfiguration.noSeparators(),
        Component.text("[", NamedTextColor.GRAY),
        Component.text("SneakyNPCs", NamedTextColor.GOLD),
        Component.text("]", NamedTextColor.GRAY),
        Component.space()
    )
    lateinit var configManager: ConfigManager
    lateinit var persistenceManager: PersistenceManager
    lateinit var currencyGraphService: CurrencyGraphService
    lateinit var balanceService: BalanceService
    lateinit var requirementService: RequirementService
    lateinit var npcWalletService: NpcWalletService
    lateinit var shopItemStockService: ShopItemStockService
    lateinit var inventoryTransactionLogger: InventoryTransactionLogger
    lateinit var shopTransactionService: ShopTransactionService
    val npcs = mutableMapOf<String, NPC>()
    private val configReloadInProgress = AtomicBoolean(false)

    override fun onLoad() {
        instance = this
    }
    override fun onEnable() {
        logger.info("Starting initialization")
        if(!Bukkit.getPluginManager().isPluginEnabled("MagicSpells")) {
            logger.severe("MagicSpells is not enabled. Aborting.")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }
        configManager = ConfigManager(this)
        persistenceManager = PersistenceManager(this)
        currencyGraphService = CurrencyGraphService(this)
        balanceService = BalanceService()
        requirementService = RequirementService()
        npcWalletService = NpcWalletService(currencyGraphService)
        shopItemStockService = ShopItemStockService()
        inventoryTransactionLogger = CoreProtectInventoryTransactionLogger.create(this) ?: NoOpInventoryTransactionLogger
        shopTransactionService = ShopTransactionService(
            currencyGraphService,
            balanceService,
            requirementService,
            npcWalletService,
            shopItemStockService,
            inventoryTransactionLogger,
        )

        //commands
        @field:Suppress("UnstableApiUsage")
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            it.registrar().register(SneakNPCCommand.root.build())
        }
        //events
        Bukkit.getServer().pluginManager.registerEvents(NPCGui.GuiListener, this)
        Bukkit.getServer().pluginManager.registerEvents(persistenceManager, this)
        Bukkit.getServer().pluginManager.registerEvents(MagicSpellsListener(this), this)

        logger.info("Plugin initialized. Waiting for MagicSpellsLoadedEvent before loading NPC configs.")

        // MagicSpells is a hard dependency and may already be fully loaded before this plugin enables.
        if (MagicSpells.isLoaded()) {
            logger.info("MagicSpells is already loaded. Triggering NPC config load immediately.")
            reloadNpcConfigs("onEnable")
        }
    }
    override fun onDisable() {
        logger.warning("Disabling SneakyNPCs")
        if (this::persistenceManager.isInitialized) {
            persistenceManager.onDisable()
        }
    }

    fun reloadNpcConfigs(trigger: String, callback: ((ConfigReloadResult) -> Unit)? = null) {
        if (!configReloadInProgress.compareAndSet(false, true)) {
            deliverReloadResult(
                ConfigReloadResult(
                loadedCount = npcs.size,
                keptCount = 0,
                errors = emptyList(),
                throwable = IllegalStateException("Config reload already in progress.")
                ),
                callback
            )
            return
        }

        logger.info("Reloading NPC configs (trigger=$trigger)")
        closeOpenNpcGuis()
        configManager.loadConfigs().handle { loadResult, throwable ->
            try {
                val resolvedThrowable = unwrapAsyncThrowable(throwable)
                if (resolvedThrowable != null) {
                    if (resolvedThrowable is ConfigValidationException) {
                        logger.warning(
                            "Reload failed validation (trigger=$trigger): ${resolvedThrowable.validationErrors.size} error groups"
                        )
                        deliverReloadResult(
                            ConfigReloadResult(
                                loadedCount = 0,
                                keptCount = npcs.size,
                                errors = resolvedThrowable.validationErrors,
                                throwable = null
                            ),
                            callback
                        )
                        return@handle
                    }

                    logger.severe("Failed to reload NPC configs (trigger=$trigger): ${resolvedThrowable.message}")
                    deliverReloadResult(
                        ConfigReloadResult(
                            loadedCount = npcs.size,
                            keptCount = 0,
                            errors = emptyList(),
                            throwable = resolvedThrowable
                        ),
                        callback
                    )
                    return@handle
                }

                val (configs, errors) = loadResult ?: Pair(
                    mutableMapOf<String, NPC>(),
                    emptyList<ConfigErrorInfo>()
                )
                val loadedCount = configs.size
                for (error in errors) {
                    configs[error.sourceId] = npcs[error.sourceId] ?: continue
                }

                npcs.clear()
                npcs.putAll(configs)
                val keptCount = npcs.size - loadedCount

                if (errors.isNotEmpty()) {
                    logger.warning("Encountered ${errors.size} config error groups while reloading (trigger=$trigger).")
                }
                logger.info(
                    "Reload complete (trigger=$trigger): loaded=$loadedCount, kept=$keptCount, total=${npcs.size}"
                )

                deliverReloadResult(
                    ConfigReloadResult(
                        loadedCount = loadedCount,
                        keptCount = keptCount,
                        errors = errors,
                        throwable = null
                    ),
                    callback
                )
            } finally {
                configReloadInProgress.set(false)
            }
        }
    }

    private fun deliverReloadResult(
        result: ConfigReloadResult,
        callback: ((ConfigReloadResult) -> Unit)?,
    ) {
        if (callback == null) return
        if (Bukkit.isPrimaryThread()) {
            callback(result)
            return
        }

        server.scheduler.runTask(this, Runnable {
            callback(result)
        })
    }

    private fun closeOpenNpcGuis() {
        val closeTask = Runnable {
            for (player in server.onlinePlayers) {
                val gui = player.openInventory.topInventory.holder as? NPCGui ?: continue
                gui.closeAllLevels()
            }
        }

        if (Bukkit.isPrimaryThread()) {
            closeTask.run()
            return
        }

        server.scheduler.runTask(this, closeTask)
    }

    data class ConfigReloadResult(
        val loadedCount: Int,
        val keptCount: Int,
        val errors: List<ConfigErrorInfo>,
        val throwable: Throwable?,
    )

    private class MagicSpellsListener(private val plugin: SneakyNPCs) : Listener {
        @EventHandler
        fun onMagicSpellsLoaded(event: MagicSpellsLoadedEvent) {
            if (event.plugin.name != "MagicSpells") return
            plugin.reloadNpcConfigs("MagicSpellsLoadedEvent")
        }
    }

    companion object {
        const val IDENTIFIER = "sneakynpcs"
        const val AUTHORS = "Team Sneakymouse"
        const val VERSION = "1.0"
        private lateinit var instance: SneakyNPCs

        fun getInstance(): SneakyNPCs {
            return instance
        }
    }
}
