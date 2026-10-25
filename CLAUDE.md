# Notes for Claude

## Sketches from Draftpad

The user draws drafts and concepts on their phone with Draftpad (`Draftpad/`, an Android app) and pushes
them to this repository: one folder per sketch, `drafts/<yyyy-mm-dd>-<title>/`, on the default branch
(`master`) unless they picked another branch or folder in the app. When they mention a sketch, drawing,
draft, mock-up or concept:

1. Look in `drafts/`. If it isn't there, it was probably pushed after this session started: run
   `git fetch origin master` and look in `origin/master:drafts/`.
2. Read the folder's `README.md` first: title, the user's notes, what is drawn and every text label with
   its position. Then view `sketch.png`.
3. For exact layout, read `sketch.svg`. Units are dp on the phone at 100% zoom, with the origin at the
   picture's top-left corner. `background.jpg`, if present, is a screenshot or photo the user drew over,
   and their marks on it are the request.

The app's own source and docs are in `Draftpad/` (see `Draftpad/README.md`).
