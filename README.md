# ToolX

A personal Android toolbox for video, audio and image tasks. Everything runs on the phone, with no network and no ads.

## Tools

| Video | Status | Audio | Status | Image | Status |
|---|---|---|---|---|---|
| Trim | ✅ | Trim | ✅ | Resize | ✅ |
| Compress (1080p / 720p / 480p / 360p) | ✅ | Cut a section | ✅ | Compress & convert (JPG / PNG / WebP) | ✅ |
| Mute | ✅ | Merge | ✅ | Rotate & flip | ✅ |
| Extract audio (M4A) | ✅ | Speed (keep or change pitch) | ✅ | Crop | ✅ |
| Volume (0–300%) | ✅ | Volume (0–300%) | ✅ | Round corners | planned |
| Crop & rotate | ✅ | Convert (MP3 needs FFmpeg) | planned | Color picker | planned |
| Split | ✅ | Reverse | planned | Watermark | planned |
| Merge | ✅ | Noise reduction (FFmpeg) | planned | Images to PDF | planned |
| Speed | ✅ | Edit tags | planned | Remove background (people) | ✅ |
| | | | | Passport photo (+ 4×6 print sheet) | ✅ |
| Reverse, Video to GIF (FFmpeg) | planned | | | | |
| Convert format, Add music, Watermark | planned | | | | |

Audio exports are AAC in `.m4a`.

### PDF (all offline; saved to `Documents/ToolX`)

| Pages | Convert | Stamp & secure |
|---|---|---|
| Merge, Split (ranges / every N / extract), Organize (reorder, rotate, delete), Rotate, Crop (auto-trim to content) | PDF to JPG/PNG (72–300 dpi), Images to PDF (A4 / Letter / fit), PDF to text or Markdown | Page numbers, Watermark, Sign (drawn), Redact (true removal), Protect (AES-256), Unlock, Compress, Repair |

Not built yet (possible offline): Edit PDF (add text/images), Scan to PDF, OCR, Compare, PDF forms.
Not possible offline: PDF ↔ Word/PowerPoint/Excel, HTML to PDF from a URL, AI summarizer, Translate, PDF/A.

### Everyday tools (all offline, results update as you type)

| Category | Tools |
|---|---|
| Text (16) | Text cases, Find & replace, Sort lines, Text counter, Repeat, Remove white spaces / empty lines / duplicate lines / duplicate words / line breaks, Prefix/suffix, Number lines, Reverse text / lines / words, Add line breaks |
| Units (12) | Length, Area, Mass, Temperature, Cooking, Time, Energy, Angle, Speed, Data, Data transfer, CSS units |
| Calculate (11) | Percentage, Average, Proportion, Aspect ratio, Fraction simplifier, Decimal to fraction, Prime number, Exponent, Factorial, Age, Time calculator |
| General (9) | Ruler, Morse code, Roman numerals, Number to words (incl. lakh/crore), Compass, Text ↔ bytes, Time zones, Level, Day counter |
| Finance (4) | Loan EMI, Investment (lump sum + SIP), Bill splitter, Discount |
| Random (5) | Lucky wheel, Flip a coin, Yes or no, Rock paper scissors, Roll dice |
| Developer (1) | Base converter |

Not built, by decision: Currency (needs live exchange rates, which conflicts with "no network") and a "Sound" unit converter.

Saved files go to `Movies/ToolX`, `Pictures/ToolX`, `Music/ToolX` and `Documents/ToolX`.

### Look and settings

- Every category has its own colour, used on its tiles and throughout its tools.
- 25 themes: System, Light, Dark, AMOLED, Material You (wallpaper), Nord, One Dark Pro, plus 10 light and 10 dark themes based on popular schemes (Catppuccin, Gruvbox, Solarized, Dracula, Tokyo Night, Rosé Pine, Everforest…).
- Settings can hide whole categories or single tools, and hidden items also disappear from search.
- Swipe left and right between Recent, All and each category. The ☰ menu jumps anywhere and opens Settings.
- A Recent page shows the tools you've used lately.
- Search sits at the bottom, within thumb reach, and covers every category.

## Stack

- Kotlin, Jetpack Compose, Material 3 (light/dark)
- AndroidX Media3 Transformer for video and audio (hardware encoders)
- Platform `ImageDecoder` / `Bitmap` for images, Coil for previews
- ML Kit selfie segmentation (bundled model, on-device) for background removal; the manifest strips its INTERNET permission
- PdfBox-Android (Apache 2.0, ~3 MB) for PDF editing; the platform `PdfRenderer` for page images
- System photo picker (images/video) and document picker (audio) for input, MediaStore for output, so the app needs no storage permissions
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
├── core/            ProcessingViewModel (run/runAll/cancel/progress), MediaExport (Transformer runner), MediaSaver, formatting
├── tools/
│   ├── ToolRegistry.kt   every tool + its id, icon, category, search keywords
│   ├── video/            VideoEngine (Media3), VideoPreview (ExoPlayer), screens
│   ├── audio/            AudioEngine (Media3), screens
│   └── image/            ImageEngine (ImageDecoder), crop editor, screens
└── ui/              theme, home grid, navigation, shared components
                     (pickers, preview player, range picker, MergeFrame)
```

### Adding a tool

For text, unit, calculator, general, finance, random or developer tools, add the `Tool` entry and its screen to that family's `ToolModule` object (e.g. `tools/text/TextTools.kt`). Registry and navigation pick it up automatically. For media tools:

1. Add (or flip `available = true` on) its entry in `ToolRegistry`.
2. Write the operation in `VideoEngine` / `AudioEngine` / `ImageEngine` so it returns `SavedMedia`.
3. Write a screen using `VideoToolFrame` / `AudioToolFrame` / `ImageToolFrame` (or `MergeFrame` for multi-file tools).
4. Map the id to the screen in `ToolXNavHost.ToolScreen`.
