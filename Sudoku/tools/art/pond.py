"""
Chapter 1, The Pond: Anna's fishing shed on stilts at the edge of the village pond on a summer evening,
seen across the water in two-point perspective. The sun is going down behind the hills, a willow hangs
over the near bank, lily pads float on water that mirrors it all, her boat waits at the jetty, and the
golden fish leaps under the lantern.

Painted at twenty times the grid's resolution, in smooth shapes drawn on layers: the far scene, the bank
and the willow, the shed and its jetty, then the water mirroring them all, and the small things on and
over it. Each of the grid's squares shows the part of the painting under it.
"""
import math
import random

import numpy as np
from PIL import Image

from hd import F, H, W, Canvas, Layer, mix, noise, paint_rgb, ramp, rgb, soft_mask, solid, xs, ys


# ---- Far away: sky, sun, clouds, birds, stars, hills, the far shore ----------------------------------

HORIZON = 28                          # eye level, where the far shore meets the water
SUN_X, SUN_Y = 22, 20
SKY = [rgb(c) for c in ("#3B3D8C", "#48468F", "#5A4C99", "#6E52A0", "#8659A5", "#A061A5", "#B968A0",
                        "#D17396", "#E5808C", "#F29684", "#F8AE7E", "#FBC67E", "#FDDB92", "#FEEBB4")]
SUN, SUN_HALO, SUN_GLOW = rgb("#FFF8E0"), rgb("#FFE9A6"), rgb("#FFD486")
CLOUD_LIT, CLOUD_WARM, CLOUD_MID, CLOUD_TOP = rgb("#FFD7AA"), rgb("#F7A28E"), rgb("#C77B9F"), rgb("#8F66A6")
STREAK = rgb("#FFDFA0")
BIRD = rgb("#3A2F63")
STAR = rgb("#F2EEFF")
FAR_HILLS, FAR_HILLS_LIT = rgb("#A88DC6"), rgb("#E6A9B4")
HILLS, HILLS_LIT, HILLS_DARK = rgb("#7469AE"), rgb("#E29A9E"), rgb("#5E5A9C")
TREES, TREES_DARK, TREES_LIT = rgb("#3B6763"), rgb("#2C5257"), rgb("#D0AE6A")
FAR_HOUSE, FAR_HOUSE_SHADE, FAR_ROOF, FAR_WINDOW = rgb("#C9A9B0"), rgb("#9E8494"), rgb("#9C4F5E"), rgb("#FFE07A")
SHORE = rgb("#4C6E58")


def sky(cv):
    y, x = ys(), xs()
    near_sun = np.clip(1 - np.hypot(x - SUN_X, (y - SUN_Y) * 1.5) / 26, 0, 1)
    t = np.clip(y / HORIZON + 0.22 * near_sun, 0, 1)
    # A little unevenness, like thin high haze, so the sky isn't a flat ramp.
    t = np.clip(t + (noise(9 * F, 1, 3) - 0.5) * 0.05, 0, 1)
    cv.px = ramp(SKY, t ** 1.05)
    # Soft rays fanning up from the sun.
    angle = np.arctan2(y - SUN_Y, x - SUN_X)
    rays = 0.5 + 0.5 * np.sin(angle * 23 + 1.3) * np.sin(angle * 9 + 0.4)
    reach = np.clip(1 - np.hypot(x - SUN_X, y - SUN_Y) / 30, 0, 1) ** 1.6
    cv.add(SUN_GLOW[None, None, :] * (rays * reach * 0.10 * (y < HORIZON))[..., None])


def sun(cv):
    y, x = ys(), xs()
    d = np.hypot(x - SUN_X, y - SUN_Y)
    glow = np.exp(-(d / 7.5) ** 2) * 0.55 + np.exp(-(d / 16) ** 2) * 0.25
    cv.add(SUN_GLOW[None, None, :] * glow[..., None])
    halo = np.clip((5.0 - d) / 1.2, 0, 1)
    cv.over(solid(SUN_HALO, halo * 0.9))
    disc = np.clip((3.4 - d) * F / 2.5, 0, 1)
    cv.over(solid(SUN, disc))


def cloud(cv, circles, bottom, seed, lit_side=1.0):
    """A cumulus of puffs, each lit from the low sun: gold on the side towards it, warm, then violet away
    from it; the lower puffs in front, and its foot flat and lit."""
    rnd = random.Random(seed)
    puffs = list(circles)
    for cx, cy, r in circles:
        for _ in range(int(5 + r * 3)):
            a = rnd.uniform(math.pi * 1.0, math.pi * 2.0)
            rr = r * rnd.uniform(0.3, 0.6)
            puffs.append((cx + math.cos(a) * r * 0.8, cy + math.sin(a) * r * 0.75, rr))
    puffs.sort(key=lambda p: p[1] - p[2])          # from the top down, so lower puffs come in front
    y, x = ys(), xs()
    for cx, cy, r in puffs:
        x0, x1 = max(0, int((cx - r - 0.5) * F)), min(W, int((cx + r + 0.5) * F) + 1)
        y0, y1 = max(0, int((cy - r - 0.5) * F)), min(H, int(min(bottom, cy + r + 0.5) * F) + 1)
        if x0 >= x1 or y0 >= y1:
            continue
        yy, xx = y[y0:y1, :], x[:, x0:x1]
        d = np.hypot(xx - cx, (yy - cy) / 0.92)
        alpha = np.clip((r - d) * F / 2.0, 0, 1) * np.clip((bottom - yy) * F / 2.0, 0, 1)
        lx, ly = SUN_X - cx, SUN_Y - cy
        n = math.hypot(lx, ly)
        lx, ly = lx / n, ly / n
        s = np.clip(((xx - cx) * lx + (yy - cy) * ly) / r, -1, 1)
        # Rounded: the middle of a puff faces us, its rim turns towards or away from the light.
        s = s * 0.75 + 0.25 * np.clip(1 - d / r, 0, 1) - 0.05
        tone = ramp([CLOUD_TOP, CLOUD_MID, CLOUD_WARM, CLOUD_LIT], (s + 1) / 2 * 0.9 + 0.08 * lit_side)
        # Lit along the flat foot.
        foot = np.clip(1 - (bottom - yy) / 0.7, 0, 1)
        tone = mix(tone, CLOUD_LIT, (foot * 0.8)[..., None])
        patch = cv.px[y0:y1, x0:x1]
        cv.px[y0:y1, x0:x1] = patch * (1 - alpha[..., None]) + tone * alpha[..., None]


def streak(cv, x0, x1, y, thick, seed):
    layer = Layer()
    rnd = random.Random(seed)
    for i in range(10):
        a = x0 + (x1 - x0) * rnd.random()
        b = min(x1, a + (x1 - x0) * rnd.uniform(0.3, 0.7))
        yy = y + rnd.uniform(-0.25, 0.25)
        layer.ellipse((a + b) / 2, yy, (b - a) / 2, thick * rnd.uniform(0.5, 1.0), (1, 1, 1), 0.7)
    mask = layer.rgba(0.08)[..., 3]
    x = xs()
    colour = np.where((np.abs(x - SUN_X) < 12)[..., None], STREAK, CLOUD_WARM) * np.ones((H, W, 1), np.float32)
    colour = mix(CLOUD_WARM, STREAK, np.clip(1 - np.abs(x - SUN_X) / 16, 0, 1)[..., None]) * np.ones((H, W, 1), np.float32)
    cv.over(paint_rgb(colour, mask * 0.9))


def clouds(cv):
    cloud(cv, [(44, 3.2, 1.8), (48, 2.4, 2.6), (53, 1.8, 3.2), (58, 2.6, 2.8), (62, 3.6, 2.2), (66, 3.4, 2.0)], 4.2, 1, 0.6)
    cloud(cv, [(24, 10.4, 1.7), (27, 9.4, 2.3), (30, 10.0, 1.8), (32, 10.6, 1.2)], 11.1, 2)
    cloud(cv, [(-1, 13.0, 1.6), (1, 13.2, 1.9), (5, 12.0, 2.6), (9, 12.6, 2.1), (12, 13.4, 1.5)], 14.2, 3)
    for i, (x0, x1, y) in enumerate(((1, 15, 18), (23, 34, 16), (6, 12, 21), (25, 31, 19), (36, 44, 17.5))):
        streak(cv, x0, x1, y, 0.32, 10 + i)


