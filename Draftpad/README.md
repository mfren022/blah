# Draftpad

A lightweight sketchpad for Android for drawing drafts and concepts and handing them to Claude. It has a
pen, lines, arrows, rectangles, ellipses and text on a dotted canvas, and each draft has a title and notes.
Send a sketch to the Claude app from the share sheet, or push it to a GitHub repository where Claude Code
can open it.

It has no account, no ads and no libraries (not even AndroidX), and the APK is about 0.7 MB. It only uses
the network when you push to GitHub.

## Install on your phone

1. Download [`dist/Draftpad.apk`](dist/Draftpad.apk) on the phone (on GitHub: open the file, then
   **Download raw file**). CI also builds it on every push: **Actions → Draftpad APK → Artifacts →
   `Draftpad-apk`**.
2. Open it. If Android asks, allow your browser or file manager to install unknown apps.
3. Launch **Draftpad**. It needs Android 8.0 or newer.

Every build is signed with the same sideloading key (`app/signing/sideload.keystore`), so new versions
install over the old one and keep your drafts.

## Drawing

- **Tools** (bottom row): pen, line, arrow, rectangle, ellipse, text, move and eraser.
  - **Text:** tap where the label goes, type it and tap Add. Tap a label with the text tool to edit or
    delete it.
  - **Move:** drag anything to move it. Dragging empty canvas pans.
  - **Eraser:** drag across whatever should go.
- **Colours and widths** (the row above): seven pen colours and three widths. The width also sets the
  size of new text. Picking a colour while moving or erasing switches back to the pen.
- **Two fingers** pan and zoom (25% to 800%). They never draw. **⋮ → Fit to screen** brings everything
  back into view.
- **Undo and redo** are in the top bar. Clear canvas can be undone too.
- **Title and notes:** tap the title. The notes go with the sketch, so use them to tell Claude what it
  is and what you want.
- **Draw over an image:** use **⋮ → Draw over an image…**, or share a screenshot or photo to Draftpad
  from any app. You draw on top of it, which is handy for marking up a screen.
- **Drafts:** the grid button lists every draft, newest first. Long-press one to edit its title and notes
  or delete it. Draftpad reopens the draft you last worked on, and **⋮ → New draft** starts another.
  Everything saves automatically.

## Getting a sketch to Claude

The share button in the top bar offers:

- **Share image…** opens Android's share sheet with the sketch as a PNG. The title and notes go along as
  the message. Pick the Claude app to start a chat about it. **Share image and SVG…** adds the vector
  file as well.
- **Push to GitHub** commits the sketch to a repository, one folder per sketch, so Claude Code can read
  it from a session on that repository.
- **Save image to Pictures** (Android 10 and newer) saves the PNG to Pictures/Draftpad.

### Setting up the GitHub push

The first push asks for:

- **Repository:** `owner/name`, for example `mfren022/blah`.
- **Token:** a fine-grained personal access token. **Create a token on GitHub** opens GitHub's form with
  the name, *Contents: Read and write* and a one-year expiry already filled in. Under *Repository
  access*, choose *Only select repositories* and pick the repository. Then generate the token, copy it
  and paste it into Draftpad. **Connect** checks the token before saving it.
- **Branch:** leave it blank for the default branch.
- **Folder:** where the sketches go. The default is `drafts`.

The token stays on the phone. It is left out of Android backups and device transfers, and it is only
sent to `api.github.com`.

### What lands in the repository

Each push is a single commit, such as `Add sketch: Level select`:

```
drafts/2026-10-25-level-select/
  README.md       title, notes, the picture, what is drawn, and every text label with its position
  sketch.png      the picture, 1600 px on its long side
  sketch.svg      the same drawing as vectors
  draft.json      Draftpad's own file
  background.jpg  the image it was drawn over, if any
```

Pushing the same draft again updates its folder. After a push, **Copy for Claude** copies a message that
points Claude Code at the folder, ready to paste.

## Formats

Coordinates are in canvas units. One unit is 1 dp on the phone at 100% zoom, so a sketch of a phone screen
is about 360–420 units wide and its sizes read like Android layout dp.

- `sketch.svg` uses the same scale, with its origin at the picture's top-left corner. Pen strokes are
  `<path>` elements. Lines, rectangles and ellipses are `<line>`, `<rect>` and `<ellipse>`. An arrow is a
  `<g data-kind="arrow">` holding a line and a filled head. Text is a `<text>` element with one `<tspan>`
  per line. The title and notes are in `<title>` and `<desc>`.
- `draft.json` lists the same elements with their colours, widths and coordinates (see
  `core/src/main/kotlin/com/draftpad/core/DraftJson.kt`).

## Project layout

```
Draftpad/
  core/            Pure Kotlin with tests: sketch model, hit testing, undo history, JSON, SVG,
                   the pushed README, and the GitHub client
  app/             Android app
    src/main/java/com/draftpad/app/
      EditorActivity  the canvas screen: tools, menus, sharing and pushing
      SketchView      the drawing surface: touch, pinch zoom, dot grid
      Renderer        draws sketches on Android canvases (screen, PNG, thumbnails)
      DraftsActivity  the drafts grid
      DraftStore, Exports, ShareProvider, Images, Dialogs, Prefs
    src/test/      Robolectric tests that drive the screens and save screenshots
  tools/sdkless/   Build without the Android SDK (see below)
  dist/Draftpad.apk  The latest signed APK
```

## Building

Open the `Draftpad` folder in Android Studio, or run:

```
./gradlew :core:test :app:testDebugUnitTest   # tests; UI screenshots land in app/build/screenshots
./gradlew :app:assembleRelease                 # app/build/outputs/apk/release/app-release.apk
```

You need JDK 21 for the UI tests (JDK 17 is enough to build) and Android SDK 35. minSdk is 26.

### Without the Android SDK (`tools/sdkless`)

A separate Gradle build compiles the same sources with only a JDK and internet access. Use it where
`dl.google.com` and Google's Maven repository are blocked:

```
gradle -p tools/sdkless assembleApk   # tools/sdkless/build/outputs/Draftpad.apk, signed and verified
gradle -p tools/sdkless test          # all tests; screenshots in tools/sdkless/build/screenshots
```

It reads the application id, SDK levels, version and signing details from `app/build.gradle.kts`, so the
two builds can't drift apart. Its tools come from public mirrors and are pinned by version or checksum:

- the API 35 `android.jar` from the Sable/android-platforms repository
- `aapt2` from apktool's Maven Central package
- D8 from Google's R8 release bucket
- `apksig` from Maven Central
- AndroidX Test's monitor (needed by Robolectric), built from its open-source release
