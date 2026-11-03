"""Art and fonts for NPC Screens 3 (/sneakynpcs opengui3) into a craftengine-resources checkout.

    uv run --no-project --with pillow python tools/npc3_art.py <craftengine-resources>/resources/dev/resourcepack/assets/lom [preview.png]

Same technique as SneakyRoamin's /roamin menu: each screen's art is two title glyphs at 4x (font `lom:npc3/art`), the
chest part (176x128 GUI px) and the player's inventory part below it (176x100), so the whole window is drawn smooth
and the items sit in real sockets. On the hub the plugin hides the player's inventory with packets (Npc3Mask) and a
ledger panel shows there; on the shop the player's own items show in the drawn sockets, so they can sell.

Live text goes on top through `lom:npc3/line_<y>` (8 px, Liberation Sans Narrow Bold with a baked shadow) and
`lom:npc3/name` (11 px, the header). Their advances go to src/main/resources/npc3_font.txt.

Layout numbers are GUI px and live here and in screens3/Npc3Layout.kt; change them together.
Slot (col c, row r) holds its item at x = 8 + 18c, y = 18 + 18r; the hidden inventory's rows at y = 139 + 18r, hotbar 197.
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont, ImageOps

sys.path.insert(0, str(Path(__file__).resolve().parent))
from npc2_render import portrait_from_skin  # noqa: E402

ROOT = Path(__file__).resolve().parent
ART = ROOT / "npc2_art"
WIDTHS = ROOT.parent / "src/main/resources/npc3_font.txt"
TEXT_TTF = "C:/Windows/Fonts/LiberationSansNarrow-Bold.ttf"
S = 4  # texels per GUI px

# ------------------------------------------------------------------ palette (Goat Town: pine green and brass)
GOATTOWN = dict(
    cloth_dark=(9, 20, 15), cloth=(28, 58, 41), panel=(13, 30, 22), panel_top=(22, 46, 34), row=(17, 38, 28),
    row_top=(26, 54, 39), socket=(6, 14, 10), shadow=(2, 6, 4), rule=(64, 84, 46),
    metal=(226, 178, 78), metal_light=(255, 230, 156), metal_dark=(128, 86, 30),
    close=(128, 30, 24), close_light=(196, 64, 48), sell=(70, 140, 70),
)
GOLD = (246, 200, 90)
CREAM = (245, 236, 215)
MUTED = (169, 191, 160)

# ------------------------------------------------------------------ layout (GUI px; keep in step with Npc3Layout.kt)
TOP_H, BOTTOM_H, W = 128, 100, 176
PORTRAIT = (6, 20)             # 52 x 52 portrait glyph
OPTION_X = (60, 171)           # option plaques, rows 0-3
LEDGER = (5, 138, 171, 192)    # purse / till / memory over the hidden inventory
REP_TRACK = (9, 106, 167, 111.5)       # under the 52-segment reputation bar (text line 104)
WALLET_TRACK = (92, 155, 166, 160.5)   # under the NPC's 24-segment wallet bar (text line 153)


def slot_xy(c, r):
    return 8 + 18 * c, 18 + 18 * r


def bottom_xy(c, r):
    """The hidden/real inventory slot: main rows 0-2, hotbar row 3."""
    return 8 + 18 * c, (139 + 18 * r) if r < 3 else 197


# ------------------------------------------------------------------ drawing helpers (all boxes in GUI px)
def g(box):
    return [int(round(v * S)) for v in box]


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def cloth(size, pal, seed):
    """Brushed cloth: a soft light from the top centre, a faint diagonal weave and fine grain."""
    w, h = size
    im = Image.new("RGBA", size)
    px = im.load()
    rnd = random.Random(seed)
    dark, mid = pal["cloth_dark"], pal["cloth"]
    for y in range(h):
        for x in range(w):
            dx, dy = (x - w / 2) / (w / 2), (y - h * 0.25) / h
            t = max(0.0, 1 - math.sqrt(dx * dx * 0.55 + dy * dy * 1.6))
            weave = 4 if ((x + y) // 12 + (x - y) // 12) % 2 == 0 else 0
            n = rnd.randint(-3, 3)
            c = lerp(dark, mid, t)
            px[x, y] = (c[0] + n, c[1] + weave + n, c[2] + n, 255)
    return im


def shadow(im, box, radius, blur=10, alpha=170, pal=GOATTOWN):
    sh = Image.new("RGBA", im.size, pal["shadow"] + (0,))
    m = Image.new("L", im.size, 0)
    x0, y0, x1, y1 = g(box)
    ImageDraw.Draw(m).rounded_rectangle([x0 + 3, y0 + 6, x1 + 3, y1 + 6], radius * S, fill=alpha)
    sh.putalpha(m.filter(ImageFilter.GaussianBlur(blur)))
    im.alpha_composite(sh)


def gradient_fill(im, box, radius, top, bottom):
    x0, y0, x1, y1 = g(box)
    fill = Image.new("RGBA", (x1 - x0, y1 - y0))
    d = ImageDraw.Draw(fill)
    for y in range(y1 - y0):
        d.line([(0, y), (x1 - x0, y)], fill=lerp(top, bottom, y / max(1, y1 - y0 - 1)) + (255,))
    m = Image.new("L", fill.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, fill.width - 1, fill.height - 1], radius * S, fill=255)
    im.paste(fill, (x0, y0), m)


def metal_ring(im, box, radius, width=1.0, pal=GOATTOWN):
    """A brass rim: dark outer line, the metal, a bright inner highlight along the top."""
    x0, y0, x1, y1 = g(box)
    d = ImageDraw.Draw(im)
    w = int(width * S)
    r = radius * S
    d.rounded_rectangle([x0, y0, x1, y1], r, outline=pal["metal_dark"], width=w + 2)
    d.rounded_rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], r, outline=pal["metal"], width=w)
    hl = Image.new("RGBA", im.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    hd.rounded_rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], r, outline=pal["metal_light"] + (255,), width=max(2, w // 2))
    fade = Image.new("L", im.size, 0)
    fd = ImageDraw.Draw(fade)
    for y in range(y0, y1 + 1):
        fd.line([(x0, y), (x1, y)], fill=int(220 * max(0.0, 1 - (y - y0) / max(1, (y1 - y0) * 0.5))))
    hl.putalpha(ImageChops.multiply(hl.getchannel("A"), fade))
    im.alpha_composite(hl)



def socket(im, x, y, pal=GOATTOWN, kind="plain"):
    """The box an item sits in: (x, y) is the item's top-left GUI px; the rim is 1 px around it."""
    box = (x - 1, y - 1, x + 17, y + 17)
    x0, y0, x1, y1 = g(box)
    d = ImageDraw.Draw(im)
    fill = {"close": pal["close"], "sell": (16, 40, 22)}.get(kind, pal["socket"])
    d.rounded_rectangle([x0, y0, x1, y1], 2 * S, fill=fill)
    # inner shadow along the top and left
    inner = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(inner).rounded_rectangle([x0 + 2, y0 + 2, x1 + 6, y1 + 6], 2 * S, outline=(0, 0, 0, 150), width=6)
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([x0, y0, x1, y1], 2 * S, fill=255)
    inner.putalpha(ImageChops.multiply(inner.getchannel("A"), m.filter(ImageFilter.GaussianBlur(0))))
    im.alpha_composite(inner.filter(ImageFilter.GaussianBlur(2)))
    if kind == "sell":
        glow = Image.new("RGBA", im.size, (120, 220, 120, 0))
        gm = Image.new("L", im.size, 0)
        ImageDraw.Draw(gm).rounded_rectangle([x0 - 6, y0 - 6, x1 + 6, y1 + 6], 3 * S, outline=150, width=8)
        glow.putalpha(gm.filter(ImageFilter.GaussianBlur(6)))
        im.alpha_composite(glow)
    metal_ring(im, box, 2, 0.75, pal)


