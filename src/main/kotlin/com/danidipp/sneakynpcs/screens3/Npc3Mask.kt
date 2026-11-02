package com.danidipp.sneakynpcs.screens3

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.Channel
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Field
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Copied from SneakyRoamin's Mask (SneakyLeveling, SneakyKnives, SneakyWizards and SneakyRacials have it too). While the
 * NPC Screens 3 hub is open its art lies over the player's inventory, and the game draws the inventory's items over anything in the menu. So for as long as the menu
 * is open, the player's client is shown their inventory slots as the menu's own items ([show]) or empty; their real
 * items aren't touched, and when the menu closes the real ones are sent again (updateInventory).
 *
 * It's done where the server's packets leave for the player, a handler in their connection's pipeline. There's no API
 * for it, so it's the server's own classes by reflection (Paper runs Mojang's names); if that ever fails the console
 * says so once and the items just show.
 */
internal class Npc3Mask(private val plugin: Plugin) {

    private val open = ConcurrentHashMap<UUID, IntArray>()   // container id (ANY until known) and size
    private val shown = ConcurrentHashMap<UUID, Array<Any?>>()
    private var broken = false

    private var empty: Any? = null
    private lateinit var setContent: Class<*>
    private lateinit var setSlot: Class<*>
    private lateinit var setInventory: Class<*>
    private lateinit var contentId: MethodHandle
    private lateinit var contentState: MethodHandle
    private lateinit var contentItems: MethodHandle
    private lateinit var contentCarried: MethodHandle
    private lateinit var newContent: MethodHandle
    private lateinit var slotId: MethodHandle
    private lateinit var slotState: MethodHandle
    private lateinit var slotSlot: MethodHandle
    private lateinit var newSlot: MethodHandle
    private lateinit var invSlot: MethodHandle
    private lateinit var newInventory: MethodHandle
    private lateinit var asNms: MethodHandle

    private fun load() {
        if (empty != null) return
        val l = MethodHandles.publicLookup()
        val stack = Class.forName("net.minecraft.world.item.ItemStack")
        setContent = Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket")
        contentId = l.findVirtual(setContent, "containerId", MethodType.methodType(Int::class.javaPrimitiveType))
        contentState = l.findVirtual(setContent, "stateId", MethodType.methodType(Int::class.javaPrimitiveType))
        contentItems = l.findVirtual(setContent, "items", MethodType.methodType(List::class.java))
        contentCarried = l.findVirtual(setContent, "carriedItem", MethodType.methodType(stack))
        newContent = l.findConstructor(setContent, MethodType.methodType(Void.TYPE, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, List::class.java, stack))
        setSlot = Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket")
        slotId = l.findVirtual(setSlot, "getContainerId", MethodType.methodType(Int::class.javaPrimitiveType))
        slotState = l.findVirtual(setSlot, "getStateId", MethodType.methodType(Int::class.javaPrimitiveType))
        slotSlot = l.findVirtual(setSlot, "getSlot", MethodType.methodType(Int::class.javaPrimitiveType))
        newSlot = l.findConstructor(setSlot, MethodType.methodType(Void.TYPE, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, stack))
        setInventory = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket")
        invSlot = l.findVirtual(setInventory, "slot", MethodType.methodType(Int::class.javaPrimitiveType))
        newInventory = l.findConstructor(setInventory, MethodType.methodType(Void.TYPE, Int::class.javaPrimitiveType, stack))
        val craft = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
        asNms = l.findStatic(craft, "asNMSCopy", MethodType.methodType(stack, ItemStack::class.java))
        empty = stack.getField("EMPTY").get(null)
    }

    /** Before the menu opens for p (size: its slots): from now on their items go out as [show]n. */
    fun opening(p: Player, size: Int) {
        if (broken) return
        try {
            load()
            val ch = channel(p)
            if (ch.pipeline().get(HANDLER) == null) ch.pipeline().addBefore("packet_handler", HANDLER, Handler(p.uniqueId))
            open[p.uniqueId] = intArrayOf(ANY, size)
        } catch (ex: Throwable) {
            fail(ex)
        }
    }

    /** The menu is open: only it (not one opened after) is masked. */
    fun opened(p: Player) {
        val o = open[p.uniqueId] ?: return
        try {
            val sp = p.javaClass.getMethod("getHandle").invoke(p)
            val menu = sp.javaClass.getField("containerMenu").get(sp)
            o[0] = menu.javaClass.getField("containerId").getInt(menu)
        } catch (ex: ReflectiveOperationException) {
            fail(ex)
            open.remove(p.uniqueId)
        } catch (ex: RuntimeException) {
            fail(ex)
            open.remove(p.uniqueId)
        }
    }

    /** The menu's closed (true if it was masked: send the real items). Not while another is opening in its place. */
    fun closed(p: Player): Boolean {
        val o = open[p.uniqueId] ?: return false
        if (o[0] == ANY) return false
        open.remove(p.uniqueId)
        shown.remove(p.uniqueId)
        return true
    }

    fun forget(id: UUID) {
        open.remove(id)
        shown.remove(id)
    }

    /** What p sees in their 36 inventory slots (27 main rows top to bottom, then the hotbar); null = empty. */
    fun show(p: Player, items: Array<ItemStack?>) {
        if (broken) return
        try {
            load()
            shown[p.uniqueId] = Array(36) { i -> items.getOrNull(i)?.let { asNms.invoke(it) } }
        } catch (ex: Throwable) {
            fail(ex)
        }
    }

    private fun channel(p: Player): Channel {
        val sp = p.javaClass.getMethod("getHandle").invoke(p)
        val listener = sp.javaClass.getField("connection").get(sp)
        val connection = findField(listener.javaClass, "connection").get(listener)
        return connection.javaClass.getField("channel").get(connection) as Channel
    }

    private fun findField(c: Class<*>, name: String): Field {
        var k: Class<*>? = c
        while (k != null) {
            try {
                return k.getDeclaredField(name).also { it.isAccessible = true }
            } catch (_: NoSuchFieldException) {
                k = k.superclass
            }
        }
        throw NoSuchFieldException(name)
    }

    private fun fail(ex: Throwable) {
        if (broken) return
        broken = true
        plugin.logger.warning("Couldn't hide the inventory under the NPC Screens 3 hub ($ex); items will show over it.")
    }

    private fun at(fake: Array<Any?>?, i: Int): Any? = fake?.getOrNull(i) ?: empty

    private fun masked(who: UUID, o: IntArray, msg: Any): Any {
        val fake = shown[who]
        val c = msg.javaClass
        if (c == setContent) {
            val id = contentId.invoke(msg) as Int
            if (!matches(o, id)) return msg
            @Suppress("UNCHECKED_CAST")
            val items = ArrayList(contentItems.invoke(msg) as List<Any?>)
            for (i in o[1] until items.size) items[i] = at(fake, i - o[1])
            return newContent.invoke(id, contentState.invoke(msg) as Int, items, contentCarried.invoke(msg))
        }
        if (c == setSlot) {
            val id = slotId.invoke(msg) as Int
            val slot = slotSlot.invoke(msg) as Int
            if (!matches(o, id) || slot < o[1]) return msg
            return newSlot.invoke(id, slotState.invoke(msg) as Int, slot, at(fake, slot - o[1]))
        }
        if (c == setInventory) return newInventory.invoke(invSlot.invoke(msg) as Int, empty)
        return msg
    }

    private fun matches(o: IntArray, id: Int) = if (o[0] == ANY) id != 0 else id == o[0]

    private inner class Handler(private val who: UUID) : ChannelDuplexHandler() {
        override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
            var out = msg
            val o = open[who]
            if (o != null && !broken) {
                try {
                    out = masked(who, o, msg)
                } catch (ex: Throwable) {
                    fail(ex)
                }
            }
            super.write(ctx, out, promise)
        }
    }

    companion object {
        private const val HANDLER = "sneakynpcs_npc3_mask"
        private const val ANY = -1
    }
}
