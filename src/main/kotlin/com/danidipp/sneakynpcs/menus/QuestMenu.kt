package com.danidipp.sneakynpcs.menus

import com.danidipp.sneakynpcs.InventoryAuditItems
import com.danidipp.sneakynpcs.NPCGui
import com.danidipp.sneakynpcs.PlayerData
import com.danidipp.sneakynpcs.SneakyNPCs
import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.util.magicitems.MagicItem
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

data class NPCQuest(
    val quest: String,
    val dialogue: String,
    val hint: NPCQuestHint?,
    val completion: NPCQuestHint?,
    val items: List<NPCQuestItem>,
    val rewards: List<NPCQuestReward>
)
data class NPCQuestHint(
    val title: String,
    val body: List<String>
)
data class NPCQuestItem(
    val magicItem: MagicItem,
    val amount: Int
)
private data class QuestItemRequirement(
    val magicItem: MagicItem,
    val magicItemId: String,
    val amount: Int,
)
sealed interface NPCQuestReward
data class NPCQuestItemReward(
    val magicItem: MagicItem,
    val amount: Int,
) : NPCQuestReward
data class NPCQuestVariableReward(
    val variableName: String,
    val operation: NPCQuestVariableOperation,
    val amount: Double,
) : NPCQuestReward
data class NPCQuestReputationReward(
    val amount: Double,
) : NPCQuestReward
data class NPCQuestCommandReward(
    val command: String,
    val asConsole: Boolean,
) : NPCQuestReward
enum class NPCQuestVariableOperation {
    SET,
    ADD,
    SUBTRACT,
}

internal fun applyQuestVariableOperation(
    current: Double,
    operation: NPCQuestVariableOperation,
    amount: Double,
): Double = when (operation) {
    NPCQuestVariableOperation.SET -> amount
    NPCQuestVariableOperation.ADD -> current + amount
    NPCQuestVariableOperation.SUBTRACT -> current - amount
}

internal val questItemDisplaySlots = (4..8) + (13..17)
internal val questConfirmButtonSlots = (23..25)

class QuestMenu(val quests: List<NPCQuest>) : NPCMenu(MenuType.QUEST) {
    val plugin = SneakyNPCs.getInstance()
    private val miniMessage = MiniMessage.miniMessage()
    private val magicItemKey = NamespacedKey("magicspells", "magicitem")
    private val placeholderPattern = Regex("%[^\\s]+%")

    override fun open(gui: NPCGui, player: Player, playerData: PlayerData?) {
        val resolvedData = playerData ?: plugin.persistenceManager.dataCache[player.uniqueId]
        if (resolvedData == null) {
            plugin.logger.warning("No player data found for ${player.name} (${player.uniqueId}) when opening quest menu")
            player.sendMessage(plugin.prefix.append(Component.text("Failed to fetch your data. Please tell Dani.", NamedTextColor.RED)))
            return
        }

        val inv = gui.inventory
        val npc = gui.npc
        val hideTooltip = shouldHideTooltip(player)

        val completedQuests = resolvedData.getCompletedQuests(npc.id)
        val currentQuest = quests.firstOrNull{ !completedQuests.contains(it.quest) }
        if (currentQuest == null) {
            plugin.logger.severe("Failed to find active quest")
            return
        }

        inv.clear()
        val isCompletable = questCompletable(player, currentQuest)
        inv.setItem(0, makeItem(npc.guiModelKey, "alt", hideTooltip))
        inv.setItem(1, makeItem(npc.questModelKey, currentQuest.dialogue, hideTooltip))
        inv.setItem(53, makeItem("lom:npcs/questbox", if (isCompletable) "complete" else "incomplete", hideTooltip))
        val tooltip = if (isCompletable && currentQuest.completion != null) currentQuest.completion else currentQuest.hint
        tooltip?.let {
            val item = buildTooltipItem(it)
            for (slot in questConfirmButtonSlots)
                inv.setItem(slot, item.clone())
        }

        for ((item, slot) in currentQuest.items.zip(questItemDisplaySlots)) {
            inv.setItem(slot, item.magicItem.itemStack.clone().apply { amount = item.amount })
        }

    }

