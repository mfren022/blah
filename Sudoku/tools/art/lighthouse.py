"""
Chapter 4, The Lighthouse: the old striped lighthouse on its rocks, lit again after years, its beam swung
out across the sky and laying a path of light over the sea to the horizon, with Anna rowing along it.
"""
import math

from pixels import W, H, Canvas, band, cloud, dither, golden_fish, mix, rgb, sky, stars

HORIZON = 28
LAMP_X, LAMP_Y = 50, 7
VANISH_X = 12  # where the path of light meets the horizon

SKY = [rgb(c) for c in ("#5862B2", "#6470BA", "#7280C3", "#8290CC", "#94A1D6", "#A8B4E0")]
STAR, STAR_DIM = rgb("#EDEFFD"), rgb("#B4BCEA")
BEAM, BEAM_CORE = rgb("#E2E6FF"), rgb("#F6F4FF")
CLOUD_HI, CLOUD, CLOUD_SH = rgb("#CBD1F2"), rgb("#B4BCE8"), rgb("#9DA7DB")
SEA = [rgb(c) for c in ("#8F9FD6", "#7D8ECC", "#6D7EC1")]
SEA_HI, FOAM = rgb("#B9C6EE"), rgb("#EEF1FD")
GLINT, GLINT_HI = rgb("#F3EFCF"), rgb("#FFFBE6")
WHITE, WHITE_SH = rgb("#F1F0FB"), rgb("#D3D2EE")
STRIPE, STRIPE_SH = rgb("#F2A9B6"), rgb("#D78E9F")
CAP, CAP_DK = rgb("#E895A6"), rgb("#C77B90")
RAIL = rgb("#B9A4C6")
LAMP, LAMP_CORE, LAMP_HALO = rgb("#FFEFB0"), rgb("#FFFCE8"), rgb("#F5E3C3")
ROCK_HI, ROCK, ROCK_DK = rgb("#B9B4DE"), rgb("#9F99CC"), rgb("#8680B8")
COTTAGE, COTTAGE_SH, ROOF, ROOF_DK = rgb("#E3DEF3"), rgb("#C9C2E2"), rgb("#B3A3CF"), rgb("#9787B8")
WINDOW_DARK = rgb("#7E82BE")
BOAT, BOAT_DK, BOAT_RIM, OAR = rgb("#F1D79F"), rgb("#D6B48C"), rgb("#FDF2D6"), rgb("#E0C7A8")
ANNA, ANNA_HAIR = rgb("#F7B7B0"), rgb("#9A7186")


def night(cv):
    sky(cv, SKY, HORIZON)
    stars(cv, [(3, 18), (9, 23), (14, 20), (21, 24), (27, 21), (33, 19), (38, 25), (60, 20), (57, 26), (62, 13)],
          STAR_DIM)
    stars(cv, [(24, 16)], STAR, twinkle=[(35, 22)])


def beam(cv):
    """The beam, swung out to the left: a wedge of light from the lamp, widening to the sky's edge."""
    for x in range(0, LAMP_X):
        reach = (LAMP_X - x) / LAMP_X
        top = LAMP_Y - 1 - reach * 6
        bottom = LAMP_Y + 1 + reach * 5.5
        core = LAMP_Y - reach * 0.6
        for y in range(math.floor(top) - 1, math.ceil(bottom) + 2):
            if not 0 <= y < HORIZON:
                continue
            inside = min(y - top, bottom - y)
            if inside < -0.5:
                continue
            # Brightest along its core and near the lamp, softening to a dithered edge.
            strength = 0.75 * (1 - reach * 0.5) * min(1.0, (inside + 0.5) / 2.0)
            if abs(y - core) < 1 and reach < 0.85:
                cv.put(x, y, mix(cv.get(x, y), BEAM_CORE, 0.8 * (1 - reach * 0.7)))
            elif inside >= 1.5 or dither(x, y, 0.5):
                cv.put(x, y, mix(cv.get(x, y), BEAM, strength))


def clouds(cv):
    cloud(cv, [(10, 12, 1.5), (14, 11, 2.2), (18, 11.6, 1.8), (22, 12.4, 1.2)], 12, CLOUD_HI, CLOUD, CLOUD_SH)
    cloud(cv, [(28, 20, 1.2), (31, 19.4, 1.7), (34, 20, 1.2)], 20, CLOUD_HI, CLOUD, CLOUD_SH)


def sea(cv):
    for y in range(HORIZON, H):
        for x in range(W):
            c = band(SEA, (y - HORIZON) / (H - HORIZON - 8), x, y, sharpness=2)
            # Long low swells, catching the light.
            if (x * 2 + y * 7) % 23 == 0 and y < HORIZON + 24:
                c = SEA_HI
            cv.put(x, y, c)
    for x in range(W):
        cv.put(x, HORIZON, SEA_HI if x % 7 in (2, 3, 4) else SEA[0])


def light_path(cv):
    """The beam's path on the water: from the horizon, widening towards us, broken into glints."""
    for y in range(HORIZON + 1, H):
        d = y - HORIZON
        centre = VANISH_X + d * 1.15
        half = 0.4 + d * 0.42
        for x in range(math.floor(centre - half), math.ceil(centre + half) + 1):
            edge = 1 - abs(x - centre) / (half + 0.5)
            if edge <= 0:
                continue
            if (x + 2 * y) % 5 in (0, 1) or (edge > 0.6 and (x + y) % 2 == 0):
                cv.put(x, y, GLINT_HI if edge > 0.55 and d < 22 else GLINT)