def birds(cv):
    layer = Layer()
    for bx, by, s in ((27, 13, 1.0), (31, 11, 0.85), (35, 14, 0.75), (29.5, 15.2, 0.6)):
        for side in (-1, 1):
            pts = []
            for k in range(9):
                t = k / 8
                pts.append((bx + side * t * 1.25 * s, by - math.sin(t * math.pi * 0.9) * 0.55 * s + t * 0.25 * s))
            layer.line(pts, BIRD, 0.16 * s)
        layer.ellipse(bx, by + 0.05, 0.16 * s, 0.12 * s, BIRD)
    cv.over(layer.rgba())


def stars(cv):
    y, x = ys(), xs()
    for sx, sy, b in ((21, 2, 1.0), (33, 4, 0.8), (9, 1, 0.9), (40, 1, 0.7), (27, 6, 0.6), (15, 4.5, 0.5), (37, 7.5, 0.4)):
        d = np.hypot(x - sx, y - sy)
        glint = np.exp(-(d / 0.18) ** 2) + 0.35 * np.exp(-(d / 0.6) ** 2)
        cross = (np.exp(-((x - sx) / 0.05) ** 2) * np.exp(-((y - sy) / 0.7) ** 2) + np.exp(-((y - sy) / 0.05) ** 2) * np.exp(-((x - sx) / 0.7) ** 2)) * 0.5
        cv.add(STAR[None, None, :] * (np.clip(glint + cross, 0, 1) * b * np.clip(1 - y / 9, 0, 1))[..., None])


def far_hills(x):
    return 23.0 - 3.6 * np.exp(-((x - 31) / 7.5) ** 2) - 2.4 * np.exp(-((x - 4) / 5.5) ** 2) - 1.2 * np.exp(-((x - 50) / 6) ** 2)


def near_hills(x):
    return 25.6 - 1.4 * np.sin(x / 6.0 + 1.1) - 0.5 * np.sin(x / 2.9) - 0.25 * np.sin(x / 1.3 + 2)


def hills(cv):
    y, x = ys(), xs()
    glow = np.clip(1 - np.abs(x - SUN_X) / 16, 0, 1)
    # The far range, pale in the haze, its ridge lit where the sun is behind it.
    ridge = far_hills(x)
    inside = np.clip((y - ridge) * F / 1.5, 0, 1) * (y <= HORIZON + 0.5)
    below = y - ridge
    colour = mix(FAR_HILLS, mix(FAR_HILLS, rgb("#C5A7D2"), 0.5), np.clip(1 - below / 4, 0, 1)[..., None])
    colour = mix(colour, FAR_HILLS_LIT, (np.clip(1 - below / 1.2, 0, 1) * glow * 0.85)[..., None])
    texture = noise(0.8 * F, 5, 3)
    colour = colour * (0.96 + 0.06 * texture[..., None])
    cv.over(paint_rgb(colour, inside))
    # The near range, darker, woods along its slopes.
    ridge = near_hills(x)
    inside = np.clip((y - ridge) * F / 1.5, 0, 1) * (y <= HORIZON + 0.5)
    below = y - ridge
    colour = mix(HILLS, HILLS_DARK, np.clip(below / 2.5, 0, 1)[..., None])
    colour = mix(colour, HILLS_LIT, (np.clip(1 - below / 0.6, 0, 1) * glow)[..., None])
    woods = noise(0.45 * F, 6, 3)
    colour = mix(colour, HILLS_DARK * 0.85, (np.clip((woods - 0.55) * 4, 0, 1) * np.clip(below / 1.5, 0, 1) * 0.6)[..., None])
    cv.over(paint_rgb(colour, inside))


def far_shore(cv, seed=4):
    rnd = random.Random(seed)
    trees = Layer()
    # A line of trees: big crowns along the shore, smaller ones crowding between them.
    crowns = []
    for x0, h, r in ((1, 0.6, 1.6), (4, 1.2, 1.9), (7, 0.4, 1.4), (10, 1.0, 1.7), (14, 0.5, 1.5), (31, 0.8, 1.8),
                     (34, 1.5, 2.2), (38, 0.6, 1.6), (41, 1.1, 1.9), (45, 0.4, 1.5), (49, 1.3, 2.1), (53, 0.7, 1.6),
                     (57, 1.2, 2.0), (61, 0.5, 1.7), (64, 0.9, 1.6), (18, 0.1, 1.0), (21, 0.0, 0.9), (17, -0.2, 0.8)):
        crowns.append((x0, HORIZON - 1.4 - h, r))
    for cx, cy, r in list(crowns):
        for _ in range(int(r * 6)):
            a = rnd.uniform(math.pi, 2 * math.pi)
            crowns.append((cx + math.cos(a) * r * 0.8, cy + math.sin(a) * r * 0.7, r * rnd.uniform(0.3, 0.55)))
    for cx, cy, r in crowns:
        trees.ellipse(cx, cy, r, r * 0.9, (1, 1, 1))
    trees.rect(-1, HORIZON - 1.2, 65, HORIZON + 0.4, (1, 1, 1))
    mask = trees.rgba()[..., 3]
    y, x = ys(), xs()
    inner = soft_mask(mask, 0.35)
    shade = noise(0.35 * F, 8, 2)
    colour = mix(TREES, TREES_DARK, np.clip(shade * 1.4 - 0.2, 0, 1)[..., None])
    near_sun = np.clip(1 - np.abs(x - SUN_X) / 20, 0, 1)
    rim = np.clip(mask - inner, 0, 1) * (y < HORIZON - 0.8)
    # Rimmed gold along their tops where the sun is behind them.
    top_edge = np.clip(mask - np.roll(mask, int(0.25 * F), axis=0), 0, 1)
    colour = mix(colour, TREES_LIT, (np.clip(top_edge * 1.5 + rim * 0.6, 0, 1) * (0.35 + 0.65 * near_sun))[..., None])
    cv.over(paint_rgb(colour, mask))
    # The cottage among them, its window lit, a little way off to the right of the sun.
    house = Layer()
    house.polygon([(24.6, HORIZON - 3.2), (28.6, HORIZON - 3.2), (28.6, HORIZON - 0.2), (24.6, HORIZON - 0.2)], FAR_HOUSE)
    house.polygon([(27.4, HORIZON - 3.2), (28.6, HORIZON - 3.2), (28.6, HORIZON - 0.2), (27.4, HORIZON - 0.2)], FAR_HOUSE_SHADE)
    house.polygon([(24.0, HORIZON - 3.0), (26.6, HORIZON - 5.4), (29.2, HORIZON - 3.0)], FAR_ROOF)
    house.polygon([(26.6, HORIZON - 5.4), (29.2, HORIZON - 3.0), (28.4, HORIZON - 3.0)], FAR_ROOF * 0.8)
    house.rect(27.9, HORIZON - 6.0, 28.4, HORIZON - 4.4, FAR_ROOF * 0.75)
    house.rect(25.4, HORIZON - 2.4, 26.2, HORIZON - 1.4, FAR_WINDOW)
    cv.over(house.rgba())
    d = np.hypot(x - 25.8, y - (HORIZON - 1.9))
    cv.add(FAR_WINDOW[None, None, :] * (np.exp(-(d / 0.9) ** 2) * 0.35)[..., None])
    # The shore itself.
    shore = np.clip(1 - np.abs(y - (HORIZON + 0.1)) * F / 3, 0, 1)
    cv.over(solid(SHORE, shore * 0.9))


def paint_far():
    cv = Canvas()
    sky(cv)
    sun(cv)
    stars(cv)
    clouds(cv)
    birds(cv)
    hills(cv)
    far_shore(cv)
    return cv


# ---- Near: the willow and the bank, the shed and its jetty, each on a layer of its own -------------

VP_LEFT, VP_RIGHT = (-60, HORIZON), (88, HORIZON)
CORNER, EAVE, FLOOR = 46, 18, 34
FRONT_LEFT = 33
PEAK = (39.5, 9.0)
WATER_LINE = 39

WILLOW = [rgb(c) for c in ("#2F5E45", "#3F7A4A", "#5A9A4E", "#7DB85A", "#A9D06A", "#D9E68A")]
BARK, BARK_DARK, BARK_LIT = rgb("#6B4E44"), rgb("#4A3636"), rgb("#9A6E58")
GRASS, GRASS_LIT, GRASS_DARK = rgb("#4F8F45"), rgb("#8CC255"), rgb("#36663E")
IRIS, IRIS_LIT, BUTTERCUP, POPPY = rgb("#7E58C8"), rgb("#B08AE8"), rgb("#F6D24A"), rgb("#E5483E")
STONE, STONE_LIT, STONE_DARK = rgb("#8D8AA0"), rgb("#B4B0C2"), rgb("#66637E")
PLANK_LIT, PLANK, PLANK_SEAM, PLANK_TOP = rgb("#F2B472"), rgb("#E29E5C"), rgb("#B9733F"), rgb("#A86B47")
SIDE_LIT, SIDE, SIDE_SEAM = rgb("#9C6A7A"), rgb("#87596C"), rgb("#6A4459")
ROOF_RIDGE, ROOF_LIT, ROOF, ROOF_SHADE, ROOF_LINE = (rgb("#F59468"), rgb("#C9504A"), rgb("#A8434C"),
                                                     rgb("#8C3A4C"), rgb("#6F3045"))
