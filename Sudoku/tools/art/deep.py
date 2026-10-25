"""
Chapter 8, The Deep: under the sea, as bright as noon, sunbeams slanting down through the water; a
swordfish turning in the light, corals of every colour, Anna sinking softly, and the golden fish leading
her down.
"""
import math
import random

from pixels import W, H, Canvas, band, dither, golden_fish, mix, rgb, stamp

WATER = [rgb(c) for c in ("#5E9DCB", "#6FB0D6", "#82C1DE", "#8ECBE2", "#86C0DE", "#77ABD6", "#6894CB", "#5C80C0")]
SURFACE, CAUSTIC = rgb("#74B3D8"), rgb("#B9E4EF")
RAY = rgb("#DFF6F5")
BUBBLE, BUBBLE_HI = rgb("#D5F1F6"), rgb("#F4FDFD")
SAND_HI, SAND, SAND_DK = rgb("#F5E3C8"), rgb("#E6CDB3"), rgb("#CDB2A6")
SWORD = {"b": rgb("#7E8FD2"), "m": rgb("#97A6DD"), "s": rgb("#DCE4F7"), "h": rgb("#B3C0EA"), "k": rgb("#5F6DB3"),
         "e": rgb("#3F4889"), "f": rgb("#8C9AD8")}
CORAL_PINK = (rgb("#FFC2CF"), rgb("#F59DB4"), rgb("#DB829E"))
CORAL_PEACH = (rgb("#FFDCC2"), rgb("#F6BC9E"), rgb("#DC9D8A"))
CORAL_LILAC = (rgb("#E3D2F8"), rgb("#C7AEEC"), rgb("#A991D6"))
CORAL_MINT = (rgb("#CFF3E2"), rgb("#A5DDC6"), rgb("#86C2AE"))
CORAL_BUTTER = (rgb("#FFF0BE"), rgb("#F4D795"), rgb("#D9B97F"))
SMALL_FISH = [rgb("#FFB6C6"), rgb("#B9E8D2"), rgb("#FFE3A3")]
ANNA_DRESS, ANNA_HAIR, ANNA_SKIN = rgb("#F59FB0"), rgb("#8E6A85"), rgb("#FBD9C6")

# The swordfish, facing left: a long bill, a tall sail of a fin, a crescent tail.
SWORDFISH = ["..............kk..........",
             ".............kmmk.........",
             "............kmmmmk.......k",
             "...........kbbbbbbk.....kk",
             "kkkkkkk.mmmbbbbbbbbbmm.kkk.",
             "......kebmmmmmmmmmmmmmmmk..",
             "........shhhhhhhhhhsssss.kk",
             "..........sssssss.f.......k",
             "..................f........"]


