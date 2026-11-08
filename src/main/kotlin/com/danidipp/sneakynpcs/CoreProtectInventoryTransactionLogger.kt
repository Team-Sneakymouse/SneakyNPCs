package com.danidipp.sneakynpcs

import net.coreprotect.CoreProtect
import net.coreprotect.CoreProtectAPI
import net.coreprotect.config.ConfigHandler
import net.coreprotect.consumer.Queue
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class CoreProtectInventoryTransactionLogger private constructor(
    private val itemsDestroyField: Field,
    private val itemsCreateField: Field,
    private val getItemIdMethod: Method,
    private val queueItemTransactionMethod: Method,
) : InventoryTransactionLogger {

    override fun log(player: Player, removedItems: List<ItemStack>, addedItems: List<ItemStack>) {
        val removed = sanitize(removedItems)
        val added = sanitize(addedItems)
        if (removed.isEmpty() && added.isEmpty()) return

        val location = player.location.clone()
        val loggingItemId = buildLoggingItemId(player.name, location)

        runCatching {
            if (removed.isNotEmpty()) {
                appendItems(itemsDestroyField, loggingItemId, removed)
            }
            if (added.isNotEmpty()) {
                appendItems(itemsCreateField, loggingItemId, added)
            }

            val itemId = getItemIdMethod.invoke(null, loggingItemId) as Int
            val time = (System.currentTimeMillis() / 1000L).toInt() + 1
            queueItemTransactionMethod.invoke(null, player.name, location, time, 0, itemId)
        }.onFailure { throwable ->
            SneakyNPCs.getInstance().logger.warning("Failed to log item transaction to CoreProtect: ${throwable.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun appendItems(field: Field, loggingItemId: String, items: List<ItemStack>) {
        val map = field.get(null) as ConcurrentHashMap<String, MutableList<ItemStack>>
        val existing = map[loggingItemId]
        val updated = if (existing == null) {
            items.toMutableList()
        } else {
            existing.toMutableList().apply { addAll(items) }
        }
        map[loggingItemId] = updated
    }

    private fun sanitize(items: List<ItemStack>): List<ItemStack> {
        return items.asSequence()
            .filter { !it.type.isAir && it.amount > 0 }
            .flatMap { InventoryAuditItems.split(it, it.amount.toLong()).asSequence() }
            .toList()
    }

    private fun buildLoggingItemId(playerName: String, location: Location): String {
        return "${playerName.lowercase(Locale.ROOT)}.${location.blockX}.${location.blockY}.${location.blockZ}"
    }

    companion object {
        fun create(plugin: JavaPlugin): InventoryTransactionLogger? {
            val coreProtectPlugin = plugin.server.pluginManager.getPlugin("CoreProtect") as? CoreProtect ?: return null
            if (!coreProtectPlugin.isEnabled) return null

            return runCatching {
                val api = coreProtectPlugin.getAPI()
                val version = api.APIVersion()
                if (!api.isEnabled || version < 11) {
                    plugin.logger.warning(
                        "CoreProtect API v11+ is required for item transaction logging. Found v$version.",
                    )
                    return null
                }

                val itemsDestroyField = ConfigHandler::class.java.getField("itemsDestroy")
                val itemsCreateField = ConfigHandler::class.java.getField("itemsCreate")
                val getItemIdMethod = Queue::class.java.getDeclaredMethod("getItemId", String::class.java).apply {
                    isAccessible = true
                }
                val queueItemTransactionMethod = Queue::class.java.getDeclaredMethod(
                    "queueItemTransaction",
                    String::class.java,
                    Location::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                ).apply {
                    isAccessible = true
                }

                plugin.logger.info("CoreProtect item transaction logging enabled.")
                CoreProtectInventoryTransactionLogger(
                    itemsDestroyField = itemsDestroyField,
                    itemsCreateField = itemsCreateField,
                    getItemIdMethod = getItemIdMethod,
                    queueItemTransactionMethod = queueItemTransactionMethod,
                )
            }.getOrElse { throwable ->
                plugin.logger.warning("Failed to initialize CoreProtect item transaction logging: ${throwable.message}")
                null
            }
        }
    }
}
