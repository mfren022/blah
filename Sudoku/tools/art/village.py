"""
Chapter 2, The Village: the fishing village asleep under a crescent moon, all in blues, with a net hung
out to dry across the lane, its knots glowing, and the golden fish swimming through the air above it.
"""
import math

from pixels import W, H, Canvas, band, golden_fish, mix, moon, rgb, sky, stars

GROUND = 38  # the lane in front of the houses

SKY = [rgb(c) for c in ("#6A72BC", "#757DC3", "#8289CB", "#9097D2", "#A0A5DA", "#B2B4E1")]
STAR, STAR_DIM = rgb("#ECEAFC"), rgb("#B8BCEC")
MOON, MOON_SH, MOON_HALO = rgb("#FFF7DA"), rgb("#F1E4C2"), rgb("#9CA2D9")
SMOKE = rgb("#C9CBEE")

# Moonlit walls, cool and pale, and pastel roofs gone bluish in the night.
WALLS = [
    (rgb("#E4E1F5"), rgb("#D2CEEC"), rgb("#B7B3DA")),  # lilac
    (rgb("#DCE7F4"), rgb("#C7D6EC"), rgb("#ADBEDB")),  # blue
    (rgb("#E8E0EE"), rgb("#D9CDE2"), rgb("#BDB0CF")),  # rose
    (rgb("#DCEDEC"), rgb("#C6E0DF"), rgb("#A9C8CB")),  # mint
]
ROOFS = [
    (rgb("#E8B5CB"), rgb("#D49BB8"), rgb("#B984A6")),  # rose
    (rgb("#A9B4E6"), rgb("#929DD8"), rgb("#7E88C6")),  # slate
    (rgb("#B6DCD3"), rgb("#98C6C0"), rgb("#7FADAD")),  # sage
    (rgb("#F0C6C0"), rgb("#DDAAB1"), rgb("#C2919E")),  # peach
]
WINDOW, WINDOW_HI = rgb("#8E92CC"), rgb("#A9ADDD")
LIT, LIT_HI = rgb("#FFE3A1"), rgb("#FFF4CE")
FRAME = rgb("#A08FB8")
DOOR, DOOR_DK = rgb("#A895C2"), rgb("#8F7DAE")
LANE_HI, LANE, LANE_DK = rgb("#BEB9E3"), rgb("#A8A3D4"), rgb("#9089C2")
POLE, POLE_DK = rgb("#B39BB5"), rgb("#957E9F")
ROPE = rgb("#C2B3D6")
NET = rgb("#D5D2F0")
KNOT = rgb("#FFF4C8")
BOAT, BOAT_DK, BOAT_RIM = rgb("#E7A9B6"), rgb("#CC8FA4"), rgb("#F8E8EC")


def night_sky(cv):
    sky(cv, SKY, GROUND)
    stars(cv, [(3, 3), (11, 7), (17, 2), (24, 9), (29, 4), (36, 12), (58, 13), (61, 4), (8, 14), (21, 15), (52, 2)],
          STAR_DIM)
    stars(cv, [(33, 2), (14, 10)], STAR, twinkle=[(56, 8), (26, 6)])
    moon(cv, 46, 8, 4.6, MOON, MOON_SH, MOON_HALO, bite=(-2.4, -1.4, 4.1))


def house(cv, left, right, wall_top, roof, walls, windows=(), lit=(), door=None, chimney=None):
    """A cottage from [left] to [right] on the lane, its gable roof overhanging by a pixel each side."""
    hi, mid, shade = walls
    peak_x = (left + right) / 2
    rise = (right - left) / 2 + 1
    for y in range(wall_top, GROUND):
        for x in range(left, right + 1):
            c = mid
            if x == left:
                c = hi
            elif x == right or y == wall_top:
                c = shade
            cv.put(x, y, c)
    # The gable: walls up into the triangle under the roof.
    for x in range(left, right + 1):
        top = wall_top - rise + abs(x - peak_x) + 1
        for y in range(math.ceil(top) + 1, wall_top):
            cv.put(x, y, hi if x < peak_x else mid)
    if chimney is not None:
        roof_line = round(wall_top - rise + abs(chimney - peak_x))
        for y in range(roof_line - 3, roof_line + 1):
            cv.put(chimney, y, roof[2])
            cv.put(chimney + 1, y, roof[1])
    # The roof, two pixels deep, lit on the left slope.
    r_hi, r_mid, r_dk = roof
    for x in range(left - 1, right + 2):
        top = round(wall_top - rise + abs(x - peak_x))
        left_side = x < peak_x
        cv.put(x, top, r_hi if left_side else r_mid)
        cv.put(x, top + 1, r_mid if left_side else r_dk)
    for wx, wy in windows:
        glow = (wx, wy) in lit
        for y in range(wy, wy + 3):
            for x in range(wx, wx + 3):
                c = (LIT_HI if (x, y) == (wx + 1, wy + 1) else LIT) if glow else (WINDOW_HI if y == wy else WINDOW)
                cv.put(x, y, c)
        for x in range(wx - 1, wx + 4):
            cv.put(x, wy + 3, FRAME)
    if door is not None:
        for y in range(GROUND - 5, GROUND):
            cv.put(door, y, DOOR)
            cv.put(door + 1, y, DOOR_DK)
        cv.put(door, GROUND - 3, LIT if lit else DOOR_DK)


