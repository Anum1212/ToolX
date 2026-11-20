# ToolX

A personal Android toolbox for video and image tasks. Everything runs on the phone, with no network and no ads.

## Tools

| Video | Status | Image | Status |
|---|---|---|---|
| Trim | ✅ | Resize | ✅ |
| Compress (1080p / 720p / 480p / 360p) | ✅ | Compress & convert (JPG / PNG / WebP) | ✅ |
| Mute | ✅ | Rotate & flip | ✅ |
| Extract audio (M4A) | ✅ | Crop | planned |
| Merge | planned | Round corners | planned |
| Speed | planned | Color picker | planned |
| Reverse | planned | Watermark | planned |
| Video to GIF | planned | Images to PDF | planned |
| Convert format | planned | | |
| Add music | planned | | |
| Watermark | planned | | |

Saved files go to `Movies/ToolX`, `Pictures/ToolX` and `Music/ToolX`.

## Stack

- Kotlin, Jetpack Compose, Material 3 (light/dark)
- AndroidX Media3 Transformer for video (hardware encoders)
- Platform `ImageDecoder` / `Bitmap` for images, Coil for previews
- System photo picker for input, MediaStore for output, so the app needs no storage permissions
- minSdk 29 (Android 10), targetSdk 35

## Getting started

1. Install [Android Studio](https://developer.android.com/studio). It bundles the JDK and Android SDK.
2. Open this folder in Android Studio and let Gradle sync. Studio uses
   `gradle/wrapper/gradle-wrapper.properties` and downloads Gradle 8.11.1.
3. To build from the command line, first create the wrapper scripts once (from Studio's terminal):
   `gradle wrapper --gradle-version 8.11.1`, then run `./gradlew assembleDebug`.
4. Run on a device or emulator (Android 10+).

## Project layout

```
app/src/main/java/com/toolx/app/
├── MainActivity.kt
├── core/            ProcessingViewModel (run/cancel/progress), MediaSaver, formatting
├── tools/
│   ├── ToolRegistry.kt   every tool + its id, icon, category, search keywords
│   ├── video/            VideoEngine (Media3), VideoPreview (ExoPlayer), screens
│   └── image/            ImageEngine (ImageDecoder), screens
└── ui/              theme, home grid, navigation, shared components
```

### Adding a tool

1. Add (or flip `available = true` on) its entry in `ToolRegistry`.
2. Write the operation in `VideoEngine` / `ImageEngine` so it returns `SavedMedia`.
3. Write a screen using `VideoToolFrame` / `ImageToolFrame`.
4. Map the id to the screen in `ToolXNavHost.ToolScreen`.