TRIM, TRIM_SHADE = rgb("#F7E6C6"), rgb("#C7A7A2")
DOOR, DOOR_SHADE, DOOR_GLOW, HANDLE = rgb("#2E8790"), rgb("#226670"), rgb("#FFC46B"), rgb("#FFE08A")
GLASS, GLASS_HI = rgb("#FFD772"), rgb("#FFF2BC")
SHUTTER, SHUTTER_SHADE = rgb("#3C8A86"), rgb("#2D6B6C")
NET, NET_KNOT = rgb("#E5D3A2"), rgb("#FFF1C8")
OAR, OAR_DARK = rgb("#D8AE72"), rgb("#B08454")
CHIMNEY, CHIMNEY_LIT, CHIMNEY_DARK = rgb("#9C8F98"), rgb("#C2B3B4"), rgb("#6F6672")
SMOKE, SMOKE_FAINT = rgb("#F6ECF4"), rgb("#D8C6E2")
POST, POST_LIT = rgb("#5B3E3C"), rgb("#8C6150")
DECK_LIT, DECK, DECK_EDGE = rgb("#E3A465"), rgb("#C98A52"), rgb("#7A5040")
FLOWER_BOX, FLOWER_PINK, LEAF = rgb("#7A4E3C"), rgb("#FF8FB2"), rgb("#5DA05A")


def toward(point, vanishing, x):
    (px, py), (vx, vy) = point, vanishing
    return py + (vy - py) * (x - px) / (vx - px)


def front_top(x): return toward((CORNER, EAVE), VP_LEFT, x)
def front_foot(x): return toward((CORNER, FLOOR), VP_LEFT, x)
def side_top(x): return toward((CORNER, EAVE), VP_RIGHT, x)
def side_foot(x): return toward((CORNER, FLOOR), VP_RIGHT, x)
def ridge(x): return toward(PEAK, VP_RIGHT, x)
def side_at(s): return CORNER + (VP_RIGHT[0] - CORNER) * s / (s + 40)
def water_line(x):
    return toward((CORNER, WATER_LINE), VP_LEFT if x < CORNER else VP_RIGHT, x)


def mask_of(points):
    layer = Layer()
    layer.polygon(points, (1, 1, 1))
    return layer.rgba()[..., 3]


# ---- The willow, and the bank under it -------------------------------------------------------------

def willow():
    rnd = random.Random(12)
    layer = Layer()
    # The trunk, leaning a little, with a branch out over the water and one back.
    trunk = [(2.3, 37.2), (2.9, 30), (3.4, 24), (4.0, 18), (4.6, 14), (5.6, 14), (5.4, 18), (5.0, 24), (4.8, 30), (5.6, 37.2)]
    layer.polygon(trunk, BARK)
    layer.polygon([(4.4, 15), (5.2, 14.4), (5.4, 18), (5.0, 24), (4.8, 30), (5.6, 37.2), (4.6, 37.2), (4.2, 30), (4.5, 24)], BARK_DARK)
    layer.polygon([(2.9, 30), (3.4, 24), (4.0, 18), (4.3, 18), (3.8, 24), (3.3, 30), (2.9, 37)], BARK_LIT, 0.8)
    layer.line([(5.0, 17.5), (7.5, 14.5), (10, 11.8), (12.5, 10.2)], BARK, 0.45)
    layer.line([(4.3, 19.5), (2.5, 17), (0.5, 14.5), (-0.5, 13.6)], BARK, 0.4)
    layer.line([(5.2, 15), (6.5, 10), (7.2, 6.5)], BARK, 0.35)
    # The crown: leaves crowding a dome over the trunk.
    for _ in range(2600):
        x = rnd.uniform(-1.5, 16)
        top = 1 + (x - 5) ** 2 * 0.07 + 0.9 * math.sin(x * 1.4)
        y = top + abs(rnd.gauss(0, 2.2))
        if y > top + 6.5:
            continue
        lit = (y - top) < 1.2 or rnd.random() < 0.18
        c = WILLOW[3] if lit else WILLOW[rnd.choice((1, 2, 2, 0))]
        if x >= 11:
            c = WILLOW[5] if rnd.random() < 0.45 else WILLOW[4]
        a = rnd.uniform(0, math.pi)
        layer.ellipse(x, y, 0.32, 0.16, c)
    # Strands hanging from it in curtains, the evening showing between them.
    for k in range(420):
        x = rnd.uniform(-1.5, 16.5)
        if (x % 2.6) > 1.9 and rnd.random() < 0.75:
            continue
        top = 1 + (x - 5) ** 2 * 0.07 + 0.9 * math.sin(x * 1.4) + rnd.uniform(1.0, 4.5)
        bottom = 33.5 - abs(x - 5) * 0.9 + rnd.uniform(-4.5, 1.5)
        sway = rnd.uniform(-0.5, 0.7)
        group = rnd.choice((0, 1, 2, 1))
        backlit = x >= 11
        y = top
        while y < bottom:
            t = (y - top) / max(1, bottom - top)
            sx = x + sway * t * t
            if backlit:
                c = WILLOW[5] if rnd.random() < 0.35 else WILLOW[4]
            else:
                c = WILLOW[min(group + (1 if rnd.random() < 0.2 else 0), 3)]
            layer.ellipse(sx + rnd.uniform(-0.07, 0.07), y, 0.075, 0.24, c)
            y += rnd.uniform(0.22, 0.4)
    return layer.rgba()


def bank():
    rnd = random.Random(31)
    layer = Layer()
    def top(x): return 31 + (max(0, x) / 18) ** 1.8 * 5
    pts = [(x / 4, top(x / 4)) for x in range(-4, 77)] + [(19.2, 37.4), (-1, 37.4)]
    layer.polygon(pts, GRASS)
    layer.polygon([(x / 4, top(x / 4) + 2.2) for x in range(-4, 77)] + [(19.2, 37.4), (-1, 37.4)], GRASS_DARK)
    # Blades of grass along its top, lit by the low sun.
    for _ in range(900):
        x = rnd.uniform(-1, 19)
        y0 = top(x) + rnd.uniform(0, 2.5)
        h = rnd.uniform(0.5, 1.6)
        lean = rnd.uniform(-0.4, 0.6)
        c = GRASS_LIT if rnd.random() < 0.45 else (GRASS if rnd.random() < 0.6 else GRASS_DARK)
        layer.polygon([(x - 0.07, y0), (x + 0.07, y0), (x + lean, y0 - h)], c)
    # Flowers among it.
    for x, y, c, lit in ((2, 31, IRIS, IRIS_LIT), (7, 31.2, BUTTERCUP, None), (10, 32, IRIS, IRIS_LIT), (13, 33, POPPY, None),
                         (5, 32, BUTTERCUP, None), (15, 34, BUTTERCUP, None), (1, 33, POPPY, None), (11.5, 33.6, BUTTERCUP, None),
                         (3.5, 33.8, BUTTERCUP, None), (8.5, 33.4, POPPY, None)):
        layer.line([(x, y + 1.6), (x + 0.1, y)], GRASS_DARK, 0.08)
        for i in range(5):
            a = i * 2 * math.pi / 5 + 0.3
            layer.ellipse(x + math.cos(a) * 0.22, y + math.sin(a) * 0.15, 0.2, 0.14, c)
        layer.ellipse(x, y, 0.1, 0.08, lit if lit is not None else rgb("#FFF3B0"))
    # Stones at the water's edge.
    for cx, cy, r in ((12.4, 36.4, 0.9), (16.4, 36.6, 0.8), (8.4, 36.6, 0.7), (18.2, 36.9, 0.5)):
        layer.ellipse(cx, cy, r, r * 0.6, STONE_DARK)
        layer.ellipse(cx - 0.1, cy - 0.1, r * 0.85, r * 0.48, STONE)
        layer.ellipse(cx - r * 0.3, cy - r * 0.25, r * 0.4, r * 0.22, STONE_LIT)
    return layer.rgba()


# ---- The shed --------------------------------------------------------------------------------------