def tower_half_width(y):
    """The tower tapers from the gallery down to the rocks."""
    return 2.5 + (y - 11) * 0.12


def lighthouse(cv):
    # Cap: a dome with a ball on top.
    cv.put(LAMP_X, 0, CAP_DK)
    for x in range(LAMP_X - 2, LAMP_X + 3):
        cv.put(x, 1, CAP if x <= LAMP_X else CAP_DK)
    for x in range(LAMP_X - 3, LAMP_X + 4):
        cv.put(x, 2, CAP if x <= LAMP_X else CAP_DK)
    # The lantern room: glass, lit, between its frames.
    for y in range(3, 9):
        for x in range(LAMP_X - 3, LAMP_X + 4):
            if x in (LAMP_X - 3, LAMP_X + 3):
                c = RAIL
            elif abs(x - LAMP_X) <= 1 and 4 <= y <= 7:
                c = LAMP_CORE
            else:
                c = LAMP
            cv.put(x, y, c)
    # The lamp's halo, over the beam.
    for y in range(1, 12):
        for x in range(LAMP_X - 7, LAMP_X + 8):
            d = math.hypot((x - LAMP_X) * 0.8, y - 5.5)
            if 4 < d < 6.5 and cv.get(x, y) not in (CAP, CAP_DK, LAMP, LAMP_CORE, RAIL) and dither(x, y, 0.45):
                cv.put(x, y, mix(cv.get(x, y), LAMP_HALO, 0.5))
    # The gallery, with its railing.
    for x in range(LAMP_X - 5, LAMP_X + 6):
        cv.put(x, 9, RAIL if x % 2 else WHITE_SH)
        cv.put(x, 10, WHITE_SH)
    # The tower: white and rose bands, lit on the left.
    for y in range(11, 31):
        half = tower_half_width(y)
        left, right = round(LAMP_X - half), round(LAMP_X + half)
        stripe = ((y - 11) // 5) % 2 == 1
        for x in range(left, right + 1):
            lit = x < LAMP_X + half * 0.3
            if stripe:
                c = STRIPE if lit else STRIPE_SH
            else:
                c = WHITE if lit else WHITE_SH
            cv.put(x, y, c)
    # A window halfway up, and the door at the foot.
    for y in (19, 20):
        cv.put(LAMP_X, y, WINDOW_DARK)
    for y in (27, 28, 29, 30):
        cv.put(LAMP_X - 1, y, WINDOW_DARK)
        cv.put(LAMP_X, y, WINDOW_DARK)


def keepers_cottage(cv):
    """The keeper's cottage, empty for years: its window dark."""
    for y in range(25, 31):
        for x in range(55, 64):
            cv.put(x, y, COTTAGE_SH if y == 25 or x == 63 else COTTAGE)
    for i, y in enumerate(range(21, 25)):
        for x in range(54 + i, 64):
            cv.put(x, y, ROOF if y < 24 or x % 3 else ROOF_DK)
    for y in (26, 27):
        for x in (58, 59):
            cv.put(x, y, WINDOW_DARK)


def rocks(cv):
    for cx, cy, r in ((44, 32, 4.5), (52, 33, 5.0), (60, 32, 4.5), (39, 34, 2.8), (63, 35, 3.5), (48, 35, 3.6)):
        for y in range(int(cy - r), int(cy + r) + 1):
            for x in range(int(cx - r), int(cx + r) + 1):
                if (x - cx) ** 2 + ((y - cy) * 1.4) ** 2 <= r * r and y >= HORIZON:
                    shade = (x - cx) + (y - cy)
                    cv.put(x, y, ROCK_HI if shade < -r * 0.6 else (ROCK if shade < r * 0.5 else ROCK_DK))
    # Foam where the swell breaks on them.
    for x, y in ((36, 35), (37, 36), (40, 37), (44, 38), (47, 39), (51, 39), (55, 38), (58, 38), (61, 39),
                 (35, 36), (42, 38)):
        cv.put(x, y, FOAM)


def rowing_boat(cv):
    """Anna, rowing out along the path, small against the sea."""
    for x in range(23, 32):
        cv.put(x, 41, BOAT_RIM)
    for x in range(24, 31):
        cv.put(x, 42, BOAT)
    for x in range(25, 30):
        cv.put(x, 43, BOAT_DK)
    cv.put(27, 39, ANNA_HAIR)
    cv.put(27, 40, ANNA)
    cv.put(28, 40, ANNA)
    cv.line(22, 43, 26, 40, OAR)
    cv.line(33, 43, 29, 40, OAR)


def the_golden_fish(cv):
    """Ahead of the boat on the path of light."""
    golden_fish(cv, 15, 33)


def paint():
    cv = Canvas()
    night(cv)
    clouds(cv)
    beam(cv)
    sea(cv)
    light_path(cv)
    rocks(cv)
    keepers_cottage(cv)
    lighthouse(cv)
    rowing_boat(cv)
    the_golden_fish(cv)
    return cv.image()
