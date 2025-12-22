package com.danidipp.sneakynpcs

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Level

class SneakyNPCs : JavaPlugin() {
    var prefix = Component.join(JoinConfiguration.noSeparators(),
        Component.text("[", NamedTextColor.GRAY),
        Component.text("SneakyNPCs", NamedTextColor.GOLD),
        Component.text("]", NamedTextColor.GRAY),
        Component.space()
    )
    lateinit var configManager: ConfigManager
    lateinit var persistenceManager: PersistenceManager
    val npcs = mutableMapOf<String, NPC>()

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

        //commands
        @field:Suppress("UnstableApiUsage")
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            it.registrar().register(SneakNPCCommand.root.build())
        }
        //events
        Bukkit.getServer().pluginManager.registerEvents(NPCGui.GuiListener, this)
        Bukkit.getServer().pluginManager.registerEvents(persistenceManager, this)

        logger.info("Plugin initialized. Asynchronously loading configs")
        configManager.loadConfigs().handle { (configs, errors), throwable ->
            if(throwable != null) {
                logger.log(Level.SEVERE, "Failed to load NPC configurations", throwable)
                return@handle
            }
            if (errors.isNotEmpty()) {
                logger.severe("Encountered ${errors.size} errors loading NPC configurations. Check previous logs for details.")
            }
            npcs.clear()
            npcs.putAll(configs)
            logger.info("Loaded ${npcs.size} NPC configurations")
        }
    }
    override fun onDisable() {
        logger.warning("Disabling SneakyNPCs")
        persistenceManager.onDisable()
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
