#!/usr/bin/env python3
"""Pack a terrain sample next to index.html so the globe works without reaching AWS.

index.html streams elevation tiles from the public Terrain Tiles bucket. Some hosts
(sandboxed previews, offline machines) cannot reach it. When the live tiles fail to load,
the page looks for terrain/manifest.json beside itself and reads tiles from packed files.

This script builds that folder:
  * the whole world at zoom 0-5, plus land tiles at zoom 6 (coarse but complete), and
  * zoom 8-13 around every featured place listed in index.html (about 30 m detail).

Tiles keep the Terrarium encoding the page already decodes (sub-metre precision dropped)
and are laid out 8 to a row in lossless WebP atlases of up to 64 tiles, so every file is an
ordinary image that any static host will serve.

Usage:
  python3 build_offline_bundle.py --out ../terrain
Requires: requests, numpy, Pillow.
"""
import argparse
import concurrent.futures as cf
import io
import json
import math
import os
import re
import sys
import time

import numpy as np
import requests
from PIL import Image

TILE_URL = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'
R = 6371008.8
# Half-width of the square kept around each featured place, per zoom level (metres).
SITE_REACH = {8: 100e3, 9: 60e3, 10: 35e3, 11: 25e3, 12: 12e3, 13: 5e3}
# Vertical precision kept per zoom level (metres): coarse tiles cover kilometres per pixel.
QUANT = {z: 4 for z in range(0, 7)} | {7: 2, 8: 2, 9: 2}


def merc(lat, lon, z):
    n = 2 ** z
    s = math.sin(math.radians(max(-85.0511, min(85.0511, lat))))
    return (lon + 180) / 360 * n, (0.5 - math.log((1 + s) / (1 - s)) / (4 * math.pi)) * n


def offset(lat, lon, dn, de):
    return lat + math.degrees(dn / R), lon + math.degrees(de / (R * math.cos(math.radians(lat))))


def read_places(index_html):
    src = open(index_html, encoding='utf-8').read()
    found = re.findall(r"id: '([^']+)', name: '([^']+)'.*?view: \[([-\d.]+), ([-\d.]+)\], look: \[([-\d.]+), ([-\d.]+)\]", src)
    return [{'id': i, 'name': n, 'view': (float(a), float(b)), 'look': (float(c), float(d))} for i, n, a, b, c, d in found]


def site_tiles(place, zooms):
    out = []
    for z in zooms:
        reach = SITE_REACH[z]
        keys = set()
        for lat, lon in (place['view'], place['look']):
            (la0, lo0), (la1, lo1) = offset(lat, lon, reach, -reach), offset(lat, lon, -reach, reach)
            x0, y0 = merc(la0, lo0, z)
            x1, y1 = merc(la1, lo1, z)
            n = 2 ** z
            for x in range(int(x0), int(x1) + 1):
                for y in range(max(0, int(y0)), min(n - 1, int(y1)) + 1):
                    keys.add((z, x % n, y))
        out.extend(sorted(keys))
    return out


class Fetcher:
    def __init__(self, cache, workers):
        self.cache, self.workers = cache, workers
        self.session = requests.Session()
        os.makedirs(cache, exist_ok=True)

    def path(self, z, x, y):
        return os.path.join(self.cache, f'{z}_{x}_{y}.png')

    def get(self, key):
        p = self.path(*key)
        if not os.path.exists(p):
            for attempt in range(5):
                try:
                    r = self.session.get(TILE_URL.format(z=key[0], x=key[1], y=key[2]), timeout=60)
                    r.raise_for_status()
                    with open(p + '.part', 'wb') as f:
                        f.write(r.content)
                    os.replace(p + '.part', p)
                    break
                except requests.RequestException:
                    if attempt == 4:
                        raise
                    time.sleep(2 ** attempt)
        return p

    def fetch_all(self, keys, label):
        keys = list(dict.fromkeys(keys))
        t0 = time.time()
        with cf.ThreadPoolExecutor(self.workers) as ex:
            for i, _ in enumerate(ex.map(self.get, keys), 1):
                if i % 200 == 0 or i == len(keys):
                    print(f'  {label}: {i}/{len(keys)} tiles ({time.time() - t0:.0f} s)', flush=True)


def elevations(png_path):
    a = np.asarray(Image.open(png_path).convert('RGB')).astype(np.float64)
    return a[..., 0] * 256 + a[..., 1] + a[..., 2] / 256 - 32768


ATLAS_COLS = 8
ATLAS_TILES = 64


