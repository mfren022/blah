"""
Chapter 6, The Kites: a warm wind full of every kite the village children ever lost, their strings
gathered in Anna's hands as her boat rises out of the clouds, and the golden fish flying among them.
"""
import math

from pixels import W, H, Canvas, blob, dither, golden_fish, mix, rgb, sky, stars

HOLD_X, HOLD_Y = 37, 31  # where Anna holds the strings

SKY = [rgb(c) for c in ("#6C73BD", "#7A7EC3", "#8B8BCA", "#9F98D0", "#B5A6D5", "#CBB3D8", "#DFC1D8")]
STAR = rgb("#ECE8FB")
STRING = rgb("#EFE6F4")
KITES = {
    "rose": (rgb("#FFD2DC"), rgb("#F2A9BD"), rgb("#D98AA3")),
    "butter": (rgb("#FFF0BC"), rgb("#F2D58F"), rgb("#D9B677")),
    "mint": (rgb("#CFF2E1"), rgb("#A2D9C3"), rgb("#7FBCA7")),
    "sky": (rgb("#D3E3FA"), rgb("#A6C3EE"), rgb("#86A5DA")),
    "lilac": (rgb("#E6D9F8"), rgb("#C3AEEA"), rgb("#A38ED4")),
    "peach": (rgb("#FFDCC8"), rgb("#F3B99B"), rgb("#D99A80")),
}
BOWS = [rgb("#FFB7C8"), rgb("#FFE59E"), rgb("#A9E3CB"), rgb("#B8D0F8")]
CLOUD_HI, CLOUD, CLOUD_MID, CLOUD_SH = rgb("#FFF0E8"), rgb("#F6DCE4"), rgb("#E7CCE2"), rgb("#CDB8DE")
BOAT_RIM, BOAT, BOAT_DK = rgb("#FDF2D6"), rgb("#F1D79F"), rgb("#D6B48C")
ANNA_DRESS, ANNA_HAIR, ANNA_SKIN = rgb("#F59FB0"), rgb("#8E6A85"), rgb("#FBD9C6")


def warm_sky(cv):
    sky(cv, SKY, 52)
    stars(cv, [(4, 3), (11, 8), (20, 2), (27, 7), (9, 15), (2, 21), (63, 9)], STAR)


def kite(cv, cx, top, height, width, colours, tail):
    """A diamond kite, its spars crossed, with a tail of bows streaming out to the left."""
    light, mid, spar = colours
    cross = top + round(height / 3)
    for y in range(top, top + height + 1):
        if y <= cross:
            half = width * (y - top) / max(1, cross - top)
        else:
            half = width * (top + height - y) / (top + height - cross)
        for x in range(math.floor(cx - half), math.ceil(cx + half) + 1):
            if abs(x - cx) > half + 0.25:
                continue
            c = light if x < cx else mid
            if x == cx or y == cross:
                c = spar
            cv.put(x, y, c)
    # The string down to Anna: a faint thread.
    n = max(abs(HOLD_X - cx), abs(HOLD_Y - top - height), 1)
    for i in range(1, n):
        x = round(cx + (HOLD_X - cx) * i / n)
        y = round(top + height + (HOLD_Y - top - height) * i / n)
        cv.put(x, y, mix(cv.get(x, y), STRING, 0.4))
    # The tail: a thread with a bow every few pixels, blown out in a curve.
    x, y = cx, top + height
    for i in range(1, tail + 1):
        x -= 1
        y += 1 if i % 3 else 0
        bow = i % 3 == 0
        cv.put(x, y + round(math.sin(i / 1.6)), BOWS[(i // 3) % len(BOWS)] if bow else spar)
        if bow:
            cv.put(x, y + round(math.sin(i / 1.6)) - 1, BOWS[(i // 3) % len(BOWS)])


def kites(cv):
    # Furthest first, so the nearer ones overlap them.
    kite(cv, 17, 5, 5, 1.6, KITES["butter"], 6)
    kite(cv, 26, 11, 5, 1.6, KITES["peach"], 6)
    kite(cv, 33, 2, 6, 2.0, KITES["sky"], 7)
    kite(cv, 50, 15, 6, 2.0, KITES["lilac"], 7)
    kite(cv, 61, 12, 7, 2.5, KITES["mint"], 8)
    kite(cv, 44, 4, 7, 2.5, KITES["butter"], 9)
    kite(cv, 55, 1, 9, 3.4, KITES["rose"], 10)


def clouds(cv):
    """A bank of cloud, warm on top, that the boat is rising out of."""
    bank = blob([(2, 44, 6), (9, 41, 6.5), (17, 43, 6), (25, 40, 5.5), (31, 43, 5), (44, 42, 5.5), (51, 39, 6),
                 (58, 41, 6.5), (64, 38, 6), (38, 46, 6)])
    # Solid below its billowing top.
    tops = {x: min([y for bx, y in bank if bx == x] or [46]) for x in range(W)}
    bank = {(x, y) for x in range(W) for y in range(tops[x], H)}
    for x, y in bank:
        if not (0 <= x < W and 0 <= y < H):
            continue
        c = CLOUD_MID
        if (x, y - 1) not in bank:
            c = CLOUD_HI
        elif (x, y - 2) not in bank or ((x, y - 3) not in bank and dither(x, y, 0.5)):
            c = CLOUD
        elif y > 52 and dither(x, y, min(1.0, (y - 52) / 14)):
            c = CLOUD_SH
        cv.put(x, y, c)


def boat(cv):
    """Anna's boat, lifted clear of the cloud, Anna standing with the strings in her hands."""
    for x in range(31, 44):
        cv.put(x, 36, BOAT_RIM)
    for x in range(32, 43):
        cv.put(x, 37, BOAT)
    for x in range(34, 41):
        cv.put(x, 38, BOAT_DK)
    # Anna: hair blown back, arms raised to the strings.
    cv.put(37, 32, ANNA_SKIN)
    cv.put(36, 32, ANNA_HAIR)
    cv.put(35, 33, ANNA_HAIR)
    for y in (33, 34, 35):
        cv.put(37, y, ANNA_DRESS)
        cv.put(38, y, ANNA_DRESS)
    cv.put(36, 35, ANNA_DRESS)
    cv.put(38, 31, ANNA_SKIN)


def the_golden_fish(cv):
    """Flying ahead among the kites, like one more."""
    golden_fish(cv, 26, 18)


def paint():
    cv = Canvas()
    warm_sky(cv)
    clouds(cv)
    kites(cv)
    boat(cv)
    the_golden_fish(cv)
    return cv.image()
