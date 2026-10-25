"""
Chapter 7, The Stars: above the clouds, stars close enough to touch, set out in groups of nine like the
boxes of a sudoku; cranes gliding past in a long white line; Anna's boat on the sea of cloud below.
"""
import math
import random

from pixels import W, H, Canvas, band, blob, dither, golden_fish, mix, rgb, sky, stamp, stars

CLOUD_TOP = 40

SKY = [rgb(c) for c in ("#5A5EAD", "#6064B2", "#686CB8", "#7276BE", "#7E81C5", "#8C8ECC", "#9C9CD3")]
STAR, STAR_HI, STAR_DIM = rgb("#F4F2FF"), rgb("#FFFDF0"), rgb("#ABAFE6")
MILKY, MILKY_PINK = rgb("#8E8CCB"), rgb("#A493CC")
CRANE = {"w": rgb("#FBFAFF"), "s": rgb("#DCDDF4"), "k": rgb("#5D5E9E"), "c": rgb("#FF9EB0"), "l": rgb("#B7B4DA")}
CLOUD_HI, CLOUD, CLOUD_MID, CLOUD_SH = rgb("#E6E3F8"), rgb("#CDC9EE"), rgb("#B7B3E3"), rgb("#A19ED6")
BOAT, BOAT_DK, BOAT_RIM = rgb("#F1D79F"), rgb("#D6B48C"), rgb("#FDF2D6")
ANNA, ANNA_HAIR = rgb("#F59FB0"), rgb("#8E6A85")

# A crane gliding left, wings up and wings down.
CRANE_UP = ["......kk...",
            ".....wwk...",
            "....wws....",
            "cwwwwwwsll.",
            "..........l"]
CRANE_DOWN = ["cwwwwwwsll.",
              "....wws...l",
              ".....wwk...",
              "......kk..."]


def night(cv):
    sky(cv, SKY, CLOUD_TOP + 4)
    # The Milky Way, a soft band from top left down to the right, thick with faint stars.
    random.seed(7)
    for y in range(0, CLOUD_TOP):
        for x in range(W):
            d = abs((y - 4) - (x * 0.42)) / 7.0
            if d < 1:
                amount = (1 - d) * 0.55
                if dither(x, y, amount):
                    cv.put(x, y, mix(cv.get(x, y), MILKY_PINK if (x + y) % 7 == 0 else MILKY, 0.7))
                if random.random() < 0.07 * (1 - d):
                    cv.put(x, y, STAR_DIM)
    random.seed(8)
    for _ in range(34):
        cv.put(random.randrange(W), random.randrange(CLOUD_TOP - 4), STAR_DIM)


def nine(cv, x0, y0, step, bright, jitter_seed):
    """Nine stars, three by three like a box of a sudoku, each nudged a little off the grid."""
    random.seed(jitter_seed)
    for row in range(3):
        for column in range(3):
            x = x0 + column * step + random.choice((-1, 0, 0, 0, 1))
            y = y0 + row * step + random.choice((0, 0, 1))
            if bright:
                stars(cv, [], STAR, twinkle=[(x, y)])
                cv.put(x, y, STAR_HI)
            else:
                cv.put(x, y, STAR)


def constellations(cv):
    nine(cv, 46, 2, 6, True, 1)
    nine(cv, 27, 6, 4, False, 2)
    nine(cv, 7, 3, 3, False, 3)
    nine(cv, 38, 21, 3, False, 4)


def clouds(cv):
    """The sea of cloud far below, its tops silvered by starlight."""
    tops = blob([(x, CLOUD_TOP + 3 + 1.5 * math.sin(x / 3.1) + math.sin(x / 1.7), 3.2) for x in range(-2, W + 3, 4)])
    edge = {x: min([y for bx, y in tops if bx == x] or [CLOUD_TOP]) for x in range(W)}
    for x in range(W):
        for y in range(edge[x], H):
            depth = y - edge[x]
            c = CLOUD_HI if depth == 0 else (CLOUD if depth < 3 else band([CLOUD_MID, CLOUD_SH], (y - CLOUD_TOP) / 20, x, y))
            cv.put(x, y, c)


def cranes(cv):
    """A long line of cranes gliding past, from the top right down towards the clouds."""
    for i, (x, y) in enumerate(((51, 19), (39, 24), (27, 29), (15, 33))):
        stamp(cv, x, y, CRANE_UP if i % 2 == 0 else CRANE_DOWN, CRANE)


def boat(cv):
    """Anna's boat, riding the sea of cloud like water."""
    for x in range(44, 55):
        cv.put(x, 39, BOAT_RIM)
    for x in range(45, 54):
        cv.put(x, 40, BOAT)
    for x in range(46, 53):
        cv.put(x, 41, BOAT_DK)
    cv.put(49, 37, ANNA_HAIR)
    cv.put(49, 38, ANNA)
    cv.put(50, 38, ANNA)


def the_golden_fish(cv):
    """Swimming through the stars, ahead of the cranes."""
    golden_fish(cv, 31, 16)


def paint():
    cv = Canvas()
    night(cv)
    constellations(cv)
    clouds(cv)
    cranes(cv)
    boat(cv)
    the_golden_fish(cv)
    return cv.image()