def chevron(im, x, y, left, pal=GOATTOWN, shaft=False):
    """An arrow drawn in the socket at item (x, y); with a shaft it's the back arrow."""
    d = ImageDraw.Draw(im)
    cx, cy = (x + 8) * S, (y + 8) * S
    k = -1 if left else 1
    if shaft:
        cx += 6 * k
        d.line([(cx - 32 * k, cy), (cx + 8 * k, cy)], fill=pal["metal_dark"], width=12)
    pts = [(cx - 10 * k, cy - 18), (cx + 10 * k, cy), (cx - 10 * k, cy + 18)]
    d.line(pts, fill=pal["metal_dark"], width=12, joint="curve")
    if shaft:
        d.line([(cx - 30 * k, cy), (cx + 8 * k, cy)], fill=pal["metal"], width=7)
    d.line(pts, fill=pal["metal"], width=7, joint="curve")


def cross(im, x, y):
    d = ImageDraw.Draw(im)
    cx, cy = (x + 8) * S, (y + 8) * S
    for a, b in (((-14, -14), (14, 14)), ((-14, 14), (14, -14))):
        d.line([(cx + a[0], cy + a[1]), (cx + b[0], cy + b[1])], fill=(80, 10, 8), width=13)
        d.line([(cx + a[0], cy + a[1]), (cx + b[0], cy + b[1])], fill=(255, 238, 220), width=8)


