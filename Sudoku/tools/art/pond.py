"""
Chapter 1, The Pond: Anna's fishing shed on stilts at the edge of the village pond on a summer evening,
seen across the water in two-point perspective. The sun is going down behind the hills, a willow hangs
over the near bank, lily pads float on water that mirrors it all, her boat waits at the jetty, and the
golden fish leaps under the lantern.
"""
import math
import random

from pixels import W, H, Canvas, band, blob, dither, golden_fish, mix, nearest, rgb, stamp

HORIZON = 28                          # eye level, where the far shore meets the water
VP_LEFT, VP_RIGHT = (-60, HORIZON), (88, HORIZON)
SUN_X, SUN_Y = 22, 20

# The shed: its near corner is where the sunlit front, facing left, meets the side in shade.
CORNER, EAVE, FLOOR = 46, 18, 34      # the corner's column, and its wall top and foot
FRONT_LEFT = 33                       # the far edge of the front wall
PEAK = (39.5, 9.0)                    # the front gable's peak
WATER_LINE = 39                       # where the stilts at the corner stand in the water

SKY = [rgb(c) for c in ("#3B3D8C", "#48468F", "#5A4C99", "#6E52A0", "#8659A5", "#A061A5", "#B968A0",
                        "#D17396", "#E5808C", "#F29684", "#F8AE7E", "#FBC67E", "#FDDB92", "#FEEBB4")]
SUN, SUN_HALO, SUN_GLOW = rgb("#FFF8E0"), rgb("#FFE9A6"), rgb("#FFD486")
CLOUD_LIT, CLOUD_WARM, CLOUD_MID, CLOUD_TOP = rgb("#FFD7AA"), rgb("#F7A28E"), rgb("#C77B9F"), rgb("#8F66A6")
STREAK = rgb("#FFDFA0")
BIRD = rgb("#3A2F63")
FAR_HILLS, FAR_HILLS_LIT = rgb("#A88DC6"), rgb("#E6A9B4")
HILLS, HILLS_LIT, HILLS_DARK = rgb("#7469AE"), rgb("#E29A9E"), rgb("#5E5A9C")
TREES, TREES_DARK, TREES_LIT = rgb("#3B6763"), rgb("#2C5257"), rgb("#D0AE6A")
FAR_HOUSE, FAR_ROOF, FAR_WINDOW = rgb("#C9A9B0"), rgb("#9C4F5E"), rgb("#FFE07A")
SHORE = rgb("#4C6E58")

WILLOW = [rgb(c) for c in ("#2F5E45", "#3F7A4A", "#5A9A4E", "#7DB85A", "#A9D06A", "#D9E68A")]
BARK, BARK_DARK = rgb("#6B4E44"), rgb("#4A3636")
GRASS, GRASS_LIT, GRASS_DARK = rgb("#4F8F45"), rgb("#8CC255"), rgb("#36663E")
IRIS, IRIS_LIT, BUTTERCUP, POPPY = rgb("#7E58C8"), rgb("#B08AE8"), rgb("#F6D24A"), rgb("#E5483E")
STONE, STONE_LIT, STONE_DARK = rgb("#8D8AA0"), rgb("#B4B0C2"), rgb("#66637E")

# The shed: the front in the evening sun, the side in shade, a red shingle roof, cream trim.
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
LANTERN_FRAME, LANTERN_GLASS, LANTERN_GLOW = rgb("#3E3046"), rgb("#FFF0A0"), rgb("#FFD27A")
CHIMNEY, CHIMNEY_LIT, CHIMNEY_DARK = rgb("#9C8F98"), rgb("#C2B3B4"), rgb("#6F6672")
SMOKE, SMOKE_FAINT = rgb("#F6ECF4"), rgb("#D8C6E2")
POST, POST_LIT = rgb("#5B3E3C"), rgb("#8C6150")
DECK_LIT, DECK, DECK_EDGE = rgb("#E3A465"), rgb("#C98A52"), rgb("#7A5040")
HULL, HULL_SHADE, HULL_RIM, HULL_STRIPE, HULL_INSIDE = (rgb("#2E8E92"), rgb("#227276"), rgb("#F6EEDB"),
                                                         rgb("#E0533F"), rgb("#9C6A47"))
ROPE = rgb("#D9C394")
FLOWER_BOX, FLOWER_PINK, LEAF = rgb("#7A4E3C"), rgb("#FF8FB2"), rgb("#5DA05A")

