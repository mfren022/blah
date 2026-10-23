#!/usr/bin/env python3
"""
Paints chapter 1's art, The Old Quarter: a fishing hut on stilts in a pond at dusk, in pastels.

Writes app/src/main/res/drawable-nodpi/art_old_quarter.png at one pixel per art pixel (128 x 96);
the app scales it up by whole pixels and dithers its bottom rows into the page. Run from Sudoku/:

    python3 tools/art/old_quarter.py [--preview build/art_preview.png]
"""
import argparse
import math
import random
from pathlib import Path

from PIL import Image

W, H = 128, 96
HORIZON = 66  # the far shore: the pond below it mirrors the scene above
SUN_X = 52

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
            put(x, y, band(SKY, y / (HORIZON - 6), x, y, sharpness=2.8))
    for x, y in ((40, 5), (71, 3), (97, 9), (119, 4), (58, 12), (12, 14)):
        put(x, y, STAR)

def sun(cx=SUN_X, cy=56, r=8.5):
    for y in range(int(cy - r - 4), int(cy + r + 5)):
        for x in range(int(cx - r - 4), int(cx + r + 5)):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                put(x, y, SUN)
            elif d <= r + 1.6:
                put(x, y, SUN_HALO)
            elif d <= r + 3.5 and dither(x, y, 0.5 - (d - r - 1.6) / 4):
                put(x, y, SUN_HALO)

def cloud(circles, bottom):
    mask = blob(circles, bottom)
    for x, y in mask:
        c = CLOUD
        if (x, y - 1) not in mask or (x - 1, y - 1) not in mask:
            c = CLOUD_HI
        if (x, y + 1) not in mask:
            c = CLOUD_SH
        elif (x, y + 2) not in mask and (x + y) % 2 == 0:
            c = CLOUD_SH
        put(x, y, c)

def clouds():
    cloud([(84, 22, 5), (90, 18, 6.5), (97, 17, 5.5), (103, 20, 5), (109, 23, 3.5), (78, 24, 3)], bottom=25)
    cloud([(20, 30, 3.5), (25, 27, 4.5), (31, 29, 3.5), (35, 31, 2.5)], bottom=32)
    for x in range(52, 70):
        if x % 7 != 3:
            put(x, 34, CLOUD_HI if x < 60 else CLOUD)
    for x in range(58, 74):
        if x % 5 != 1:
            put(x, 35, CLOUD)

def birds():
    for bx, by, size in ((64, 22, 2), (71, 19, 1), (57, 26, 1)):
        put(bx, by, BIRD)
        for i in range(1, size + 1):
            put(bx - i, by - i, BIRD)
            put(bx + i, by - i, BIRD)

# ---- Land --------------------------------------------------------------------------------------

def hill_top(x):
    return 57 + 3 * math.sin(x / 11 + 0.6) + 2 * math.sin(x / 5.3 + 2.1) + 1.5 * math.sin(x / 23 + 4)

def hills():
    for x in range(W):
        top = round(hill_top(x))
        for y in range(top, HORIZON):
            c = HILL
            if y == top:
                c = HILL_HI
            elif y >= HORIZON - 4 and dither(x, y, (y - (HORIZON - 5)) / 4):
                c = HILL_SH
            put(x, y, c)

def far_trees():
    random.seed(7)
    x = 38
    while x < 82:
        r = random.choice((2, 2.5, 3, 3.5))
        cy = HORIZON - 2 - r * 0.6
        for px, py in blob([(x, cy, r), (x + r * 0.8, cy + 0.8, r * 0.8)], bottom=HORIZON - 1):
            c = TREE_FAR_HI if (px - 1, py - 1) not in blob([(x, cy, r)]) and px < x else TREE_FAR
            put(px, py, c)
        x += int(r * 2.2) + random.choice((0, 1, 2))

def bank():
    for x in range(0, 40):
        top = round(60 + (x / 39) ** 2.2 * 6)
        for y in range(top, HORIZON + 1):
            c = SAGE
            if y == top:
                c = MINT
            elif y == top + 1 and dither(x, y, 0.5):
                c = MINT
            elif y >= HORIZON - 1:
                c = SAGE_DK
            put(x, y, c)
        if x % 3 == 0 and x < 34:
            put(x, top - 1, MINT_HI)

