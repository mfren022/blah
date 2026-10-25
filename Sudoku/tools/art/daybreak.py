"""
Chapter 9, Daybreak: Anna's window from inside her hut at sunrise, nine panes like a box of a sudoku,
the pond beyond them gold with the first sun and gulls in the sky, and on the sill, beside the lantern,
a single golden scale.
"""
import math

from pixels import GOLD, W, H, Canvas, band, dither, mix, rgb, sky, stamp

# The window: the view inside its frame, cut into three by three panes by the glazing bars.
LEFT, TOP = 24, 6
PANE_W, PANE_H = 11, 9
RIGHT = LEFT + 3 * PANE_W + 2   # 59, the last column of the view
BOTTOM = TOP + 3 * PANE_H + 2   # 35
SILL = BOTTOM + 2
SHORE = 23                      # the far shore of the pond, seen through the window
SUN_X = 47

# The room is still in shadow, so the window glows.
BEAM, BEAM_DK = rgb("#5F4D6C"), rgb("#54445F")
WALL_HI, WALL, WALL_DK = rgb("#8C7491"), rgb("#7D6686"), rgb("#6D5878")
FRAME_HI, FRAME, FRAME_DK = rgb("#F6E3CC"), rgb("#E6C8AB"), rgb("#C7A38F")
SILL_TOP, SILL_FRONT = rgb("#FBEBD6"), rgb("#D9B79F")
DAWN = [rgb(c) for c in ("#D3C2EA", "#E6C6E4", "#F5CBD9", "#FCD5CB", "#FFDFBE", "#FFE9BF", "#FFF2CF")]
SUN, SUN_HALO = rgb("#FFF8DE"), rgb("#FFE7B0")
CLOUD = rgb("#FBD9DF")
HILL, HILL_HI = rgb("#C9AED6"), rgb("#DCC2E2")
TREES = rgb("#B79FCB")
POND = [rgb(c) for c in ("#F4D3D8", "#E6CBE2", "#D3C4E9")]
GLINT, GLINT_HI = rgb("#FFE9B8"), rgb("#FFF8E2")
GULL = rgb("#8C83B8")
CURTAIN_HI, CURTAIN, CURTAIN_DK = rgb("#F4BCCB"), rgb("#DE9DB3"), rgb("#BD8199")
POT, POT_DK, LEAF, FLOWER = rgb("#E9A99B"), rgb("#CD8C85"), rgb("#9FD3AE"), rgb("#FFC4D3")
LANTERN_FRAME, LANTERN_GLASS = rgb("#9F7C70"), rgb("#F4EAD8")


def room(cv):
    """The plank wall around the window, with a beam across the top."""
    for y in range(H):
        for x in range(W):
            c = WALL if (x % 6) != 5 else WALL_DK
            if x % 6 == 0:
                c = WALL_HI
            cv.put(x, y, c)
    for y in range(0, 3):
        for x in range(W):
            cv.put(x, y, BEAM if y < 2 else BEAM_DK)
    # Dawn light spilling from the window onto the wall around it.
    for y in range(TOP - 4, SILL + 6):
        for x in range(LEFT - 6, W):
            d = max(LEFT - 2 - x, TOP - 2 - y, y - SILL - 1, 0)
            if 0 < d < 6 and dither(x, y, (6 - d) / 8):
                cv.put(x, y, mix(cv.get(x, y), FRAME_HI, 0.3))


def view(cv):
    """Through the glass: the sky at sunrise, the far shore, and the pond catching the sun."""
    scene = Canvas()
    sky(scene, DAWN, SHORE + 1, top=TOP)
    for y in range(TOP, SHORE + 1):
        for x in range(LEFT, RIGHT + 1):
            d = math.hypot(x - SUN_X, y - SHORE)
            if d <= 5.2:
                scene.put(x, y, SUN)
            elif d <= 6.4:
                scene.put(x, y, SUN_HALO)
            elif d <= 8.5 and dither(x, y, 0.5):
                scene.put(x, y, SUN_HALO)
    for x0, x1, y in ((27, 38, 11), (44, 57, 14), (30, 36, 16)):
        for x in range(x0, x1 + 1):
            if (x - x0) % 5 != 4:
                scene.put(x, y, CLOUD)
    # The far shore: low hills, a line of trees.
    for x in range(LEFT, RIGHT + 1):
        top = round(SHORE - 1.6 - 1.2 * math.sin(x / 4.0) - 0.7 * math.sin(x / 1.9))
        for y in range(top, SHORE + 1):
            if not (y < SHORE - 1 and math.hypot(x - SUN_X, y - SHORE) < 5.2):
                scene.put(x, y, HILL_HI if y == top else HILL)
    for x, h in ((27, 3), (29, 4), (31, 3), (55, 3), (57, 4)):
        for y in range(SHORE - h - 1, SHORE - 1):
            scene.put(x, y, TREES)
    # The pond, gold under the sun.
    for y in range(SHORE + 1, BOTTOM + 1):
        for x in range(LEFT, RIGHT + 1):
            c = band(POND, (y - SHORE) / (BOTTOM - SHORE), x, y, sharpness=2)
            half = 1 + (y - SHORE) * 0.45
            if abs(x - SUN_X) < half and (x + y) % 3:
                c = GLINT_HI if abs(x - SUN_X) < half * 0.5 else GLINT
            scene.put(x, y, c)
    # Gulls.
    for gx, gy in ((32, 9), (36, 12), (52, 8)):
        for dx, dy in ((-1, -1), (0, 0), (1, 0), (2, -1)):
            scene.put(gx + dx, gy + dy, GULL)
    for y in range(TOP, BOTTOM + 1):
        for x in range(LEFT, RIGHT + 1):
            cv.put(x, y, scene.get(x, y))