WATER = [rgb(c) for c in ("#7A62A6", "#62589F", "#504F98", "#434893", "#3A428B", "#333C83")]
# Everything the water shows snaps to these, so its reflections stay crisp.
WATER_TONES = WATER + [rgb(c) for c in (
    "#E9A28E", "#F2BE8A", "#FBD9A0", "#C98AA4", "#A877A8",   # the evening sky
    "#8A78B6", "#6E66A8",                                    # hills
    "#4A5C78", "#5B6E86",                                    # trees on the far shore
    "#D49870", "#B57E6E", "#7A5A80", "#9A4E62", "#4E3E5E",   # the shed: front, side, roof, posts
    "#4E7A62", "#6E9A6A", "#8DB56E",                         # the willow and the bank
    "#3E8A8E", "#C9504A",                                    # the boat
)]
RIPPLE_WARM, RIPPLE, RIPPLE_COOL = rgb("#FFDDA8"), rgb("#C7A3D8"), rgb("#8B90D2")
GLITTER, GLITTER_HI = rgb("#FFE3A2"), rgb("#FFF8DA")
PAD_DARK, PAD, PAD_LIT = rgb("#2F7650"), rgb("#4C9A5C"), rgb("#7EC46E")
LILY, LILY_HI, LILY_HEART = rgb("#FF8FB2"), rgb("#FFD3E1"), rgb("#FFD54F")
REED, REED_LIT, REED_DARK, CATTAIL = rgb("#5F8E3B"), rgb("#9CC75A"), rgb("#3F6A35"), rgb("#7A4630")
FIREFLY = rgb("#FFF7A6")
STAR = rgb("#E8E2FF")
ROD, LINE = rgb("#8C5A3C"), rgb("#E9DCC8")
BUCKET, BUCKET_RIM = rgb("#7FA6B8"), rgb("#C7DCE6")
DRAGONFLY, WING = rgb("#2FB0C8"), rgb("#D8F2F6")


def toward(point, vanishing, x):
    """The y at column [x] on the line from [point] towards a [vanishing] point."""
    (px, py), (vx, vy) = point, vanishing
    return py + (vy - py) * (x - px) / (vx - px)


def polygon(cv, points, paint):
    """Fills the polygon through [points], each pixel inside coloured paint(x, y), or left if that's None."""
    top = math.floor(min(y for _, y in points))
    bottom = math.ceil(max(y for _, y in points))
    for y in range(top, bottom + 1):
        centre = y + 0.5
        crossings = []
        for (x0, y0), (x1, y1) in zip(points, points[1:] + points[:1]):
            if (y0 <= centre < y1) or (y1 <= centre < y0):
                crossings.append(x0 + (centre - y0) * (x1 - x0) / (y1 - y0))
        crossings.sort()
        for a, b in zip(crossings[::2], crossings[1::2]):
            for x in range(math.ceil(a - 0.5), math.ceil(b - 0.5)):
                c = paint(x, y)
                if c is not None:
                    cv.put(x, y, c)


# ---- Far away: sky, sun, clouds, hills, the far shore -------------------------------------------

def sky(cv):
    for y in range(HORIZON + 1):
        for x in range(W):
            # Warmer towards the horizon, and towards the sun.
            near_sun = max(0.0, 1 - math.hypot(x - SUN_X, (y - SUN_Y) * 1.5) / 26)
            cv.put(x, y, band(SKY, min(1.0, y / HORIZON + 0.22 * near_sun), x, y, sharpness=2.2))


def sun(cv):
    for y in range(SUN_Y - 9, HORIZON + 1):
        for x in range(SUN_X - 10, SUN_X + 11):
            d = math.hypot(x - SUN_X, y - SUN_Y)
            if d <= 3.3:
                cv.put(x, y, SUN)
            elif d <= 4.5:
                cv.put(x, y, SUN_HALO)
            elif d <= 8 and dither(x, y, (8 - d) / 6):
                cv.put(x, y, mix(cv.get(x, y), SUN_GLOW, 0.55))


def cloud(cv, circles, bottom):
    """A cumulus lit from below by the low sun: gold underneath, warm on its sunward side, violet on top."""
    mask = blob(circles, bottom)
    for x, y in mask:
        if (x, y + 1) not in mask:
            c = CLOUD_LIT
        elif (x, y + 2) not in mask or (x - 1, y) not in mask:
            c = CLOUD_WARM
        elif (x, y - 1) not in mask or (x, y - 2) not in mask:
            c = CLOUD_TOP
        else:
            c = CLOUD_MID
        cv.put(x, y, c)


