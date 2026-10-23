#!/usr/bin/env python3
"""
Paints chapter 1's art, The Old Quarter: a fishing hut on stilts in a pond at dusk, in pastels.

The app shows it through the chapter screen's grid of small squares, one square per art pixel,
anchored to the top-right corner and lit by the same circular gradient as the plain grid. So the
picture is 64 x 72: as wide as a phone's grid, and tall enough to reach where the light runs out.
Writes app/src/main/res/drawable-nodpi/art_old_quarter.png. Run from Sudoku/:

    python3 tools/art/old_quarter.py [--preview build/art_preview.png]

The preview shows the art as the app does, on a 411 x 891 dp phone, beside the bare pixels.
"""
import argparse
import math
import random
from pathlib import Path

from PIL import Image

W, H = 64, 72
HORIZON = 34  # the far shore: the pond below it mirrors the scene above
SUN_X, SUN_Y = 23, 30

def rgb(code):
    return tuple(int(code[i:i + 2], 16) for i in (1, 3, 5))

# Pastel dusk, top of the sky to the horizon. The deepest tone keeps the app's white back arrow legible.
SKY = [rgb(c) for c in ("#7B80C6", "#9296D4", "#ACA9E0", "#C7B4E3", "#E0BDDF", "#F1C6D7", "#F9D4CF", "#FEE3D2")]
SUN, SUN_HALO = rgb("#FFF8E4"), rgb("#FFEACB")
STAR = rgb("#E9E6FB")
CLOUD_HI, CLOUD, CLOUD_SH = rgb("#FFF6F4"), rgb("#F8DDE6"), rgb("#E4C3DB")
BIRD = rgb("#6F73B4")
HILL_HI, HILL, HILL_SH = rgb("#CDBEE6"), rgb("#B9AADB"), rgb("#A797CD")
TREE_FAR, TREE_FAR_HI = rgb("#9DBFC4"), rgb("#B4D3D2")
MINT_HI, MINT, SAGE, SAGE_DK = rgb("#D6EFD3"), rgb("#B2DCBF"), rgb("#93C6A8"), rgb("#76AC92")
BLOSSOM_HI, BLOSSOM, BLOSSOM_DK = rgb("#FFE0E8"), rgb("#F9BFD0"), rgb("#EC9FB8")
BARK, BARK_DK = rgb("#9C7D8F"), rgb("#7F6578")
WOOD_HI, WOOD, WOOD_DK, WOOD_XDK = rgb("#F7E0C4"), rgb("#E8C7A2"), rgb("#CBA184"), rgb("#9F7C70")
ROOF_HI, ROOF, ROOF_DK = rgb("#F8C2B8"), rgb("#EDA5A4"), rgb("#D48A96")
STONE, STONE_DK = rgb("#DCD3EA"), rgb("#B9AFD0")
LIGHT, LIGHT_GLOW = rgb("#FFF2B2"), rgb("#FFE2A6")
WATER_HI, WATER, WATER_MID, WATER_DK = rgb("#D6F1F6"), rgb("#A8D9EB"), rgb("#8EC7E3"), rgb("#76AFD7")
# The pond's tones: reflections snap to the nearest, so the water keeps a small palette.
WATER_TONES = [WATER_HI, WATER, WATER_MID, WATER_DK] + [rgb(c) for c in (
    "#C3CDEE",  # lilac sky
    "#DCCDE6",  # pink sky
    "#EBDCD9",  # peach sky and sun
    "#B2CFE0",  # hills
    "#A6CFCB",  # greens
    "#CFC5D6",  # wood
    "#A99DBE",  # posts and shadows
    "#E4C3D2",  # roof and blossom
    "#F4E8C6",  # lamplight
)]
LILY, LILY_DK, LILY_FLOWER = rgb("#A3D4AC"), rgb("#82BA96"), rgb("#FFC9D9")
REED, CATTAIL = rgb("#9ECDA4"), rgb("#B18C86")
BOAT_RIM, BOAT, BOAT_DK = rgb("#FFF4EE"), rgb("#ABDCCB"), rgb("#86C1B0")
HULL, HULL_DK = rgb("#EFA9A6"), rgb("#D4899A")
FIREFLY = rgb("#FFF5BE")

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]

img = [[None] * W for _ in range(H)]

def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        img[y][x] = c

def get(x, y):
    return img[min(max(y, 0), H - 1)][min(max(x, 0), W - 1)]

def dither(x, y, amount):
    """True where an ordered dither of [amount] (0 to 1) lands on this pixel."""
    return amount > (BAYER[y % 4][x % 4] + 0.5) / 16

