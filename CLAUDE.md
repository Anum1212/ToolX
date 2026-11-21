# ToolX — notes for Claude

Personal-use Android toolbox for video and image tasks. README.md covers the tool list, stack and the "adding a tool" steps; this file records the decisions behind them.

## Product decisions

- **Personal use.** No ads, no paywall, no analytics, no network. Files never leave the phone.
- **Inspirations:** Tooly (com.yousx.thetoolsapp) provides the structure: a category grid, search, the same screen layout for every tool, offline. Vaux (com.vaux.vaux_editor) provides the video/image tool set. Do better than both: free exports have no watermark and there's no consent-screen nonsense.
- **minSdk 29 (Android 10).** This means no storage permissions: input comes from the system photo picker, output goes through MediaStore to `Movies/ToolX`, `Pictures/ToolX` and `Music/ToolX`.
- **Media3 Transformer for video and audio, no FFmpeg yet.** Add FFmpeg later for video Reverse, Video to GIF, MP3 output, noise reduction and MKV/AVI, which Media3 can't do. Its 20–30 MB is acceptable for personal use. `ffmpeg-kit` was retired in 2025, so the options are a community fork or our own build.
- **Audio input** uses the document picker (`OpenDocument`, `audio/*`), because the photo picker can't show audio. Read access is kept with `takePersistableUriPermission`. Audio output is always AAC in `.m4a`, saved to `Music/ToolX`.
- **No DI framework** (no Hilt). Keep it simple.
- **Everyday tools** follow Tooly's catalogue: Text, Units, Calculate, General, Finance, Random and Developer categories.
  - Each family is a `ToolModule` object (`tools/<family>/…Tools.kt`) that owns its `Tool` entries and its screens map. `ToolRegistry.modules` lists them, and `ToolXNavHost` falls through to `ToolRegistry.moduleScreen(id)`.
  - Pure logic lives in `TextOps` / `GeneralOps` so it can be unit-tested.
  - Shared form pieces are in `ui/common/Forms.kt`: `NumberField`, `Dropdown`, `DateField`, `ResultCard` (tap a row to copy it), `BigResult`, `ToggleChip`, `ChipRow`, and `fmt`/`money`.
  - These tools compute live as you type. There's no Run button, which matches the "feedback before saving" rule.
