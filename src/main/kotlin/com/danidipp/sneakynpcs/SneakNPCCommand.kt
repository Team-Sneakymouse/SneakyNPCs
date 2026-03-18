@file:Suppress("UnstableApiUsage")
package com.danidipp.sneakynpcs

import com.danidipp.sneakynpcs.commands.OpenGUICommand
import com.danidipp.sneakynpcs.commands.QuestCommand
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
        root.then(OpenGUICommand.createCommand())
        root.then(QuestCommand.createCommand())
        root.then(WalletCommand.createCommand())
    }


}