def band(colours, t, x, y, sharpness=2.5):
    """A colour from a list of bands at [t] (0 to 1), dithered only near each boundary."""
    t = min(max(t, 0.0), 1.0) * (len(colours) - 1)
    i = min(int(t), len(colours) - 2)
    f = min(max((t - i - 0.5) * sharpness + 0.5, 0.0), 1.0)
    return colours[i + 1] if dither(x, y, f) else colours[i]

def blob(circles, bottom=H):
    """Pixels inside any of the circles (cx, cy, r), above [bottom]."""
    mask = set()
    for cx, cy, r in circles:
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and y <= bottom:
                    mask.add((x, y))
    return mask

def line(x0, y0, x1, y1, c, step=1):
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(0, n + 1, step):
        put(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), c)

def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

def nearest(c, colours):
    return min(colours, key=lambda p: sum((p[i] - c[i]) ** 2 for i in range(3)))

# ---- Sky ---------------------------------------------------------------------------------------

def sky():
    for y in range(HORIZON):
        for x in range(W):
            put(x, y, band(SKY, y / (HORIZON - 3), x, y, sharpness=3))
    for x, y in ((5, 2), (19, 1), (30, 5), (60, 3), (13, 7)):
        put(x, y, STAR)

def sun(r=4.5):
    for y in range(SUN_Y - 8, SUN_Y + 8):
        for x in range(SUN_X - 8, SUN_X + 8):
            d = math.hypot(x - SUN_X, y - SUN_Y)
            if d <= r:
                put(x, y, SUN)
            elif d <= r + 1:
                put(x, y, SUN_HALO)
            elif d <= r + 2.2 and (x + y) % 2 == 0:
                put(x, y, SUN_HALO)

def cloud(circles, bottom):
    mask = blob(circles, bottom)
    for x, y in mask:
        c = CLOUD
        if (x, y - 1) not in mask:
            c = CLOUD_HI
        if (x, y + 1) not in mask:
            c = CLOUD_SH
        put(x, y, c)

def clouds():
    cloud([(37, 9, 2.2), (41, 7.5, 3.2), (46, 6.5, 3.4), (51, 7.8, 2.8), (55, 9.2, 1.8)], bottom=10)
    cloud([(8, 14, 1.8), (11, 12.6, 2.4), (14.5, 14, 1.6)], bottom=15)
    for x in range(26, 37):
        if x % 4 != 2:
            put(x, 18, CLOUD_HI if x < 31 else CLOUD)

def birds():
    for bx, by in ((29, 14), (33, 12)):
        put(bx, by, BIRD)
        put(bx - 1, by - 1, BIRD)
        put(bx + 1, by - 1, BIRD)

# ---- Land --------------------------------------------------------------------------------------

def hill_top(x):
    return 28.6 + 1.6 * math.sin(x / 5.5 + 0.6) + 1.0 * math.sin(x / 2.7 + 2.1) + 0.8 * math.sin(x / 11.5 + 4)

def hills():
    for x in range(W):
        top = round(hill_top(x))
        for y in range(top, HORIZON):
            put(x, y, HILL_HI if y == top else (HILL_SH if y == HORIZON - 1 else HILL))

def far_trees():
    for x, r in ((20, 1.4), (23, 1.8), (27, 1.3), (30, 1.7), (34, 1.4), (37, 1.6)):
        for px, py in blob([(x, HORIZON - 1.5, r)], bottom=HORIZON - 1):
            put(px, py, TREE_FAR_HI if py < HORIZON - 2 and px <= x else TREE_FAR)

def bank():
    for x in range(0, 21):
        top = round(31 + (x / 20) ** 2.2 * 3)
        for y in range(top, HORIZON + 1):
            put(x, y, MINT if y == top else (SAGE_DK if y == HORIZON else SAGE))

def blossom_tree():
    for y in range(21, 32):
        put(8, y, BARK)
        put(9, y, BARK_DK)
    line(8, 24, 4, 20, BARK)
    line(9, 23, 13, 19, BARK)
    canopy = blob([(5, 19, 3.2), (9, 16.5, 3.8), (13, 17.5, 3.4), (16, 20.5, 2.6), (3, 22.5, 2.4),
                   (11, 21.5, 2.8), (7, 22.5, 2.6)])
    random.seed(3)
    for x, y in canopy:
        c = BLOSSOM
        if (x - 1, y - 1) not in canopy or (x, y - 1) not in canopy:
            c = BLOSSOM_HI
        elif (x + 1, y + 1) not in canopy or (x, y + 1) not in canopy:
            c = BLOSSOM_DK
        elif random.random() < 0.15:
            c = BLOSSOM_HI if x + y < 28 else BLOSSOM_DK
        put(x, y, c)
    for x, y in ((7, 21), (11, 19)):
        put(x, y, BARK)
    for x, y in ((19, 22), (21, 26), (18, 28), (24, 21)):
        put(x, y, BLOSSOM)

