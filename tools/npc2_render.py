"""Hi-res art for NPC Screens 2: 3/4 portraits rendered from a player skin, and text atlases from a TTF.

Used by npc2_pack.py. A bitmap font glyph may hold more texels than GUI pixels (scale = height / cell rows), so
the portrait and text are drawn at 4x and 3x and stay sharp in game.
"""
import math

from PIL import Image, ImageDraw, ImageFilter, ImageFont

# (skin u, v, w, h, d) boxes and their model origin (x, y, z), in skin pixels; the NPC faces +z, y is up
BOXES = [
    ((0, 0, 8, 8, 8), (-4, 24, -4), 0.0),     # head
    ((32, 0, 8, 8, 8), (-4, 24, -4), 0.5),    # hat layer
    ((16, 16, 8, 12, 4), (-4, 12, -2), 0.0),  # body
    ((16, 32, 8, 12, 4), (-4, 12, -2), 0.25),
    ((40, 16, 4, 12, 4), (-8, 12, -2), 0.0),  # right arm (viewer's left)
    ((40, 32, 4, 12, 4), (-8, 12, -2), 0.25),
    ((32, 48, 4, 12, 4), (4, 12, -2), 0.0),   # left arm
    ((48, 48, 4, 12, 4), (4, 12, -2), 0.25),
]


def _faces(box, origin, inflate):
    """Each face as (corner, edge along texture x, edge along texture y, texture rect, shade)."""
    u, v, w, h, d = box
    x0, y0, z0 = (o - inflate for o in origin)
    x1, y1, z1 = origin[0] + w + inflate, origin[1] + h + inflate, origin[2] + d + inflate
    W, H, D = x1 - x0, y1 - y0, z1 - z0
    return [
        ((x0, y1, z1), (W, 0, 0), (0, -H, 0), (u + d, v + d, w, h), 0.92),            # front
        ((x1, y1, z0), (-W, 0, 0), (0, -H, 0), (u + 2 * d + w, v + d, w, h), 0.5),    # back
        ((x0, y1, z0), (0, 0, D), (0, -H, 0), (u, v + d, d, h), 0.62),                # right side (x min)
        ((x1, y1, z1), (0, 0, -D), (0, -H, 0), (u + d + w, v + d, d, h), 0.74),       # left side (x max)
        ((x0, y1, z0), (W, 0, 0), (0, 0, D), (u + d, v, w, d), 1.0),                  # top
    ]


def portrait_from_skin(skin_path, size=208, yaw=-35, pitch=15, background=None):
    """A head-and-shoulders render of a skin, turned three quarters, over background (or transparent)."""
    skin = Image.open(skin_path).convert("RGBA")
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def rot(p):
        x, y, z = p
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    scale, cx, cyy = size / 16.0, size * 0.42, size * 0.56
    def screen(p):
        x, y, z = rot(p)
        return cx + x * scale, cyy - (y - 26) * scale, z

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    zbuf = [[-1e9] * size for _ in range(size)]
    pix = img.load()
    for box, origin, inflate in BOXES:
        for corner, ex, ey, (tu, tv, tw, th), shade in _faces(box, origin, inflate):
            a = screen(corner)
            b = screen(tuple(corner[i] + ex[i] for i in range(3)))
            c = screen(tuple(corner[i] + ey[i] for i in range(3)))
            ux, uy, vx, vy = b[0] - a[0], b[1] - a[1], c[0] - a[0], c[1] - a[1]
            det = ux * vy - uy * vx
            if det <= 1e-6:  # facing away
                continue
            xs = [a[0], b[0], c[0], b[0] + vx]
            ys = [a[1], b[1], c[1], b[1] + vy]
            for py in range(max(0, int(min(ys))), min(size, int(max(ys)) + 1)):
                for px in range(max(0, int(min(xs))), min(size, int(max(xs)) + 1)):
                    dx, dy = px + 0.5 - a[0], py + 0.5 - a[1]
                    s = (dx * vy - dy * vx) / det
                    t = (ux * dy - uy * dx) / det
                    if not (0 <= s < 1 and 0 <= t < 1):
                        continue
                    z = a[2] + s * (b[2] - a[2]) + t * (c[2] - a[2])
                    if z <= zbuf[py][px]:
                        continue
                    r, g, bb, al = skin.getpixel((tu + min(tw - 1, int(s * tw)), tv + min(th - 1, int(t * th))))
                    if al < 128:
                        continue
                    zbuf[py][px] = z
                    lit = shade * (1.06 - 0.16 * t)  # light from above
                    pix[px, py] = tuple(min(255, int(ch * lit)) for ch in (r, g, bb)) + (255,)
    # a dark outline around the figure so it reads against any background
    alpha = img.getchannel("A")
    ring = alpha.filter(ImageFilter.MaxFilter(5))
    outline = Image.new("RGBA", img.size, (24, 16, 10, 255))
    outline.putalpha(ring)
    out = background.copy() if background is not None else Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.alpha_composite(outline)
    out.alpha_composite(img)
    return out