def coin_mark(im, x, y, pal=GOATTOWN):
    """A faint coin in the sell box, so it reads as 'put things here'."""
    d = ImageDraw.Draw(im)
    cx, cy = (x + 8) * S, (y + 8) * S
    d.ellipse([cx - 20, cy - 20, cx + 20, cy + 20], outline=(70, 120, 70), width=5)
    d.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], outline=(54, 96, 56), width=3)


def stud(im, cx, cy, r=2.2, pal=GOATTOWN):
    """A brass diamond stud."""
    d = ImageDraw.Draw(im)
    x, y, rr = cx * S, cy * S, r * S
    d.polygon([(x, y - rr - 2), (x + rr + 2, y), (x, y + rr + 2), (x - rr - 2, y)], fill=pal["metal_dark"])
    d.polygon([(x, y - rr), (x + rr, y), (x, y + rr), (x - rr, y)], fill=pal["metal"])
    d.polygon([(x, y - rr), (x + rr * 0.2, y - rr * 0.2), (x - rr * 0.5, y)], fill=pal["metal_light"])


def rule(im, x0, x1, y, pal=GOATTOWN):
    """A thin brass rule with a stud in the middle."""
    d = ImageDraw.Draw(im)
    d.line([(x0 * S, y * S + 1), (x1 * S, y * S + 1)], fill=pal["metal_dark"], width=4)
    d.line([(x0 * S, y * S), (x1 * S, y * S)], fill=pal["metal"], width=2)
    stud(im, (x0 + x1) / 2, y + 0.25, 1.6, pal)


def window(pal, seed):
    """The window's base, drawn once at full height and cut at TOP_H, so the top and bottom art are one board."""
    w, hh = W * S, (TOP_H + BOTTOM_H) * S
    im = cloth((w, hh), pal, seed)
    d = ImageDraw.Draw(im)
    box = [0, 0, w - 1, hh - 1]
    d.rounded_rectangle(box, 16, outline=pal["metal_dark"], width=10)
    d.rounded_rectangle([box[0] + 3, box[1] + 3, box[2] - 3, box[3] - 3], 14, outline=pal["metal"], width=4)
    d.rounded_rectangle([box[0] + 7, box[1] + 7, box[2] - 7, box[3] - 7], 10, outline=pal["metal_light"] + (90,), width=1)
    return im


def split(im):
    return im.crop((0, 0, W * S, TOP_H * S)), im.crop((0, TOP_H * S, W * S, (TOP_H + BOTTOM_H) * S))


LABEL_GREY = (64, 64, 64)  # the client's 0x404040 "Inventory" label at x 8, y 128