    override fun onClick(gui: NPCGui, event: InventoryClickEvent) {
        val playerData = plugin.persistenceManager.dataCache[event.whoClicked.uniqueId]
        if (playerData == null) {
            plugin.logger.warning("No player data found for ${event.whoClicked.name} (${event.whoClicked.uniqueId}) when clicking GUI page ${this.javaClass.simpleName}")
            event.whoClicked.sendMessage(plugin.prefix.append(Component.text("Failed to fetch your data. Please tell Dani.", NamedTextColor.RED)))
            return
        }

        val completedQuests = playerData.getCompletedQuests(gui.npc.id)
        val currentQuest = quests.firstOrNull{ !completedQuests.contains(it.quest) }
        if (currentQuest == null) {
            plugin.logger.severe("Failed to find active quest")
            return
        }

        val player = event.whoClicked as? Player ?: run {
            event.whoClicked.sendMessage(plugin.prefix.append(Component.text("Must be a player to click", NamedTextColor.RED)))
            return
        }

        if (event.slot in questConfirmButtonSlots) {
            if (!questCompletable(player, currentQuest)) return
            completeQuest(player, playerData, gui.npc.id, currentQuest)
            gui.close()
        }
    }

    private fun buildTooltipItem(tooltip: NPCQuestHint) = makeItem("lom:invisible").apply {
        editMeta { meta ->
            meta.displayName(formatTooltipText(tooltip.title))
            meta.lore(tooltip.body.map(::formatTooltipText))
        }
    }

    private fun formatTooltipText(input: String): Component {
        return miniMessage.deserialize(input)
            .colorIfAbsent(NamedTextColor.WHITE)
            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
    }

    fun questCompletable(player: Player, quest: NPCQuest): Boolean {
        if (quest.items.isEmpty()) {
            player.sendMessage("Quest has no items - uncompletable")
            return false // End of quest chain
        }
        val requirements = tallyQuestItemRequirements(quest) ?: return false

        for (requirement in requirements) {
            val ownedAmount = player.inventory.sumOf { stack ->
                if (magicItemId(stack) == requirement.magicItemId) stack?.amount ?: 0 else 0
            }
            if (ownedAmount < requirement.amount) {
                val itemName = PlainTextComponentSerializer.plainText().serialize(requirement.magicItem.itemStack.displayName())
                plugin.logger.warning("Player ${player.name} doesn't have enough ${itemName}: $ownedAmount/${requirement.amount}")
                return false
            }
        }
        return true
    }

    // Pre-check: quest is current quest, quest is completable
    private fun completeQuest(player: Player, playerData: PlayerData, npcId: String, quest: NPCQuest) {
        val missingRewardVariable = quest.rewards
            .filterIsInstance<NPCQuestVariableReward>()
            .firstOrNull { MagicSpells.getVariableManager().getVariable(it.variableName) == null }
        if (missingRewardVariable != null) {
            plugin.logger.warning(
                "Failed to complete quest '${quest.quest}' for player '${player.name}': " +
                    "reward variable '${missingRewardVariable.variableName}' is not configured"
            )
            player.sendMessage(plugin.prefix.append(Component.text("Quest rewards are not configured correctly. Please tell Dani.", NamedTextColor.RED)))
            return
        }

        val requirements = tallyQuestItemRequirements(quest) ?: return

        // Remove required amounts from player's inventory
        for (requirement in requirements) {
            var remaining = requirement.amount
            val inv = player.inventory

            for (slot in 0 until inv.size) {
                if (remaining <= 0) break
                val stack = inv.getItem(slot) ?: continue
                if (magicItemId(stack) != requirement.magicItemId) continue

                val take = minOf(stack.amount, remaining)
                if (stack.amount > take) {
                    val newStack = stack.clone()
                    newStack.amount = stack.amount - take
                    inv.setItem(slot, newStack)
                } else {
                    inv.setItem(slot, null)
                }
                remaining -= take
            }
        }

        applyQuestRewards(player, playerData, quest.rewards, npcId)

        plugin.inventoryTransactionLogger.log(
            player = player,
            removedItems = requirements.flatMap { requirement ->
                InventoryAuditItems.split(requirement.magicItem.itemStack, requirement.amount.toLong())
            },
            addedItems = quest.rewards.filterIsInstance<NPCQuestItemReward>().flatMap { reward ->
                InventoryAuditItems.split(reward.magicItem.itemStack, reward.amount.toLong())
            },
        )

        playerData.completeQuest(quest.quest)
        player.sendMessage(plugin.prefix.append(Component.text("Quest completed!", NamedTextColor.GREEN)))
    }

