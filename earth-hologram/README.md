# Earth Hologram

A blue holographic 3D globe built from real elevation data. Zoom toward any spot and the
camera tilts down until you are standing on the ground there, looking at the terrain the
way a person on the spot would.

## Run it

Open `index.html` in a current browser (Chrome, Edge, Firefox or Safari 16.4+). It needs an
internet connection: the 3D engine (three.js) loads from jsDelivr and elevation tiles stream
from Amazon's public Terrain Tiles bucket. Nothing else to install.

If your browser blocks pages opened from disk, serve the folder instead, for example
`npx serve earth-hologram`, or turn on GitHub Pages for the repository.

## Getting around

| | Orbit view | Ground view |
|---|---|---|
| Drag | Move the globe | Look around |
| Scroll / pinch | Zoom toward the cursor; the view tilts as you descend | Binocular zoom; scroll out to lift off |
| Right-drag / Ctrl-drag | Rotate and tilt | Look around |
| Click | Read the elevation at a point | |
| Double-click / double-tap | Stand on the ground there | Walk to that spot |
| W A S D / arrows | Pan | Walk |
| Q / E | Rotate | Fly down / up |
| R / F | Tilt | |
| Shift | Faster | 4× faster |
| Esc | | Back to orbit |

On phones, a joystick and up/down buttons appear in ground view.

**Places** lists 20 curated viewpoints, each standing at a real overlook and facing its
landmark: Everest from Kala Patthar, Yosemite from Tunnel View, the Matterhorn from
Gornergrat, Death Valley from Dante's View and more. The search box also accepts
coordinates (`27.99, 86.93` or `36.06 N 112.11 W`) and place names (worldwide search via
OpenStreetMap Nominatim).

## How it works

- **Terrain**: [Terrain Tiles on AWS](https://registry.opendata.aws/terrain-tiles/), Terrarium
  PNG encoding, zoom 0–15. Detail is roughly 30 m across most land (SRTM), finer in parts of
  the US (3DEP) and coarser at high latitudes and on the sea floor (GMTED2010, ETOPO1).
- **Level of detail**: a quadtree of Web Mercator tiles on a sphere. Each tile is a 64×64
  grid (32×32 on phones) placed relative to its own centre, so float precision holds from
  orbit down to eye level. Tiles split when their cells exceed about 7 screen pixels, and each
  tile only fetches the data zoom that gives one sample per vertex. Skirts hide cracks between
  levels; caps cover the poles beyond the Mercator range.
- **Hologram shading**: hillshade lit from the north-west, contour lines whose interval adapts
  to keep them about 16 px apart (every tenth emphasised), a glowing coastline, bathymetric
  contours, a latitude/longitude graticule from orbit, a world-anchored survey grid up close,
  a scanning pulse around the viewer, distance haze and a logarithmic depth buffer.
- **Camera**: scrolling dollies toward the point under the cursor; below 250 km the pitch
  follows altitude, orbiting that point so it stays under the cursor until you land. Ground
  view follows the rendered mesh at 1.7 m eye height. Flights arc over long distances, and
  "Stand here" turns you toward the most prominent terrain in sight.
- **Water**: ground below sea level is drawn as sea, except known dry basins (Dead Sea, Death
  Valley, Qattara, Turpan and others). The Caspian Sea is drawn at its own level of −28 m.

## Offline copy

Some places cannot reach the AWS tiles: sandboxed previews, machines without internet.
`tools/build_offline_bundle.py` packs a terrain sample into `terrain/` next to `index.html`:
the whole world at coarse detail plus about 30 m detail around every featured place, around
160 MB as lossless WebP atlases. When the live tiles fail to load, the page switches to that
folder automatically and tells you where the detail is coarse.

```
pip install requests numpy pillow
python3 tools/build_offline_bundle.py
```

## Data credits

Elevation data from Terrain Tiles (Mapzen / AWS Open Data), including USGS SRTM, GMTED2010
and 3DEP, NOAA ETOPO1, and the other sources listed in the
[Tilezen attribution notes](https://github.com/tilezen/joerd/blob/master/docs/attribution.md).
Place search © OpenStreetMap contributors.
