# Topple Corp

A pixel-art demolition puzzle game for Android. Knock down towers of Tetris-like blocks with your
three-person crew, using as few turns as possible.

## Gameplay

- Each level is a tower of polyomino blocks made of different materials. Scroll it vertically by dragging.
- Pick a tool from the toolbar, tap a block to aim (a preview shows the damage), then tap again to strike.
- Blocks that lose their support fall. They crush whatever they land on, and can break themselves.
- Destroy every block before you run out of turns. Fewer turns earn more stars.
- **Gold block:** bring it down to your crew intact for 500 gold, once per level. It's lost if a tool hits it
  or a falling block crushes it.

| Stars | Reward |
|-------|--------|
| 1 (easy) | 200 gold |
| 2 (medium) | 300 gold |
| 3 (hard) | 500 gold |
| Gold block | 500 gold |

Star gold is paid up to the best rating you've reached on a level, so replaying for more stars pays the
difference. Vaults pay +100 gold the first time you clear a level.

### Materials
Glass (shatters when it falls), Wood, Plastic (takes no fall damage), Brick, Stone, Metal and Steel
(both immune to the hammer).

### Special blocks
Generator (shocks touching blocks), AC Unit (+1 turn), TNT (explodes), Water Tank (floods everything below it),
Vault (+100 gold), Gold Block.

### Tools
Hammer (unlimited, 1 damage), Drill (3 damage), Dynamite (2 damage to the target and everything touching it),
Laser (1 damage down a column), Wrecking Ball (2 damage across a row).

### Campaign
There are 5 cities with 30 levels each: Maple Hollow, Rustport, Neon Bay, Dune City and Sky Capital.
Towers get taller and wider as you progress, and each new tool and block type gets an intro level with a
foreman tip. Clear all 30 levels in a city to unlock the next one.

## Project layout

```
ToppleCorp/
  core/   Pure Kotlin (no Android): level model, physics/turn engine, solver, generator, JSON.
  app/    Android app: SurfaceView game loop, renderer, scenes, synthesized sound effects.
    src/main/assets/levels/   150 campaign levels as JSON (c<city>_l<level>.json)
```

- **Rendering:** everything is drawn into a low-resolution buffer (about 240–320 px wide), then scaled up by an
  integer factor with nearest-neighbour filtering. All pixel art (blocks, crew, icons, font, skylines) is
  generated in code. The 3D look comes from:
  - extruded block faces;
  - parallax skyline layers;
  - a perspective-tilted city carousel;
  - debris particles with depth and shadows;
  - an extruded title.
- **Sound:** every sound effect is synthesized at first launch into small WAV files in the cache and played
  through `SoundPool`. There is no music.
- **Scenes:** Menu → Cities (horizontal snapping carousel) → Level select → Game. Store and Settings are
  reached from the menu.

## Building

Open the `ToppleCorp` folder in Android Studio (Koala or newer), or run:

```
./gradlew :app:assembleDebug      # APK in app/build/outputs/apk/debug
./gradlew :core:test              # engine tests, incl. "every shipped level is beatable"
./gradlew :core:generateLevels    # regenerate the 150 campaign levels into app assets
```

Requires JDK 17+ and Android SDK 34. minSdk is 24.

## Level editor hooks

The game is built so an in-game level editor can be added on top of what exists:

- `LevelDef` (in `core/Model.kt`) is the complete, serializable description of a level: grid size, pieces
  (id, material, cells), tool charges, star turn limits and an optional hint.
- `LevelJson` reads and writes the human-editable JSON format used by the assets.
- `LevelRepository.saveOverride(level)` writes an edited level to app storage, and the game loads it
  instead of the shipped one. `clearOverride` reverts to the shipped level.
- `Solver.bestSolve(level)` plus `Solver.limitsFor(turns)` can automatically compute the star turn limits
  for an edited tower.
- `Board` is a pure simulation, so an editor can offer a "test play" using the same engine.
- `TowerRenderer` draws any list of `VisualPiece`s, so an editor canvas can reuse it.

## Store

The store lists placeholder tilesets and crew skins (marked "soon"). `StoreCatalog` and the purchase
and equip helpers in `SaveData` are ready for real content. Crew colours come from palettes
(`Sprites.CrewLook`), so skins can be added as palette swaps.
