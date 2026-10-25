"""
Chapter 3, The Harbour: fishing boats adrift in the harbour under the moon, their moorings slipped, the
old rowing boat waiting at the steps, and the golden fish leaping between them towards the harbour mouth.
"""
from pixels import W, H, Canvas, cloud, golden_fish, mix, moon, reflect, rgb, sky, stars

SEA = 26     # the open sea's horizon, beyond the harbour wall
WATER_TOP = 30  # the harbour's water, below the wall

SKY = [rgb(c) for c in ("#5E6AB6", "#6876BE", "#7583C6", "#8491CE", "#95A1D7", "#A8B3E0")]
STAR, STAR_DIM = rgb("#EEF0FD"), rgb("#B6BFEC")
MOON, MOON_SH, MOON_HALO = rgb("#FFF8E0"), rgb("#F0E6C8"), rgb("#97A3DA")
CLOUD_HI, CLOUD, CLOUD_SH = rgb("#C9D0F1"), rgb("#B3BBE6"), rgb("#9EA8DB")
FAR_SEA, FAR_SEA_HI = rgb("#8796D0"), rgb("#A9B6E3")
STONE_HI, STONE, STONE_DK, STONE_XDK = rgb("#CBC9EA"), rgb("#B1AFDB"), rgb("#9896C9"), rgb("#8582B8")
BEACON, BEACON_SH, LAMP, LAMP_GLOW = rgb("#EEEDFB"), rgb("#CFCDEB"), rgb("#FFC4CC"), rgb("#E9B9D3")
WATER = [rgb(c) for c in ("#93A6DB", "#8197D2", "#7088C7")]
WATER_HI = rgb("#C4D2F2")
TONES = [rgb(c) for c in ("#A9B5E4", "#B7BEE6", "#A3A4D6", "#C9C6E6", "#E0DCE6")]
MAST, MAST_DK, RIGGING = rgb("#DCCCE4"), rgb("#B6A6C8"), rgb("#B4B8E6")
PENNANT, LANTERN = rgb("#F7A8B8"), rgb("#FFE6A6")
CABIN, CABIN_SH, PORTHOLE = rgb("#EFECFA"), rgb("#D2CDEB"), rgb("#8C90CC")
HULLS = {
    "mint": (rgb("#D5F0E6"), rgb("#A8D9C9"), rgb("#88BFB0")),
    "coral": (rgb("#FCD9D4"), rgb("#EFAFAE"), rgb("#D2909C")),
    "butter": (rgb("#FBEFC9"), rgb("#EED19C"), rgb("#D2B08C")),
}
OAR = rgb("#D9C2A6")


def night(cv):
    sky(cv, SKY, SEA)
    stars(cv, [(4, 2), (12, 6), (20, 3), (27, 9), (34, 4), (39, 12), (46, 2), (8, 13), (23, 15), (31, 18), (62, 15)],
          STAR_DIM)
    stars(cv, [(16, 9)], STAR, twinkle=[(41, 6), (61, 2)])
    moon(cv, 54, 9, 4.6, MOON, MOON_SH, MOON_HALO)
    cloud(cv, [(46, 13, 1.6), (49, 12, 2.2), (53, 12.6, 1.8), (57, 13.2, 1.4), (60, 13, 1.2)], 13, CLOUD_HI, CLOUD, CLOUD_SH)
    cloud(cv, [(9, 19, 1.4), (12, 18, 2.0), (15, 18.8, 1.5)], 19, CLOUD_HI, CLOUD, CLOUD_SH)


def harbour_wall(cv):
    """The far sea, then the breakwater across the harbour mouth, with a beacon at its end."""
    for y in range(SEA, WATER_TOP):
        for x in range(W):
            cv.put(x, y, FAR_SEA_HI if y == SEA and x % 6 in (1, 2) else FAR_SEA)
    for x in range(0, 41):
        top = 24 if x < 38 else 24 + (x - 37)
        for y in range(top, WATER_TOP):
            c = STONE
            if y == top:
                c = STONE_HI
            elif y == WATER_TOP - 1:
                c = STONE_XDK
            elif y == 27 or (x + (3 if y > 27 else 0)) % 6 == 0:
                c = STONE_DK  # two courses of blocks
            cv.put(x, y, c)
    # The beacon: a short white post on the end, its lamp pink.
    for y in range(17, 24):
        cv.put(35, y, BEACON)
        cv.put(36, y, BEACON_SH)
    for x, y in ((35, 15), (36, 15), (35, 16), (36, 16)):
        cv.put(x, y, LAMP)
    for x, y in ((34, 15), (37, 16), (35, 14), (36, 17), (34, 16), (37, 15)):
        cv.put(x, y, mix(cv.get(x, y), LAMP_GLOW, 0.6))