- **UI, themes and settings** were redone on 2026-11-21 at the user's request ("I don't like that cyan"; one unique colour per category).
  - **Themes** (`ui/theme/Themes.kt`): an `AppTheme` palette has `accents`, one colour per `ToolCategory` in enum order. Adding a category means adding an accent to *every* theme; `ThemeCatalogueTest` enforces that.
  - **The catalogue:** System, Light, Dark, AMOLED, Material You (API 31+), Nord, One Dark Pro, 10 light themes and 10 dark ones (Catppuccin, Gruvbox, Solarized, Rosé Pine, Everforest, GitHub, Ayu, Tokyo Night, Nord Light, One Light / Mocha, Dracula, Monokai Pro, Kanagawa, Palenight…).
  - **Building the scheme:** `AppTheme.toColorScheme()` builds the whole Material scheme. `secondaryContainer` is a soft tint of the primary colour; the old Teal90 there was the cyan the user disliked.
  - **Readability:** colours are made readable with `readable()`, which nudges them to 3:1 contrast. The tests check every theme: body text ≥ 4.5:1, category colours ≥ 3:1, and category colours distinct from each other.
  - **Category colours everywhere:** `CategoryTheme(category)` wraps every tool screen in ToolXNavHost, so buttons, chips and sliders take the category's colour. Home tiles use `categoryColor` / `categoryContainer`.
  - **Settings** (`core/AppSettings.kt`): SharedPreferences exposed as Compose state, holding the theme id, hidden categories (by enum name) and hidden tools (by id). `ToolRegistry.search` filters hidden items, which also removes them from search. The status-bar icon style follows the app theme, not the phone's setting.
  - **Home** (revised the same day at the user's request) is a `HorizontalPager` with pages Recent, All, then one per visible category; you swipe left and right between them.
    - The chip row mirrors the pager: tapping a chip jumps to its page, and the row auto-scrolls to follow a swipe.
    - The current page is remembered by key, so it survives rotation and categories being hidden.
    - **Recent:** `AppSettings.recordRecent` is called from ToolXNavHost on every tool open. It keeps up to 12, newest first; `pushRecent` is unit-tested.
    - **Search:** the only thing at the bottom (no tab bar). It searches every category and replaces the pager with results while there is text. It sits above the keyboard via the union of the navigation-bar and keyboard insets; padding with both would leave a gap. Back clears it.
    - **Burger menu:** the ☰ beside the title opens a `ModalNavigationDrawer` with Recent, All tools, each category, and Settings. Drawer swipe gestures are on only while it's open, so they don't fight the pager.
    - **Settings** is its own nav route ("settings") with a back arrow.
- **PDF tools** (`tools/pdf/`, a `ToolModule` like the everyday tools) were added 2026-11-21, following iLovePDF's tool list, offline only.
  - **Libraries:** PdfBox-Android 2.0.27.0 does editing (call `PdfEngine.init` first; it's lazy). The platform `PdfRenderer` (pdfium) renders pages; it applies `/Rotate`, so its bitmaps are what the reader sees.
  - **Files:** every operation works on a cache copy (`PdfEngine.localCopy`, keyed by Uri + size). Output goes to `Documents/ToolX` through the MediaStore Files collection (`MediaKind.DOCUMENT`).
  - **Rotated pages:** all placement goes through `PdfOps.visualToUser` / `cropToUser`, which map "what the reader sees, top-left origin" to PDF user space for /Rotate 0/90/180/270. Stamps use a text matrix rotated by the page rotation, so they come out upright. Keep using these for anything new that places content.
  - **Copying pages** (split, organize, previews) must use `copyPage`:
    - It copies inherited MediaBox, CropBox, Resources and Rotate explicitly.
    - It trims resources to the XObjects and fonts the page draws (`usedResources`). Without that, a text-only split kept a shared 340 KB photo: 353 KB became 1.6 KB.
  - **Previews:** Page numbers and Watermark render the real operation on a one-page copy (`previewPage`). Compress runs every level in the background and shows the real resulting sizes, plus the first page that actually *draws* a re-encoded image. That page is found from its `Do` commands, because pages often list images they never draw.
  - **Redact** rasterizes only the pages with boxes (200 dpi), so their text is truly gone. Text-extraction checks confirmed that.
  - **Repair** retries with a fresh `%PDF` header if PdfBox rejects the original.
  - **Not built yet:** Edit PDF, Scan to PDF, OCR, Compare, Forms. **Impossible offline:** the Office conversions, HTML from a URL, AI summarize and translate, PDF/A.
- **Deliberately not built (user confirmed, 2026-11-21):** Currency and a "Sound" unit converter. The Lucky wheel's upside-down labels are accepted as they are.
- **Background removal** (`tools/image/BackgroundEngine.kt`, `MaskOps.kt`, `BackgroundScreens.kt`) uses ML Kit **selfie** segmentation with the *bundled* model (~4.5 MB).
  - It runs on the device and finds **people only**. The Subject Segmentation API was rejected because Play services downloads its model.
  - The manifest uses `tools:node="remove"` to strip ML Kit's INTERNET and ACCESS_NETWORK_STATE permissions. Keep it that way.
  - The model runs once per photo, on the 2048 px working copy. The confidence map is turned into alpha with a smoothstep (the Edge and Softness sliders). The export scales that mask up to full resolution, capped at ~12 MP.
  - **Edge refinement:** the model's mask is coarse (256² inside), so on a real photo it overshot shoulders by 15–20 px into the background. `BackgroundEngine.refine` runs a fast *colour* guided filter (`MaskOps.guidedCoefficients` / `applyGuided`).
    - Settings: radius = side / 32 at ≤640 px, and eps 1e-3. At eps 1e-4 white flecks appeared in hair, and 2e-3 was no better on the shoulders.
    - Where the raw model confidence is below 0.05, refinement output is forced to 0, because otherwise it created stray specks in the background.
    - It removes most overshoot, but can't separate same-colour neighbours (navy suit against a navy flag). A matting model would be the next step.
  - **Passport photo:** `MaskOps.findHead` reads the head from the silhouette alone.
    - The crown is the first row of a solid run (≥3% of the height), so specks above the hair are skipped.
    - The chin is the narrowest row of the first real dip after the head. That dip must be ≥12% narrower than the head and come after ≥0.8× the head width in height.
    - Fallback: chin = top + 1.3 × head width.
    - Shoulders widen gradually, so the first version (a "much wider than the head" test) framed the whole body. The tests cover sloping shoulders and stray blobs.
    - `passportCrop` sizes the crop so the head fills each preset's `headFraction`. The crop box draws a dashed oval head guide, and the user adjusts by dragging.
  - Output is 300 dpi (35×45 mm = 413×531 px). The optional 4×6 in sheet (1800×1200) uses 2 mm gaps, unless butting the photos together fits more copies (2×2 in: 6 instead of 2).

## Live previews (design to keep consistent)

- **Images** (`tools/image/LivePreview.kt`): decode once at a capped size (longest side 2048 px), then on each option change wait ~150 ms and re-encode that copy in the background. Show the *encoded* result, so artifacts are visible, plus an estimated output size. When the output is larger than the preview, the size is extrapolated by pixel count and shown with `≈`. Long-press shows the original.
- **Video compress** (`tools/video/CompressPreview.kt`): each quality chip shows an instant size estimate (bitrate × duration). On a preset change, it waits ~0.5 s and then encodes a 3-second sample from the middle of the video with the exact export settings. The sample loops and can be toggled against the original, and its measured size refines the estimate. Samples live in the cache dir and are deleted when replaced or when leaving the screen. Sampling pauses during the real export.
- **Crop** (`tools/image/CropEditor.kt`): the crop box is a normalized `CropRect` (0..1), so it maps onto the working copy, the full decode and a downsampled decode alike. Corner drags keep the opposite corner fixed and enforce ratios in *pixel* space. The output estimate crops the working copy and reuses `renderPreview`.
- **Speed and volume** preview live in the player (`ui/common/Playback.kt`): `PlaybackParameters` handles speed and pitch, and `LoudnessEnhancer` handles boosts above 100%. Exports use the matching Media3 processors: the speed-changing effect pair, `SonicAudioProcessor` for pitch, and `ChannelMixingAudioProcessor` for gain.
  - **Gotcha:** Media3's speed-changing processor shifts pitch along with speed, like a tape. To keep pitch, `MediaExport.speedEffects` appends a Sonic `setPitch(1/speed)`. Video Speed always keeps pitch, to match the preview.
- **Full-width sliders** use `Modifier.systemGestureExclusion()`. Their end handles sit in the system Back-gesture zone, and dragging them used to leave the screen.
- **Video Crop & rotate** grabs a middle frame (`VideoEngine.frameAt`, rotated to display orientation) and reuses the image `CropCanvas`. The export applies the rotation first, then the crop in rotated coordinates. Media3 rotates counter-clockwise, and `Crop` uses NDC with y pointing up.
- **Merge** fits every clip into the first clip's resolution with `Presentation` (letterbox) and forces an audio track so silent clips work.
- Compression bitrate is capped at 90% of the source bitrate, so output can never be larger than the input.
- New tools should follow the same idea: give the user feedback before saving, using cheap proxies instead of full re-encodes.

## Build / environment

- AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1, Compose BOM 2024.12.01, Media3 1.5.1.
- **Run Gradle on JDK 21** (Android Studio's bundled JBR). Gradle 8.11.1 does not support Java 25. Upgrading to Gradle 9 would also require upgrading AGP, so upgrade the whole toolchain together, deliberately.
- There are no `gradlew` scripts checked in. For a command-line build, use the Gradle 8.11.1 distribution Studio downloaded (`~/.gradle/wrapper/dists/gradle-8.11.1-bin/*/gradle-8.11.1/bin/gradle`), with `JAVA_HOME` set to the JBR, and run `gradle --console=plain :app:compileDebugKotlin`.
- On the laptop, Android Studio's bundled JBR (`D:\installation\Android Studio\jbr`) is **Java 25**, so it can't be used. Use `C:\Users\anama\.jdks\jbr-21.0.11` as `JAVA_HOME`.
- Development has happened on Windows machines where git, a JDK or the Android SDK may not be on PATH. Check before assuming any of them is there.
  - The laptop has Git for Windows 2.56.1 (`C:\Program Files\Git\cmd`) since 2026-11-21. A shell opened before that install needs its PATH reloaded from the registry.

## Testing on the emulator

Files dragged onto the emulator land in `/sdcard/Download/`, but the photo picker won't see them until MediaStore rescans:

```
adb shell content call --method scan_volume --uri content://media --arg external_primary
```

(In Git Bash, `export MSYS_NO_PATHCONV=1` first, so device paths aren't rewritten.)

## Status

- **Built:**
  - Video: Trim, Compress, Mute, Extract audio, Volume, Speed, Crop & rotate, Split, Merge.
  - Audio: Trim, Cut, Merge, Speed, Volume.
  - Image: Resize, Compress & convert, Rotate & flip, Crop, Remove background, Passport photo.
  - PDF (16): Merge, Split, Compress, Organize, Rotate, Crop, PDF to JPG, Images to PDF, PDF to text/Markdown, Page numbers, Watermark, Sign, Redact, Protect, Unlock, Repair. All were export-tested on the emulator (see TESTING.md).
  - Everyday tools (2026-11-21): 16 text, 12 unit, 11 calculation, 9 general, 4 finance, 5 random and 1 developer.
    - All 58 were opened on the emulator with no crashes.
    - Spot-checked results: EMI, unit values, Roman numerals, number to words, base conversion, fraction, prime, factorial, and the wheel winner matching the pointer.
    - Compass, Level and Ruler need a real phone to judge accuracy.
  - The other tools in `ToolRegistry` show "Soon".
- **Tests:** see `TESTING.md`.
  - 111 JVM unit tests (`gradle :app:testDebugUnitTest`) cover the pure logic.
  - The calc/finance helpers are `internal` top-level functions (`averageStats`, `emi`, `loanSchedule`, `futureValue`, `factorize`, …) so tests can reach them. Keep new logic out of composables, and add a test with an independently known expected value.
  - On the emulator, sensors can be faked with `adb emu sensor set acceleration x:y:z` and `adb emu sensor set magnetic-field x:y:z`.
- **Export-tested on the emulator (2026-11-21):**
  - Video:
    - Volume, Speed (3.93 s from 7.8 s at 2×) and Split (3 parts).
    - Crop & rotate: the output frame matched the selected box.
    - Merge: square + portrait clips gave 720×720 with letterboxing, 19.6 s.
  - Audio: Trim, Cut, Merge (mixed 44.1 kHz stereo + 22 kHz mono), Speed (both pitch modes) and Volume.
  - Audio outputs were decoded on the PC (Windows MediaTranscoder) and measured: gain ×1.50 at 150%, and pitch correct after the fix.
  - **Not tested yet:** the image tools' live preview, the compress preview, image Crop, and the audio level of video Volume (the emulator camera's audio is synthetic).
- **Export-tested (2026-11-21), background tools:** both run on a public-domain portrait: the 2012 White House photo, `/sdcard/Download/portrait.jpg` on the emulator. Results:
  - Remove background: white and transparent output.
  - Passport 35×45 mm: 413×531 px, with an 8-copy sheet.
  - Passport 2×2 in: 600×600 px, with a 6-copy touching sheet.
- **Testing tips:**
  - **PDF test files:** a script in the session scratchpad wrote a 4-page test PDF by hand (headings, bullets, an account number to redact, an embedded JPEG, a `/Rotate 90` page, and a MediaBox inherited from /Pages), plus damaged copies with a cut-off xref, a bad header and leading junk. Outputs were checked by rendering them with the app's own PDF to JPG (pdfium, independent of PdfBox) or by extracting their text.
  - On screens with a drawing canvas (Redact, Sign, Crop), adb swipes that start on the page draw or move things instead of scrolling. Scroll from the left margin (x ≈ 30).
  - Escape doesn't always close the keyboard. Use `KEYCODE_BACK`.
  - The photo picker lists the *newest* image first, and your own outputs and screenshots count. Save screenshots to `/data/local/tmp`, not `/sdcard`, and clear `Pictures/ToolX` between runs, or the next test silently uses a previous output as its input.
  - `uiautomator dump` fails while an ExoPlayer view is on screen, so tap by coordinates there.
  - The audio document picker path on the emulator is Audio › Unknown › Music.
  - `screenrecord` clips of a static screen are only a frame or two long, so they're useless as test inputs.
