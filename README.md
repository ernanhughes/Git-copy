# Git Copy

Git Copy is a small Android utility with one job: take a public GitHub repository URL and copy the repository snapshot onto the Android device.

## v0.1 starter

- Paste `https://github.com/owner/repository`.
- **Copy to Downloads** recreates the repo under `Downloads/GitCopy/<repository>`.
- **Choose another folder** uses Android's Storage Access Framework and creates `<repository>` inside the selected directory.
- Existing files created at the same destination are replaced where Android permits it.
- No login, database, background service, or broad all-files storage permission.

The implementation downloads GitHub's ZIP snapshot for the repository's default branch and expands it locally. It is intentionally **not a full Git clone yet**: `.git` history, remotes, branches, tags, submodules, LFS objects, and private-repository credentials are outside the first milestone.

## Android project

- Kotlin
- Jetpack Compose + Material 3
- Android Gradle Plugin 9.4.0
- Compose BOM 2026.06.00
- `compileSdk` / `targetSdk`: 37 (Android 17)
- `minSdk`: 29 (Android 10)
- JDK 17

Open the project with a current Android Studio installation and install Android SDK 37.

## Why Downloads is handled separately

Android 11+ does not allow apps to obtain folder-tree access to the entire Downloads root through `ACTION_OPEN_DOCUMENT_TREE`. The default export therefore writes through `MediaStore.Downloads`, while the optional custom destination uses the Storage Access Framework.

## Sensible next milestones

1. Private repositories via a GitHub token stored in Android Keystore-backed preferences.
2. Branch/tag selector.
3. Recent repositories and one-tap repeat copy.
4. Explicit replace / merge / new-copy behavior.
5. Android share-sheet support so a GitHub URL can be sent straight to Git Copy.
6. True Git clone mode if preserving history becomes a product requirement.