# ---- The hut -----------------------------------------------------------------------------------

HUT_L, HUT_R, WALL_TOP, DECK = 44, 56, 22, 32
PEAK_X, PEAK_Y = 50, 13

def roof_top(x):
    return PEAK_Y + abs(x - PEAK_X) * (WALL_TOP - 1 - PEAK_Y) / 9

def hut():
    # Chimney behind the right slope, with a wisp of smoke.
    for y in range(14, 19):
        put(54, y, STONE)
        put(55, y, STONE_DK)
    for x in (53, 54, 55, 56):
        put(x, 13, STONE_DK)
    for x, y in ((55, 11), (56, 10), (56, 8), (57, 7)):
        put(x, y, CLOUD_HI)
    # Walls: planks, lit on the left, in the eave's shadow along the top.
    for y in range(WALL_TOP, DECK):
        for x in range(HUT_L, HUT_R + 1):
            c = WOOD_DK if (x - HUT_L) % 3 == 2 else WOOD
            if x == HUT_L:
                c = WOOD_HI
            if x == HUT_R or y == WALL_TOP:
                c = WOOD_DK
            put(x, y, c)
    # Gable end, and its vent.
    for x in range(HUT_L, HUT_R + 1):
        for y in range(math.ceil(roof_top(x)) + 2, WALL_TOP):
            put(x, y, WOOD if y % 2 == 0 else WOOD_HI)
    put(PEAK_X, 17, WOOD_XDK)
    # Roof: two pixels deep down each slope, overhanging the walls.
    for x in range(HUT_L - 3, HUT_R + 4):
        top = round(roof_top(x))
        if x < PEAK_X:
            colours = (ROOF_HI, ROOF)
        elif x > PEAK_X:
            colours = (ROOF, ROOF_DK)
        else:
            colours = (ROOF_HI, ROOF_HI)
        put(x, top, colours[0])
        put(x, top + 1, colours[1])
    put(PEAK_X, PEAK_Y - 1, ROOF_HI)
    # Door, ajar, with lamplight inside.
    for y in range(25, DECK):
        put(46, y, WOOD_XDK)
        put(47, y, WOOD_DK)
        put(48, y, LIGHT_GLOW if y > 25 else WOOD_XDK)
    put(47, 25, WOOD_XDK)
    # Window, lit warm, in a frame, with flowers on the sill.
    for y in range(24, 29):
        for x in range(50, 56):
            c = LIGHT if y < 27 else LIGHT_GLOW
            if x in (50, 55) or y in (24, 28) or x == 53:
                c = WOOD_XDK
            put(x, y, c)
    for x in range(50, 56):
        put(x, 29, ROOF_DK if x in (50, 55) else ROOF)
    put(51, 28, BLOSSOM)
    put(54, 28, MINT)
    # Lantern hanging from the left eave.
    put(42, 22, WOOD_XDK)
    put(42, 23, LIGHT)
    put(42, 24, LIGHT_GLOW)
    put(42, 25, WOOD_XDK)
    put(41, 24, LIGHT_GLOW)

def deck_and_pier():
    for x in range(27, 61):
        put(x, DECK, WOOD_DK if x % 3 == 0 else WOOD_HI)
        put(x, DECK + 1, WOOD_DK)
    for px in (29, 35, 42, 46, 50, 54, 58):
        for y in range(DECK + 2, HORIZON + 3):
            put(px, y, WOOD_XDK)
    # A fishing rod propped at the end of the pier, its line down to a bobber.
    line(29, 31, 24, 24, WOOD_XDK)
    for x, y in ((23, 27), (23, 30), (22, 33)):
        put(x, y, CLOUD_HI)

def boat():
    for x in range(11, 18):
        put(x, 36, BOAT_RIM)
    for x in range(10, 19):
        put(x, 37, HULL)
    for x in range(12, 17):
        put(x, 38, HULL_DK)
    put(9, 35, WOOD_XDK)

# ---- Water -------------------------------------------------------------------------------------