def window_frame(cv):
    """The frame and its glazing bars: three by three panes."""
    for y in range(TOP - 2, BOTTOM + 2):
        for x in (LEFT - 2, LEFT - 1, RIGHT + 1, RIGHT + 2):
            cv.put(x, y, FRAME_HI if x == LEFT - 2 else (FRAME_DK if x == RIGHT + 2 else FRAME))
    for x in range(LEFT - 2, RIGHT + 3):
        cv.put(x, TOP - 2, FRAME_HI)
        cv.put(x, TOP - 1, FRAME)
    for i in (1, 2):
        bar_x = LEFT + i * (PANE_W + 1) - 1
        bar_y = TOP + i * (PANE_H + 1) - 1
        for y in range(TOP, BOTTOM + 1):
            cv.put(bar_x, y, FRAME)
        for x in range(LEFT, RIGHT + 1):
            cv.put(x, bar_y, FRAME)


def sill(cv):
    for x in range(LEFT - 4, RIGHT + 5):
        cv.put(x, BOTTOM + 1, SILL_TOP)
        cv.put(x, BOTTOM + 2, SILL_TOP if x % 9 else FRAME)
        cv.put(x, BOTTOM + 3, SILL_FRONT)
    for x in range(LEFT - 3, RIGHT + 4):
        cv.put(x, BOTTOM + 4, mix(WALL_DK, SILL_FRONT, 0.4))


def curtain(cv):
    """A curtain drawn back to the left of the window, in folds."""
    for y in range(3, SILL + 8):
        sway = 0 if y < 14 else round((y - 14) * 0.12)
        for x in range(13, 23 - sway):
            fold = (x - 13) % 4
            c = CURTAIN if fold in (1, 2) else (CURTAIN_HI if fold == 0 else CURTAIN_DK)
            cv.put(x, y, c)
    # Its tie-back.
    for x in range(12, 22):
        cv.put(x, 24, FRAME_DK)


def things_on_the_sill(cv):
    """A pot of flowers, the lantern, put out now it's day, and the golden scale, catching the light."""
    for y in range(BOTTOM - 2, BOTTOM + 1):
        for x in range(27, 31):
            cv.put(x, y, POT if x < 30 else POT_DK)
    for x, y in ((27, BOTTOM - 3), (28, BOTTOM - 4), (29, BOTTOM - 3), (30, BOTTOM - 4), (28, BOTTOM - 5)):
        cv.put(x, y, LEAF)
    cv.put(28, BOTTOM - 6, FLOWER)
    cv.put(30, BOTTOM - 5, FLOWER)
    for y in range(BOTTOM - 5, BOTTOM + 1):
        for x in range(53, 57):
            c = LANTERN_FRAME if x in (53, 56) or y in (BOTTOM - 5, BOTTOM) else LANTERN_GLASS
            cv.put(x, y, c)
    cv.put(54, BOTTOM - 6, LANTERN_FRAME)
    cv.put(55, BOTTOM - 6, LANTERN_FRAME)
    # The scale: a little gold shield, catching the sun, and its glint.
    stamp(cv, 40, BOTTOM - 2, [".ggg.", "ghhgd", ".gdd."], GOLD)
    for x, y in ((44, BOTTOM - 5), (44, BOTTOM - 7), (43, BOTTOM - 6), (45, BOTTOM - 6)):
        cv.put(x, y, GOLD["h"])
    cv.put(44, BOTTOM - 6, SUN)


def paint():
    cv = Canvas()
    room(cv)
    view(cv)
    window_frame(cv)
    sill(cv)
    curtain(cv)
    things_on_the_sill(cv)
    return cv.image()