def houses(cv):
    house(cv, 22, 31, 27, ROOFS[0], WALLS[2], windows=[(24, 30)], door=28)
    house(cv, 32, 42, 22, ROOFS[1], WALLS[0], windows=[(34, 25), (38, 25)], lit=[(38, 25)], door=35)
    house(cv, 43, 51, 26, ROOFS[2], WALLS[3], windows=[(45, 29)], door=48)
    house(cv, 52, 64, 19, ROOFS[3], WALLS[1], windows=[(54, 23), (58, 23), (54, 30)], door=59, chimney=60)
    # Smoke from the chimney, drifting left.
    for x, y in ((60, 8), (59, 7), (57, 6), (56, 6), (54, 5)):
        cv.put(x, y, SMOKE)


def lane(cv):
    for y in range(GROUND, H):
        for x in range(W):
            c = band([LANE, LANE_DK], (y - GROUND) / 20, x, y, sharpness=2)
            # Cobbles, in staggered rows, picked out by a light corner each.
            row = (y - GROUND - 1) // 3
            if y > GROUND and (y - GROUND - 1) % 3 == 0 and (x + row * 3) % 6 == 0 and y < GROUND + 16:
                c = LANE_HI
            cv.put(x, y, c)
    for x in range(W):
        cv.put(x, GROUND, LANE_HI)


NET_LEFT, NET_RIGHT = 3, 19  # the poles


def rope(x):
    """The net's rope, sagging between its two poles."""
    return 19 + 2.5 * math.sin(math.pi * (x - NET_LEFT) / (NET_RIGHT - NET_LEFT))


def net(cv):
    """A net hung out to dry on a rack at the end of the lane: a square mesh, glowing at every knot."""
    for x0 in (NET_LEFT, NET_RIGHT):
        for y in range(18, GROUND + 1):
            cv.put(x0, y, POLE if y > 18 else POLE_DK)
    for x in range(NET_LEFT + 1, NET_RIGHT):
        cv.put(x, round(rope(x)), ROPE)
    # The mesh hangs straight down from the rope, its rows following the sag, to a ragged hem.
    for x in range(NET_LEFT + 1, NET_RIGHT):
        top = round(rope(x))
        hem = top + 12 + (x % 3 == 1) - (x % 5 == 0)
        for y in range(top + 1, hem):
            on_column = (x - NET_LEFT) % 3 == 0
            on_row = (y - top) % 3 == 0
            if on_column and on_row:
                cv.put(x, y, KNOT)
            elif on_column or on_row:
                cv.put(x, y, mix(cv.get(x, y), NET, 0.75))


def boat(cv):
    """A dinghy pulled up on the lane for the night."""
    for x in range(44, 55):
        cv.put(x, GROUND + 1, BOAT_RIM)
    for x in range(43, 56):
        cv.put(x, GROUND + 2, BOAT)
    for x in range(45, 54):
        cv.put(x, GROUND + 3, BOAT_DK)


def the_golden_fish(cv):
    """Swimming through the air above the net, a trail of glints behind it."""
    golden_fish(cv, 31, 12)
    for x, y in ((40, 12), (43, 13), (39, 14)):
        cv.put(x, y, STAR)


def paint():
    cv = Canvas()
    night_sky(cv)
    houses(cv)
    lane(cv)
    net(cv)
    boat(cv)
    the_golden_fish(cv)
    return cv.image()
