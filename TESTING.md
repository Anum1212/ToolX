# ToolX testing

## Unit tests (JVM, ~5 s)

```
gradle --console=plain :app:testDebugUnitTest
```

Run with `JAVA_HOME` set to a JDK 21 (see CLAUDE.md). There are 143 tests in `app/src/test/`, and every expected value comes from an independent reference: SI/NIST unit definitions, standard amortization tables, and known math results.

| Suite | Covers |
|---|---|
| `TextOpsTest` (30) | All 16 text tools: cases (including "don't" in Title Case), literal vs regex find/replace, sorting, counter stats, duplicates, wrapping, emoji-safe reverse |
| `UnitConversionTest` (15) | All 12 unit categories against reference values, a round-trip check on every unit, and valid default units |
| `GeneralOpsTest` (13) | Morse (encode, decode, round trip), Roman numerals (round trip of all 1–3999), number to words (million and lakh/crore), bytes in 4 formats, weekday counting |
| `CalcMathTest` (7) | gcd/aspect ratio, fractions and mixed numbers, decimal → fraction (π ≈ 355/113), primes (including a Carmichael number), factorization, durations, averages |
| `FinanceMathTest` (4) | EMI (e.g. 200k at 7.5% over 30 years = 1,398.43), schedule totals, future value of a lump sum and SIP |
| `FormatTest` (4) | Number formatting and parsing |
| `ThemeCatalogueTest` (8) | Contrast maths (incl. the known 4.54:1 #767676), 10 light + 10 dark themes, one distinct colour per category in every theme, readable text, category colours and buttons in every theme, System/unknown-id fallback |
| `StepMathTest` (9) | Column addition carries, subtraction borrowing across zeros and negatives, 187 × 54 with every carry and partial row, 432 ÷ 15 = 28 r 12 = 28 4/5, decimals stopping when exact, 20-digit products |
| `FractionStepsTest` (7) | 4/5 × 3/7 = 12/35 (with the "4 × 3 over 5 × 7" line), LCD for 1/4 + 1/6, mixed 2 1/3 − 3/4 = 1 7/12, ÷ by reciprocal, whole × fraction, simplifying, parsing |
| `ShoeSizesTest` (4) | Every system round-trips; US M = UK + 1 and US W = UK + 2.5 by definition; matches a common chart row (26 cm); kids' sizes only for small feet |
| `RecentsTest` (2) | Recent tools: newest first, no duplicates, capped at 12 |
| `ToolVisibilityTest` (3) | Hidden categories and tools disappear from the list and from search |
| `PdfOpsTest` (11) | Page ranges (incl. open-ended and invalid), split groups, page labels, visual↔PDF coordinates for every /Rotate, crop mapping, anchors, content bounds (auto-trim), image page layout, downscale targets, text → Markdown |
| `MaskOpsTest` (14) | Mask alpha ramp, head finding (step shoulders, gradually sloping shoulders, a stray blob above the head), passport crop proportions and clamping, box mean, guided filter removing a 6 px overshoot (including navy vs dark red with equal brightness), bilinear resampling, 300 dpi sizes, print-sheet grid, hex colours |

## On-device checks (emulator, 2026-11-21)

Real values were typed into the actual screens, and the displayed results were compared with independently worked-out answers. **53 scripted checks plus 4 compass headings, all pass:**

- **Percentage:** all 5 modes (15% of 2400 = 360; 80 → 100 = +25%; 100 → 80 = −20%; 1000 ± 17%).
- **Proportion:** direct 3:12 = 7:28; inverse 4 workers × 12 days = 6 × 8.
- **Aspect ratio:** 2560×1440 = 16:9; resized to width 1280 → 720; 1024×768 = 4:3.
- **Fractions:** 18/24 = 3/4; 22/8 = 2 3/4; /0 shows an error; 0.125 = 1/8; 3.14159 ≈ 355/113.
- **Prime number:** 600851475143 = 71 × 839 × 1471 × 6857; 2³¹−1 is prime; 100 = 2²×5².
- **Exponent and factorial:** 2¹⁰⁰ exact (31 digits); ∛27 and ∛−8; 10!, 25!, 0!.
- **Time calculator:** 1:30 + 0:45 = 2:15; 22:30 → 06:15 = 7:45 overnight.
- **Day counter:** today + 100 days = Mar 1, 2027 (a Monday).
- **Age calculator:** born 15 May 1990 → 36 y 6 m 6 d, 13,339 days, born on a Tuesday, next birthday in 175 days (a Saturday).
- **Loan EMI:** 50 lakh at 9.5% over 20 years ≈ 46,607.
- **Investment:** 5,000/month at 8% for 15 years ≈ 17.4 lakh.
- **Bill splitter:** 2,450 + 10% tip ÷ 3 = 898.33 (rounded up: 899).
- **Discount:** 5,000 − 20% − 10% = 3,600 (28% effective).
- **Base converter:** 0xDEADBEEF = 3735928559.
- **Time zones:** Karachi 14:00 in 8 zones, plus the next-day "+1 day" marker.
- **Text tools:** cases (including "Don't"), find & replace count, counter.
- **Decoders:** Morse decode, Roman numerals (MCMXCIV and invalid IIII), hex bytes → "Hello".
- **Units:** length, temperature (−40 °C = −40 °F) and cooking, with typed values.
- **Sensors (values injected with `adb emu sensor set`):**
  - Level: flat, a 10° tilt and a 30° tilt.
  - Compass: settles on N 0°, E 90°, S 180° and W 270° (3–6 s on the emulator).
- **Ruler:** cm and inches agree with each other and with the 420 dpi screen.

## Step-by-step math and shoe sizes (emulator, 2026-11-21)

- **Long multiplication 187 × 54:** matches the user's textbook picture (748 + 9350 = 10098). The ×4 carries (2, 3) and ×5 carries (3, 4) sit above the top number in each row's colour, the addition carries (1, 1) sit above the partial rows, and the placeholder 0 is grey.
- **Long division 432 ÷ 15:** quotient 28 over 432, "2 × 15" 30 and "8 × 15" 120 subtracted, the brought-down 2 highlighted, remainder 12, 28 4/5 and 28.8.
- **Fractions 4/5 × 3/7:** the 4 × 3 over 5 × 7 = 12/35 chain, as in the user's picture.
- **Subtraction 503 − 278 = 225:** 5→4, 0→9 and 3→13 shown with crossed-out digits.
- **Shoe size UK 7:** 25.8 cm, EU 41, US M 8, US W 9.5, JP 26 cm, 260 mm, BR 39, RU 40. Kids' rows are hidden for an adult foot.
- **Bug found and fixed:** the division answer step dropped the whole number ("28 12/15 = 4/5"). It now reads "28 12/15 = 28 4/5", and a unit test covers it.

## UI and themes (emulator, 2026-11-21)

- **Default (System, light):** category-coloured tiles and chips, no cyan, search at the bottom, Tools / Settings tabs.
- **Nord, Gruvbox Light and Catppuccin Mocha** applied live. Status-bar icons flip correctly for dark and light themes. Tool screens take their category's colour (Split PDF in Nord purple, Text cases in Catppuccin blue).
- **Search with the keyboard open:** the field sits on the keyboard and the tab bar hides.
- **Hiding:** turning Video off removes its chip, section and search results. Hiding Trim audio shows "8 of 9 tools", and both settings survive an app restart. "Show all" restores everything.
- **Bug found and fixed:** a new search kept the old scroll position and hid the top matches.
- **Swipe, Recent and menu** (revised layout):
  - Swiping left from All goes to Video, with the chip row following.
  - After opening Merge then Trim, Recent lists Trim first, then Merge, with category labels.
  - The ☰ drawer lists all pages with the current one highlighted. Tapping PDF jumps to the PDF page.
  - Settings opens with a back arrow and Back returns to the same page.
  - Searching "merge" finds 4 tools across categories, and Back clears the search.
  - No crashes logged.

Not covered: all 25 themes on the device (the palettes themselves are checked by unit tests), Material You on a phone with a real wallpaper, and tablets or landscape.

## PDF tools (emulator, 2026-11-21)

Input: a hand-written 4-page PDF (346 KB). It has headings, bullets, a fake account number, an embedded photo, a `/Rotate 90` page and a MediaBox inherited from the page tree, plus damaged copies. Each output was checked by rendering it with pdfium (the app's PDF to JPG, independent of PdfBox) or by extracting its text.

| Tool | Result |
|---|---|
| PDF to text / Markdown | All 4 pages extracted (101 words). Markdown gives `#`/`##` headings, the two-line paragraph joined, bullets, and paragraph breaks. Saved `.md` to Documents/ToolX. |
| Page numbers | "Page N of 4" bottom-right on every page, upright on the rotated page. The live preview matched the output. |
| Watermark | Centred, 45°, 25% opacity, the same as the reader sees it on the rotated page. |
| Compress | Light 337 KB, Medium 174 KB, Strong 67 KB (−81%). The shared photo was converted once and the preview shows page 2, where the photo is. |
| Split | "1-2, 3-4" gives 2 files. The text-only half is 1.6 KB after resource trimming (353 KB before). |
| Merge | 2 files give 8 pages, in order. |
| Organize | Delete 4, rotate 1, move 1. The output matched exactly. |
| Rotate | All pages +90°. The page already at 90° ends at 180°. |
| Crop | Auto-trim covers the union of all pages' content. Output is 84% of Letter, and the rotated page stays landscape. |
| Sign | Signature drawn, moved, placed on the rotated page. It is upright and exactly where it was placed. |
| Redact | The box is black in the output. "1234-5678" is no longer extractable, and the other pages keep their text. |
| Protect / Unlock | Other tools refuse the protected file. A wrong password says "Wrong password", the right one unlocks, and the text is identical. |
| Repair | Truncated xref: 4 pages. Leading junk (pdfium can't open it): 4 pages. Bad header: 4 pages after the header retry. |
| Images to PDF | A4 page matching the layout preview. |

Found and fixed during this testing:
1. Split/extract carried every shared image along: resources are now trimmed to what each page draws.
2. Compress counted one shared image as 4 and previewed a page without it.
3. The default crop cut off a heading. It now auto-trims to the content of all pages.
4. Thumbnail rows misaligned with landscape pages, shifting buttons after a delete. Cells are now fixed-size, and rotated thumbnails fill them.
5. The signature box distorted on landscape pages.
6. A stale cached copy could be used for a changed file with the same Uri.
7. A bad-header PDF showed PdfBox's raw error; Repair now recovers it.

Not covered: large PDFs (hundreds of pages, 100 MB+), fonts outside Latin in real-world files, PDFs with forms, layers or annotations, and opening the outputs in other PDF apps.

## Background tools on a real photo (emulator, 2026-11-21)

Input: the official 2012 White House portrait (public domain, 1280×1599). It's a hard case: a flag, curtains and a navy flag sit behind a navy suit. Each output was pulled to the PC and inspected at full size.

- **Remove background:** the person is cut out cleanly and the hair edge is good. White JPG and transparent PNG outputs work.
- **Passport 35×45 mm:** auto-frame puts the crown and chin on the oval. The photo is exactly 413×531 px, and the 4×6 sheet is 1800×1200 with 8 copies and cut lines.
- **Passport 2×2 in:** 600×600 px, with the eyes ~64% up from the bottom (US rule: 56–69%). The sheet has 6 touching copies.

Found and fixed:
1. Shoulders picked up 15–20 px of red flag and curtain. The coarse 256² model mask was overshooting; a colour guided filter now snaps it to the photo's edges.
2. Auto-frame framed the whole body. Sloping shoulders inflated the "head width".
3. Auto-frame produced a blank white photo. A speck above the hair was taken as the crown; the speck itself came from the refinement and is now gated by the raw model confidence.
4. White flecks in hair at a too-low guided-filter eps (1e-4 → 1e-3).

Still visible: a thin dark fringe where the suit meets the curtain and the navy flag (same colour as the suit). A plain-wall passport shot is the easy case and wasn't the one tested here.

## Former "coming soon" tools (emulator, 2026-11-21)

Outputs were pulled to the PC and checked independently of the app where possible.

- **Round corners:** rounded PNG 1280×1599 with transparent corners; circle mode 1280×1280.
- **Color picker:** a tapped tie pixel gave #1D4B98 / rgb(29,75,152) / hsl(218,68%,35%) / cmyk(81%,51%,0%,40%), plus a 6-colour palette.
- **Watermark image / video:** tiled text at full resolution; the video mark is burned in (reopened the output with the overlay cleared).
- **Convert video:** H.265 output is hvc1 with the right duration. The emulator's software HEVC encoder can't make 720×1600 (or even 480×1066), and Media3 silently shrank the video to 180×400; the screen now warns when the encoder can't make the chosen size.
- **Video to GIF:** 3 s at 10 fps → 320×710, 31 frames, decoded by Windows (System.Drawing) with clean colours.
- **Add music:** 2 sequences (video + looping song); the output's soundtrack, extracted and converted to WAV, has the song for the full 6 s.
- **Reverse video:** 576×1280 H.264 + AAC, 6.1 s. A GIF of the output starts on the frame the original ends on.
- **Convert audio:** WAV, FLAC and Opus (OGG) outputs converted back to WAV in-app: FLAC is bit-identical to the WAV, Opus decodes at 48 kHz with the same RMS and pitch.
- **Reverse audio:** on a test file of 1 s hiss then a loud tone, the reversed copy starts loud (RMS 0.21) and ends on the hiss (0.03).
- **Noise reduction:** same file, strength 60%: hiss RMS 0.035 → 0.004 (≈ −19 dB), tone RMS 0.215 → 0.209, and the zero-crossing pitch estimate goes from 981 Hz (hiss-polluted) to exactly 440 Hz. Measured on the preview WAVs pulled from the app cache (`run-as`).
- **Edit tags:** on an M4A with no tags, set title, artist, album, album artist, year, track, genre, comment and a cover photo. The tagged copy was saved as `… (1).m4a` next to the untouched original (same size as before). Read on the PC with a library-free `ilst` parser: every value present, track 3, genre as `gnre` 53 (ID3v1 "Electronic" + 1), cover a 115 KB JPEG, audio still 6.04 s. Android's media scanner filed it under artist ToolX Band › album Emulator Sessions, and reopening it in the tool showed every field and the cover.

## Bugs found by this testing (all fixed)

1. Title Case capitalized letters after apostrophes ("Don'T").
2. Date fields were invisible to screen readers: a tap overlay hid their label and value.
3. The Ruler's readout overlapped its help text near the middle of the screen.
4. Searching "reverse video" found nothing: the whole phrase had to sit in one field. Search now matches every word across title, description, keywords and category, title matches first.
5. H.265 Convert silently shrank videos the encoder couldn't make (see above); now warned.
6. Hints under export buttons said "Saved as …" before anything was saved; now "Saves as …".
7. *(Earlier)* Audio Speed "keep pitch" didn't keep pitch, and edge slider handles triggered the Back gesture.

## Not covered

- Accuracy of Compass, Level and Ruler on real hardware.
- `fmt`/`money` follow the device locale, so locales that use different digits or separators haven't been checked.
- Background tools: tested on one photo only. Not tried yet: a selfie against a plain wall, long hair, glasses, more than one person, or a 12 MP phone photo (memory).