def build_atlas(job):
    """Lay tiles out 8 per row as Terrarium RGB and encode the sheet as lossless WebP."""
    paths, quants = job
    rows = (len(paths) + ATLAS_COLS - 1) // ATLAS_COLS
    sheet = np.zeros((rows * 256, ATLAS_COLS * 256, 3), np.uint8)
    for slot, (png_path, q) in enumerate(zip(paths, quants)):
        v = np.clip(np.round(elevations(png_path) / q) * q + 32768, 0, 65535).astype(np.uint32)
        r, c = divmod(slot, ATLAS_COLS)
        sheet[r * 256:(r + 1) * 256, c * 256:(c + 1) * 256, 0] = v >> 8
        sheet[r * 256:(r + 1) * 256, c * 256:(c + 1) * 256, 1] = v & 255
    buf = io.BytesIO()
    # higher WebP effort levels are many times slower here for no smaller files
    Image.fromarray(sheet, 'RGB').save(buf, 'WEBP', lossless=True, quality=100, method=2)
    return buf.getvalue()


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--index', default=os.path.join(here, '..', 'index.html'), help='page to read featured places from')
    ap.add_argument('--out', default=os.path.join(here, '..', 'terrain'), help='output folder (served next to index.html)')
    ap.add_argument('--cache', default=os.path.join(here, '.tile-cache'), help='where downloaded tiles are kept')
    ap.add_argument('--world-zoom', type=int, default=6, help='deepest zoom kept worldwide (land only above 5)')
    ap.add_argument('--site-zoom', type=int, default=13, help='deepest zoom kept around featured places')
    ap.add_argument('--workers', type=int, default=16)
    args = ap.parse_args()

    places = read_places(args.index)
    if not places:
        sys.exit('No featured places found in ' + args.index)
    fetch = Fetcher(args.cache, args.workers)

    # 1. the whole world down to zoom 5
    world = [(z, x, y) for z in range(0, min(5, args.world_zoom) + 1) for x in range(2 ** z) for y in range(2 ** z)]
    fetch.fetch_all(world, 'world')
    # 2. deeper world zooms, skipping tiles that are open ocean in their parent
    for z in range(6, args.world_zoom + 1):
        keep = []
        for px in range(2 ** (z - 1)):
            for py in range(2 ** (z - 1)):
                e = elevations(fetch.path(z - 1, px, py))
                for qx in (0, 1):
                    for qy in (0, 1):
                        if e[qy * 128:(qy + 1) * 128, qx * 128:(qx + 1) * 128].max() > -5:
                            keep.append((z, px * 2 + qx, py * 2 + qy))
        fetch.fetch_all(keep, f'land z{z}')
        world += keep
    # 3. detail around featured places
    zooms = [z for z in sorted(SITE_REACH) if z <= args.site_zoom]
    sites = {p['id']: site_tiles(p, zooms) for p in places}
    fetch.fetch_all([k for ks in sites.values() for k in ks], 'places')

    # 4. pack: z0 alone (needed first), z1-3 together, then by ancestor tile, one pack per place
    groups = {}
    def group_of(z, x, y):
        if z == 0:
            return 'w0'
        if z <= 3:
            return 'w1-3'
        return f'w{z}-{x >> (z - 2 if z < 6 else 3)}-{y >> (z - 2 if z < 6 else 3)}'
    for key in world:
        groups.setdefault(group_of(*key), []).append(key)
    seen = set(world)
    for pid, keys in sites.items():
        for key in keys:
            if key not in seen:
                seen.add(key)
                groups.setdefault(f'p-{pid}', []).append(key)

    os.makedirs(args.out, exist_ok=True)
    manifest = {'version': 2, 'mime': 'image/webp', 'tileSize': 256, 'atlasCols': ATLAS_COLS,
                'maxZoom': max(args.site_zoom, args.world_zoom), 'packs': [], 'tiles': {}, 'places': [p['id'] for p in places]}
    sheets = []
    for name, keys in sorted(groups.items()):
        for part in range(0, len(keys), ATLAS_TILES):
            chunk = keys[part:part + ATLAS_TILES]
            suffix = f'-{part // ATLAS_TILES}' if len(keys) > ATLAS_TILES else ''
            sheets.append((f'{name}{suffix}.webp', chunk))
    total = 0
    with cf.ProcessPoolExecutor() as pool:
        jobs = [([fetch.path(*k) for k in chunk], [QUANT.get(k[0], 1) for k in chunk]) for _, chunk in sheets]
        for gi, ((fname, chunk), data) in enumerate(zip(sheets, pool.map(build_atlas, jobs))):
            with open(os.path.join(args.out, fname), 'wb') as f:
                f.write(data)
            for slot, (z, x, y) in enumerate(chunk):
                manifest['tiles'][f'{z}/{x}/{y}'] = [gi, slot]
            manifest['packs'].append(fname)
            total += len(data)
            print(f'  {fname}: {len(chunk)} tiles, {len(data) / 1e6:.1f} MB', flush=True)
    with open(os.path.join(args.out, 'manifest.json'), 'w') as f:
        json.dump(manifest, f, separators=(',', ':'))
    print(f'Wrote {len(manifest["tiles"])} tiles in {len(sheets)} atlases, {total / 1e6:.1f} MB, to {args.out}')


if __name__ == '__main__':
    main()