def shed():
    y, x = ys(), xs()
    out = Canvas()
    alpha = np.zeros((H, W), np.float32)

    def put(mask, colour):
        nonlocal alpha
        out.px = out.px * (1 - mask[..., None]) + colour * mask[..., None]
        alpha = np.maximum(alpha, mask)

    grain = noise(0.06 * F, 41, 2, (H, W))
    grain_long = np.asarray(Image.fromarray((noise(0.5 * F, 42, 2) * 255).astype(np.uint8)).resize((W, H)), np.float32) / 255

    # The front wall, in the sun: vertical boards, a dark seam between each, the grain running down them.
    front = mask_of([(FRONT_LEFT, front_top(FRONT_LEFT)), (CORNER + 1, EAVE), (CORNER + 1, FLOOR), (FRONT_LEFT, front_foot(FRONT_LEFT))])
    board = (x - FRONT_LEFT) / 1.5
    within = board - np.floor(board)
    tone = mix(PLANK, PLANK_LIT, (np.clip(1 - within * 3, 0, 1) * 0.8)[..., None])
    tone = tone * (0.92 + 0.1 * np.sin((x * 37 + np.floor(board) * 11) % 7)[..., None] * 0.2)
    streaks = noise(0.1 * F, 43, 1, (H, W))
    tone = tone * (0.93 + 0.1 * np.repeat(streaks[:1, :], H, 0)[..., None] + 0.04 * grain[..., None])
    seam = np.clip(1 - np.abs(within - 0.98) * 1.5 * 20, 0, 1) + (within > 0.94)
    tone = mix(tone, PLANK_SEAM, np.clip(seam, 0, 1)[..., None])
    eave_shadow = np.clip((front_top(x) + 1.3 - y) * 1.2, 0, 1)
    tone = mix(tone, PLANK_TOP, (eave_shadow * 0.85)[..., None])
    put(front, tone)

    # The gable above it: boards running across, and a round window under the peak.
    gable = mask_of([(FRONT_LEFT, front_top(FRONT_LEFT) + 0.3), PEAK, (CORNER + 1, EAVE + 0.3)])
    course = y / 0.8
    w = course - np.floor(course)
    tone = mix(PLANK, PLANK_LIT, (np.clip(1 - w * 3, 0, 1) * 0.7)[..., None])
    tone = mix(tone, PLANK_SEAM, (w > 0.88)[..., None] * 0.9)
    tone = tone * (0.95 + 0.06 * grain[..., None])
    put(gable, tone)
    win = Layer()
    win.ellipse(39.6, 14.6, 1.45, 1.45, TRIM)
    win.ellipse(39.6, 14.6, 1.05, 1.05, GLASS)
    win.ellipse(39.35, 14.3, 0.45, 0.4, GLASS_HI)
    win.line([(38.55, 14.6), (40.65, 14.6)], TRIM, 0.16)
    win.line([(39.6, 13.55), (39.6, 15.65)], TRIM, 0.16)
    wr = win.rgba()
    put(wr[..., 3], wr[..., :3])

    # The side wall, in shade: boards closing up as they go away.
    side = mask_of([(CORNER + 1, EAVE), (W / F + 1, side_top(W / F + 1)), (W / F + 1, side_foot(W / F + 1)), (CORNER + 1, FLOOR)])
    seams = [side_at(2.5 * k) for k in range(1, 16)]
    tone = mix(SIDE, SIDE_LIT, np.clip(1 - (y - side_top(x)) / 4, 0, 1)[..., None])
    tone = tone * (0.95 + 0.06 * grain[..., None])
    for sx in seams:
        width = 0.12 * (VP_RIGHT[0] - sx) / (VP_RIGHT[0] - CORNER)
        tone = mix(tone, SIDE_SEAM, np.clip(1 - np.abs(x - sx) / max(0.03, width), 0, 1)[..., None])
    put(side, tone)
    # A lit window in the side wall, a shutter folded back, a box of flowers under it.
    w0, w1 = side_at(11), side_at(17)
    def wtop(xx): return side_top(xx) + 4
    win = Layer()
    win.polygon([(w0 - 0.15, wtop(w0) - 0.15), (w1 + 0.15, wtop(w1) - 0.15), (w1 + 0.15, wtop(w1) + 5.15), (w0 - 0.15, wtop(w0) + 5.15)], TRIM_SHADE)
    win.polygon([(w0 + 0.2, wtop(w0) + 0.2), (w1 - 0.2, wtop(w1) + 0.2), (w1 - 0.2, wtop(w1) + 4.8), (w0 + 0.2, wtop(w0) + 4.8)], GLASS)
    mid = (w0 + w1) / 2
    win.line([(mid, wtop(mid) + 0.2), (mid, wtop(mid) + 4.8)], TRIM_SHADE, 0.14)
    win.line([(w0 + 0.2, wtop(w0) + 2.5), (w1 - 0.2, wtop(w1) + 2.5)], TRIM_SHADE, 0.14)
    win.polygon([(w0 + 0.3, wtop(w0) + 0.35), (w0 + 0.9, wtop(w0) + 0.35), (w0 + 0.5, wtop(w0) + 1.6), (w0 + 0.3, wtop(w0) + 1.6)], GLASS_HI, 0.8)
    win.polygon([(w0 - 1.9, wtop(w0 - 1.9) - 0.1), (w0 - 0.3, wtop(w0 - 0.3) - 0.1), (w0 - 0.3, wtop(w0 - 0.3) + 5.2), (w0 - 1.9, wtop(w0 - 1.9) + 5.2)], SHUTTER)
    win.polygon([(w0 - 1.0, wtop(w0 - 1.0) - 0.1), (w0 - 0.3, wtop(w0 - 0.3) - 0.1), (w0 - 0.3, wtop(w0 - 0.3) + 5.2), (w0 - 1.0, wtop(w0 - 1.0) + 5.2)], SHUTTER_SHADE)
    bx0, bx1 = w0 - 0.6, w1 + 0.6
    win.polygon([(bx0, wtop(bx0) + 5.4), (bx1, wtop(bx1) + 5.4), (bx1, wtop(bx1) + 6.3), (bx0, wtop(bx0) + 6.3)], FLOWER_BOX)
    rnd = random.Random(5)
    for i in range(14):
        fx = bx0 + 0.2 + (bx1 - bx0 - 0.4) * i / 13
        fy = wtop(fx) + 5.35
        win.ellipse(fx, fy - 0.1, 0.28, 0.22, LEAF)
        win.ellipse(fx + rnd.uniform(-0.1, 0.1), fy - 0.35, 0.18, 0.15, (rgb("#E5483E"), FLOWER_PINK, rgb("#FFD54F"))[i % 3])
    wr = win.rgba()
    put(wr[..., 3], wr[..., :3])
    # The corner post, catching the sun.
    post = mask_of([(CORNER - 0.35, EAVE), (CORNER + 0.45, EAVE), (CORNER + 0.45, FLOOR + 0.2), (CORNER - 0.35, FLOOR + 0.2)])
    put(post, mix(PLANK_LIT, TRIM, 0.3) * np.ones((H, W, 1), np.float32))

    # The roof's side slope, in courses of shingles, its ridge catching the sun; cream barge boards.
    eave_corner = (CORNER + 2.0, EAVE + 1.0)
    def lower(xx):
        if xx < eave_corner[0]:
            return PEAK[1] + (xx - PEAK[0]) * (eave_corner[1] - PEAK[1]) / (eave_corner[0] - PEAK[0])
        return toward(eave_corner, VP_RIGHT, xx)
    pts_top = [(xx / 4, ridge(xx / 4) - 0.25) for xx in range(int(PEAK[0] * 4), 4 * 66)]
    pts_bottom = [(xx / 4, lower(xx / 4) + 0.15) for xx in range(4 * 66 - 1, int(PEAK[0] * 4) - 1, -1)]
    roof = mask_of(pts_top + pts_bottom)
    top_y = ridge(x)
    low_y = np.vectorize(lower)(x[0])[None, :]
    v = np.clip((y - top_y) / np.maximum(0.5, low_y - top_y), 0, 1)
    courses = 7
    cv_ = v * courses
    c_index = np.floor(cv_)
    within = cv_ - c_index
    # Along the slope, shingles foreshortened towards the far end, staggered course by course.
    along = (x - PEAK[0]) / (VP_RIGHT[0] - x) * 24 + c_index * 0.5
    jw = along - np.floor(along)
    shingle = np.floor(along) + c_index * 7
    vary = ((np.sin(shingle * 12.9898) * 43758.5453) % 1.0)
    tone = ramp([ROOF_LIT, ROOF, ROOF_SHADE], np.clip(c_index / courses + 0.15 * (vary - 0.5), 0, 1))
    tone = mix(tone, ROOF_SHADE * 0.9, (within > 0.82)[..., None] * 0.8)
    tone = mix(tone, ROOF_SHADE, (jw < 0.06)[..., None] * 0.6)
    tone = mix(tone, ROOF_RIDGE, (np.clip(1 - (y - top_y) / 0.45, 0, 1) * 0.95)[..., None])
    tone = mix(tone, ROOF_LINE, (np.clip(1 - (low_y - y) / 0.35, 0, 1))[..., None])
    put(roof, tone)
    trim = Layer()
    left_end = (FRONT_LEFT - 2, front_top(FRONT_LEFT - 2) + 1)
    trim.line([(PEAK[0] - 0.15, PEAK[1] + 0.45), (left_end[0] - 0.1, left_end[1] + 0.45)], ROOF_SHADE, 0.45)
    trim.line([(PEAK[0], PEAK[1]), (left_end[0], left_end[1])], TRIM, 0.5)
    trim.line([(PEAK[0], PEAK[1]), (eave_corner[0], eave_corner[1])], TRIM, 0.45)
    trim.line([(PEAK[0], PEAK[1] - 0.2), (PEAK[0], PEAK[1] - 1.6)], ROOF_LINE, 0.22)
    trim.ellipse(PEAK[0], PEAK[1] - 1.7, 0.28, 0.28, ROOF_RIDGE)
    tr = trim.rgba()
    put(tr[..., 3], tr[..., :3])

    # A stone chimney through the roof near the back.
    cx = 56
    base = (ridge(cx) + lower(cx)) / 2
    ch = Layer()
    ch.rect(cx - 0.1, base - 9.5, cx + 1.9, base + 0.3, CHIMNEY)
    ch.rect(cx - 0.1, base - 9.5, cx + 0.8, base + 0.3, CHIMNEY_LIT)
    ch.rect(cx - 0.5, base - 10.4, cx + 2.3, base - 9.4, CHIMNEY_DARK)
    for k in range(9):
        yy = base - 9.3 + k * 1.05
        ch.line([(cx - 0.1, yy), (cx + 1.9, yy)], CHIMNEY_DARK, 0.1)
        off = 0.6 if k % 2 else 1.2
        ch.line([(cx - 0.1 + off, yy), (cx - 0.1 + off, yy + 1.05)], CHIMNEY_DARK, 0.08)
    cr = ch.rgba()
    put(cr[..., 3], cr[..., :3])

    # The door, ajar onto lamplight.
    dl = Layer()
    door_bottom = front_foot(38)
    dl.rect(35.7, 22.6, 40.3, door_bottom, TRIM)
    dl.rect(36.2, 23.1, 39.8, door_bottom, DOOR_GLOW)
    dl.polygon([(37.2, 23.1), (39.8, 23.1), (39.8, door_bottom), (37.2, door_bottom - 0.05)], DOOR)
    dl.polygon([(38.9, 23.1), (39.8, 23.1), (39.8, door_bottom), (38.9, door_bottom)], DOOR_SHADE)
    for k in range(3):
        dl.line([(37.4, 24 + k * 3.2), (38.7, 24 + k * 3.2)], DOOR_SHADE, 0.12)
    dl.ellipse(37.7, 29.2, 0.22, 0.22, HANDLE)
    lr = dl.rgba()
    put(lr[..., 3], lr[..., :3])
    # A net hung to dry beside it, sagging a little between the pegs it hangs from.
    net = Layer()
    for i in range(-12, 13):
        net.line([(41 + i * 0.7, 21), (41 + i * 0.7 + 9, 30)], NET, 0.06, 0.9)
        net.line([(45.6 - i * 0.7, 21), (45.6 - i * 0.7 - 9, 30)], NET, 0.06, 0.9)
    for px_ in (41.0, 43.3, 45.6):
        net.ellipse(px_, 20.9, 0.15, 0.15, POST)
    nr = net.rgba()
    sag = 21 + 0.6 * np.sin((x - 41) / 4.6 * np.pi) ** 2
    hang = (x >= 40.9) & (x <= 45.7) & (y >= sag - 0.1) & (y <= 30 - 0.5 * np.sin((x - 41) / 4.6 * np.pi))
    nr[..., 3] *= hang
    put(nr[..., 3], nr[..., :3])
    # An oar leant against the wall.
    oar = Layer()
    oar.line([(34.0, 21.0), (35.0, 33.2)], OAR, 0.22)
    oar.polygon([(34.7, 29.6), (35.25, 29.5), (35.45, 33.4), (34.95, 33.5)], OAR_DARK)
    orr = oar.rgba()
    put(orr[..., 3], orr[..., :3])
    return paint_rgb(out.px, alpha)


