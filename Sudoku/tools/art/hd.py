"""
Machinery for painting a chapter's art at high resolution rather than in pixels: a canvas of floating
point colour, and layers of smooth shapes in the grid's units, drawn larger than needed and brought down
so their edges are smooth. A picture painted this way is [F] times the grid's size each way.
"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

GW, GH = 64, 72          # the grid, in squares
F = 20                   # pixels per square
W, H = GW * F, GH * F
SS = 2                   # shapes are drawn this much larger, then brought down, for smooth edges


def rgb(code):
    code = code.lstrip("#")
    return np.array([int(code[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32) / 255


def mix(a, b, t):
    return a + (b - a) * t


def ramp(colours, t):
    """Colours graded evenly through [colours] by t in 0..1 (an array)."""
    t = np.clip(t, 0, 1) * (len(colours) - 1)
    i = np.minimum(np.floor(t).astype(int), len(colours) - 2)
    f = (t - i)[..., None]
    c = np.stack(colours)
    return c[i] * (1 - f) + c[i + 1] * f


def noise(scale, seed, octaves=1, shape=(H, W)):
    """Smooth value noise in 0..1 with features about [scale] pixels across."""
    rng = np.random.default_rng(seed)
    out = np.zeros(shape, np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        s = max(2, scale / (2 ** o))
        gh, gw = int(shape[0] / s) + 3, int(shape[1] / s) + 3
        grid = rng.random((gh, gw)).astype(np.float32)
        im = Image.fromarray((grid * 255).astype(np.uint8)).resize((int(gw * s), int(gh * s)), Image.BICUBIC)
        a = np.asarray(im, np.float32)[: shape[0], : shape[1]] / 255
        out += a * amp
        total += amp
        amp *= 0.5
    return out / total


class Canvas:
    def __init__(self, colour=(0, 0, 0)):
        self.px = np.zeros((H, W, 3), np.float32) + np.array(colour, np.float32)

    def over(self, rgba, opacity=1.0):
        a = rgba[..., 3:4] * opacity
        self.px = self.px * (1 - a) + rgba[..., :3] * a

    def add(self, rgb_light):
        self.px = 1 - (1 - self.px) * (1 - np.clip(rgb_light, 0, 1))  # screen

    def image(self):
        return Image.fromarray((np.clip(self.px, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")


def ys():
    return np.arange(H, dtype=np.float32)[:, None] / F


def xs():
    return np.arange(W, dtype=np.float32)[None, :] / F


class Layer:
    """Shapes in grid units, drawn supersampled onto a clear layer."""

    def __init__(self):
        self.im = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    @staticmethod
    def c(colour, alpha=1.0):
        r, g, b = (np.clip(np.asarray(colour), 0, 1) * 255).round().astype(int)
        return (int(r), int(g), int(b), int(round(alpha * 255)))

    def p(self, x, y):
        return (x * F * SS, y * F * SS)

    def polygon(self, points, colour, alpha=1.0):
        self.d.polygon([self.p(x, y) for x, y in points], fill=self.c(colour, alpha))

    def ellipse(self, cx, cy, rx, ry, colour, alpha=1.0):
        x0, y0 = self.p(cx - rx, cy - ry)
        x1, y1 = self.p(cx + rx, cy + ry)
        self.d.ellipse([x0, y0, x1, y1], fill=self.c(colour, alpha))

    def line(self, points, colour, width, alpha=1.0):
        self.d.line([self.p(x, y) for x, y in points], fill=self.c(colour, alpha), width=max(1, round(width * F * SS)), joint="curve")

    def rect(self, x0, y0, x1, y1, colour, alpha=1.0):
        a, b = self.p(x0, y0)
        c, d = self.p(x1, y1)
        self.d.rectangle([a, b, c, d], fill=self.c(colour, alpha))

    def rgba(self, blur=0.0):
        im = self.im.convert("RGBa").resize((W, H), Image.LANCZOS)
        if blur:
            im = im.filter(ImageFilter.GaussianBlur(blur * F))
        im = im.convert("RGBA")
        return np.asarray(im, np.float32) / 255


def soft_mask(mask, blur):
    """A boolean or 0..1 mask, softened by a blur of [blur] grid units."""
    im = Image.fromarray((np.clip(mask, 0, 1) * 255).astype(np.uint8))
    im = im.filter(ImageFilter.GaussianBlur(blur * F))
    return np.asarray(im, np.float32) / 255


def solid(colour, alpha):
    """An RGBA layer of one colour, [alpha] a 0..1 array."""
    out = np.zeros((H, W, 4), np.float32)
    out[..., :3] = colour
    out[..., 3] = alpha
    return out


def paint_rgb(rgb_array, alpha):
    out = np.zeros((H, W, 4), np.float32)
    out[..., :3] = rgb_array
    out[..., 3] = alpha
    return out