def text_atlas(ttf, gui_height, texels, cell_w, baseline, size, weight=700, shadow=2):
    """vanilla's ascii layout (16 x 16 cells, chars 0x20-0x7e) drawn white with a soft dark drop shadow."""
    font = ImageFont.truetype(str(ttf), size)
    font.set_variation_by_axes([weight])
    cell_h = gui_height * texels
    atlas = Image.new("RGBA", (16 * cell_w, 16 * cell_h), (0, 0, 0, 0))
    for code in range(0x21, 0x7F):
        ch = chr(code)
        glyph = Image.new("RGBA", (cell_w, cell_h), (0, 0, 0, 0))
        d = ImageDraw.Draw(glyph)
        left = -font.getbbox(ch, anchor="ls")[0]  # flush left, so the client's +1 gap is the only spacing
        d.text((left + shadow, baseline + shadow), ch, font=font, fill=(10, 6, 2, 150), anchor="ls")
        d.text((left, baseline), ch, font=font, fill=(255, 255, 255, 255), anchor="ls")
        atlas.alpha_composite(glyph, ((code % 16) * cell_w, (code // 16) * cell_h))
    return atlas


def advances(atlas, cell_w, cell_h, gui_height):
    """The client's advance for each char 0x20-0x7e: round(width x scale) + 1, width = last column with any alpha."""
    scale = gui_height / cell_h
    alpha = atlas.getchannel("A").load()
    out = []
    for code in range(0x20, 0x7F):
        x0, y0 = (code % 16) * cell_w, (code // 16) * cell_h
        width = 0
        for x in range(cell_w - 1, -1, -1):
            if any(alpha[x0 + x, y0 + y] for y in range(cell_h)):
                width = x + 1
                break
        out.append(int(0.5 + width * scale) + 1)
    return out


def backdrop(size, top=(116, 168, 214), bottom=(214, 226, 200), hill=(98, 140, 66), hill2=(70, 108, 48)):
    """A Goat Town sky over two meadow hills, with a soft vignette."""
    img = Image.new("RGBA", (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        k = y / (size - 1)
        d.line([(0, y), (size, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * k) for i in range(3)) + (255,))
    d.ellipse([-size * 0.4, size * 0.62, size * 0.9, size * 1.6], fill=hill)
    d.ellipse([size * 0.3, size * 0.7, size * 1.5, size * 1.7], fill=hill2)
    vignette = Image.new("L", (size, size), 0)
    ImageDraw.Draw(vignette).ellipse([-size * 0.25, -size * 0.25, size * 1.25, size * 1.25], fill=255)
    shade = Image.new("RGBA", (size, size), (20, 12, 6, 110))
    shade.putalpha(Image.eval(vignette.filter(ImageFilter.GaussianBlur(size / 8)), lambda a: int((255 - a) * 0.45)))
    img.alpha_composite(shade)
    return img
