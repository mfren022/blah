#!/usr/bin/env python3
"""
Paints the story chapters' pixel art into app/src/main/res/drawable-nodpi/, one module per chapter,
as art_<module>.png. Run from Sudoku/:

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


def preview(art):
    phone = as_on_a_phone(art)
    bare = art.resize((W * 8, H * 8), Image.NEAREST)
    sheet = Image.new("RGB", (phone.width + 20 + bare.width, max(phone.height, bare.height)), (128, 128, 128))
    sheet.paste(phone, (0, 0))
    sheet.paste(bare, (phone.width + 20, 0))
    return sheet


def contact_sheet(arts):
    """All the chapters in three rows of three: each as on a phone, its bare pixels beneath."""
    tiles = []
    for art in arts:
        phone = as_on_a_phone(art).resize((411, 445), Image.LANCZOS)
        bare = art.resize((W * 4, H * 4), Image.NEAREST)
        tile = Image.new("RGB", (411, 445 + 10 + bare.height), (40, 44, 52))
        tile.paste(phone, (0, 0))
        tile.paste(bare, ((411 - bare.width) // 2, 455))
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
        art.save(RES / f"art_{name}.png", optimize=True)
        arts.append(art)
        if args.preview:
            Path(args.preview).mkdir(parents=True, exist_ok=True)
            preview(art).save(Path(args.preview) / f"{name}.png")
    if args.preview and len(arts) == len(CHAPTERS):
        contact_sheet(arts).save(Path(args.preview) / "chapters.png")


if __name__ == "__main__":
    main()
