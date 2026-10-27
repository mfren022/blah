"""
Shared tools for the story chapters' pixel art.

The app shows each picture through the chapter screen's grid of small squares, one square per art
pixel, anchored to the top-right corner and lit by a circular gradient that is brightest just left of
that corner and fades out towards the middle of the screen. So a picture is 64 x 72: as wide as a
phone's grid, and tall enough to reach where the light runs out. What matters goes top right; the lower
left is barely lit, and the chapter's title is written over everything below row 40 or so.

The top rows sit under the status bar's white icons, and the top-left corner under the back arrow,
so they stay mid-toned. The page behind is deep blue, so pastels glow and dark colours vanish.
"""
import math

from PIL import Image

W, H = 64, 72

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]

def rgb(code):
    return tuple(int(code[i:i + 2], 16) for i in (1, 3, 5))


# The golden fish that leads Anna through her dream turns up in every chapter.
GOLD = {"h": rgb("#FFF2C2"), "g": rgb("#FFD670"), "d": rgb("#F5AE55"), "e": rgb("#C77A4A")}
FISH = [".hhg..g",
        "geggggd",
        ".dddg.g"]
FISH_LEAPING = ["g....",
                ".eh..",
                ".dgh.",
                "..dgg",
                "...d."]


def dither(x, y, amount):
    """True where an ordered dither of [amount] (0 to 1) lands on this pixel."""
    return amount > (BAYER[y % 4][x % 4] + 0.5) / 16


def band(colours, t, x, y, sharpness=2.5):
    """A colour from a list of bands at [t] (0 to 1), dithered only near each boundary."""
    t = min(max(t, 0.0), 1.0) * (len(colours) - 1)
    i = min(int(t), len(colours) - 2)
    f = min(max((t - i - 0.5) * sharpness + 0.5, 0.0), 1.0)
    return colours[i + 1] if dither(x, y, f) else colours[i]


def blob(circles, bottom=H):
    """Pixels inside any of the circles (cx, cy, r), above [bottom]."""
    mask = set()
    for cx, cy, r in circles:
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and y <= bottom:
                    mask.add((x, y))
    return mask


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def nearest(c, colours):
    return min(colours, key=lambda p: sum((p[i] - c[i]) ** 2 for i in range(3)))


class Canvas:
    """A W x H picture, painted pixel by pixel; anything left unpainted shows magenta."""

    def __init__(self):
        self.px = [[None] * W for _ in range(H)]

    def put(self, x, y, c):
        if 0 <= x < W and 0 <= y < H:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[min(max(y, 0), H - 1)][min(max(x, 0), W - 1)]

    def line(self, x0, y0, x1, y1, c, step=1):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(0, n + 1, step):
            self.put(round(x0 + (x1 - x0) * i / max(n, 1)), round(y0 + (y1 - y0) * i / max(n, 1)), c)

    def snapshot(self):
        return [row[:] for row in self.px]

    def image(self):
        out = Image.new("RGB", (W, H))
        for y in range(H):
            for x in range(W):
                out.putpixel((x, y), self.px[y][x] or (255, 0, 255))
        return out


def stamp(cv, x, y, rows, legend, flip=False):
    """A little picture drawn as rows of characters, each a colour from [legend]; others are clear."""
    width = max(len(row) for row in rows)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in legend:
                cv.put(x + (width - 1 - i if flip else i), y + j, legend[ch])


def golden_fish(cv, x, y, facing=-1, leaping=False):
    """The golden fish with its top-left at (x, y), swimming left (or right, [facing] 1), or leaping up and left."""
    stamp(cv, x, y, FISH_LEAPING if leaping else FISH, GOLD, flip=facing > 0)


# ---- Pieces most scenes share -------------------------------------------------------------------

def sky(cv, colours, bottom, top=0, sharpness=3):
    """Rows [top] to [bottom] graded through [colours], first at the top."""
    for y in range(top, bottom):
        for x in range(W):
            cv.put(x, y, band(colours, (y - top) / max(1, bottom - top - 2), x, y, sharpness))