def water():
    scene = [row[:] for row in img]
    ripple = (0, 0, 0, 1, 0, 0, 0, -1)
    for y in range(HORIZON, H):
        depth = y - HORIZON
        for x in range(W):
            base = band([WATER, WATER_MID, WATER_DK], depth / (H - HORIZON - 6), x, y, sharpness=2)
            if depth > 18:
                put(x, y, base)
                continue
            # The mirror image fades into the water with depth.
            reflected = scene[HORIZON - 1 - depth][min(max(x + ripple[y % len(ripple)], 0), W - 1)]
            put(x, y, nearest(mix(reflected, base, min(0.55 + depth * 0.025, 0.95)), WATER_TONES))
    for x in range(W):
        if x % 5 not in (0, 3) and scene[HORIZON - 1][x] in (HILL, HILL_SH, TREE_FAR, SAGE_DK):
            put(x, HORIZON, WATER_HI)
    # Sunlight on the water under the sun, and glints across the pond.
    for y in range(HORIZON + 1, HORIZON + 16, 2):
        half = max(1, 3 - (y - HORIZON) // 5)
        for x in range(SUN_X - half + (y % 4 == 3), SUN_X + half + (y % 4 == 3)):
            put(x, y, SUN if abs(x - SUN_X) < half - 0.5 else SUN_HALO)
    random.seed(11)
    for _ in range(22):
        x = random.randrange(0, W - 4)
        y = random.randrange(HORIZON + 2, H - 2)
        if SUN_X - 5 <= x <= SUN_X + 3 and y < HORIZON + 17:
            continue
        for i in range(random.choice((2, 2, 3))):
            put(x + i, y, WATER_HI)
    # The bobber, and its ripples.
    put(22, 35, ROOF)
    put(22, 36, BOAT_RIM)
    put(20, 37, WATER_HI)
    put(24, 37, WATER_HI)

def lilies():
    for cx, cy, flower in ((5, 46, True), (12, 50, False), (3, 55, False), (16, 44, False), (9, 60, True),
                           (44, 48, False), (56, 53, True), (38, 57, False), (60, 62, False)):
        for x in range(cx - 1, cx + 2):
            put(x, cy, LILY)
        put(cx - 2, cy, LILY_DK)
        put(cx, cy + 1, LILY_DK)
        if flower:
            put(cx, cy - 1, LILY_FLOWER)

def reeds():
    random.seed(5)
    for base_x, bottom in ((0, HORIZON + 2), (59, HORIZON + 3)):
        for i in range(5):
            x = base_x + i
            height = random.randrange(4, 9)
            for y in range(bottom - height, bottom):
                put(x, y, REED if (y + i) % 4 else SAGE)
            if random.random() < 0.6:
                put(x, bottom - height - 1, CATTAIL)
                put(x, bottom - height - 2, CATTAIL)

def fireflies():
    for x, y in ((5, 26), (16, 28), (61, 24), (38, 26), (33, 23)):
        put(x, y, FIREFLY)

def paint():
    sky()
    sun()
    clouds()
    birds()
    hills()
    far_trees()
    bank()
    blossom_tree()
    hut()
    deck_and_pier()
    water()
    boat()
    lilies()
    reeds()
    fireflies()
    out = Image.new("RGB", (W, H))
    for y in range(H):
        for x in range(W):
            out.putpixel((x, y), img[y][x] or (255, 0, 255))
    return out

# ---- Preview -----------------------------------------------------------------------------------

def as_on_a_phone(art, page=(0x0A, 0x3C, 0x8C)):
    """The art as the app draws it on a 411 x 891 dp phone at 3x: SquareGlowView's grid and light."""
    width, height = 1233, 2673
    gap, size = 3, 17
    pitch = size + gap
    columns = (width + gap) // pitch
    left = (width - columns * pitch + gap) // 2
    cx, cy = width * 0.82, height * 0.03
    radius = math.hypot(cx - width / 2, cy - height / 2)
    out = Image.new("RGB", (width, height // 2), page)
    for row in range(art.height):
        for column in range(columns):
            ax = art.width - columns + column
            if ax < 0:
                continue
            x, y = left + column * pitch, left + row * pitch
            light = 1 - math.hypot(x + size / 2 - cx, y + size / 2 - cy) / radius
            if light <= 0:
                continue
            colour = mix(page, art.getpixel((ax, row)), light)
            out.paste(colour, (x, y, x + size, min(y + size, out.height)))
    return out

def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--preview", help="also write a preview here")
    args = parser.parse_args()
    art = paint()
    target = Path(__file__).resolve().parents[2] / "app/src/main/res/drawable-nodpi/art_old_quarter.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    art.save(target, optimize=True)
    if args.preview:
        phone = as_on_a_phone(art)
        bare = art.resize((W * 8, H * 8), Image.NEAREST)
        sheet = Image.new("RGB", (phone.width + 20 + bare.width, max(phone.height, bare.height)), (128, 128, 128))
        sheet.paste(phone, (0, 0))
        sheet.paste(bare, (phone.width + 20, 0))
        sheet.save(args.preview)

if __name__ == "__main__":
    main()