def water(cv):
    for y in range(H):
        for x in range(W):
            cv.put(x, y, band(WATER, y / (H - 6), x, y, sharpness=2.2))
    # The underside of the surface: a rippling band, criss-crossed with light.
    for x in range(W):
        for y in range(0, 3):
            cv.put(x, y, SURFACE)
        cv.put(x, round(2.5 + math.sin(x / 2.2) * 0.8), CAUSTIC if x % 5 else SURFACE)
        if x % 7 in (1, 2):
            cv.put(x, 4 + (x // 7) % 2, mix(cv.get(x, 4), CAUSTIC, 0.6))


def sunbeams(cv):
    """Shafts of light slanting down from the top right, fading as they go deeper."""
    for start, width in ((62, 4), (50, 3), (40, 5), (27, 3), (17, 2)):
        for y in range(3, 46):
            centre = start - y * 0.55
            fade = 1 - y / 46
            for x in range(math.floor(centre - width), math.ceil(centre + width) + 1):
                inside = 1 - abs(x - centre) / (width + 0.5)
                if inside > 0 and dither(x, y, inside * 0.9):
                    cv.put(x, y, mix(cv.get(x, y), RAY, 0.32 * fade))


def seabed(cv):
    for x in range(W):
        top = round(50 - 3.5 * math.sin((x + 6) / 9.0) - 1.2 * math.sin(x / 3.3))
        for y in range(top, H):
            # Ripples in the sand: short dashes, staggered.
            ripple = (y - top) % 4 == 2 and (x + (y // 4) * 3) % 7 < 3
            cv.put(x, y, SAND_HI if y == top else (SAND_DK if ripple else SAND))


def branch(cv, x, y, height, colours, seed):
    """Branching coral: a trunk that forks as it rises, lit on its left."""
    hi, mid, dk = colours
    random.seed(seed)
    tips = [(x, y)]
    for step in range(height):
        nxt = []
        for tx, ty in tips:
            cv.put(tx, ty, mid)
            cv.put(tx + 1, ty, dk)
            if step % 3 == 2 and len(tips) < 4:
                nxt.append((tx - 2, ty - 1))
                nxt.append((tx + 2, ty - 1))
            else:
                nxt.append((tx + random.choice((-1, 0, 0, 1)) * (step % 2), ty - 1))
        tips = nxt
    for tx, ty in tips:
        cv.put(tx, ty, hi)
        cv.put(tx, ty + 1, hi)


def dome(cv, cx, cy, rx, ry, colours):
    """A rounded brain coral, its grooves picked out."""
    hi, mid, dk = colours
    for y in range(int(cy - ry), int(cy) + 1):
        for x in range(int(cx - rx), int(cx + rx) + 1):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1:
                c = hi if (x - cx) + (y - cy) < -rx * 0.6 else mid
                if (x + 2 * y) % 4 == 0:
                    c = dk
                cv.put(x, y, c)


def fan(cv, cx, base, height, colours):
    """A sea fan: a lacy fan of fine branches spreading up from one stalk."""
    hi, mid, dk = colours
    for y in range(base - height, base + 1):
        half = (base - y) * 0.75
        for x in range(math.floor(cx - half), math.ceil(cx + half) + 1):
            if (x - cx) ** 2 + ((base - y) * 1.1) ** 2 > (height * 1.05) ** 2:
                continue
            if (x + y) % 2 == 0 or abs(x - cx) < 1:
                cv.put(x, y, hi if y < base - height * 0.7 else mid)
            elif (x * 3 + y) % 5 == 0:
                cv.put(x, y, dk)


def tubes(cv, x, base, heights, colours):
    hi, mid, dk = colours
    for i, h in enumerate(heights):
        for y in range(base - h, base + 1):
            cv.put(x + i * 2, y, mid)
            cv.put(x + i * 2 + 1, y, dk)
        cv.put(x + i * 2, base - h, hi)
        cv.put(x + i * 2 + 1, base - h, hi)


def corals(cv):
    fan(cv, 56, 47, 10, CORAL_LILAC)
    branch(cv, 44, 48, 9, CORAL_PINK, 1)
    tubes(cv, 60, 49, (5, 8, 4), CORAL_BUTTER)
    dome(cv, 50, 49, 4.5, 3.5, CORAL_PEACH)
    branch(cv, 33, 50, 7, CORAL_MINT, 2)
    dome(cv, 38, 51, 3.5, 2.5, CORAL_LILAC)
    fan(cv, 24, 50, 7, CORAL_PEACH)
    branch(cv, 14, 51, 6, CORAL_PINK, 3)
    tubes(cv, 6, 52, (4, 6), CORAL_MINT)


def swordfish(cv):
    stamp(cv, 31, 13, SWORDFISH, SWORD)


def small_fish(cv):
    for i, (x, y) in enumerate(((22, 30), (25, 32), (20, 33), (54, 33), (57, 35), (60, 32), (12, 22))):
        c = SMALL_FISH[i % len(SMALL_FISH)]
        cv.put(x, y, c)
        cv.put(x + 1, y, c)
        cv.put(x + 2, y - 1, c)
        cv.put(x + 2, y + 1, c)


def bubbles(cv):
    random.seed(4)
    for x, y in ((21, 18), (22, 15), (20, 12), (23, 9), (21, 6), (47, 40), (48, 36), (46, 32), (59, 41), (60, 37),
                 (13, 40), (14, 36)):
        cv.put(x, y, BUBBLE_HI if y < 20 else BUBBLE)
    for cx, cy in ((19, 4), (49, 28)):
        for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
            cv.put(cx + dx, cy + dy, BUBBLE)


def anna(cv):
    """Anna, sinking softly, her hair and dress lifted by the water."""
    for x, y in ((18, 18), (19, 17), (17, 19), (20, 18)):
        cv.put(x, y, ANNA_HAIR)
    cv.put(19, 19, ANNA_SKIN)
    cv.put(18, 19, ANNA_HAIR)
    for x, y in ((18, 20), (19, 20), (20, 20), (17, 21), (18, 21), (19, 21), (20, 21), (21, 21), (18, 22), (20, 22)):
        cv.put(x, y, ANNA_DRESS)
    cv.put(21, 19, ANNA_SKIN)
    cv.put(16, 20, ANNA_SKIN)
    cv.put(18, 23, ANNA_SKIN)
    cv.put(20, 23, ANNA_SKIN)


def the_golden_fish(cv):
    """Below her, leading the way down."""
    golden_fish(cv, 24, 25)


def paint():
    cv = Canvas()
    water(cv)
    sunbeams(cv)
    seabed(cv)
    corals(cv)
    small_fish(cv)
    swordfish(cv)
    bubbles(cv)
    anna(cv)
    the_golden_fish(cv)
    return cv.image()