def clouds(cv):
    cloud(cv, [(44, 3.2, 1.8), (48, 2.4, 2.6), (53, 1.8, 3.2), (58, 2.6, 2.8), (62, 3.6, 2.2)], 4)
    cloud(cv, [(24, 10.4, 1.7), (27, 9.4, 2.3), (30, 10.0, 1.8)], 11)
    cloud(cv, [(1, 13.2, 1.9), (5, 12.0, 2.6), (9, 12.6, 2.1), (12, 13.4, 1.5)], 14)
    # Long thin streaks low in the sky, catching the last of the sun.
    for x0, x1, y in ((1, 15, 18), (23, 34, 16), (6, 12, 21), (25, 31, 19)):
        for x in range(x0, x1 + 1):
            cv.put(x, y, STREAK if abs(x - SUN_X) < 12 else CLOUD_WARM)
        cv.put(x0 - 1, y, mix(cv.get(x0 - 1, y), STREAK, 0.5))
        cv.put(x1 + 1, y, mix(cv.get(x1 + 1, y), STREAK, 0.5))
    for bx, by in ((27, 13), (31, 11), (35, 14)):
        cv.put(bx, by, BIRD)
        cv.put(bx - 1, by - 1, BIRD)
        cv.put(bx + 1, by - 1, BIRD)


def hills(cv):
    """Two ranges of hills, the far one paler in the haze; their edges glow where the sun is behind."""
    for x in range(W):
        far = 23.0 - 3.6 * math.exp(-((x - 31) / 7.5) ** 2) - 2.4 * math.exp(-((x - 4) / 5.5) ** 2) - 1.2 * math.exp(-((x - 50) / 6) ** 2)
        near = 25.6 - 1.4 * math.sin(x / 6.0 + 1.1) - 0.5 * math.sin(x / 2.9)
        glow = max(0.0, 1 - abs(x - SUN_X) / 14)
        for y in range(round(far), HORIZON + 1):
            c = mix(FAR_HILLS, FAR_HILLS_LIT, 0.65 * glow) if y < round(far) + 2 else FAR_HILLS
            cv.put(x, y, c)
        for y in range(round(near), HORIZON + 1):
            c = HILLS if y < round(near) + 2 else HILLS_DARK
            if y == round(near) and glow > 0.15:
                c = mix(HILLS, HILLS_LIT, glow)
            cv.put(x, y, c)


def far_shore(cv):
    """The far shore: a line of trees, rimmed gold where the sun is behind them, and a cottage among them."""
    crowns = [(x, HORIZON - 1.4 - h, r) for x, h, r in (
        (1, 0.6, 1.6), (4, 1.2, 1.9), (7, 0.4, 1.4), (10, 1.0, 1.7), (14, 0.5, 1.5), (31, 0.8, 1.8),
        (34, 1.5, 2.2), (38, 0.6, 1.6), (41, 1.1, 1.9), (45, 0.4, 1.5), (49, 1.3, 2.1), (53, 0.7, 1.6),
        (57, 1.2, 2.0), (61, 0.5, 1.7))]
    trees = blob(crowns, bottom=HORIZON)
    trees |= {(x, y) for x in range(W) for y in (HORIZON - 1, HORIZON)}
    for x, y in trees:
        if not (0 <= x < W):
            continue
        c = TREES_DARK if (x * 3 + y) % 4 == 0 else TREES
        if (x, y - 1) not in trees:
            c = TREES_LIT if abs(x - SUN_X) < 18 else mix(TREES, TREES_LIT, 0.35)
        cv.put(x, y, c)
    for x in range(W):
        cv.put(x, HORIZON, SHORE)
    # A cottage among the trees, its window lit, a little way off to the right of the sun.
    for y in range(HORIZON - 3, HORIZON):
        for x in range(25, 29):
            cv.put(x, y, FAR_HOUSE)
    for x in range(24, 30):
        cv.put(x, HORIZON - 4, FAR_ROOF)
    for x in range(25, 29):
        cv.put(x, HORIZON - 5, FAR_ROOF)
    cv.put(26, HORIZON - 2, FAR_WINDOW)
    cv.put(28, HORIZON - 6, FAR_ROOF)


# ---- The near bank, and the willow ----------------------------------------------------------------

