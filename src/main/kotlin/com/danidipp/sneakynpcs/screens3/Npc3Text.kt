package com.danidipp.sneakynpcs.screens3

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor

/**
 * Draws an NPC Screens 3 screen through its title (SneakyRoamin's MenuText technique), with the fonts from
 * tools/npc3_art.py: `lom:npc3/art` holds the window art and portraits, `lom:npc3/line_<y>` puts text at container y,
 * `lom:npc3/name` is the header, and private-use spaces move the pen. x and y are container GUI px; the pen starts at 8.
 * Every text glyph is followed by KERN (-1), which undoes the client's 1 px gap after bitmap glyphs.
 */
internal class Npc3Text {
    private var title: Component = Component.empty()
    private var pen = 8

    /** Paints art (a window half or a portrait) at x; call it before any text so the text draws on top. */
    fun art(glyph: Char, x: Int = 0): Npc3Text {
        moveTo(x)
        title = title.append(Component.text(glyph).font(ART_FONT).color(WHITE))
        pen += widths[glyph.code] ?: 0
        return this
    }

    fun text(x: Int, y: Int, text: String, color: Int): Npc3Text {
        moveTo(x)
        val clean = clean(text)
        title = title.append(Component.text(kerned(clean)).font(line(y)).color(TextColor.color(color)))
        pen += width(clean)
        return this
    }

    fun right(x: Int, y: Int, text: String, color: Int) = text(x - width(text), y, text, color)

    fun center(x: Int, y: Int, text: String, color: Int) = text(x - width(text) / 2, y, text, color)

    /** The header line (name font) at y [Npc3Layout.NAME_Y]. */
    fun name(x: Int, text: String, color: Int): Npc3Text {
        moveTo(x)
        val clean = clean(text)
        title = title.append(Component.text(kerned(clean)).font(NAME_FONT).color(TextColor.color(color)))
        pen += nameWidth(clean)
        return this
    }

    fun build(): Component = title

    private fun moveTo(x: Int) {
        var delta = x - pen
        val shifts = StringBuilder()
        for (bit in 7 downTo 0) {
            val step = 1 shl bit
            while (delta >= step) {
                shifts.append((SHIFT_BASE + 8 + bit).toChar())
                delta -= step
            }
            while (delta <= -step) {
                shifts.append((SHIFT_BASE + bit).toChar())
                delta += step
            }
        }
        if (shifts.isNotEmpty()) title = title.append(Component.text(shifts.toString()).font(line(FIRST_LINE)))
        pen = x
    }

    companion object {
        private const val FIRST_LINE = 5
        private const val SHIFT_BASE = 0xE110
        private const val KERN = ''
        private val WHITE = TextColor.color(0xffffff)
        private val ART_FONT = Key.key("lom", "npc3/art")
        private val NAME_FONT = Key.key("lom", "npc3/name")
        const val BAR = ''

        private val widths = HashMap<Int, Int>()
        private val nameWidths = HashMap<Int, Int>()

        init {
            Npc3Text::class.java.getResourceAsStream("/npc3_font.txt")!!.bufferedReader().readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .forEach { line ->
                    val parts = line.split(' ')
                    if (parts[0] == "name") nameWidths[parts[1].toInt()] = parts[2].toInt()
                    else widths[parts[0].toInt()] = parts[1].toInt()
                }
        }

        /** Only the fonts' glyphs: printable ASCII and the bar; anything else becomes '?'. */
        private fun clean(text: String) = text.map { if (it.code in 32..126 || it == BAR) it else '?' }.joinToString("")

        private fun kerned(text: String) = buildString { text.forEach { append(it); if (it != ' ') append(KERN) } }

        fun width(text: String): Int = clean(text).sumOf { widths[it.code] ?: 0 }

        fun nameWidth(text: String): Int = clean(text).sumOf { nameWidths[it.code] ?: 0 }

        /** text cut to fit [max] px, ending in ".." when cut */
        fun fit(text: String, max: Int): String {
            if (width(text) <= max) return text
            var cut = text
            while (cut.isNotEmpty() && width("$cut..") > max) cut = cut.dropLast(1)
            return "${cut.trimEnd()}.."
        }

        private fun line(y: Int) = Key.key("lom", "npc3/line_${y.coerceIn(FIRST_LINE, 222)}")
    }
}

/** Layout numbers in GUI px; tools/npc3_art.py draws the art to the same ones. */
internal object Npc3Layout {
    const val NAME_X = 9
    const val RIGHT = 167
    const val PORTRAIT_X = 6
    const val OPTION_TEXT_X = 83
    const val PANEL_LEFT = 10
    const val PANEL_RIGHT = 166
    const val BAR_SEGMENTS = 52
    const val WALLET_X = 93
    const val WALLET_BAR_Y = 153
    const val WALLET_SEGMENTS = 24

    const val GOLD = 0xF6C85A
    const val CREAM = 0xF5ECD7
    const val MUTED = 0xA9BFA0
    const val BAR_EMPTY = 0x344A32
    const val WALLET_GREEN = 0x5FD35F

    const val HUB_TOP = ''
    const val HUB_BOTTOM = ''
    const val SHOP_TOP = ''
    const val SHOP_BOTTOM = ''

    /** in tools/npc3_art.py's PORTRAIT_ORDER */
    private val portraits = listOf("default", "carl")

    fun portrait(name: String): Char = (0xE210 + portraits.indexOf(name).coerceAtLeast(0)).toChar()

    fun slot(col: Int, row: Int) = row * 9 + col

    fun rowY(row: Int) = 18 + 18 * row
}
