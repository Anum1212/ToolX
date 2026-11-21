# ToolX testing

## Unit tests (JVM, ~5 s)

```
gradle --console=plain :app:testDebugUnitTest
```

Run with `JAVA_HOME` set to a JDK 21 (see CLAUDE.md). There are 98 tests in `app/src/test/`, and every expected value comes from an independent reference: SI/NIST unit definitions, standard amortization tables, and known math results.

| Suite | Covers |
|---|---|
| `TextOpsTest` (30) | All 16 text tools: cases (including "don't" in Title Case), literal vs regex find/replace, sorting, counter stats, duplicates, wrapping, emoji-safe reverse |
| `UnitConversionTest` (15) | All 12 unit categories against reference values, a round-trip check on every unit, and valid default units |
| `GeneralOpsTest` (13) | Morse (encode, decode, round trip), Roman numerals (round trip of all 1–3999), number to words (million and lakh/crore), bytes in 4 formats, weekday counting |
| `CalcMathTest` (7) | gcd/aspect ratio, fractions and mixed numbers, decimal → fraction (π ≈ 355/113), primes (including a Carmichael number), factorization, durations, averages |
| `FinanceMathTest` (4) | EMI (e.g. 200k at 7.5% over 30 years = 1,398.43), schedule totals, future value of a lump sum and SIP |
| `FormatTest` (4) | Number formatting and parsing |
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

## Bugs found by this testing (all fixed)

1. Title Case capitalized letters after apostrophes ("Don'T").
2. Date fields were invisible to screen readers: a tap overlay hid their label and value.
3. The Ruler's readout overlapped its help text near the middle of the screen.
4. *(Earlier)* Audio Speed "keep pitch" didn't keep pitch, and edge slider handles triggered the Back gesture.

## Not covered

- Accuracy of Compass, Level and Ruler on real hardware.
- `fmt`/`money` follow the device locale, so locales that use different digits or separators haven't been checked.
- Background tools: tested on one photo only. Not tried yet: a selfie against a plain wall, long hair, glasses, more than one person, or a 12 MP phone photo (memory).