def strap(im, pal):
    """An iron strap across the board at the seam. Its face is the label's own grey, so the client's
    "Inventory" label (drawn over the title, no shadow) disappears into it."""
    d = ImageDraw.Draw(im)
    x0, x1 = int(3.5 * S), int((W - 3.5) * S)  # inside the brass frame, which runs on unbroken
    d.rectangle([x0, int(126.5 * S), x1, 138 * S - 1], fill=pal["shadow"])
    d.rectangle([x0, 127 * S, x1, int(137.5 * S) - 1], fill=LABEL_GREY)
    d.line([(x0, 127 * S), (x1, 127 * S)], fill=(98, 98, 94), width=2)
    d.line([(x0, int(137.5 * S) - 2), (x1, int(137.5 * S) - 2)], fill=(40, 40, 40), width=2)
    for x in (66, 110, 154):
        stud(im, x, 132.5, 1.4, pal)


def track(im, box, pal):
    """The recessed groove a segment bar sits in, with a thin brass rim."""
    x0, y0, x1, y1 = g(box)
    ImageDraw.Draw(im).rounded_rectangle([x0, y0, x1, y1], 4, fill=pal["socket"])
    metal_ring(im, box, 1, 0.5, pal)


def header(im, pal):
    """Name band at the top: a darker strip and a rule under it."""
    sh = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rectangle([12, 10, W * S - 12, 15 * S], fill=pal["shadow"] + (110,))
    im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(6)))
    rule(im, 6, W - 6, 16, pal)
    for x in (4.5, W - 4.5):
        stud(im, x, 5, 1.8, pal)


# ------------------------------------------------------------------ screens
def hub(pal=GOATTOWN):
    """Everything sits straight on the board: sockets and text, no panels inside panels (Rob, 2026-11-02)."""
    im = window(pal, 11)
    header(im, pal)
    # portrait: one brass frame, the standing under it
    px, py = PORTRAIT
    shadow(im, (px - 1, py - 1, px + 53, py + 53), 2, pal=pal)
    metal_ring(im, (px - 1.5, py - 1.5, px + 53.5, py + 53.5), 2, 0.75, pal)
    # options, rows 0-3: an icon socket in column 3, label and hint beside it
    for r in range(4):
        socket(im, *slot_xy(3, r), pal)
    rule(im, 6, W - 6, 90, pal)
    track(im, REP_TRACK, pal)
    strap(im, pal)
    track(im, WALLET_TRACK, pal)
    # ledger over the hidden inventory: purse | wallet, memory line under them
    d = ImageDraw.Draw(im)
    mid = (LEDGER[0] + LEDGER[2]) / 2
    d.line([(mid * S, (LEDGER[1] + 4) * S), (mid * S, (LEDGER[1] + 37) * S)], fill=pal["rule"], width=2)
    x, y = bottom_xy(4, 3)
    socket(im, x, y, pal, kind="close")
    cross(im, x, y)
    for c in (1, 7):
        stud(im, 8 + 18 * c + 8, y + 8, 2.4, pal)
    return split(im)


def shop(pal=GOATTOWN):
    im = window(pal, 13)
    header(im, pal)
    for r in range(4):
        for c in range(9):
            socket(im, *slot_xy(c, r), pal)
    for c, kind in ((0, "back"), (2, "prev"), (4, "next")):
        x, y = slot_xy(c, 5)
        socket(im, x, y, pal)
        chevron(im, x, y, kind != "next", pal, shaft=kind == "back")
    x, y = slot_xy(8, 5)
    socket(im, x, y, pal, kind="sell")
    coin_mark(im, x, y, pal)
    strap(im, pal)
    for r in range(4):
        for c in range(9):
            socket(im, *bottom_xy(c, r), pal)
    return split(im)


def portrait_bg(size=208, pal=GOATTOWN):
    """Behind the portrait: a warm lamp glow fading to the alcove's dark green."""
    im = Image.new("RGBA", (size, size))
    px = im.load()
    for y in range(size):
        for x in range(size):
            dx, dy = (x - size * 0.45) / size, (y - size * 0.38) / size
            t = max(0.0, 1 - math.sqrt(dx * dx + dy * dy) * 1.7)
            c = lerp((10, 22, 16), (112, 92, 52), t * t)
            px[x, y] = c + (255,)
    return im


