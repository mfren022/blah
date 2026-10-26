# Sudoku Blue

A clean sudoku app for Android: square edges everywhere, Roboto type, dark blue highlights, and light
and dark themes. It has random puzzles in five difficulties, a daily challenge, a step-by-step solver,
and a placeholder story mode.

## Install on your phone

1. Download [`dist/Sudoku.apk`](dist/Sudoku.apk) on the phone (on GitHub: open the file, then
   **Download raw file**).
2. Open it. If Android asks, allow your browser or file manager to install unknown apps.
3. Launch **Sudoku Blue**. Android 8.0 or newer is required.

Every build is signed with the same sideloading key (`app/signing/sideload.keystore`), so new versions
install over the old one and keep your saved games and statistics.

## Home

The home screen has four tabs along the bottom, each with an icon:

- **Play**: the game in progress (see *Continue where you left off*) and the modes below.
- **Learn**: Strategies.
- **Records**: Solved boards and Statistics.
- **Settings**: theme, gameplay and assist switches, and about.

Back on any other tab returns to Play; the tab you're on survives a turn of the phone or a new theme.

## Modes

- **Random**: choose Easy, Medium, Hard, Expert or Extreme and get a puzzle generated on the phone.
  Every puzzle has exactly one solution and a symmetric clue layout. Its difficulty comes from the
  hardest technique needed to solve it, not from the clue count alone.
- **Daily challenge**: the same puzzle for everyone each day, easing in early in the week (Monday is
  Easy, the weekend is Expert). Completions build a streak.
- **Solver**: type or paste any puzzle.
  - **Solve** answers instantly. It reports whether there is no solution, one, or several, how long the
    search took, and how hard the puzzle is.
  - **Step** shows the next deduction a person would make, highlighted on the board with every
    candidate pencilled in.
  - **Play it** turns a valid puzzle into a game.
- **Story mode** (placeholder): *Anna's Dream*, in nine chapters. Anna mends nets in a fishing village.
  One summer night she dreams, and a golden fish leads her from the pond through the sleeping village and
  the harbour, past the lighthouse, over the open sea, up with the kites to the stars, down into the deep,
  and home at daybreak. Each chapter lists the techniques it will teach (`StoryActivity.CHAPTERS`), so the
  generator and grader can target them when the mode is built. Every chapter is open. The chapter list
  shows each one's number and name, a badge counting its finished levels out of all, on a darker block
  that turns blue once the chapter is complete (`data/StoryProgress.kt` keeps the count), and a chevron (a
  locked chapter would show a faint red lock instead). Tapping a
  chapter opens its screen, on the story's deep blue in either theme:
  - its picture, a pixel-art scene shown as a fine grid of small squares 1dp apart, one square per
    pixel, lit by a circular gradient that is brightest just left of the top-right corner and fades out
    by the middle of the screen. The golden fish is somewhere in every one. The pictures are painted by
    `tools/art/paint.py`, one module per chapter;
  - the chapter's title and its part of the story;
  - underneath, a **Levels (finished/total)** header and a node graph of the levels, metro-map style:
    the routes branch and merge from the entrance down to the chapter's final level, marked with a star.
- **Chapter 1, The Pond, can be played.** Its eleven levels are laid out as designed: level 1, then two
  routes (2 then 3, or 4 then 5) to level 6, then two more (7 then 8, or 9 then 10) to the final level.
  Each level is a fixed puzzle (`core/StoryPuzzles.kt`), the same for everyone and solvable with only
  full houses and hidden singles, as the chapter teaches: Easy, from 40 clues down to 36, except level 6
  and the final level, which are Medium (32 and 30 clues). Level 1 is open to begin with; finishing a level
  opens the levels its paths lead to, lights up the paths walked, and counts towards the chapter's
  Levels (X/Y) and its badge on the chapter list. On the map, levels ready to play are blue, finished
  ones white and the rest dim. Story games have their own save slot, so a level in
  progress is picked up where it was left and never replaces a random game. The other chapters' levels
  are still to come.

