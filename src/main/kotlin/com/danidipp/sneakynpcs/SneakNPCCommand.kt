@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs

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
        // NPC Screens 3 is the only NPC window (Rob, 2026-11-27); opengui3 stays as an alias
        root.then(OpenGUI3Command.createCommand("opengui"))
        root.then(OpenGUI3Command.createCommand("opengui3"))
        root.then(QuestCommand.createCommand())
        root.then(ReputationCommand.createCommand())
        root.then(WalletCommand.createCommand())
    }


}
