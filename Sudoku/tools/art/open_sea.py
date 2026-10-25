"""
Chapter 5, The Open Sea: no land anywhere, only a great moon and the silver road it lays on the water,
Anna's boat small upon it, a whale diving alongside, and the golden fish waiting just ahead.
"""
import math

from pixels import W, H, Canvas, band, dither, golden_fish, mix, moon, rgb, sky, stamp, stars

HORIZON = 32
MOON_X, MOON_Y = 50, 12

SKY = [rgb(c) for c in ("#5762B0", "#616DB8", "#6D7ABF", "#7B88C8", "#8B98D1", "#9DAADA", "#B2BCE3")]
STAR, STAR_DIM = rgb("#EEF0FD"), rgb("#B3BBE9")
MOON, MOON_SH, MOON_HALO = rgb("#FFF9E6"), rgb("#F1E8CF"), rgb("#9EA9DC")
CRATER = rgb("#EDE2C8")
CLOUD_HI, CLOUD, CLOUD_SH = rgb("#E1D3EE"), rgb("#C9BBE5"), rgb("#AFA4D6")
SEA = [rgb(c) for c in ("#8494D0", "#7586C7", "#6676BC")]
SWELL = rgb("#9AA8DB")
SILVER, SILVER_HI = rgb("#E3E6F7"), rgb("#FFFCEC")
WHALE_HI, WHALE, WHALE_DK = rgb("#E4E5F8"), rgb("#C3C7EE"), rgb("#A3A9DE")
SPRAY = rgb("#E9EDFC")
BOAT, ANNA, ANNA_HAIR = rgb("#6C68A8"), rgb("#7A72B0"), rgb("#625C9B")


def night(cv):
    sky(cv, SKY, HORIZON)
    stars(cv, [(3, 4), (9, 9), (15, 3), (21, 11), (27, 6), (33, 2), (38, 9), (30, 16), (12, 17), (5, 22),
               (18, 23), (61, 26), (63, 3), (24, 1)], STAR_DIM)
    stars(cv, [(36, 4)], STAR, twinkle=[(19, 6), (61, 21)])
    moon(cv, MOON_X, MOON_Y, 7.4, MOON, MOON_SH, MOON_HALO)
    for x, y in ((47, 9), (48, 9), (52, 14), (53, 14), (53, 13), (46, 14), (51, 8)):
        cv.put(x, y, CRATER)
    # A second, fainter ring of light around the moon.
    for y in range(MOON_Y - 12, MOON_Y + 13):
        for x in range(MOON_X - 12, MOON_X + 13):
            d = math.hypot(x - MOON_X, y - MOON_Y)
            if 9.2 < d < 11.5 and dither(x, y, 0.3) and 0 <= y < HORIZON:
                cv.put(x, y, mix(cv.get(x, y), MOON_HALO, 0.6))


def streak(cv, x0, x1, y, lit_from):
    """A long thin cloud, lit along its top edge, thinning at both ends."""
    for x in range(x0, x1 + 1):
        t = (x - x0) / max(1, x1 - x0)
        thick = 2 if 0.15 < t < 0.85 else 1
        for dy in range(thick):
            if t < 0.08 and dither(x, y + dy, 0.5) is False:
                continue
            cv.put(x, y + dy, CLOUD_HI if dy == 0 and x >= lit_from else (CLOUD if dy == 0 else CLOUD_SH))


def clouds(cv):
    streak(cv, 38, 63, 16, 40)
    streak(cv, 30, 47, 19, 32)
    streak(cv, 4, 22, 13, 4)
    streak(cv, 10, 26, 25, 12)


def sea(cv):
    for y in range(HORIZON, H):
        for x in range(W):
            c = band(SEA, (y - HORIZON) / (H - HORIZON - 10), x, y, sharpness=2)
            if (x * 3 + y * 5) % 29 == 0 and y < HORIZON + 26:
                c = SWELL
            cv.put(x, y, c)
    for x in range(W):
        cv.put(x, HORIZON, SWELL if x % 5 else SEA[0])


def moon_road(cv):
    """The moon's road on the water, straight towards us, breaking into silver at its edges."""
    for y in range(HORIZON, H):
        d = y - HORIZON
        half = 1.2 + d * 0.38
        wobble = round(math.sin(y * 1.7) * 0.8)
        for x in range(math.floor(MOON_X - half), math.ceil(MOON_X + half) + 1):
            edge = 1 - abs(x - MOON_X - wobble) / (half + 0.5)
            if edge <= 0:
                continue
            if edge > 0.5 and (x + y) % 3:
                cv.put(x, y, SILVER_HI if d < 18 else SILVER)
            elif (x + 2 * y) % 4 in (0, 1):
                cv.put(x, y, SILVER)


TAIL = ["h...................h",
        "hh.................hh",
        ".hmh.............hmh.",
        ".hmmmh.........hmmmd.",
        "..hmmmmh.....hmmmmd..",
        "...hmmmmmh.hmmmmmd...",
        "....dmmmmmmmmmmmd....",
        "......ddmmmmmdd......",
        "........hmmmd........",
        "........hmmmd........",
        "........hmmmd........",
        ".......hmmmmmd......."]


def whale(cv):
    """The whale's tail, raised as it dives, water pouring from its flukes."""
    stamp(cv, 22, 20, TAIL, {"h": WHALE_HI, "m": WHALE, "d": WHALE_DK})
    for x, y in ((22, 22), (21, 24), (22, 26), (21, 28), (42, 22), (43, 24), (42, 26), (43, 29), (26, 27),
                 (38, 27), (24, 30), (40, 31)):
        cv.put(x, y, SPRAY)
    # Foam ringing it where it goes under.
    for x in range(26, 39):
        if x not in range(29, 35) and x % 3 != 1:
            cv.put(x, HORIZON + 1, SPRAY)
    for x in (28, 36):
        cv.put(x, HORIZON + 2, SPRAY)


def boat(cv):
    """Anna's boat on the moon's road, dark against the silver."""
    for x in range(45, 56):
        cv.put(x, 41, BOAT)
    for x in range(46, 55):
        cv.put(x, 42, BOAT)
    for x in range(47, 54):
        cv.put(x, 43, BOAT)
    cv.put(44, 40, BOAT)
    cv.put(56, 40, BOAT)
    cv.put(50, 38, ANNA_HAIR)
    cv.put(50, 39, ANNA)
    cv.put(51, 39, ANNA)
    cv.put(50, 40, ANNA)
    cv.put(51, 40, ANNA)
    cv.line(42, 43, 48, 40, BOAT)
    cv.line(59, 43, 53, 40, BOAT)


def the_golden_fish(cv):
    """Waiting just ahead, where the road meets the sky."""
    golden_fish(cv, 52, 33)


def paint():
    cv = Canvas()
    night(cv)
    clouds(cv)
    sea(cv)
    moon_road(cv)
    whale(cv)
    boat(cv)
    the_golden_fish(cv)
    return cv.image()