## Strategies

**Strategies**, on the home screen's Learn tab, teaches all 23 techniques the solver knows, simplest
first and grouped by tier: Singles, Basic, Intermediate, Advanced and Expert. The list opens with the
few words every explanation uses (candidate, unit, cells that see each other, R3C5), and marks each
strategy you've practised with its number on a blue block.

Each strategy has its own page: what it does in a sentence, then a worked example on a real board,
then **How it works** (why the logic holds) and **How to spot it** (where to look in a puzzle), with
the strategies either side a tap away. The example is interactive:

- **Your turn**: the board shows where to look, and you tap the cell the strategy fills in or clears
  candidates from. A wrong cell gets a red mark and a nudge; the right one counts the strategy as
  practised. **Show me** skips ahead.
- **Step by step**: three frames build the step up on the board (where to look, the pattern and why
  it works, then the result, with digits placed or candidates struck out), with Back and Next.
- **Another example**: every strategy has three, from different puzzles.

The examples come from random puzzles (`core/StrategyExamples.kt`). Beyond the singles, each is a
position with no single left and where no easier technique could make the same deduction, and its
pencil marks are exactly what the placed digits leave, so every candidate on show can be checked by
eye. The walkthroughs (`core/Lessons.kt`) are built from the solver's own step, and the tests check
every example against the puzzle's solution.

## Continue where you left off

While a random or custom game is in progress, the home screen's **Continue** card shows a live preview
of it: the board as you left it (your digits, notes and any mistakes), the difficulty, time played and
how much is filled in. Tapping the card grows it into the game: the card expands to fill the screen while
its board travels to the exact spot where the game draws its own, then the game's controls fade in
around it. Back during the growth shrinks it back into the card.

## Solved boards

Every board you complete, in any mode, is recorded with its starting combination (the clues), its
solution, your solve time (pauses excluded), mistakes, hints and when you finished it. **Records →
Solved boards** lists them newest first, with:

- a thumbnail of each starting board's clue pattern, and totals and average time for the list;
- filters for random, daily and custom puzzles;
- a detail view of each board: the full starting board or its solution, your results, **Play again**
  and **Open in solver**;
- **Copy as CSV** and **Share as CSV**, one row per board, with each board as an 81-digit string
  (0 for empty cells) so it can be loaded into a spreadsheet or another solver.

The records live in a small SQLite database (`data/History.kt`), separate from statistics, so clearing
one leaves the other alone.

## Playing

- Notes (pencil marks), fill-in-all-notes, and notes that clear themselves when you place a digit.
- Undo (kept across app restarts), erase, restart, reveal a cell, show the solution, abandon.
- **Hints** explain the next logical step from your position and highlight it: the cells involved, the
  candidates that form the pattern, and the candidates it removes. If you have entered a wrong digit,
  the hint points that out first. Apply carries the step out.
- Mistakes show in red as you make them; three mistakes end the game. Both can be switched off.
- Highlights for the selected cell's row, column, box and matching digits. A finished row, column or box
  flashes. The keypad counts what's left of each digit and hides finished ones.
- Timer, and a large pause button in the middle of the action bar. Pausing puts the puzzle's menu in
  the board's place: a large PAUSED title; fill in or clear all notes, reveal the selected cell, show
  the solution, copy the puzzle; and at the bottom a white Restart and a red Abandon, each confirmed
  first. Abandoning counts as a loss. Statistics per difficulty, and a win screen with confetti.
- Landscape layout and hardware keyboard support: digits, arrows, Backspace, N for notes, H for a hint,
  Space to pause, Ctrl+Z to undo.

## The engine (`core/`)

Pure Kotlin with no Android code, unit-tested on the JVM.

- `Solver` is an exhaustive depth-first search that always branches on the empty cell with the fewest
  candidates, using bitmasks for rows, columns and boxes. Typical puzzles take well under a
  millisecond, and puzzles built to defeat brute force take a few. The generator uses it to check
  uniqueness, and the Solver screen uses it to answer.