def willow(cv):
    """A weeping willow on the near bank: a crown of leaves, and curtains of strands hanging from it, the
    ones nearest the sun shining where it comes through them."""
    random.seed(12)
    # The trunk, with a branch reaching out over the water.
    for y in range(14, 37):
        lean = round((36 - y) * 0.1)
        cv.put(3 + lean, y, BARK)
        cv.put(4 + lean, y, BARK_DARK)
        if y > 32:
            cv.put(2 + lean, y, BARK)
            cv.put(5 + lean, y, BARK_DARK)
    cv.line(6, 17, 12, 10, BARK)
    cv.line(4, 19, 0, 14, BARK)
    for x in range(-1, 16):
        crown_top = 1 + (x - 5) ** 2 * 0.07 + 0.9 * math.sin(x * 1.4)
        bottom = 33 - abs(x - 5) * 0.9 + random.randrange(-2, 2)
        strand = (x * 7) % 3
        for y in range(max(0, round(crown_top)), min(round(bottom), 35)):
            below_crown = y - crown_top
            # The strands hang apart below the crown, the evening showing between them.
            if below_crown > 6 and x % 3 == 2:
                continue
            if x >= 13 and (x + y) % 2:
                continue
            cluster = (y + x * 2) % 4 == 0
            if below_crown < 5:
                c = WILLOW[3] if cluster or y == round(crown_top) else WILLOW[2]
            else:
                c = (WILLOW[1], WILLOW[2], WILLOW[0])[strand]
                if cluster:
                    c = WILLOW[min(strand + 2, 3)]
            # Backlit: the strands nearest the sun shine.
            if x >= 11:
                c = WILLOW[5] if cluster or (y + x) % 3 == 0 else WILLOW[4]
            cv.put(x, y, c)


def near_bank(cv):
    """Grass and flowers along the near bank under the willow, stones at the water's edge."""
    for x in range(0, 19):
        top = round(31 + (x / 18) ** 1.8 * 5)
        for y in range(top, 37):
            c = GRASS_LIT if y == top else (GRASS if y < top + 3 else GRASS_DARK)
            cv.put(x, y, c)
    for x, y, c in ((2, 31, IRIS), (2, 30, IRIS_LIT), (7, 31, BUTTERCUP), (10, 32, IRIS), (10, 31, IRIS_LIT),
                    (13, 33, POPPY), (5, 32, BUTTERCUP), (15, 34, BUTTERCUP), (1, 33, POPPY)):
        cv.put(x, y, c)
    for cx, cy in ((12, 36), (16, 36), (8, 36)):
        cv.put(cx, cy, STONE_LIT)
        cv.put(cx + 1, cy, STONE)
        cv.put(cx + 1, cy + 1, STONE_DARK)
        cv.put(cx, cy + 1, STONE)


# ---- The shed -------------------------------------------------------------------------------------

def front_top(x):
    return toward((CORNER, EAVE), VP_LEFT, x)


def front_foot(x):
    return toward((CORNER, FLOOR), VP_LEFT, x)


def side_top(x):
    return toward((CORNER, EAVE), VP_RIGHT, x)


def side_foot(x):
    return toward((CORNER, FLOOR), VP_RIGHT, x)


def ridge(x):
    return toward(PEAK, VP_RIGHT, x)


def side_at(s):
    """The column of a point [s] along the side wall, foreshortened as it goes away from us."""
    return CORNER + (VP_RIGHT[0] - CORNER) * s / (s + 40)