def smoke():
    """Smoke from the chimney, drifting off to the left, thinning as it goes."""
    rnd = random.Random(9)
    layer = Layer()
    cx = 56.8
    base = (ridge(56) + toward((CORNER + 2.0, EAVE + 1.0), VP_RIGHT, 56)) / 2 - 10.6
    for i in range(28):
        t = i / 27
        px_ = cx - t * 13 - t * t * 2
        py_ = base - t * 4.2 - math.sin(t * 5) * 0.5
        r = 0.45 + t * 1.4
        layer.ellipse(px_ + rnd.uniform(-0.2, 0.2), py_ + rnd.uniform(-0.2, 0.2), r, r * 0.8, SMOKE if t < 0.5 else SMOKE_FAINT, 0.55 * (1 - t) ** 1.2)
    return layer.rgba(0.25)


def jetty():
    """The landing along the front of the shed, running on as a jetty, on posts in the water."""
    layer = Layer()
    def back(xx): return toward((CORNER + 1, FLOOR), VP_LEFT, xx)
    left = 19.5
    # Posts first, under the deck.
    for px_ in (left + 0.4, left + 6, FRONT_LEFT - 2, 39, CORNER):
        wl = toward((CORNER, WATER_LINE), VP_LEFT, px_)
        layer.rect(px_ - 0.32, back(px_) + 1.5, px_ + 0.32, wl + 0.2, POST)
        layer.rect(px_ - 0.32, back(px_) + 1.5, px_ - 0.05, wl + 0.2, POST_LIT)
    for s in (8, 18, 30):
        px_ = side_at(s)
        wl = toward((CORNER, WATER_LINE), VP_RIGHT, px_)
        layer.rect(px_ - 0.22, side_foot(px_), px_ + 0.22, wl + 0.2, POST)
    # The deck: boards along it, lit, its front edge in shade.
    pts_top = [(xx / 4, back(xx / 4) - 0.2) for xx in range(int(left * 4), int((CORNER + 2) * 4) + 1)]
    pts_mid = [(xx / 4, back(xx / 4) + 0.75) for xx in range(int((CORNER + 2) * 4), int(left * 4) - 1, -1)]
    layer.polygon(pts_top + pts_mid, DECK_LIT)
    pts_edge = [(xx / 4, back(xx / 4) + 1.55) for xx in range(int((CORNER + 2) * 4), int(left * 4) - 1, -1)]
    layer.polygon([(xx / 4, back(xx / 4) + 0.75) for xx in range(int(left * 4), int((CORNER + 2) * 4) + 1)] + pts_edge, DECK_EDGE)
    for k in range(int(left), int(CORNER + 2), 2):
        layer.line([(k, back(k) - 0.2), (k + 0.3, back(k + 0.3) + 0.75)], DECK, 0.1)
    layer.line([(left, back(left) + 0.3), (CORNER + 2, back(CORNER + 2) + 0.3)], DECK, 0.08)
    return layer.rgba()


# ---- The water, and everything on it and over it ------------------------------------------------