- `LogicSolver` solves the way a person does. It always applies the easiest of 23 techniques that makes
  progress and explains each step:

  | Tier | Techniques |
  |------|------------|
  | 1 | Full House, Hidden Single, Naked Single |
  | 2 | Pointing, Claiming, Naked Pair, Hidden Pair, Naked Triple |
  | 3 | Hidden Triple, X-Wing, Skyscraper, 2-String Kite, Naked Quad, Hidden Quad |
  | 4 | XY-Wing, XYZ-Wing, W-Wing, Swordfish, Simple Coloring, Unique Rectangle, BUG+1 |
  | 5 | Jellyfish, XY-Chain |

- `Generator` removes clues in symmetric pairs while the solution stays unique and the puzzle stays
  within its difficulty's techniques, then grades it. Easy needs singles only and Extreme needs tier 5
  or more than the solver knows. Generation is deterministic for a seed, which is how every phone gets
  the same daily puzzle.
- `Game` holds a game in progress: entries, notes, undo history, mistakes, hints and time, plus saving.
- `Strategies` holds each technique's explanations and example positions, and `Lessons` turns the
  solver's step in an example into a challenge and a walkthrough. `LogicSolver.find` runs a single
  technique on its own.
- `LevelMap` is a story chapter's levels as a graph: rows from an entrance to a final level, up to three
  lanes, and paths that branch and merge without crossing. Until the levels are designed, each chapter
  gets a placeholder map that is the same every time.

The tests replay the logic solver on 1,500 random minimal puzzles and check that every placement and
every elimination agrees with the known solution. That check is what keeps hints from ever being wrong.

## Building

### With the Android SDK (Android Studio, CI)

Open this folder in Android Studio, or run:

```
./gradlew :app:assembleRelease                # APK in app/build/outputs/apk/release
./gradlew :core:test :app:testDebugUnitTest   # engine tests and Robolectric UI tests (JDK 21)
```

### Without the Android SDK (`tools/sdkless`)

A separate Gradle build compiles the same sources with only a JDK and internet access. It is useful
where `dl.google.com` and Google's Maven repository are blocked:

```
gradle -p tools/sdkless assembleApk   # tools/sdkless/build/outputs/Sudoku.apk, signed and verified
gradle -p tools/sdkless test          # all tests; UI screenshots land in tools/sdkless/build/screenshots
```

It reads the application id, SDK levels, version and signing details from `app/build.gradle.kts`, so the
two builds can't drift apart. Its tools come from public mirrors, pinned by version or checksum:

- the API 35 `android.jar` from the Sable/android-platforms repository
- `aapt2` from apktool's Maven Central package
- D8 from Google's R8 release bucket
- `apksig` from Maven Central
- AndroidX Test's monitor (needed by Robolectric), built from its open-source release

## Project layout

```
Sudoku/
  core/            Engine: grid, solvers, generator, game model (pure Kotlin + tests)
  app/             Android app
    src/main/java/com/ultimatesudoku/app/
      *Activity    Home (with its tabs), Random, Game, Solver, Story, Chapter, Strategies,
                   Solved boards, Statistics
      data/        Settings, statistics, saved games, story and strategy progress
                   (SharedPreferences); solved boards (SQLite)
      ui/          Custom views: board, keypad, tool buttons, tiles, toggles, dialogs, and the
                   continue card's grow transition (GrowOverlay)
    src/test/      Robolectric tests that drive every screen and save screenshots
  tools/sdkless/   SDK-free build (see above)
  tools/art/       Scripts that paint the story's pixel art (Python with Pillow)
  dist/Sudoku.apk  The latest signed APK
```

The design system lives in `res/values`: palette roles in `attrs.xml` (set by `Theme.Sudoku` and
`Theme.Sudoku.Dark`), the type scale in `styles.xml`, and Roboto in `res/font`. Roboto is licensed
under the SIL Open Font License 1.1 (`assets/licenses/roboto-OFL.txt`).