def shed(cv):
    # The front wall, in the sun: vertical planks, a darker seam every third column.
    def front(x, y):
        if y < front_top(x) + 1:
            return PLANK_TOP  # in the shadow of the eaves
        c = PLANK_LIT if (x - FRONT_LEFT) % 3 == 0 else PLANK
        if (x - FRONT_LEFT) % 3 == 2:
            c = PLANK_SEAM
        return c
    polygon(cv, [(FRONT_LEFT, front_top(FRONT_LEFT)), (CORNER + 1, EAVE), (CORNER + 1, FLOOR),
                 (FRONT_LEFT, front_foot(FRONT_LEFT))], front)
    # The gable above it, its boards running across, and a round window under the peak.
    polygon(cv, [(FRONT_LEFT, front_top(FRONT_LEFT) + 0.4), PEAK, (CORNER + 1, EAVE + 0.4)],
            lambda x, y: PLANK_SEAM if y % 3 == 0 else (PLANK_LIT if y % 3 == 1 else PLANK))
    for x, y, c in ((39, 13, TRIM), (40, 13, TRIM), (38, 14, TRIM), (41, 14, TRIM), (39, 14, GLASS_HI),
                    (40, 14, GLASS), (39, 15, GLASS), (40, 15, GLASS), (38, 15, TRIM), (41, 15, TRIM),
                    (39, 16, TRIM), (40, 16, TRIM)):
        cv.put(x, y, c)

    # The side wall, in shade: planks closing up as they go away.
    seams = {round(side_at(2.5 * k)) for k in range(1, 12)}
    def side(x, y):
        if y < side_top(x) + 1:
            return SIDE_SEAM
        if x in seams:
            return SIDE_SEAM
        return SIDE_LIT if y < side_top(x) + 4 else SIDE
    polygon(cv, [(CORNER + 1, EAVE), (W + 1, side_top(W + 1)), (W + 1, side_foot(W + 1)), (CORNER + 1, FLOOR)], side)
    # A lit window in the side wall, with a shutter folded back.
    w0, w1 = round(side_at(11)), round(side_at(17))
    for x in range(w0, w1 + 1):
        top = round(side_top(x)) + 4
        for y in range(top, top + 5):
            if x in (w0, w1) or y in (top, top + 4):
                c = TRIM_SHADE
            else:
                c = GLASS_HI if (x, y) == (w0 + 1, top + 1) else GLASS
            cv.put(x, y, c)
    for y in range(round(side_top(w0 - 2)) + 4, round(side_top(w0 - 2)) + 9):
        cv.put(w0 - 2, y, SHUTTER)
        cv.put(w0 - 1, y, SHUTTER_SHADE)
    for x in range(w0 - 1, w1 + 2):
        box = round(side_top(x)) + 9
        cv.put(x, box, FLOWER_BOX)
        cv.put(x, box - 1, (POPPY, FLOWER_PINK, LEAF)[x % 3])
    # The corner post, catching the sun.
    for y in range(EAVE, FLOOR + 1):
        cv.put(CORNER, y, PLANK_LIT)

    # The roof: the side slope in courses of shingles, its ridge catching the sun; cream barge boards.
    eave_corner = (CORNER + 2.0, EAVE + 1.0)
    def lower(x):
        if x < eave_corner[0]:
            return PEAK[1] + (x - PEAK[0]) * (eave_corner[1] - PEAK[1]) / (eave_corner[0] - PEAK[0])
        return toward(eave_corner, VP_RIGHT, x)
    for x in range(math.ceil(PEAK[0]), W):
        top, bottom = round(ridge(x) - 0.5), round(lower(x))
        for y in range(top, bottom + 1):
            k = y - top
            course = (k - 1) // 2
            if k == 0:
                c = ROOF_RIDGE
            elif y == bottom:
                c = TRIM if x < eave_corner[0] else ROOF_LINE
            elif (k - 1) % 2 == 1 and bottom - top > 3:
                c = ROOF_SHADE
            elif (x + course * 2) % 4 == 0:
                c = ROOF_SHADE
            else:
                c = ROOF_LIT if course == 0 else (ROOF if course < 2 else ROOF_SHADE)
            cv.put(x, y, c)
    # The left slope's barge board, and the roof's edge just showing above it.
    left_end = (FRONT_LEFT - 2, front_top(FRONT_LEFT - 2) + 1)
    cv.line(round(PEAK[0]), round(PEAK[1]), round(left_end[0]), round(left_end[1]), TRIM)
    cv.line(round(PEAK[0]) - 1, round(PEAK[1]), round(left_end[0]) - 1, round(left_end[1]), ROOF_SHADE)
    # A finial at the peak.
    cv.put(round(PEAK[0]), round(PEAK[1]) - 1, ROOF_LINE)
    cv.put(round(PEAK[0]), round(PEAK[1]) - 2, ROOF_RIDGE)

    # A stone chimney rising through the roof near the back, and its smoke drifting off to the left.
    cx = 56
    base = round((ridge(cx) + lower(cx)) / 2)
    for y in range(base - 9, base + 1):
        mortar = (y - base) % 3 == 0
        cv.put(cx, y, CHIMNEY_DARK if mortar else CHIMNEY_LIT)
        cv.put(cx + 1, y, CHIMNEY_DARK if mortar else CHIMNEY)
    for x in range(cx - 1, cx + 3):
        cv.put(x, base - 10, CHIMNEY_DARK)
    for i, (x, y) in enumerate(((cx, base - 12), (cx - 1, base - 13), (cx - 1, base - 14), (cx - 2, base - 15),
                                (cx - 3, base - 15), (cx - 4, base - 16), (cx - 6, base - 16), (cx - 7, base - 17),
                                (cx - 9, base - 17), (cx - 11, base - 18))):
        cv.put(x, y, SMOKE if i < 6 else SMOKE_FAINT)
        if i in (1, 4, 7):
            cv.put(x, y + 1, SMOKE_FAINT)

    # The door, ajar onto lamplight.
    for y in range(24, round(front_foot(37))):
        cv.put(36, y, TRIM)
        cv.put(37, y, DOOR_GLOW)
        cv.put(38, y, DOOR)
        cv.put(39, y, DOOR_SHADE)
        cv.put(40, y, TRIM)
    for x in range(36, 41):
        cv.put(x, 23, TRIM)
    cv.put(38, 29, HANDLE)
    # A net hung to dry beside it.
    for y in range(21, 30):
        for x in range(41, 46):
            if (x + y) % 3 == 0 or (x - y) % 3 == 0:
                cv.put(x, y, NET_KNOT if (x + y) % 3 == 0 and (x - y) % 3 == 0 else NET)
    # An oar leant against the wall.
    cv.line(34, 21, 35, 33, OAR)
    cv.put(35, 32, OAR_DARK)
    cv.put(36, 33, OAR_DARK)