    private fun applyQuestRewards(player: Player, playerData: PlayerData, rewards: List<NPCQuestReward>, npcId: String) {
        for (reward in rewards) {
            when (reward) {
                is NPCQuestItemReward -> deliverItemReward(player, reward)
                is NPCQuestReputationReward -> playerData.addReputation(npcId, reward.amount)
                is NPCQuestVariableReward -> applyVariableReward(player, reward)
                is NPCQuestCommandReward -> executeCommandReward(player, reward)
            }
        }
    }

    private fun executeCommandReward(player: Player, reward: NPCQuestCommandReward) {
        val placeholderApiEnabled = plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")
        val command = if (placeholderApiEnabled) {
            PlaceholderAPI.setPlaceholders(player, reward.command)
        } else {
            if (placeholderPattern.containsMatchIn(reward.command)) {
                plugin.logger.warning(
                    "PlaceholderAPI is unavailable while executing quest reward command '${reward.command}' " +
                        "for player '${player.name}'"
                )
                player.sendMessage(
                    plugin.prefix.append(
                        Component.text(
                            "Quest reward command placeholders could not be parsed because PlaceholderAPI is unavailable. Please tell Dani.",
                            NamedTextColor.RED,
                        )
                    )
                )
            }
            reward.command
        }

        val sender: CommandSender = if (reward.asConsole) Bukkit.getConsoleSender() else player
        val senderLabel = if (reward.asConsole) "console" else "player"
        val dispatched = Bukkit.dispatchCommand(sender, command)
        if (!dispatched) {
            plugin.logger.warning(
                "Failed to dispatch quest reward command '$command' as $senderLabel for player '${player.name}'"
            )
            player.sendMessage(
                plugin.prefix.append(
                    Component.text("Failed to run a quest reward command. Please tell Dani.", NamedTextColor.RED)
                )
            )
        }
    }

    private fun applyVariableReward(player: Player, reward: NPCQuestVariableReward) {
        val variableManager = MagicSpells.getVariableManager()
        val current = variableManager.getValue(reward.variableName, player)
        val next = applyQuestVariableOperation(current, reward.operation, reward.amount)
        variableManager.set(reward.variableName, player, next)
    }

    private fun deliverItemReward(player: Player, reward: NPCQuestItemReward) {
        for (stack in splitRewardItem(reward.magicItem.itemStack, reward.amount)) {
            val leftovers = player.inventory.addItem(stack)
            for (leftover in leftovers.values) {
                player.world.dropItemNaturally(player.location, leftover)
            }
        }
    }

    private fun splitRewardItem(template: ItemStack, amount: Int): List<ItemStack> {
        if (amount <= 0 || template.type.isAir) return emptyList()
        val maxStackSize = template.maxStackSize.coerceAtLeast(1)
        val stacks = mutableListOf<ItemStack>()
        var remaining = amount
        while (remaining > 0) {
            val stackAmount = minOf(maxStackSize, remaining)
            stacks += template.clone().apply { this.amount = stackAmount }
            remaining -= stackAmount
        }
        return stacks
    }

    private fun tallyQuestItemRequirements(quest: NPCQuest): List<QuestItemRequirement>? {
        val requirements = linkedMapOf<String, QuestItemRequirement>()
        for (item in quest.items) {
            val magicItemId = magicItemId(item.magicItem.itemStack) ?: run {
                plugin.logger.warning(
                    "Quest '${quest.quest}' requires a MagicItem whose template lacks " +
                        "'magicspells:magicitem' persistent data"
                )
                return null
            }
            val current = requirements[magicItemId]
            requirements[magicItemId] = if (current == null) {
                QuestItemRequirement(item.magicItem, magicItemId, item.amount)
            } else {
                current.copy(amount = current.amount + item.amount)
            }
        }
        return requirements.values.toList()
    }

    private fun magicItemId(stack: ItemStack?): String? {
        return stack?.itemMeta
            ?.persistentDataContainer
            ?.get(magicItemKey, PersistentDataType.STRING)
            ?.takeIf { it.isNotBlank() }
    }

}