def water(cv):
    reflect(cv, WATER_TOP, WATER, TONES + [WATER_HI], fade=0.6, reach=14)
    # The moon's path, breaking into glints as it comes nearer.
    for y in range(WATER_TOP + 1, H, 2):
        half = 1 + (y - WATER_TOP) // 6
        shift = (y // 2) % 3 - 1
        for x in range(54 - half + shift, 54 + half + shift):
            if (x + y) % 3:
                cv.put(x, y, WATER_HI if abs(x - 54 - shift) < half - 1 else TONES[1])


def boat(cv, bow, stern, deck, colours, mast=None, cabin=None):
    """A fishing boat side-on, from its [bow] (pointed) to its [stern], its deck at row [deck]."""
    hi, mid, dk = colours
    left, right = min(bow, stern), max(bow, stern)
    # Its reflection first: the hull's colour, broken, a few rows down.
    for y in range(deck + 3, deck + 7):
        for x in range(left + 1, right):
            if (x + y) % 2 == 0 or y == deck + 3:
                cv.put(x, y, mix(cv.get(x, y), dk, 0.5))
    if mast is not None:
        for y in range(deck + 3, deck + 13):
            cv.put(mast + (1 if y % 4 == 1 else 0), y, mix(cv.get(mast, y), MAST_DK, 0.55))
        top = deck - 16
        cv.line(mast, top + 1, right, deck - 1, RIGGING)
        cv.line(mast, top + 1, left + 1, deck - 1, RIGGING)
        for y in range(top, deck):
            cv.put(mast, y, MAST if y > top else MAST_DK)
        cv.put(mast - 1, top + 5, MAST_DK)
        cv.put(mast + 1, top + 5, MAST_DK)
        # A pennant streaming from the top, and a lantern hung halfway down.
        for i, x in enumerate(range(mast - 1, mast - 4, -1)):
            cv.put(x, top + 1 + i // 2, PENNANT)
        cv.put(mast + 1, top + 9, LANTERN)
    if cabin is not None:
        cx0, cx1 = cabin
        for y in range(deck - 4, deck):
            for x in range(cx0, cx1 + 1):
                cv.put(x, y, CABIN_SH if x == cx1 or y == deck - 4 else CABIN)
        cv.put(cx0 + 1, deck - 2, LANTERN)
        cv.put(cx0 + 3, deck - 2, PORTHOLE)
    # The hull: a light rim, then tapering to the keel, the bow raked.
    for x in range(left, right + 1):
        cv.put(x, deck, hi)
    for x in range(left + 1, right + (0 if bow > stern else 1)):
        cv.put(x, deck + 1, mid)
    for x in range(left + 2, right - 1):
        cv.put(x, deck + 2, dk)
    tip = bow + (1 if bow > stern else -1)
    cv.put(tip, deck - 1, hi)


def boats(cv):
    boat(cv, bow=44, stern=61, deck=33, colours=HULLS["mint"], mast=49, cabin=(54, 58))
    boat(cv, bow=36, stern=22, deck=36, colours=HULLS["coral"], mast=30, cabin=(23, 26))


def rowing_boat(cv):
    """Grandfather's rowing boat, waiting by the steps, its oars shipped."""
    hi, mid, dk = HULLS["butter"]
    for y in range(44, 47):
        for x in range(9, 22):
            if (x + y) % 2 == 0:
                cv.put(x, y, mix(cv.get(x, y), dk, 0.45))
    for x in range(9, 21):
        cv.put(x, 41, hi)
    for x in range(10, 21):
        cv.put(x, 42, mid)
    for x in range(12, 19):
        cv.put(x, 43, dk)
    # The oars laid along the thwarts, blades out over the stern.
    for x in range(12, 24):
        cv.put(x, 40, OAR)
    cv.put(23, 41, OAR)


def ripples(cv):
    """Rings on the water where the boats are slowly turning."""
    for x, y in ((40, 37), (42, 38), (62, 37), (63, 36), (18, 39), (16, 40), (20, 40), (8, 42)):
        cv.put(x, y, WATER_HI)


def the_golden_fish(cv):
    """Leaping high over the harbour mouth, out past the wall, and the ring where it left the water."""
    golden_fish(cv, 39, 17, facing=1, leaping=True)
    for x, y in ((38, 31), (40, 32), (42, 31), (39, 30), (41, 30)):
        cv.put(x, y, WATER_HI)


def paint():
    cv = Canvas()
    night(cv)
    harbour_wall(cv)
    water(cv)
    boats(cv)
    rowing_boat(cv)
    ripples(cv)
    the_golden_fish(cv)
    return cv.image()