def blossom_tree():
    # Trunk and branches.
    for y in range(44, 62):
        w = 3 if y > 54 else 2
        x0 = 17 + (1 if y < 50 else 0)
        for x in range(x0, x0 + w):
            put(x, y, BARK_DK if x == x0 + w - 1 else BARK)
    line(18, 47, 11, 41, BARK)
    line(19, 46, 27, 39, BARK)
    line(18, 51, 9, 48, BARK_DK)
    line(19, 49, 30, 45, BARK_DK)
    # Canopy, lit from the sunset on the left.
    canopy = blob([(10, 39, 6), (17, 34, 7), (25, 35, 6.5), (31, 41, 5), (6, 45, 4.5), (23, 43, 5.5),
                   (14, 45, 5), (30, 35, 4)])
    random.seed(3)
    for x, y in canopy:
        c = BLOSSOM
        if (x - 1, y - 1) not in canopy or (x, y - 1) not in canopy:
            c = BLOSSOM_HI
        elif (x + 1, y + 1) not in canopy or (x, y + 1) not in canopy:
            c = BLOSSOM_DK
        elif random.random() < 0.13:
            c = BLOSSOM_HI if x + y < 58 else BLOSSOM_DK
        put(x, y, c)
    # A few branches showing through, and petals on the wind.
    for x, y in ((13, 43), (21, 40), (26, 41), (9, 46)):
        put(x, y, BARK)
    for x, y in ((38, 44), (42, 49), (36, 52), (47, 41), (52, 47), (40, 58), (45, 63), (33, 60)):
        put(x, y, BLOSSOM)

# ---- The hut -----------------------------------------------------------------------------------

HUT_L, HUT_R, WALL_TOP, DECK = 88, 112, 46, 61
PEAK_X, PEAK_Y = 100, 31

def roof_top(x):
    return PEAK_Y + abs(x - PEAK_X) * (WALL_TOP - PEAK_Y) / 17