def jetty(cv):
    """The landing along the front of the shed, running on as a jetty, on its posts in the water."""
    def back(x):
        return toward((CORNER + 1, FLOOR), VP_LEFT, x)
    left = 20
    for x in range(left, CORNER + 2):
        top = round(back(x))
        cv.put(x, top, DECK_LIT)
        cv.put(x, top + 1, DECK)
        cv.put(x, top + 2, DECK_EDGE if (x - left) % 3 else POST)
    # Posts down into the water, under the landing and the side wall.
    for x in (left, left + 6, FRONT_LEFT - 2, 39, CORNER):
        water = round(toward((CORNER, WATER_LINE), VP_LEFT, x))
        for y in range(round(back(x)) + 3, water + 1):
            cv.put(x, y, POST_LIT if x in (CORNER, left) else POST)
    for s in (8, 18, 30):
        x = round(side_at(s))
        for y in range(round(side_foot(x)) + 1, round(toward((CORNER, WATER_LINE), VP_RIGHT, x)) + 1):
            cv.put(x, y, POST)


BOAT_SHAPE = ["..wwwwwwwwwwwww.",
              ".wbbbbbtbbbbbbbw",
              "wwwwwwwwwwwwwwww",
              ".hhhhhhhhhhhhhh.",
              "..sssssssssssss.",
              "....HHHHHHHHHH.."]


def boat(cv):
    """Her rowing boat, tied up at the end of the jetty, the oars shipped."""
    stamp(cv, 3, 37, BOAT_SHAPE, {"w": HULL_RIM, "b": HULL_INSIDE, "t": HULL_RIM, "h": HULL, "s": HULL_STRIPE,
                                   "H": HULL_SHADE})
    for y in range(43, 47):
        for x in range(5 + (y - 43), 18 - (y - 43)):
            if (x + y) % 2 == 0 or y == 43:
                cv.put(x, y, nearest(mix(cv.get(x, y), HULL if y < 45 else HULL_STRIPE, 0.5), WATER_TONES))
    cv.line(5, 36, 17, 37, OAR)
    cv.put(4, 36, OAR_DARK)
    cv.line(18, 39, 21, 37, ROPE)


def water(cv, near_layers):
    """The pond: the far scene mirrored about the far shore, nearer things about where they stand in the
    water, all fading into the deep water as it comes towards us, and broken by ripples."""
    scene = cv.snapshot()
    shift = (0, 1, 0, -1)
    for y in range(HORIZON + 1, H):
        depth = y - HORIZON
        base = band(WATER, depth / (H - HORIZON - 8), (y * 7) % 4, y, sharpness=1.6)
        strength = 0.75 if depth < 4 else (0.55 if depth < 9 else (0.4 if depth < 15 else 0.0))
        for x in range(W):
            source = 2 * HORIZON - y
            c = base
            if strength and source >= 0:
                c = mix(base, scene[source][min(max(x + shift[y % 4], 0), W - 1)], strength)
            cv.put(x, y, nearest(c, WATER_TONES))
    # Nearer things, each mirrored about its own water line, in strips the ripples break up.
    for layer, line in near_layers:
        for x in range(W):
            w = line(x)
            for y in range(math.ceil(w), min(H, math.ceil(w) + 14)):
                if (y - math.ceil(w)) % 3 == 2:
                    continue
                source = round(2 * w - y)
                c = layer.px[source][min(max(x + shift[y % 4], 0), W - 1)] if source >= 0 else None
                if c is not None:
                    fade = 0.6 - (y - w) * 0.03
                    cv.put(x, y, nearest(mix(cv.get(x, y), c, fade), WATER_TONES))


