@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.commands.OpenGUICommand
import com.danidipp.sneakynpcs.commands.OpenGUI2Command
import com.danidipp.sneakynpcs.commands.OpenGUI3Command
import com.danidipp.sneakynpcs.commands.QuestCommand
import com.danidipp.sneakynpcs.commands.ReputationCommand
import com.danidipp.sneakynpcs.commands.ReloadCommand
import com.danidipp.sneakynpcs.commands.WalletCommand
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands


object SneakNPCCommand {
    var root: LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("sneakynpcs")
    init {
        root.requires {
            it.sender.hasPermission("sneakynpcs")
        }
        root.then(ReloadCommand.createCommand())
        // every NPC opens NPC Screens 3 now (Rob, 2026-11-27); the old chest menus stay on opengui1
        root.then(OpenGUI3Command.createCommand("opengui"))
        root.then(OpenGUICommand.createCommand())
        root.then(OpenGUI2Command.createCommand())
        root.then(OpenGUI3Command.createCommand("opengui3"))
        root.then(QuestCommand.createCommand())
        root.then(ReputationCommand.createCommand())
        root.then(WalletCommand.createCommand())
    }


}
