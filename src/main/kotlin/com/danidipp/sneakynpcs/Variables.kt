package com.danidipp.sneakynpcs

import com.sneakyvariables.api.Scope
import com.sneakyvariables.api.SneakyVariables
import com.sneakyvariables.api.SneakyVariablesApi
import com.sneakyvariables.api.VariableDefinition
import com.sneakyvariables.api.VariableOwner
import org.bukkit.entity.Player

/** SneakyVariables helpers for shop bank balances, quest rewards and taxes. */
object Variables {
    fun defined(name: String): Boolean =
        SneakyVariables.api().definition(name) != null

    fun getNumber(player: Player, name: String): Double {
        val owner = owner(player, name) ?: return 0.0
        return owner.getNumber(name)
    }

    fun setNumber(player: Player, name: String, value: Double) {
        owner(player, name)?.setNumber(name, value)
    }

    private fun owner(player: Player, name: String): VariableOwner? {
        val vars: SneakyVariablesApi = SneakyVariables.api()
        val def: VariableDefinition = vars.definition(name) ?: return null
        return when (def.scope()) {
            Scope.PLAYER -> vars.player(player)
            Scope.CHARACTER -> vars.character(player)
            Scope.GLOBAL -> vars.global()
        }
    }
}
