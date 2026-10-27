#!/usr/bin/env python3
"""
Paints the story chapters' art into app/src/main/res/drawable-nodpi/, one module per chapter: pixel
art, one pixel to each of the grid's squares, as art_<module>.png; or a painting at a whole multiple of
the grid's size, which the squares look onto, as art_<module>.webp. Run from Sudoku/:

    python3 tools/art/paint.py [chapter ...] [--preview DIR]

With no chapters named, it paints all nine. A preview shows each picture as the app does, on a
411 x 891 dp phone, beside its bare pixels; DIR/chapters.png sets all nine side by side.
"""
import argparse
import importlib
from pathlib import Path

from PIL import Image

from pixels import W, H, as_on_a_phone

CHAPTERS = ["pond", "village", "harbour", "lighthouse", "open_sea", "kites", "stars", "deep", "daybreak"]
RES = Path(__file__).resolve().parents[2] / "app/src/main/res/drawable-nodpi"


def bare(art, scale):
    """The art at [scale] times the grid's size: pixels kept square, a painting smoothly resized."""
    return art.resize((W * scale, H * scale), Image.NEAREST if art.width == W else Image.LANCZOS)


def preview(art):
    phone = as_on_a_phone(art)
    bare_art = bare(art, 8)
    sheet = Image.new("RGB", (phone.width + 20 + bare_art.width, max(phone.height, bare_art.height)), (128, 128, 128))
    sheet.paste(phone, (0, 0))
    sheet.paste(bare_art, (phone.width + 20, 0))
    return sheet


def contact_sheet(arts):
    """All the chapters in three rows of three: each as on a phone, its bare pixels beneath."""
    tiles = []
    for art in arts:
        phone = as_on_a_phone(art).resize((411, 445), Image.LANCZOS)
        bare_art = bare(art, 4)
        tile = Image.new("RGB", (411, 445 + 10 + bare_art.height), (40, 44, 52))
        tile.paste(phone, (0, 0))
        tile.paste(bare_art, ((411 - bare_art.width) // 2, 455))
        tiles.append(tile)
    tw, th = tiles[0].size
    sheet = Image.new("RGB", (3 * tw + 40, 3 * th + 40), (40, 44, 52))
    for i, tile in enumerate(tiles):
        sheet.paste(tile, (10 + (i % 3) * (tw + 10), 10 + (i // 3) * (th + 10)))
    return sheet


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("chapters", nargs="*", help="chapters to paint, of " + ", ".join(CHAPTERS) + " (default: all)")
    parser.add_argument("--preview", help="also write previews into this directory")
    args = parser.parse_args()
    unknown = [name for name in args.chapters if name not in CHAPTERS]
    if unknown:
        parser.error("no such chapter: " + ", ".join(unknown))
    names = args.chapters or CHAPTERS
    arts = []
    for name in names:
        art = importlib.import_module(name).paint()
        # One file per chapter: a painting as WebP, small for its size; pixel art as PNG.
        painted = art.width > W
        (RES / f"art_{name}.{'png' if painted else 'webp'}").unlink(missing_ok=True)
        if painted:
            art.save(RES / f"art_{name}.webp", "WEBP", quality=92, method=6)
        else:
            art.save(RES / f"art_{name}.png", optimize=True)
        arts.append(art)
        if args.preview:
            Path(args.preview).mkdir(parents=True, exist_ok=True)
            preview(art).save(Path(args.preview) / f"{name}.png")
    if args.preview and len(arts) == len(CHAPTERS):
        contact_sheet(arts).save(Path(args.preview) / "chapters.png")


if __name__ == "__main__":
    main()