def portrait_default(pal=GOATTOWN):
    im = portrait_bg(208, pal)
    d = ImageDraw.Draw(im)
    d.ellipse([64, 40, 144, 124], fill=(18, 28, 22))
    d.rounded_rectangle([34, 120, 174, 260], 60, fill=(18, 28, 22))
    return im


def portraits():
    # mirrored so the NPC faces right, toward the option plaques (Rob, 2026-11-02)
    return {"default": portrait_default(), "carl": ImageOps.mirror(portrait_from_skin(ART / "carl_skin.png", background=portrait_bg()))}


# ------------------------------------------------------------------ fonts
LINE_CELL, LINE_H, LINE_BASE, LINE_SIZE = 32, 8, 26, 30
NAME_CELL, NAME_H, NAME_BASE, NAME_SIZE = 44, 11, 36, 42
NAME_Y = 4
LINES = range(5, 223)  # one font per container y; below 5 the ascent would exceed the glyph height
COLUMNS = 16
BAR_FULL, BAR_EMPTY = 0xE101, 0xE102
SHIFT_BASE = 0xE110  # E110-E117: -1 .. -128, E118-E11F: +1 .. +128
SPACE = 3
KERN = 0xE120        # -1: the plugin puts it after every glyph, undoing the client's +1, so letters sit at their own spacing
ART_BASE = 0xE200    # hub top, hub bottom, shop top, shop bottom
PORTRAIT_BASE = 0xE210
PORTRAIT_ORDER = ["default", "carl"]  # Npc3Portraits


def text_cell(code, cell, base, size):
    f = ImageFont.truetype(TEXT_TTF, size)
    im = Image.new("RGBA", (cell, cell), (0, 0, 0, 0))
    if code in (BAR_FULL, BAR_EMPTY):
        d = ImageDraw.Draw(im)
        d.rectangle([0, 11, 9, 25], fill=(255, 255, 255, 255))  # full and empty differ only by the plugin's colour
        return im
    glyph = Image.new("L", (cell, cell), 0)
    left = max(0, -f.getbbox(chr(code), anchor="ls")[0])  # keep the left bearing: with KERN that's the letter gap
    ImageDraw.Draw(glyph).text((left, base), chr(code), font=f, fill=255, anchor="ls")
    glyph = glyph.point(lambda v: 255 if v >= 110 else 0)
    # a dark drop shadow half a pixel down-right, tinted with the text (texture grey x text colour)
    sh = ImageChops.offset(glyph, 2, 2)
    dark = Image.new("RGBA", (cell, cell), (40, 34, 26, 255))
    dark.putalpha(sh)
    im.alpha_composite(dark)
    white = Image.new("RGBA", (cell, cell), (255, 255, 255, 255))
    white.putalpha(glyph)
    im.alpha_composite(white)
    return im


def advance(im, height):
    a = im.getchannel("A")
    bbox = a.getbbox()
    width = bbox[2] if bbox else 0
    return int(0.5 + width * height / im.height) + 1