def stars(cv, points, colour, twinkle=None):
    """Single-pixel stars; any in [twinkle] get four dimmer points around them."""
    for x, y in points:
        cv.put(x, y, colour)
    for x, y in twinkle or ():
        cv.put(x, y, colour)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            cv.put(x + dx, y + dy, mix(cv.get(x + dx, y + dy), colour, 0.5))


def moon(cv, cx, cy, r, body, shade, halo, bite=None):
    """A moon with a faint ring around it; [bite] (dx, dy, r) cuts a crescent, showing the sky behind."""
    behind = cv.snapshot()
    for y in range(int(cy - r - 3), int(cy + r + 4)):
        for x in range(int(cx - r - 3), int(cx + r + 4)):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                # Shaded along the rim away from the light, top left.
                cv.put(x, y, shade if math.hypot(x - cx + 1.3, y - cy + 1.3) > r else body)
            elif d <= r + 1.6 and (x + y) % 2 == 0:
                cv.put(x, y, halo)
    if bite:
        bx, by, br = cx + bite[0], cy + bite[1], bite[2]
        for y in range(int(by - br - 1), int(by + br + 2)):
            for x in range(int(bx - br - 1), int(bx + br + 2)):
                if math.hypot(x - bx, y - by) <= br and 0 <= x < W and 0 <= y < H:
                    cv.put(x, y, behind[y][x])


def cloud(cv, circles, bottom, hi, mid, shade):
    """A puffy cloud from overlapping circles, flat along [bottom], lit from above."""
    mask = blob(circles, bottom)
    for x, y in mask:
        c = mid
        if (x, y - 1) not in mask:
            c = hi
        if (x, y + 1) not in mask:
            c = shade
        cv.put(x, y, c)
    return mask


def reflect(cv, horizon, water, tones, fade=0.55, fade_step=0.025, reach=18, ripple=(0, 0, 0, 1, 0, 0, 0, -1)):
    """Water from [horizon] down, graded through [water], mirroring the scene above it for [reach] rows
    as it fades, every pixel snapped to the nearest of [water] and [tones] to keep the palette small."""
    scene = cv.snapshot()
    palette = list(water) + list(tones)
    for y in range(horizon, H):
        depth = y - horizon
        for x in range(W):
            base = band(water, depth / (H - horizon - 6), x, y, sharpness=2)
            if depth > reach or horizon - 1 - depth < 0:
                cv.put(x, y, base)
                continue
            reflected = scene[horizon - 1 - depth][min(max(x + ripple[y % len(ripple)], 0), W - 1)]
            cv.put(x, y, nearest(mix(reflected, base, min(fade + depth * fade_step, 0.95)), palette))


def as_on_a_phone(art, page=(0x0A, 0x3C, 0x8C)):
    """The art as the app draws it on a 411 x 891 dp phone at 3x: SquareGlowView's grid and light. The art
    lies under the grid, a square's worth of it to each step from one square to the next, so each square
    shows one pixel of pixel art, or the part of a painting under it."""
    width, height = 1233, 2673
    gap, size = 3, 17
    pitch = size + gap
    columns = (width + gap) // pitch
    left = (width - columns * pitch + gap) // 2
    cx, cy = width * 0.82, height * 0.03
    radius = math.hypot(cx - width / 2, cy - height / 2)
    scale = art.width // W
    under = art.convert("RGB").resize((W * pitch, H * pitch), Image.NEAREST if scale == 1 else Image.LANCZOS)
    out = Image.new("RGB", (width, height // 2), page)
    for row in range(H):
        for column in range(columns):
            ax = W - columns + column
            if ax < 0:
                continue
            x, y = left + column * pitch, left + row * pitch
            light = 1 - math.hypot(x + size / 2 - cx, y + size / 2 - cy) / radius
            if light <= 0 or y >= out.height:
                continue
            square = under.crop((ax * pitch, row * pitch, ax * pitch + size, row * pitch + size))
            out.paste(Image.blend(Image.new("RGB", square.size, page), square, light), (x, y))
    return out