WATER = [rgb(c) for c in ("#7A62A6", "#62589F", "#504F98", "#434893", "#3A428B", "#333C83")]
RIPPLE_WARM, RIPPLE, RIPPLE_COOL = rgb("#FFDDA8"), rgb("#C7A3D8"), rgb("#8B90D2")
GLITTER, GLITTER_HI = rgb("#FFE3A2"), rgb("#FFF8DA")
PAD_DARK, PAD, PAD_LIT = rgb("#2F7650"), rgb("#4C9A5C"), rgb("#7EC46E")
LILY, LILY_HI, LILY_HEART = rgb("#FF8FB2"), rgb("#FFD3E1"), rgb("#FFD54F")
REED, REED_LIT, REED_DARK, CATTAIL = rgb("#5F8E3B"), rgb("#9CC75A"), rgb("#3F6A35"), rgb("#7A4630")
FIREFLY = rgb("#FFF7A6")
ROD, LINE, FLOAT = rgb("#8C5A3C"), rgb("#E9DCC8"), rgb("#FF8FB2")
BUCKET, BUCKET_RIM, BUCKET_SHADE = rgb("#7FA6B8"), rgb("#C7DCE6"), rgb("#5F8498")
DRAGONFLY, WING = rgb("#2FB0C8"), rgb("#D8F2F6")
HULL, HULL_SHADE, HULL_RIM, HULL_STRIPE, HULL_INSIDE = (rgb("#2E8E92"), rgb("#227276"), rgb("#F6EEDB"),
                                                         rgb("#E0533F"), rgb("#9C6A47"))
OAR, OAR_DARK, ROPE = rgb("#D8AE72"), rgb("#B08454"), rgb("#D9C394")
LANTERN_FRAME, LANTERN_GLASS, LANTERN_GLOW = rgb("#3E3046"), rgb("#FFF0A0"), rgb("#FFD27A")
DOOR_GLOW = rgb("#FFC46B")
GOLD_HI, GOLD, GOLD_MID, GOLD_DEEP = rgb("#FFF0A8"), rgb("#FFD54F"), rgb("#F5A83A"), rgb("#C9741F")
CLOUD_WARM = rgb("#F7A28E")


