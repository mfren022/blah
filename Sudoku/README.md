# Ultimate Sudoku

A clean sudoku app for Android: square edges everywhere, Roboto type, dark blue highlights, and light
and dark themes. It has random puzzles in five difficulties, a daily challenge, a step-by-step solver,
and a placeholder story mode.

## Install on your phone

1. Download [`dist/Sudoku.apk`](dist/Sudoku.apk) on the phone (on GitHub: open the file, then
   **Download raw file**).
2. Open it. If Android asks, allow your browser or file manager to install unknown apps.
3. Launch **Sudoku**. Android 8.0 or newer is required.

Every build is signed with the same sideloading key (`app/signing/sideload.keystore`), so new versions
install over the old one and keep your saved games and statistics.

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
- **Story mode** (placeholder): *The Nine Districts*, nine locked chapters. Each chapter lists the
  techniques it will teach (`StoryActivity.CHAPTERS`), so the generator and grader can target them when
  the mode is built.

## Playing

- Notes (pencil marks), fill-in-all-notes, and notes that clear themselves when you place a digit.
- Undo (kept across app restarts), erase, restart, reveal a cell, show the solution.
- **Hints** explain the next logical step from your position and highlight it: the cells involved, the
  candidates that form the pattern, and the candidates it removes. If you have entered a wrong digit,
  the hint points that out first. Apply carries the step out.
- Mistakes show in red as you make them; three mistakes end the game. Both can be switched off.
- Highlights for the selected cell's row, column, box and matching digits. A finished row, column or box
  flashes. The keypad counts what's left of each digit and hides finished ones.
- Timer with pause (the board is hidden while paused), statistics per difficulty, and a win screen with
  confetti.
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
      *Activity    Home, Random, Game, Solver, Story, Statistics, Settings
      data/        Settings, statistics and saved games (SharedPreferences)
      ui/          Custom views: board, keypad, tool buttons, tiles, toggles, dialogs
    src/test/      Robolectric tests that drive every screen and save screenshots
  tools/sdkless/   SDK-free build (see above)
  dist/Sudoku.apk  The latest signed APK
```

The design system lives in `res/values`: palette roles in `attrs.xml` (set by `Theme.Sudoku` and
`Theme.Sudoku.Dark`), the type scale in `styles.xml`, and Roboto in `res/font`. Roboto is licensed
under the SIL Open Font License 1.1 (`assets/licenses/roboto-OFL.txt`).