def hut():
    # Chimney behind the right slope, with a wisp of smoke.
    for y in range(33, 42):
        for x in (107, 108, 109):
            put(x, y, STONE_DK if x == 109 else STONE)
    for x in (106, 107, 108, 109, 110):
        put(x, 32, STONE_DK)
    for x, y in ((108, 30), (109, 29), (109, 27), (110, 26), (111, 24), (111, 23), (112, 21), (114, 20)):
        put(x, y, CLOUD_HI if y > 25 else CLOUD)
    # Walls: planks, lit on the left, in the eave's shadow at the top.
    for y in range(WALL_TOP, DECK):
        for x in range(HUT_L, HUT_R + 1):
            c = WOOD
            if (x - HUT_L) % 4 == 3:
                c = WOOD_DK
            if x == HUT_L:
                c = WOOD_HI
            if x >= HUT_R - 1:
                c = WOOD_DK
            if y <= WALL_TOP + 1 and (y == WALL_TOP or dither(x, y, 0.5)):
                c = WOOD_DK
            if y == DECK - 1:
                c = WOOD_DK
            put(x, y, c)
    # Gable end: horizontal boards and a round vent.
    for x in range(HUT_L, HUT_R + 1):
        for y in range(math.ceil(roof_top(x)) + 3, WALL_TOP):
            put(x, y, WOOD if (y - PEAK_Y) % 3 == 0 else WOOD_HI)
    for x, y in ((99, 39), (100, 39), (99, 40), (100, 40)):
        put(x, y, WOOD_XDK)
    put(99, 39, WOOD_DK)
    # Roof: a three-pixel band down each slope, overhanging the walls.
    for x in range(HUT_L - 5, HUT_R + 6):
        top = round(roof_top(x))
        for i in range(3):
            if x < PEAK_X:
                c = (ROOF_HI, ROOF, ROOF_DK)[i]
            elif x > PEAK_X:
                c = (ROOF, ROOF_DK, ROOF_DK)[i]
            else:
                c = (ROOF_HI, ROOF_HI, ROOF)[i]
            if i == 1 and (x + top) % 4 == 0:
                c = ROOF_DK
            put(x, top + i, c)
    put(PEAK_X, PEAK_Y - 1, ROOF_HI)
    # Door, ajar, with lamplight inside.
    for y in range(50, DECK):
        for x in range(91, 96):
            c = WOOD_DK
            if x in (91, 95) or y == 50:
                c = WOOD_XDK
            if x == 94 and y > 50:
                c = LIGHT_GLOW
            put(x, y, c)
    put(92, 56, LIGHT)
    # Window, lit warm, with a flower box.
    for y in range(49, 56):
        for x in range(101, 109):
            c = LIGHT if y < 52 else LIGHT_GLOW
            if x in (101, 108) or y in (49, 55) or x == 104 or y == 52:
                c = WOOD_XDK
            put(x, y, c)
    for x in range(100, 110):
        put(x, 56, ROOF_DK if x in (100, 109) else ROOF)
    # Lamplight spilling onto the boards around the window.
    for x, y in ((100, 50), (100, 52), (109, 49), (109, 51), (102, 48), (106, 48)):
        put(x, y, LIGHT_GLOW)
    for x in (101, 103, 105, 107):
        put(x, 55, BLOSSOM if x % 4 == 1 else MINT)
    # Lantern hanging from the left eave.
    put(85, 46, WOOD_XDK)
    put(85, 47, WOOD_XDK)
    for y, c in ((48, WOOD_XDK), (49, LIGHT), (50, LIGHT), (51, WOOD_XDK)):
        put(84, y, c if c != LIGHT else LIGHT_GLOW)
        put(85, y, c)
        put(86, y, c if c != LIGHT else LIGHT_GLOW)
    for x, y in ((83, 49), (87, 50), (83, 51), (87, 48), (85, 53)):
        put(x, y, LIGHT_GLOW)
    # A pail on the deck.
    for x, y, c in ((84, 58, BOAT_RIM), (85, 58, BOAT_RIM), (86, 58, BOAT_RIM), (84, 59, BOAT), (85, 59, BOAT),
                    (86, 59, BOAT_DK), (84, 60, BOAT_DK), (85, 60, BOAT_DK)):
        put(x, y, c)

def deck_and_pier():
    # Deck under the hut and a pier out to the left, on posts in the water.
    for x in range(54, HUT_R + 5):
        put(x, DECK, WOOD_DK if x % 4 == 0 else WOOD_HI)
        put(x, DECK + 1, WOOD_DK)
        put(x, DECK + 2, WOOD_XDK if x % 4 == 0 else WOOD_DK)
    for px in (56, 64, 72, 80, 86, 94, 102, 110, 115):
        for y in range(DECK + 3, HORIZON + 4):
            put(px, y, WOOD_XDK)
            put(px + 1, y, WOOD_DK if y < HORIZON else WOOD_XDK)
    # A fishing rod propped at the end of the pier, its line down to a bobber.
    line(57, 60, 47, 46, WOOD_XDK)
    put(56, 59, STONE)
    for x, y in ((46, 48), (45, 51), (45, 54), (44, 57), (44, 60), (44, 63), (44, 65)):
        put(x, y, CLOUD_HI)

def boat():
    for x in range(26, 37):
        put(x, 68, BOAT_RIM)
    for x in range(25, 38):
        put(x, 69, HULL)
    for x in range(27, 36):
        put(x, 70, HULL_DK)
    put(25, 68, HULL_DK)
    put(37, 68, HULL_DK)
    for x in range(28, 35):
        put(x, 71, WATER_TONES[10])   # its shadow on the water
    put(31, 67, WOOD_DK)   # an oar handle
    put(32, 66, WOOD_DK)
    # Moored to the bank.
    for x, y in ((24, 67), (22, 66), (20, 65)):
        put(x, y, WOOD_XDK)

# ---- Water -------------------------------------------------------------------------------------