def ripple_shift(seed, amount):
    """How far each pixel of the water looks across for what it mirrors: long low waves, growing nearer us."""
    wave = noise(2.5 * F, seed, 2, (H, W)) - 0.5
    # Stretched along the waves: sample a short, wide field.
    flat = np.asarray(Image.fromarray(((noise(1.2 * F, seed + 1, 2, (H // 6, W)) ) * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC), np.float32) / 255 - 0.5
    depth = np.clip(ys() - HORIZON, 0, None)
    return (flat * 0.8 + wave * 0.2) * (0.25 + depth * amount) * F


def water(cv, near_layers):
    y, x = ys(), xs()
    depth = y - HORIZON
    base = ramp(WATER, np.clip(depth / (72 - HORIZON - 8), 0, 1) ** 0.9)
    base = base * (0.97 + 0.05 * noise(1.5 * F, 50, 2)[..., None])
    scene = cv.px.copy()
    shift = ripple_shift(51, 0.05)
    out = cv.px
    rows = np.arange(H)
    cols = np.arange(W)
    top = int(HORIZON * F)
    for r in range(top, H):
        d = r / F - HORIZON
        src = int(round((2 * HORIZON - r / F) * F))
        row = base[r]
        if 0 <= src < top:
            strength = 0.82 * math.exp(-d / 10.0)
            c = np.clip(cols + shift[r].round().astype(int), 0, W - 1)
            row = row * (1 - strength) + scene[src, c] * strength
        out[r] = row
    # Nearer things, each mirrored about where it stands in the water, broken into bands by the ripples.
    bands = np.asarray(Image.fromarray((noise(0.6 * F, 52, 1, (H // 8, W)) * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC), np.float32) / 255
    for layer, line, reach in near_layers:
        w = np.array([line(c / F) for c in range(W)], np.float32)
        r_idx = rows[:, None]
        dist = r_idx / F - w[None, :]
        src = np.round((2 * w[None, :] - r_idx / F) * F).astype(int)
        ok = (dist > 0) & (dist < reach) & (src >= 0)
        src = np.clip(src, 0, H - 1)
        c_idx = np.clip(cols[None, :] + shift.round().astype(int), 0, W - 1)
        sample = layer[src, c_idx]
        fade = np.clip(0.62 - dist * 0.035, 0, 1) * ok * sample[..., 3] * np.clip((bands - 0.28) * 4, 0, 1)
        out[:] = out * (1 - fade[..., None]) + sample[..., :3] * 0.88 * fade[..., None]


def sun_on_the_water(cv):
    """The sun's road: glitter beneath it, breaking up and widening as it comes nearer."""
    rnd = random.Random(23)
    layer = Layer()
    for _ in range(700):
        d = rnd.uniform(0.2, 20) ** 1.0
        half = 0.6 + d * 0.16
        off = rnd.gauss(0, half * 0.5)
        if abs(off) > half:
            continue
        yy = HORIZON + d
        length = rnd.uniform(0.2, 0.6) + d * 0.04
        bright = GLITTER_HI if (abs(off) < half * 0.4 and d < 12) else GLITTER
        layer.ellipse(SUN_X + off, yy, length, 0.06 + d * 0.004, bright, 0.85 * (1 - d / 22))
    cv.add(layer.rgba()[..., :3] * layer.rgba()[..., 3:4])


def ripples(cv):
    """Ripple lines catching the light: short and close together far off, longer and wider apart nearby."""
    rnd = random.Random(21)
    layer = Layer()
    y = HORIZON + 1.2
    gap = 0.45
    while y < 72:
        d = y - HORIZON
        x = rnd.uniform(-2, 3)
        while x < 66:
            length = rnd.uniform(0.6, 1.4) + d * 0.09
            if abs(x - SUN_X) > 3 + d * 0.25:
                warm = 1 - d / 30
                colour = RIPPLE_WARM if warm > 0.75 else (RIPPLE if warm > 0.2 else RIPPLE_COOL)
                layer.ellipse(x + length / 2, y + rnd.uniform(-0.1, 0.1), length / 2, 0.05 + d * 0.003, colour, rnd.uniform(0.3, 0.6))
            x += length + rnd.uniform(1.5, 5) + d * 0.15
        gap *= 1.09
        y += gap
    cv.over(layer.rgba())


def cloud_reflections(cv):
    layer = Layer()
    for yy, x0, x1 in ((52.8, 44, 61), (54.6, 47, 63), (56.4, 50, 59), (53.7, 42, 50)):
        layer.ellipse((x0 + x1) / 2, yy, (x1 - x0) / 2, 0.22, CLOUD_WARM, 0.32)
    cv.over(layer.rgba(0.05))


def lily_pads(cv, solid_alpha):
    """Lily pads, slivers far off and broader nearer us, notched, some in flower; only on open water."""
    rnd = random.Random(33)
    layer = Layer()
    for cx, cy, flower in ((9, 31, False), (27, 31, True), (4, 33, False), (35, 30, False), (52, 33, False),
                           (6, 43, True), (17, 46, False), (37, 41, False), (2, 50, True), (13, 54, False),
                           (26, 51, True), (44, 48, False), (56, 45, True), (38, 58, True), (57, 57, False),
                           (21, 62, False), (47, 63, True), (31, 66, False), (8, 67, True), (62, 61, False), (16, 59, False)):
        rx = min(2.7, 0.8 + (cy - HORIZON) / 14)
        ry = rx * (0.2 + 0.006 * (cy - HORIZON))
        # Only on open water: not under the boat, the jetty or the bank.
        r0, c0 = int(cy * F), int(cx * F)
        if 0 <= r0 < H and 0 <= c0 < W and solid_alpha[r0, max(0, c0 - int(rx * F)):c0 + int(rx * F)].max() > 0.05:
            continue
        notch = rnd.uniform(-0.6, 0.6)
        pts = []
        for k in range(48):
            a = notch + 0.35 + k / 47 * (2 * math.pi - 0.7)
            pts.append((cx + math.cos(a) * rx, cy + math.sin(a) * ry))
        pts.append((cx, cy))
        layer.polygon([(px, py + ry * 0.22) for px, py in pts], PAD_DARK)
        layer.polygon(pts, PAD)
        layer.polygon([(cx + (px - cx) * 0.75 - rx * 0.12, cy + (py - cy) * 0.7 - ry * 0.12) for px, py in pts], PAD_LIT, 0.55)
        for k in range(7):
            a = notch + 0.6 + k * 0.8
            layer.line([(cx, cy), (cx + math.cos(a) * rx * 0.85, cy + math.sin(a) * ry * 0.85)], PAD_DARK, 0.04, 0.6)
        if flower:
            s = 0.45 + rx * 0.22
            fx, fy = cx - rx * 0.15, cy - ry * 0.3
            for k in range(8):
                a = math.pi + k * math.pi / 7
                layer.polygon([(fx, fy), (fx + math.cos(a - 0.22) * s * 0.55, fy + math.sin(a - 0.22) * s * 0.45),
                               (fx + math.cos(a) * s, fy + math.sin(a) * s * 0.75 - 0.05),
                               (fx + math.cos(a + 0.22) * s * 0.55, fy + math.sin(a + 0.22) * s * 0.45)], LILY)
            for k in range(5):
                a = math.pi * 1.15 + k * math.pi * 0.7 / 4
                layer.polygon([(fx, fy), (fx + math.cos(a - 0.2) * s * 0.4, fy + math.sin(a - 0.2) * s * 0.4),
                               (fx + math.cos(a) * s * 0.75, fy + math.sin(a) * s * 0.75),
                               (fx + math.cos(a + 0.2) * s * 0.4, fy + math.sin(a + 0.2) * s * 0.4)], LILY_HI)
            layer.ellipse(fx, fy - s * 0.12, s * 0.2, s * 0.14, LILY_HEART)
    cv.over(layer.rgba())


def boat():
    """Her rowing boat, tied up at the end of the jetty, the oars shipped."""
    layer = Layer()
    # The far gunwale and the inside of the boat, seen over the near one.
    layer.polygon([(4.6, 37.6), (8, 37.1), (13, 36.95), (17.6, 37.2), (18.4, 37.8), (17.8, 38.9), (4.2, 38.9)], HULL_INSIDE)
    layer.line([(4.6, 37.6), (8, 37.1), (13, 36.95), (17.6, 37.2), (18.4, 37.8)], HULL_RIM, 0.22)
    layer.polygon([(9.6, 37.6), (10.6, 37.6), (10.8, 38.9), (9.4, 38.9)], rgb("#B88A5C"))   # a thwart
    # The hull: the near gunwale, teal sides with a red stripe, the bow to the right.
    hull = [(3.0, 38.4), (6, 38.9), (12, 39.0), (17, 38.8), (19.2, 38.2), (18.6, 39.6), (17.2, 41.2), (14, 42.1), (8, 42.2), (4.6, 41.6), (3.4, 40.2)]
    layer.polygon(hull, HULL)
    layer.polygon([(3.4, 40.2), (4.6, 41.6), (8, 42.2), (14, 42.1), (17.2, 41.2), (18.6, 39.6), (18.2, 41.0), (14, 42.6), (8, 42.7), (4.2, 42.2)], HULL_SHADE)
    layer.polygon([(3.3, 40.6), (8, 40.95), (14, 40.95), (18.2, 40.1), (17.8, 40.8), (14, 41.55), (8, 41.6), (3.6, 41.25)], HULL_STRIPE)
    layer.line([(3.0, 38.4), (6, 38.9), (12, 39.0), (17, 38.8), (19.2, 38.2)], HULL_RIM, 0.3)
    for k in range(1, 6):
        xx = 3.6 + k * 2.7
        layer.line([(xx, 39.2), (xx + 0.1, 40.5)], HULL_SHADE, 0.05, 0.6)
    # The oars, shipped across her.
    layer.line([(4.8, 36.6), (17.6, 37.5)], OAR, 0.16)
    layer.polygon([(3.6, 36.35), (5.4, 36.55), (5.3, 36.95), (3.5, 36.8)], OAR_DARK)
    layer.line([(6.2, 37.9), (16.4, 36.6)], OAR, 0.14)
    layer.polygon([(16.2, 36.45), (18.0, 36.2), (18.1, 36.6), (16.3, 36.85)], OAR_DARK)
    # The painter, tied to the jetty's first post.
    layer.line([(18.9, 38.6), (19.4, 38.9), (19.8, 38.6), (20.0, 37.6)], ROPE, 0.08)
    return layer.rgba()


def reeds():
    """Reeds and bulrushes standing in the shallows."""
    rnd = random.Random(44)
    layer = Layer()
    for x0, bottom, n in ((13, 37.2, 9), (48, 41, 6), (59, 40, 10), (21.5, 38.4, 4)):
        for i in range(n):
            dx = rnd.uniform(-1.2, 1.6)
            h = rnd.uniform(3.5, 9.5) * (0.8 if x0 == 48 else 1)
            lean = rnd.uniform(-1.3, 1.6)
            base = x0 + dx
            pts = []
            for k in range(9):
                t = k / 8
                pts.append((base + lean * t * t, bottom - h * t))
            c = REED_LIT if rnd.random() < 0.35 else (REED if rnd.random() < 0.6 else REED_DARK)
            left = [(px - 0.09 * (1 - t / 1.2), py) for (px, py), t in zip(pts, [k / 8 for k in range(9)])]
            right = [(px + 0.09 * (1 - t / 1.2), py) for (px, py), t in zip(pts, [k / 8 for k in range(9)])]
            layer.polygon(left + right[::-1], c)
            layer.polygon([(px - 0.03, py) for px, py in pts[:4]] + [(px + 0.06, py) for px, py in pts[:4]][::-1], REED_DARK, 0.6)
            if rnd.random() < 0.55:
                tx, ty = pts[-1]
                hx, hy = pts[6]
                layer.ellipse(hx, hy - 0.2, 0.2, 0.75, CATTAIL)
                layer.ellipse(hx - 0.06, hy - 0.4, 0.07, 0.4, rgb("#9A6244"))
                layer.line([(hx, hy - 0.9), (tx, ty)], REED_LIT, 0.05)
    return layer.rgba()


def on_the_jetty(cv):
    """Lamplight from the door across the boards, Anna's rod propped with its line out, a bucket."""
    y, x = ys(), xs()
    d = np.hypot((x - 38.5) / 3.2, (y - (toward((CORNER + 1, FLOOR), VP_LEFT, 38.5) + 0.2)) / 0.8)
    cv.add(DOOR_GLOW[None, None, :] * (np.exp(-d ** 2) * 0.45)[..., None])
    layer = Layer()
    layer.line([(23.4, 33.2), (20.5, 28.4), (18.0, 25.0)], ROD, 0.12)
    layer.line([(23.0, 33.1), (23.9, 33.4)], rgb("#5B3E3C"), 0.2)
    layer.line([(18.0, 25.0), (16.9, 26.4), (16.0, 28.6), (15.4, 31.4), (15.2, 34.7)], LINE, 0.035, 0.85)
    layer.ellipse(15.2, 34.95, 0.16, 0.24, FLOAT)
    layer.ellipse(15.2, 34.8, 0.12, 0.1, rgb("#FFFFFF"))
    # A bucket on the boards.
    deck = toward((CORNER + 1, FLOOR), VP_LEFT, 26)
    layer.polygon([(24.9, deck - 2.0), (27.1, deck - 2.0), (26.8, deck - 0.1), (25.2, deck - 0.1)], BUCKET)
    layer.polygon([(26.2, deck - 2.0), (27.1, deck - 2.0), (26.8, deck - 0.1), (26.1, deck - 0.1)], BUCKET_SHADE)
    layer.ellipse(26.0, deck - 2.0, 1.1, 0.28, BUCKET_RIM)
    layer.ellipse(26.0, deck - 1.97, 0.9, 0.18, rgb("#3E4F6B"))
    layer.line([(25.0, deck - 2.0), (25.4, deck - 2.9), (26.6, deck - 2.9), (27.0, deck - 2.0)], rgb("#5F6B78"), 0.06)
    cv.over(layer.rgba())


def lantern(cv):
    """The lantern, hung out on a bracket from the corner of the eaves, glowing; its light in the water."""
    y, x = ys(), xs()
    d = np.hypot(x - 30.0, (y - 22.7) * 1.1)
    cv.add(LANTERN_GLOW[None, None, :] * (np.exp(-(d / 2.2) ** 2) * 0.55 + np.exp(-(d / 5.5) ** 2) * 0.18)[..., None])
    layer = Layer()
    layer.line([(32.3, 20.1), (29.6, 20.1)], LANTERN_FRAME, 0.16)
    layer.line([(32.2, 20.1), (32.8, 21.0)], LANTERN_FRAME, 0.1)
    layer.line([(30.0, 20.1), (30.0, 20.9)], LANTERN_FRAME, 0.07)
    layer.polygon([(29.3, 21.4), (30.7, 21.4), (30.4, 20.9), (29.6, 20.9)], LANTERN_FRAME)
    layer.rect(29.35, 21.4, 30.65, 23.9, LANTERN_GLASS)
    layer.ellipse(30.0, 22.6, 0.35, 0.6, rgb("#FFFFFF"))
    layer.line([(29.35, 21.4), (29.35, 23.9)], LANTERN_FRAME, 0.09)
    layer.line([(30.65, 21.4), (30.65, 23.9)], LANTERN_FRAME, 0.09)
    layer.line([(30.0, 21.4), (30.0, 23.9)], LANTERN_FRAME, 0.05)
    layer.polygon([(29.2, 23.9), (30.8, 23.9), (30.5, 24.3), (29.5, 24.3)], LANTERN_FRAME)
    cv.over(layer.rgba())
    # Its light in the water below, broken by the ripples.
    rnd = random.Random(7)
    glints = Layer()
    for k in range(16):
        yy = 39.6 + k * 0.45 + rnd.uniform(-0.1, 0.1)
        half = 0.25 + rnd.uniform(0, 0.5)
        glints.ellipse(30.0 + rnd.uniform(-0.4, 0.4), yy, half, 0.07, LANTERN_GLOW, 0.75 * (1 - k / 17))
    g = glints.rgba()
    cv.add(g[..., :3] * g[..., 3:4])


def the_golden_fish(cv):
    """Leaping beside the jetty, right under the lantern, and the rings where it rose."""
    y, x = ys(), xs()
    # Rings spreading where it broke the water.
    rings = Layer()
    for r, a in ((1.0, 0.75), (1.9, 0.55), (2.9, 0.35)):
        rings.ellipse(29.6, 41.9, r, r * 0.3, rgb("#FFE8B8"), a)
        rings.ellipse(29.6, 41.9, r - 0.1, r * 0.3 - 0.05, (0, 0, 0), 0)
    ring_px = rings.rgba()
    rr = np.zeros((H, W), np.float32)
    for r, a in ((1.0, 0.8), (1.9, 0.6), (2.9, 0.4)):
        e = np.hypot((x - 29.6) / r, (y - 41.9) / (r * 0.3))
        rr += np.exp(-((e - 1) * r * 9) ** 2) * a
    cv.add(rgb("#FFE8B8")[None, None, :] * (np.clip(rr, 0, 1) * 0.8)[..., None])
    # The fish: arched mid-leap, head up and to the right.
    fish = Layer()
    cx, cy, ang = 31.2, 37.6, -0.6
    ca, sa = math.cos(ang), math.sin(ang)
    k_ = 1.45
    def at(u, v):
        return (cx + (u * ca - v * sa) * k_, cy + (u * sa + v * ca) * k_)
    body = []
    for k in range(25):
        u = -2.0 + 4.0 * k / 24
        half = 0.6 * math.sin(math.pi * (k / 24) ** 0.75) ** 0.6 + 0.04
        bow = 0.25 * (1 - (u / 2) ** 2)
        body.append(at(u, -half - bow))
    for k in range(24, -1, -1):
        u = -2.0 + 4.0 * k / 24
        half = 0.6 * math.sin(math.pi * (k / 24) ** 0.75) ** 0.6 + 0.04
        bow = 0.25 * (1 - (u / 2) ** 2)
        body.append(at(u, half * 0.85 - bow))
    tail = [at(-1.85, 0.0), at(-3.0, -0.95), at(-2.65, -0.05), at(-3.05, 0.85)]
    fish.polygon(tail, GOLD_MID)
    fish.polygon([at(-1.9, 0.0), at(-2.8, -0.6), at(-2.6, 0.0), at(-2.8, 0.55)], GOLD)
    fish.polygon([at(-0.6, -0.75), at(0.4, -1.45), at(0.9, -0.7)], GOLD_MID)     # the dorsal fin
    fish.polygon(body, GOLD_DEEP)
    fish.polygon([(px, py) for px, py in body[:25]] + [at(u, -0.05) for u in np.linspace(2.0, -2.0, 25)], GOLD)
    fish.polygon([at(u, -0.45 * math.sin(math.pi * (u + 2) / 4) - 0.15) for u in np.linspace(-1.6, 1.6, 20)] + [at(u, -0.12) for u in np.linspace(1.6, -1.6, 20)], GOLD_HI, 0.7)
    fish.polygon([at(0.3, 0.35), at(-0.5, 0.95), at(0.2, 0.55)], GOLD_HI)       # the pectoral fin
    for k in range(5):
        u = -1.2 + k * 0.5
        fish.line([at(u, -0.3), at(u + 0.2, 0.0), at(u, 0.3)], GOLD_DEEP, 0.04, 0.5)
    ex, ey = at(1.35, -0.18)
    fish.ellipse(ex, ey, 0.16, 0.16, rgb("#3A2A1A"))
    fish.ellipse(ex + 0.05, ey - 0.05, 0.06, 0.06, rgb("#FFFFFF"))
    fp = fish.rgba()
    glow = np.exp(-(np.hypot(x - cx, y - cy) / 3.2) ** 2) * 0.5 + np.exp(-(np.hypot(x - cx, y - cy) / 1.4) ** 2) * 0.3
    cv.add(GOLD[None, None, :] * glow[..., None])
    cv.over(fp)
    # Drops thrown up as it leapt.
    rnd = random.Random(5)
    drops = Layer()
    for _ in range(22):
        a = rnd.uniform(math.pi * 1.05, math.pi * 1.95)
        r = rnd.uniform(0.6, 2.4)
        dx, dy = 28.6 + math.cos(a) * r * 0.9, 41.3 + math.sin(a) * r * 0.8
        s = rnd.uniform(0.05, 0.13)
        drops.ellipse(dx, dy, s, s * 1.3, rgb("#FFF4D6"), rnd.uniform(0.6, 1.0))
    cv.over(drops.rgba())
    sparkle = np.zeros((H, W), np.float32)
    for sx, sy, b in ((33.6, 35.4, 1.0), (28.4, 36.2, 0.7), (34.8, 38.6, 0.6), (30.6, 34.6, 0.5)):
        sparkle += (np.exp(-((x - sx) / 0.05) ** 2) * np.exp(-((y - sy) / 0.55) ** 2) + np.exp(-((y - sy) / 0.05) ** 2) * np.exp(-((x - sx) / 0.55) ** 2)) * b
        sparkle += np.exp(-(np.hypot(x - sx, y - sy) / 0.12) ** 2) * b
    cv.add(GOLD_HI[None, None, :] * np.clip(sparkle, 0, 1)[..., None])


def dragonfly(cv):
    layer = Layer()
    layer.ellipse(41.3, 37.4, 0.75, 0.22, WING, 0.6)
    layer.ellipse(42.6, 37.5, 0.7, 0.2, WING, 0.6)
    layer.ellipse(41.4, 38.6, 0.7, 0.2, WING, 0.5)
    layer.ellipse(42.5, 38.5, 0.65, 0.18, WING, 0.5)
    layer.line([(39.6, 38.25), (43.6, 37.95)], DRAGONFLY, 0.13)
    layer.ellipse(43.75, 37.92, 0.22, 0.2, rgb("#1E7F92"))
    cv.over(layer.rgba())


def fireflies(cv):
    y, x = ys(), xs()
    glow = np.zeros((H, W), np.float32)
    for fx, fy in ((9, 27), (14, 24), (3, 22), (12, 30), (20, 30), (52, 37), (61, 33), (6.5, 25.5), (17.5, 27.5)):
        d = np.hypot(x - fx, y - fy)
        glow += np.exp(-(d / 0.14) ** 2) + 0.4 * np.exp(-(d / 0.55) ** 2)
    cv.add(FIREFLY[None, None, :] * np.clip(glow, 0, 1)[..., None])


def paint():
    cv = paint_far()
    the_willow, the_bank = willow(), bank()
    the_shed, the_smoke, the_jetty = shed(), smoke(), jetty()
    the_boat = boat()
    # The shed and its jetty mirror together, as do the willow and its bank.
    building = the_shed.copy()
    a = the_jetty[..., 3:4]
    building[..., :3] = building[..., :3] * (1 - a) + the_jetty[..., :3] * a
    building[..., 3:4] = np.maximum(building[..., 3:4], a)
    shore = the_bank.copy()
    a = the_willow[..., 3:4]
    shore[..., :3] = the_willow[..., :3] * a + shore[..., :3] * (1 - a)
    shore[..., 3:4] = np.maximum(shore[..., 3:4], a)
    water(cv, [(shore, lambda xx: 36.6, 16), (building, water_line, 16), (the_boat, lambda xx: 41.9, 5)])
    sun_on_the_water(cv)
    ripples(cv)
    cloud_reflections(cv)
    for part in (the_smoke, the_willow, the_bank, the_jetty, the_shed):
        cv.over(part)
    lily_pads(cv, np.maximum(np.maximum(the_bank[..., 3], the_boat[..., 3]), building[..., 3]))
    cv.over(the_boat)
    cv.over(reeds())
    on_the_jetty(cv)
    lantern(cv)
    the_golden_fish(cv)
    dragonfly(cv)
    fireflies(cv)
    return cv.image()