def sheet(codes, cell, base, size):
    rows = (len(codes) + COLUMNS - 1) // COLUMNS
    im = Image.new("RGBA", (cell * COLUMNS, cell * rows), (0, 0, 0, 0))
    cells = {}
    for i, code in enumerate(codes):
        c = text_cell(code, cell, base, size)
        cells[code] = c
        im.alpha_composite(c, ((i % COLUMNS) * cell, (i // COLUMNS) * cell))
    return im, cells


def sheet_rows(codes):
    out = []
    for r in range((len(codes) + COLUMNS - 1) // COLUMNS):
        row = "".join(chr(c) for c in codes[r * COLUMNS:(r + 1) * COLUMNS])
        out.append(row + "\u0000" * (COLUMNS - len(row)))
    return out


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=True, indent=1) + "\n", encoding="utf-8")


def build(assets: Path):
    tex = assets / "textures/font/npc3"
    tex.mkdir(parents=True, exist_ok=True)
    (hub_top, hub_bottom), (shop_top, shop_bottom) = hub(), shop()
    arts = [("hub_top", hub_top, 0), ("hub_bottom", hub_bottom, TOP_H), ("shop_top", shop_top, 0), ("shop_bottom", shop_bottom, TOP_H)]
    providers, widths, art_images = [], {}, {}
    for i, (name, im, y) in enumerate(arts):
        im.save(tex / f"{name}.png", optimize=True)
        art_images[name] = im
        providers.append({"type": "bitmap", "file": f"lom:font/npc3/{name}.png", "height": im.height // S, "ascent": 13 - y, "chars": [chr(ART_BASE + i)]})
        widths[ART_BASE + i] = advance(im, im.height // S)
    pics = portraits()
    for i, name in enumerate(PORTRAIT_ORDER):
        pics[name].save(tex / f"portrait_{name}.png", optimize=True)
        providers.append({"type": "bitmap", "file": f"lom:font/npc3/portrait_{name}.png", "height": 52, "ascent": 13 - PORTRAIT[1], "chars": [chr(PORTRAIT_BASE + i)]})
        widths[PORTRAIT_BASE + i] = 53
    write_json(assets / "font/npc3/art.json", {"providers": providers})

    shifts = {chr(SHIFT_BASE + i): -(1 << i) for i in range(8)}
    shifts.update({chr(SHIFT_BASE + 8 + i): 1 << i for i in range(8)})
    shifts[chr(KERN)] = -1
    space = {"type": "space", "advances": {" ": SPACE, **shifts}}

    codes = list(range(33, 127)) + [BAR_FULL, BAR_EMPTY]
    line_sheet, line_cells = sheet(codes, LINE_CELL, LINE_BASE, LINE_SIZE)
    line_sheet.save(tex / "text.png", optimize=True)
    for y in LINES:
        write_json(assets / f"font/npc3/line_{y}.json", {"providers": [
            space, {"type": "bitmap", "file": "lom:font/npc3/text.png", "height": LINE_H, "ascent": 13 - y, "chars": sheet_rows(codes)}]})
    name_codes = list(range(33, 127))
    name_sheet, name_cells = sheet(name_codes, NAME_CELL, NAME_BASE, NAME_SIZE)
    name_sheet.save(tex / "name.png", optimize=True)
    write_json(assets / "font/npc3/name.json", {"providers": [
        space, {"type": "bitmap", "file": "lom:font/npc3/name.png", "height": NAME_H, "ascent": 13 - NAME_Y, "chars": sheet_rows(name_codes)}]})

    widths[32] = SPACE
    for code, c in line_cells.items():
        widths[code] = advance(c, LINE_H) - 1
    name_widths = {32: SPACE, **{code: advance(c, NAME_H) - 1 for code, c in name_cells.items()}}
    WIDTHS.write_text(
        "# Generated by tools/npc3_art.py: codepoint and advance (GUI px) of each lom:npc3 glyph (text glyphs net of their KERN); 'name' lines are the header font.\n"
        + "".join(f"{c} {w}\n" for c, w in sorted(widths.items()))
        + "".join(f"name {c} {w}\n" for c, w in sorted(name_widths.items())),
        encoding="utf-8",
    )
    return art_images, pics, widths, line_cells, name_widths, name_cells


# ------------------------------------------------------------------ preview (sample text placed like Npc3Screen)
def draw(canvas, cells, widths, x, y, text, color, height=LINE_H, cell=LINE_CELL):
    for ch in text:
        code = ord(ch)
        if code in cells:
            c = cells[code]
            tinted = Image.new("RGBA", c.size)
            r, gg, b, a = c.split()
            tinted = Image.merge("RGBA", (
                r.point(lambda v: v * color[0] // 255), gg.point(lambda v: v * color[1] // 255), b.point(lambda v: v * color[2] // 255), a))
            canvas.alpha_composite(tinted, (int(x * S), int(y * S) + (0 if height == LINE_H else 0)))
        x += widths.get(code, 0)
    return x


def width_of(widths, text):
    return sum(widths.get(ord(c), 0) for c in text)


def bar(out, cells, widths, ox, x, y, segments, fraction, colour):
    for k in range(segments):
        full = k < int(segments * fraction)
        x = draw(out, cells, widths, ox + x, 8 + y, chr(BAR_FULL if full else BAR_EMPTY), colour if full else (52, 74, 50)) - ox


def preview(path, built):
    art, pics, widths, cells, nwidths, ncells = built
    pad = 8
    out = Image.new("RGBA", ((W * 2 + pad * 3) * S, (TOP_H + BOTTOM_H + pad * 2) * S), (24, 24, 28, 255))
    items = Image.new("RGBA", (16 * S, 16 * S), (0, 0, 0, 0))
    ImageDraw.Draw(items).rounded_rectangle([8, 8, 56, 56], 10, fill=(150, 110, 60, 255))

    def T(ox, x, y, s, col):
        return draw(out, cells, widths, ox + x, pad + y, s, col)

    def R(ox, x, y, s, col):
        return T(ox, x - width_of(widths, s), y, s, col)

    def C(ox, x, y, s, col):
        return T(ox, x - width_of(widths, s) // 2, y, s, col)

    for i, page in enumerate(("hub", "shop")):
        ox = pad + i * (W + pad)
        out.alpha_composite(art[f"{page}_top"], (ox * S, pad * S))
        out.alpha_composite(art[f"{page}_bottom"], (ox * S, (pad + TOP_H) * S))
        draw(out, ncells, nwidths, ox + 9, pad + NAME_Y, "Carl the Kobold", GOLD, NAME_H, NAME_CELL)
        T(ox, 8, 128, "Inventory", LABEL_GREY)  # stand-in for the client's label; it should vanish into the strap
        if page == "hub":
            if 9 + width_of(nwidths, "Carl the Kobold") + 8 + width_of(widths, "Goat Town Merchant") <= 167:
                R(ox, 167, 6, "Goat Town Merchant", MUTED)
            out.alpha_composite(pics["carl"], ((ox + PORTRAIT[0]) * S, (pad + PORTRAIT[1]) * S))
            C(ox, 32, 77, "Stranger", GOLD)
            for r, (label, hint) in enumerate((("Browse wares", "See what's for sale"), ("Sell goods", "Trade goods for coin"), ("Farewell", "Be on your way"))):
                x, y = slot_xy(3, r)
                out.alpha_composite(items, ((ox + x) * S, (pad + y) * S))
                T(ox, 83, y, label, CREAM)
                T(ox, 83, y + 8, hint, MUTED)
            T(ox, 10, 94, "Reputation", GOLD)
            R(ox, 166, 94, "0 / 100", CREAM)
            bar(out, cells, widths, ox, 10, 104, 52, 0.06, GOLD)
            T(ox, 10, 114, "10 more to become Familiar", MUTED)
            T(ox, 10, 141, "Your Wallet", GOLD)
            for k, (line, col) in enumerate((("Silver: 13,164", (210, 214, 218)), ("Gold: 1,288", (255, 201, 60)), ("Dragon Coins: 417", (255, 138, 128)))):
                T(ox, 10, 151 + 9 * k, line, col)
            T(ox, 93, 141, "Carl's Wallet", GOLD)
            bar(out, cells, widths, ox, 93, 153, 24, 0.7, (95, 211, 95))
            C(ox, 88, 181, "You last bought Bread here", MUTED)
        else:
            R(ox, 167, 6, "Wares", MUTED)
            for k in range(13):
                x, y = slot_xy(k % 9, k // 9)
                out.alpha_composite(items, ((ox + x) * S, (pad + y) * S))
            T(ox, 10, 94, "Wallet", GOLD)
            T(ox, 10 + width_of(widths, "Wallet") + 4, 94, "1,288 Gold", CREAM)
            R(ox, 166, 94, "1,000 Gold", CREAM)
            R(ox, 166 - width_of(widths, "1,000 Gold") - 4, 94, "Till", GOLD)
            C(ox, 70, 112, "1 / 2", CREAM)
            R(ox, 147, 112, "Sell", GOLD)
    out.save(path)


if __name__ == "__main__":
    built = build(Path(sys.argv[1]))
    if len(sys.argv) > 2:
        preview(sys.argv[2], built)