def sun_on_the_water(cv):
    """The sun's road: glitter beneath it, breaking up and widening as it comes nearer."""
    for y in range(HORIZON + 1, 48):
        d = y - HORIZON
        half = 0.6 + d * 0.15
        for x in range(math.floor(SUN_X - half - 1), math.ceil(SUN_X + half + 2)):
            edge = 1 - abs(x - SUN_X - (d % 3 == 1)) / (half + 0.5)
            if edge > 0 and (x * 3 + y * 5) % 7 < 3 + edge * 3:
                cv.put(x, y, GLITTER_HI if edge > 0.55 and d < 12 else GLITTER)


def ripples(cv):
    """Ripple lines catching the light: short and close together far off, longer and wider apart nearby."""
    random.seed(21)
    y = HORIZON + 1.5
    gap = 1.0
    while y < H:
        row = round(y)
        length = 1 + round((row - HORIZON) * 0.13)
        x = random.randrange(0, 5)
        while x < W:
            if not abs(x - SUN_X) < 4 + (row - HORIZON) * 0.25:
                warm = 1 - (row - HORIZON) / 30
                colour = RIPPLE_WARM if warm > 0.75 else (RIPPLE if warm > 0.2 else RIPPLE_COOL)
                for i in range(length):
                    cv.put(x + i, row, mix(cv.get(x + i, row), colour, 0.55))
            x += length + random.randrange(3, 9) + round((row - HORIZON) * 0.2)
        gap *= 1.17
        y += gap


