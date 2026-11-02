"""Generates the NPC Screens 2 client assets (frames, portrait, fonts) and a preview.

usage: python tools/npc2_pack.py <craftengine-resources>/resources/dev/resourcepack/assets/lom [preview-dir]

Coordinates are GUI pixels of a 6-row chest (176 x 222): slot (c, r) has its cell at (7 + 18c, 17 + 18r), 18 x 18.
The title's text baseline is y 13, so a glyph of ascent a is drawn from y 13 - a. Frames start at FRAME_X, FRAME_Y.
Keep the constants in step with Npc2Layout.kt.
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

FRAME_X, FRAME_Y, FRAME_W, FRAME_H = -20, -12, 216, 140  # ends at y 128, so the "Inventory" label sits below it
# one font per text line top, lom:npc2/text_<top>; a bitmap font's ascent (13 - top) can't exceed its height (8)
TEXT_TOPS = range(5, 141)

# Goat Town: pastoral wood, parchment and meadow green
WOOD = [(74, 46, 24), (104, 66, 34), (134, 88, 46), (166, 114, 62)]
PAPER = [(196, 170, 120), (222, 200, 152), (236, 220, 178), (246, 236, 204)]
INK = (70, 44, 24)
GREEN = [(58, 92, 40), (86, 128, 56), (122, 166, 78)]
CELL = [(120, 98, 70), (150, 126, 92), (176, 152, 114)]
GOLD = [(150, 104, 26), (212, 160, 48), (246, 214, 110)]


def cell(c, r):
    return 7 + 18 * c, 17 + 18 * r


class Canvas:
    def __init__(self):
        self.img = Image.new("RGBA", (FRAME_W, FRAME_H), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        self.rng = random.Random(7)

    def px(self, x, y):
        return x - FRAME_X, y - FRAME_Y

    def rect(self, x0, y0, x1, y1, fill):
        a, b = self.px(x0, y0)
        c, d = self.px(x1, y1)
        self.d.rectangle([a, b, c - 1, d - 1], fill=fill)

    def textured(self, x0, y0, x1, y1, shades, grain=True):
        """A fill of mixed shades: wood grain runs in rows, paper is speckled."""
        for y in range(y0, y1):
            row = self.rng.random()
            for x in range(x0, x1):
                if grain:
                    k = 1 + (1 if (self.rng.random() < 0.18 or row < 0.2) else 0) - (1 if self.rng.random() < 0.08 else 0)
                else:
                    k = 2 + (-1 if self.rng.random() < 0.12 else 0) + (1 if self.rng.random() < 0.05 else 0)
                self.img.putpixel(self.px(x, y), shades[max(0, min(len(shades) - 1, k))] + (255,))

    def bevel(self, x0, y0, x1, y1, light, dark):
        self.rect(x0, y0, x1, y0 + 1, light)
        self.rect(x0, y0, x0 + 1, y1, light)
        self.rect(x0, y1 - 1, x1, y1, dark)
        self.rect(x1 - 1, y0, x1, y1, dark)

    def panel(self, x0, y0, x1, y1, shades=PAPER):
        self.textured(x0, y0, x1, y1, shades, grain=False)
        self.bevel(x0, y0, x1, y1, shades[-1], shades[0])

    def plaque(self, x0, y0, x1, y1):
        self.textured(x0, y0, x1, y1, WOOD[1:], grain=True)
        self.bevel(x0, y0, x1, y1, WOOD[3], WOOD[0])
        for x, y in ((x0 + 2, y0 + 2), (x1 - 3, y0 + 2), (x0 + 2, y1 - 3), (x1 - 3, y1 - 3)):
            self.rect(x, y, x + 1, y + 1, GOLD[1])

    def slot(self, c, r):
        x, y = cell(c, r)
        self.rect(x, y, x + 18, y + 18, CELL[1])
        self.rect(x, y, x + 17, y + 1, CELL[0])
        self.rect(x, y, x + 1, y + 17, CELL[0])
        self.rect(x + 1, y + 17, x + 18, y + 18, PAPER[3])
        self.rect(x + 17, y + 1, x + 18, y + 18, PAPER[3])

    def horn(self, cx, cy, flip):
        """A curled goat horn ornament."""
        pts = [(0, 0), (1, -1), (2, -2), (3, -2), (4, -2), (5, -1), (6, 0), (6, 1), (5, 2), (4, 3), (3, 3), (2, 2), (2, 1), (3, 0), (4, 0)]
        for i, (x, y) in enumerate(pts):
            shade = GOLD[2] if i < 5 else GOLD[1] if i < 10 else GOLD[0]
            xx = cx - x if flip else cx + x
            self.rect(xx, cy + y, xx + 1, cy + y + 1, shade)
            self.rect(xx, cy + y + 1, xx + 1, cy + y + 2, GOLD[0])

    def frame(self):
        x0, y0, x1, y1 = FRAME_X, FRAME_Y, FRAME_X + FRAME_W, FRAME_Y + FRAME_H
        self.textured(x0, y0, x1, y1, WOOD, grain=True)
        self.bevel(x0, y0, x1, y1, WOOD[3], WOOD[0])
        self.bevel(x0 + 1, y0 + 1, x1 - 1, y1 - 1, WOOD[2], WOOD[0])
        # the page, down to just below the bottom slot row
        self.panel(x0 + 6, 15, x1 - 6, y1 - 2)
        # meadow hem along the top of the page
        for x in range(x0 + 7, x1 - 7):
            for y in (15, 16):
                self.img.putpixel(self.px(x, y), GREEN[(x + y) % 3] + (255,))
        # header plaque for the name (text top 5), horns on its ends
        self.plaque(x0 + 30, 0, x1 - 30, 15)
        self.horn(x0 + 22, 6, False)
        self.horn(x1 - 23, 6, True)

    def portrait_window(self):
        x0, y0 = cell(0, 0)
        self.rect(x0 - 1, y0 - 1, x0 + 55, y0 + 55, GOLD[0])
        self.rect(x0, y0, x0 + 54, y0 + 54, GOLD[1])
        self.rect(x0 + 1, y0 + 1, x0 + 53, y0 + 53, WOOD[0])


def hub():
    cv = Canvas()
    cv.frame()
    cv.portrait_window()
    # speech bubble, tail toward the portrait
    bx0, by0 = cell(3, 0)
    cv.panel(bx0 + 3, by0 + 1, 170, by0 + 53, PAPER[1:])
    for i in range(4):
        cv.rect(bx0 + 3 - i, by0 + 10 + i, bx0 + 4, by0 + 11 + i, PAPER[2])
    # three option plaques
    for i in range(3):
        x, y = cell(3, 3 + i)
        cv.plaque(x + 1, y + 1, 169, y + 17)
    # reputation panel
    x, y = cell(0, 3)
    cv.panel(x, y + 1, x + 54, y + 54, PAPER)
    cv.rect(x + 4, y + 13, x + 50, y + 14, WOOD[1])
    return cv.img


def shop():
    cv = Canvas()
    cv.frame()
    cv.portrait_window()
    for r in range(4):
        for c in range(3, 9):
            cv.slot(c, r)
    # speech strip, sell box, page row
    x, y = cell(3, 4)
    cv.panel(x + 1, y + 1, x + 71, y + 17, PAPER[1:])
    sx, sy = cell(7, 4)
    cv.rect(sx, sy, sx + 18, sy + 18, GOLD[0])
    cv.rect(sx + 1, sy + 1, sx + 17, sy + 17, GOLD[1])
    cv.slot(7, 4)
    cv.slot(3, 5)
    cv.slot(8, 5)
    px, py = cell(4, 5)
    cv.plaque(px + 1, py + 1, px + 71, py + 17)
    # purse panel and back plaque
    x, y = cell(0, 3)
    cv.panel(x, y + 1, x + 54, y + 36, PAPER)
    cv.slot(0, 5)
    cv.slot(2, 3)
    cv.slot(2, 4)
    return cv.img


def portrait():
    """A placeholder bust: a hooded figure against a Goat Town sky, 52 x 52."""
    img = Image.new("RGBA", (52, 52), (150, 196, 226, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 34, 51, 51], fill=GREEN[1])
    d.polygon([(0, 36), (14, 28), (30, 34), (51, 26), (51, 38), (0, 40)], fill=GREEN[2])
    d.ellipse([14, 6, 38, 32], fill=(92, 70, 52))
    d.ellipse([18, 12, 34, 30], fill=(214, 172, 132))
    d.rectangle([20, 19, 22, 21], fill=INK)
    d.rectangle([30, 19, 32, 21], fill=INK)
    d.rectangle([23, 25, 29, 26], fill=(150, 90, 70))
    d.polygon([(6, 51), (12, 34), (26, 30), (40, 34), (46, 51)], fill=(92, 70, 52))
    d.polygon([(20, 51), (26, 34), (32, 51)], fill=(214, 172, 132))
    return img


# vanilla ascii.png's characters, rows 2-7 (the rest left blank)
ASCII_ROWS = ["\u0000" * 16] * 2 + [
    " !\"#$%&'()*+,-./",
    "0123456789:;<=>?",
    "@ABCDEFGHIJKLMNO",
    "PQRSTUVWXYZ[\\]^_",
    "`abcdefghijklmno",
    "pqrstuvwxyz{|}~\u0000",
] + ["\u0000" * 16] * 8

SPACE_POS = {chr(0xE100 + i): 1 << i for i in range(9)}
SPACE_NEG = {chr(0xE120 + i): -(1 << i) for i in range(9)}
FRAMES = {"": "npc2/goattown_hub.png", "": "npc2/goattown_shop.png"}
PORTRAITS = {"": "npc2/portrait_default.png"}


def text_font_name(top):
    return f"text_{top}" if top >= 0 else f"text_n{-top}"


def write(out):
    tex = out / "textures/font/npc2"
    tex.mkdir(parents=True, exist_ok=True)
    hub().save(tex / "goattown_hub.png")
    shop().save(tex / "goattown_shop.png")
    portrait().save(tex / "portrait_default.png")
    fonts = out / "font/npc2"
    fonts.mkdir(parents=True, exist_ok=True)
    px, py = cell(0, 0)
    providers = [{"type": "space", "advances": {**SPACE_POS, **SPACE_NEG}}]
    providers += [{"type": "bitmap", "file": f"lom:font/{f}", "ascent": 13 - FRAME_Y, "height": FRAME_H, "chars": [c]} for c, f in FRAMES.items()]
    providers += [{"type": "bitmap", "file": f"lom:font/{f}", "ascent": 13 - (py + 1), "height": 52, "chars": [c]} for c, f in PORTRAITS.items()]
    (fonts / "frame.json").write_text(json.dumps({"providers": providers}, indent=1, ensure_ascii=True) + "\n")
    for top in TEXT_TOPS:
        font = {"providers": [{"type": "space", "advances": {" ": 4}}, {"type": "bitmap", "file": "minecraft:font/ascii.png", "ascent": 13 - top, "height": 8, "chars": ASCII_ROWS}]}
        (fonts / f"{text_font_name(top)}.json").write_text(json.dumps(font, ensure_ascii=True) + "\n")


def preview(out, dest):
    """The frames over a chest, for eyeballing: text isn't drawn (it's the client's font)."""
    dest.mkdir(parents=True, exist_ok=True)
    for name in ("goattown_hub", "goattown_shop"):
        frame = Image.open(out / f"textures/font/npc2/{name}.png")
        canvas = Image.new("RGBA", (FRAME_W + 40, FRAME_H + 120), (40, 40, 40, 255))
        d = ImageDraw.Draw(canvas)
        ox, oy = 40, 24
        d.rectangle([ox, oy, ox + 175, oy + 221], fill=(198, 198, 198, 255))
        canvas.alpha_composite(frame, (ox + FRAME_X, oy + FRAME_Y))
        if name.endswith("hub") or name.endswith("shop"):
            canvas.alpha_composite(Image.open(out / "textures/font/npc2/portrait_default.png"), (ox + 8, oy + 18))
        canvas = canvas.resize((canvas.width * 3, canvas.height * 3), Image.NEAREST)
        canvas.save(dest / f"{name}_preview.png")


if __name__ == "__main__":
    target = Path(sys.argv[1])
    write(target)
    if len(sys.argv) > 2:
        preview(target, Path(sys.argv[2]))