def water():
    scene = [row[:] for row in img]
    ripple = (0, 0, 1, 1, 0, 0, -1, -1)
    for y in range(HORIZON, H):
        depth = y - HORIZON
        base = band([WATER, WATER_MID, WATER_DK], depth / (H - HORIZON - 4), 0, y, sharpness=1.6)
        for x in range(W):
            base = band([WATER, WATER_MID, WATER_DK], depth / (H - HORIZON - 4), x, y, sharpness=1.6)
            # The mirror image fades into the water with depth.
            src_y = HORIZON - 1 - depth
            if depth > 24:
                put(x, y, base)
                continue
            reflected = scene[src_y][min(max(x + ripple[y % len(ripple)], 0), W - 1)]
            put(x, y, nearest(mix(reflected, base, min(0.6 + depth * 0.016, 0.95)), WATER_TONES))
    # Light catching the water's edge along the far shore.
    for x in range(W):
        if x % 7 not in (0, 4) and scene[HORIZON - 1][x] in (HILL, HILL_SH, TREE_FAR, TREE_FAR_HI, SAGE_DK):
            put(x, HORIZON, WATER_HI)
    # Glints: a column of sunlight under the sun, and streaks across the pond.
    random.seed(11)
    for y in range(HORIZON + 1, H - 6):
        if y % 2 == 0:
            half = max(1, 5 - (y - HORIZON) // 4)
            for x in range(SUN_X - half + (y % 4) - 1, SUN_X + half + (y % 4) - 1):
                put(x, y, SUN_HALO if abs(x - SUN_X) > half - 2 else SUN)
    for _ in range(26):
        x = random.randrange(0, W - 8)
        y = random.randrange(HORIZON + 2, H - 8)
        if SUN_X - 10 <= x <= SUN_X + 4:
            continue
        for i in range(random.choice((2, 3, 4, 5))):
            put(x + i, y, WATER_HI)
    # The bobber, and its ripples.
    for x, y in ((42, 68), (46, 68), (40, 69), (48, 69)):
        put(x, y, WATER_HI)
    put(44, 66, ROOF)
    put(44, 67, BOAT_RIM)

def lilies():
    for cx, cy, flower in ((10, 78, True), (18, 82, False), (7, 85, False), (24, 76, False), (31, 84, True),
                           (100, 80, False), (110, 84, True), (118, 77, False)):
        for x in range(cx - 3, cx + 4):
            for y in (cy, cy + 1):
                if y == cy + 1 and abs(x - cx) == 3:
                    continue
                if y == cy and abs(x - cx) == 3 and x < cx:
                    continue
                put(x, y, LILY_DK if y == cy + 1 else LILY)
        put(cx + 1, cy, get(cx + 1, cy + 3))   # the notch
        if flower:
            put(cx - 1, cy - 1, LILY_FLOWER)
            put(cx, cy - 1, BOAT_RIM)
            put(cx + 1, cy - 1, LILY_FLOWER)
            put(cx, cy - 2, LILY_FLOWER)

def reeds():
    random.seed(5)
    for base_x, count in ((1, 7), (119, 7)):
        for i in range(count):
            x = base_x + i + random.choice((0, 0, 1))
            height = random.randrange(7, 15)
            bottom = HORIZON + 4 if base_x > 100 else HORIZON + 2
            for y in range(bottom - height, bottom):
                put(x, y, REED if (y + i) % 5 else SAGE)
            if random.random() < 0.6:
                for y in range(bottom - height - 3, bottom - height):
                    put(x, y, CATTAIL)
                put(x, bottom - height - 4, REED)

def fireflies():
    for x, y in ((9, 54), (15, 58), (121, 55), (77, 52), (67, 49), (115, 47), (4, 49)):
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

def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--preview", help="also write an upscaled preview here")
    args = parser.parse_args()
    art = paint()
    target = Path(__file__).resolve().parents[2] / "app/src/main/res/drawable-nodpi/art_old_quarter.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    art.save(target, optimize=True)
    if args.preview:
        art.resize((W * 6, H * 6), Image.NEAREST).save(args.preview)

if __name__ == "__main__":
    main()