def lily_pads(cv):
    """Lily pads, slivers far off and broader nearer us, notched, some in flower; only on open water."""
    for cx, cy, flower in ((9, 31, False), (27, 31, True), (4, 33, False), (35, 30, False), (52, 33, False),
                           (6, 43, True), (17, 46, False), (37, 41, False), (2, 50, True), (13, 54, False),
                           (26, 51, True), (44, 48, False), (56, 45, True), (38, 58, True), (57, 57, False),
                           (21, 62, False), (47, 63, True), (31, 66, False), (8, 67, True)):
        width = min(7, 2 + (cy - HORIZON) // 5)
        cells = [(x, cy) for x in range(cx - width // 2, cx + (width + 1) // 2)]
        if cy - HORIZON > 12:
            cells += [(x, cy + 1) for x in range(cx - width // 2 + 1, cx + (width + 1) // 2 - 1)]
        if not all(cv.get(x, y) in WATER_TONES for x, y in cells if 0 <= x < W):
            continue
        for x, y in cells:
            if y > cy:
                c = PAD_DARK
            elif x == cx + (width + 1) // 2 - 1 and width > 3:
                c = PAD_DARK if y == cy else PAD  # the notch
            else:
                c = PAD_LIT if x < cx else PAD
            cv.put(x, y, c)
        if flower:
            cv.put(cx, cy - 1, LILY_HEART if width > 4 else LILY)
            cv.put(cx - 1, cy - 1, LILY)
            cv.put(cx + 1, cy - 1, LILY)
            cv.put(cx, cy - 2, LILY_HI)


def reed_clump(cv, x0, bottom, blades):
    """Reeds and bulrushes standing in the shallows: each blade (offset, height, lean, has a head)."""
    for i, (dx, height, lean, head) in enumerate(blades):
        for k in range(height):
            x = x0 + dx + round(lean * k / height)
            c = REED_DARK if k < height * 0.3 else (REED_LIT if (k + i) % 3 == 0 else REED)
            cv.put(x, bottom - k, c)
        top = x0 + dx + round(lean)
        if head:
            cv.put(top, bottom - height, CATTAIL)
            cv.put(top, bottom - height - 1, CATTAIL)
            cv.put(top, bottom - height - 2, REED_LIT)


def reeds(cv):
    reed_clump(cv, 13, 37, [(0, 6, -1, True), (1, 9, 0, True), (2, 5, 1, False), (3, 8, 1, True), (4, 4, 2, False)])
    reed_clump(cv, 48, 41, [(0, 5, -1, False), (1, 8, 0, True), (2, 6, 1, False)])
    reed_clump(cv, 59, 40, [(0, 7, 0, True), (1, 5, -1, False), (2, 9, 0, True), (3, 6, 1, False), (4, 8, 1, True)])


def lantern(cv):
    """The lantern, hung out on a bracket from the corner of the eaves, glowing; and its light in the water."""
    cv.line(FRONT_LEFT - 1, 20, FRONT_LEFT - 3, 20, LANTERN_FRAME)
    for y in range(17, 26):
        for x in range(25, 36):
            d = math.hypot(x - 30, (y - 22.5) * 1.2)
            if 1.5 < d < 4.2 and dither(x, y, (4.2 - d) / 3.5) and cv.get(x, y) not in (PLANK, PLANK_LIT, PLANK_SEAM):
                cv.put(x, y, mix(cv.get(x, y), LANTERN_GLOW, 0.5))
    stamp(cv, 29, 21, [".f.", "fgf", "fgf", ".f."], {"f": LANTERN_FRAME, "g": LANTERN_GLASS})
    cv.put(30, 22, GLASS_HI)
    for y in (40, 42, 44):
        cv.put(30, y, mix(cv.get(30, y), LANTERN_GLOW, 0.7))
        cv.put(29 + y % 4 // 2, y + 1, mix(cv.get(29, y + 1), LANTERN_GLOW, 0.4))


def the_golden_fish(cv):
    """Leaping beside the jetty, right under the lantern, and the rings where it rose."""
    golden_fish(cv, 29, 37, facing=1, leaping=True)
    for x, y in ((27, 42), (29, 43), (32, 43), (34, 42), (33, 41)):
        cv.put(x, y, RIPPLE_WARM)


def early_stars(cv):
    """The first stars, high up where the sky has gone deep blue."""
    for x, y in ((21, 2), (33, 4), (9, 1), (40, 1), (27, 6)):
        if cv.get(x, y) in SKY[:4]:
            cv.put(x, y, STAR)


def on_the_jetty(cv):
    """Lamplight from the door across the boards, Anna's rod propped with its line out, a bucket."""
    for x in range(35, 42):
        y = round(toward((CORNER + 1, FLOOR), VP_LEFT, x))
        cv.put(x, y, mix(DECK_LIT, DOOR_GLOW, 0.6))
    cv.line(23, 33, 18, 25, ROD)
    for x, y in ((17, 26), (16, 28), (15, 31), (15, 34)):
        cv.put(x, y, LINE)
    cv.put(15, 35, LILY)  # the float
    stamp(cv, 25, 30, ["bbb", "BbB", ".B."], {"b": BUCKET_RIM, "B": BUCKET})


def cloud_reflections(cv):
    """The big cloud, faint and broken, in the water far below it."""
    for y, x0, x1 in ((53, 44, 60), (55, 47, 63), (57, 50, 58)):
        for x in range(x0, x1 + 1):
            if (x + y) % 3:
                cv.put(x, y, mix(cv.get(x, y), CLOUD_WARM, 0.35))


def dragonfly(cv):
    for x, y, c in ((40, 38, DRAGONFLY), (41, 38, DRAGONFLY), (42, 38, DRAGONFLY), (43, 38, DRAGONFLY),
                    (41, 37, WING), (42, 37, WING), (41, 39, WING), (42, 39, WING)):
        cv.put(x, y, c)


def fireflies(cv):
    for x, y in ((9, 27), (14, 24), (3, 22), (12, 30), (20, 30), (52, 37), (61, 33)):
        cv.put(x, y, FIREFLY)


def paint():
    cv = Canvas()
    sky(cv)
    sun(cv)
    clouds(cv)
    early_stars(cv)
    hills(cv)
    far_shore(cv)

    # The nearer things are painted on layers of their own, so the water can mirror each about its foot.
    bank = Canvas()
    willow(bank)
    near_bank(bank)
    building = Canvas()
    shed(building)
    jetty(building)
    water(cv, [(bank, lambda x: 36.5),
               (building, lambda x: toward((CORNER, WATER_LINE), VP_LEFT if x < CORNER else VP_RIGHT, x))])
    sun_on_the_water(cv)
    ripples(cv)
    cloud_reflections(cv)
    for layer in (bank, building):
        for y in range(H):
            for x in range(W):
                if layer.px[y][x] is not None:
                    cv.put(x, y, layer.px[y][x])
    lily_pads(cv)
    boat(cv)
    reeds(cv)
    on_the_jetty(cv)
    lantern(cv)
    the_golden_fish(cv)
    dragonfly(cv)
    fireflies(cv)
    return cv.image()
