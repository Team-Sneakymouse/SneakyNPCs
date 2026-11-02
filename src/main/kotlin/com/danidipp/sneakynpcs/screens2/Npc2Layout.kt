package com.danidipp.sneakynpcs.screens2

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor

/**
 * Where things sit on an NPC Screens 2 screen, in GUI pixels of a 6-row chest (slot (c, r) has its cell at
 * 7 + 18c, 17 + 18r). The pack's art and fonts come from tools/npc2_pack.py; keep the two in step.
 */
internal object Npc2Layout {
    const val FRAME_X = -20
    const val FRAME_W = 216
    const val PORTRAIT_X = 8
    const val PORTRAIT_W = 52
    const val TITLE_X = 8
    /** the hub's reputation bar: REP_SEGMENTS glyphs 4px wide (advance 5) from REP_BAR_X */
    const val REP_BAR_X = 86
    const val REP_SEGMENTS = 16
    const val REP_FULL = ''
    const val REP_EMPTY = ''

    val FRAME_FONT: Key = Key.key("lom", "npc2/frame")
    val INK: TextColor = TextColor.color(0x462C18)
    val CREAM: TextColor = TextColor.color(0xF6ECCC)

    fun cellX(column: Int) = 7 + 18 * column
    fun cellY(row: Int) = 17 + 18 * row
    fun slot(column: Int, row: Int) = row * 9 + column
}

/** A district's frames, as the frame font's characters. */
internal enum class Npc2District(val hub: Char, val shop: Char) {
    GOATTOWN('', '');

    companion object {
        fun of(name: String?): Npc2District? = entries.firstOrNull { it.name.equals(name?.replace("_", ""), ignoreCase = true) }
    }
}

internal object Npc2Portraits {
    private val chars = mapOf("default" to '', "carl" to '')
    fun of(name: String?): Char = chars[name?.lowercase()] ?: chars.getValue("default")
}

/** Which of the pack's Cinzel text fonts a line uses: lom:npc2/<prefix><top>. */
internal enum class Npc2Font(val prefix: String, val tops: IntRange, val advances: IntArray) {
    /** 8 high; a font's ascent (13 - top) can't exceed its height, so no line starts above y 5 */
    TEXT("text_", 5..140, Npc2Glyphs.TEXT),
    /** 11 high, for the name plaque */
    NAME("name_", 2..20, Npc2Glyphs.NAME);

    fun key(top: Int): Key = Key.key("lom", "npc2/$prefix${top.coerceIn(tops)}")
}

/** The pack's text fonts' advances (generated into Npc2Glyphs), for centring and wrapping. */
internal object Npc2Text {
    /** Characters the pack's text fonts can't draw are swapped for '?'. */
    fun clean(text: String): String = text.map { if (it.code in 32..126) it else '?' }.joinToString("")

    /** the text fonts' -1px space char, put after each glyph to undo the client's 1px gap (npc2_pack.py KERN) */
    private const val KERN = ''

    fun kerned(text: String): String = buildString { text.forEach { append(it); if (it != ' ') append(KERN) } }

    fun width(text: String, font: Npc2Font = Npc2Font.TEXT): Int = clean(text).sumOf { font.advances[it.code - 32] }

    /** Splits text into lines of at most maxWidth pixels, breaking at spaces (or inside a word too long for a line). */
    fun wrap(text: String, maxWidth: Int, maxLines: Int = Int.MAX_VALUE): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in clean(text).split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (width(candidate) <= maxWidth) {
                line = candidate
                continue
            }
            if (line.isNotEmpty()) lines.add(line)
            line = word
            while (width(line) > maxWidth) {
                var cut = line.length
                while (cut > 1 && width(line.substring(0, cut)) > maxWidth) cut--
                lines.add(line.substring(0, cut))
                line = line.substring(cut)
            }
        }
        if (line.isNotEmpty()) lines.add(line)
        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        var last = kept.last()
        while (last.isNotEmpty() && width("$last...") > maxWidth) last = last.dropLast(1)
        kept[kept.lastIndex] = "$last..."
        return kept
    }

    /** The space font's characters that move the pen by px (powers of two, either way). */
    fun spaces(px: Int): String {
        val out = StringBuilder()
        var left = px
        val base = if (left >= 0) 0xE100 else 0xE120
        left = kotlin.math.abs(left)
        for (bit in 8 downTo 0) {
            while (left >= (1 shl bit)) {
                out.append((base + bit).toChar())
                left -= 1 shl bit
            }
        }
        return out.toString()
    }
}

/**
 * Builds an inventory title that draws the screen: the frame, the portrait and lines of text, each put where it
 * belongs by moving the pen with the space font.
 */
internal class Npc2Title {
    private val parts = mutableListOf<Component>()
    private var x = Npc2Layout.TITLE_X

    fun moveTo(target: Int) = apply {
        if (target == x) return@apply
        parts.add(Component.text(Npc2Text.spaces(target - x)).font(Npc2Layout.FRAME_FONT))
        x = target
    }

    fun glyph(left: Int, char: Char, width: Int) = apply {
        moveTo(left)
        parts.add(Component.text(char.toString()).font(Npc2Layout.FRAME_FONT))
        x += width + 1
    }

    fun text(left: Int, top: Int, text: String, color: TextColor = Npc2Layout.INK, font: Npc2Font = Npc2Font.TEXT) = apply {
        val clean = Npc2Text.clean(text)
        if (clean.isEmpty()) return@apply
        moveTo(left)
        parts.add(Component.text(Npc2Text.kerned(clean)).font(font.key(top)).color(color))
        x += Npc2Text.width(clean, font)
    }

    fun centered(centre: Int, top: Int, text: String, color: TextColor = Npc2Layout.INK, font: Npc2Font = Npc2Font.TEXT) =
        text(centre - Npc2Text.width(text, font) / 2, top, text, color, font)

    /** white, so the art isn't tinted by the title's default grey (0x404040) */
    fun build(): Component = Component.text().color(NamedTextColor.WHITE).append(parts).build()
}
