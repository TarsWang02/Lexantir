# WordBook — App Spec

A desktop vocabulary app for Chinese-speaking English learners, built around Content and Language Integrated Learning (CLIL). Users capture English words from any context they're already in (reading articles, watching videos, working in other apps) and the app builds a personal study loop around those words.

The defining UX move is the **highlight capture**: while reading anything anywhere on macOS, the user double-clicks an English word and a small floating card appears next to the cursor with the word, its part of speech, and a Chinese translation. Two buttons let them either add the word to their daily pool, or open a follow-up dialog to attach an example sentence. The word is read aloud automatically the moment it's captured.

The visual language is intentionally restrained: a warm-white "notebook paper" background with a faint dot grid, cream-colored cards, soft drop shadows, and rounded buttons in muted sand orange — a Morandi-inspired palette (desaturated, dusty, warm) that should feel calm rather than urgent. No vivid greens, reds, or blues anywhere. The reference points are Apple Notes, the Claude app on Mac, and the Duolingo spell-check screen.

---

## 1. Core Concept & Word Lifecycle

A word lives in one of two states:

- **Daily** — actively being learned. Newly captured words land here.
- **Mastered** — archived. The word has been "graduated" by being spelled correctly on the *first try* in spell-check mode.

Inside Daily, a word can additionally be flagged for **Practice** (a queue for the next spell session). The Practice flag is an attribute on a Daily word; it is not a separate state.

The lifecycle:

```
capture → Daily (mastered=0)
        → user flags it ✎  → Practice queue
        → user opens Spell Check
        → spells correctly on first try → Mastered (mastered=1)
        → spells correctly after 1+ wrong attempts → stays in Daily, wrong_count++
        → spells wrong & skips → wrong_count++, stays in Daily
```

A word in the **All Words** view (the Mastered archive) is color-tinted by how hard it was to master — its accumulated `wrong_count`:

- `0` wrong → muted sage (clean master)
- `1–2` wrong → warm grey
- `3–5` wrong → dusty amber
- `6+` wrong → dusty clay/rose ("this one fought back")

These tier colors appear as a 4-pixel left rim on the card and a small "× N" badge next to the word.

---

## 2. Screen Layout

A single window with a left **sidebar** and a right **content area**.

### Sidebar (220 px wide, cream background)
- App title "WORDBOOK" + tagline "A small notebook for words."
- Nav (3 items): **Daily**, **All Words**, **Calendar**. Active item: cream background, dark text, slightly heavier font.
- A **Spell Check** primary button (rounded, sand-colored, claymorphic shadow). Text reads `✦  Spell Check`; when there are practice-flagged words, it becomes `✦  Spell Check  ·  N`.
- Progress stats: current day streak, mastered count, daily-pool count.

### Content area (warm white, with a faint dot grid painted in warm-grey on the scroll canvas)
- Header row: large serif title (the current view name), then on the right: `↑ TXT`, `↑ CSV`, `+ Add word`, `☐ Select`.
- Search bar (filters by word / translation / example / note as you type).
- Scrollable card list (Daily and All Words views), or scroll-less single page (Calendar view).
- Batch action bar (appears when in Select mode): "N selected" + All / None / Add to Practice / Delete.

---

## 3. Capture Flow (the centerpiece)

The app installs a global keyboard + mouse listener so it can respond no matter which app is in front.

### Trigger
- **Double-click** an English word in any app (Safari, Notes, Obsidian, anywhere selectable text exists).
- Or a **drag-select** of ≤ 4 words.
- Or press **Cmd + Shift + D** with the word already on the clipboard.

The app sends Cmd+C to grab the selected text, restores the previous clipboard, and runs the captured text through a validator (English letters / hyphens / spaces only, ≤ 4 words).

### The floating capture popup
A small, borderless, **NSStatusWindowLevel** floating card (~320 × 150 px) appears:

- Near the cursor if we know the highlight's location (just below the cursor, clamped to the screen edges).
- Otherwise centered on the screen.

The card has:
- The captured word in large serif bold.
- POS badge color + meta line: `[adj]   短暂的`
- A horizontal divider.
- Two rounded buttons:
  - `Add daily` — adds it straight into the Daily pool and closes.
  - `+ Example` — closes this popup and opens a follow-up dialog where the user pastes an example sentence (clipboard auto-fills if it contains the word).
- A round `✕` close button at top-right (hovers to dusty rose).

**Behavior requirements (the hard-won ones):**
- The popup must float on top of every other app's windows, **without** stealing focus from the app the user was already in (Safari etc.). The user's text highlight must NOT be cancelled.
- Popup auto-dismisses after 15 seconds of no interaction.
- Escape closes it (when focused).
- Auto-speak the word via TTS the instant the popup opens.

The popup is read aloud in a clear English voice — the audio plays whether or not the user clicks anything.

---

## 4. Daily View

A vertical stack of word cards. Each card:

```
[POS]   word                                  [✎] [♪] [✎] [✕]
        translation                            
        e.g. example sentence in italic
        ┌──────────────────────────────────┐
        │ ✎  user's short note (if any)    │  ← sand-colored stripe
        └──────────────────────────────────┘
```

POS badge colors:
- `adj` — sand
- `verb` — dusty blue-grey
- `noun` — sage
- `adv` — warm grey

### Card actions (small icon buttons on the right)
1. **✎ Practice toggle** — toggles the Practice flag. When active, the icon background lights up sand-colored.
2. **♪ Hear word** — speaks the word via TTS.
3. **✎ Add/edit note** — opens a small dialog with a multi-line text area where the user types a short note (gloss, mnemonic, etc.). Saved to the word.
4. **✕ Delete** — confirm-prompts and removes.

### Hover effect
On mouse-over: the card's drop shadow color shifts from neutral warm grey to deep clay, and the border tint shifts from grey to sand. Subtle "lift". On leave, return to default after a tiny delay (so moving the cursor between child widgets doesn't flicker the state).

### Select mode
A header button toggles select mode. Each card grows a checkbox at the left; selections are tracked across search/filter. A bottom action bar appears with "N selected", All, None, "Add to Practice", and Delete.

---

## 5. All Words View

Identical layout to Daily, except:
- Only `mastered = 1` words.
- The card has a 4-px left rim in the wrong-count tier color.
- A small pill next to the word: `✓` if 0 wrong, `× N` otherwise, tinted by tier.
- No Practice toggle (mastered words can't go back into rotation).

---

## 6. Spell Check (Duolingo-inspired)

A separate `Toplevel` window (~520 × 500 px). Opens from the sidebar button. Pulls the user's practice-flagged words.

Flow per word:
- Top: a slim progress bar + `n / total`.
- Center: POS badge, then the **Chinese translation only**, in large serif bold. A small "♪ Hear word" link plays the English audio (the word the user is trying to spell).
- A single big text entry, center-aligned.
- Buttons: `→ Skip` and `Check` (sand primary).
- On Enter or `Check`:
  - **Wrong** → per-letter feedback: each letter rendered, correctly-positioned letters in sage on neutral background, wrong/missing positions in clay-rose on dusty-rose background. Word is read aloud. After 1.2 s the entry resets so the user can try again. `wrong_count++` on this word.
  - **Correct** → per-letter feedback in sage (if word was mastered this round) or sand (if it took more than one try). Feedback text: "Mastered ✓" vs. "Correct (keep practising)". If this was the user's first try with no wrong attempts, the word is **marked mastered** (mastered=1, removed from Daily). Always logs `+1` toward today's heatmap count.
  - **Skip** → no count change, no mastering.

### Completion celebration

When the user finishes the queue:
- Window expands to ~520 × 680.
- Big iconified glyph at the top (★ for 100% accuracy, ✓ for ≥75%, ↑ for less). Brief pulse animation: 56 → 64 → 72 → 64 → 58 → 60 → 58 px over ~0.5 s.
- **Confetti animation** on the same canvas: ~28 small soft-colored ovals (Morandi palette: sand, sage, clay, warm grey, dusty amber) fall from the top with mild gravity (`vy += 0.04` per frame), slight horizontal drift, and exit when they pass the bottom. No neon, no sparkles — just soft, muted dots drifting down.
- Title text (e.g. "Perfect!" / "Well done!" / "Good effort!") and a subtitle.
- Three stat cards in a row: `correct / total`, `accuracy %`, `mastered this session`.
- A mini heatmap card (same as Calendar view, just smaller).
- TTS speaks the title ("Perfect!" etc.).
- "Back to words" button at the bottom.

---

## 7. Calendar View

A static, single-page view (no scrollbar). Three sections, stacked:

### Motivational quote card
Big serif quote + attribution. Three quote pools, chosen by streak / month activity:
- Excellent tier (streak ≥ 7 or ≥ 20 days this month): James Clear, Aristotle, Robert Collier
- Good tier (streak ≥ 3 or ≥ 5 days this month): Robin Sharma, Mark Twain, Sam Levenson
- Encouragement tier (otherwise): Lao Tzu, Confucius, Zig Ziglar, Chinese Proverb

Quote rotates daily (`day-of-year % pool_size`).

### Heatmap card (GitHub-style)
A spell-activity heatmap. ~30 columns × 7 rows of small rounded squares (one per day). Each day's color is one of 5 depth tiers based on how many words were correctly spelled that day:

- 0 → almost background tone
- 1–2 → faint sand
- 3–5 → warm sand
- 6–9 → deeper sand
- 10+ → deepest

Month labels on top, weekday letters (M, W, F) on the left. A legend strip: "Less" + 5 swatches + "More".

### Stats row
Four small cards: Current streak, Longest streak, This month (days), Active days (lifetime).

---

## 8. Add Word Dialog (manual entry)

A small dialog (~440 × 340). Three fields:

1. **English word** entry — autofocus.
2. **Translation** entry with a status label next to it ("translating…"). **As the user types in the word, after a 600 ms debounce**, the translator runs in a background thread and **auto-fills the translation field**. The user can edit it. If they've already manually edited it, auto-fill won't overwrite.
3. **Example sentence** (optional).

Enter cycles fields → submit. Sand `Add` button at the bottom.

---

## 9. Export

Two header buttons: `↑ TXT` and `↑ CSV`. They export **whatever's currently visible** (post-filter, in the active view). CSV uses Python's csv module — properly escapes commas/quotes. Columns: word, translation, pos, example, note, status, mastered, wrong_count, date_added.

---

## 10. Settings, paths, etc.

- Data dir: `~/Library/Application Support/WordBook/wordbook.db` (SQLite).
- TTS cache: `<tmp>/wordbook_tts/<voice>__<word>.mp3`.
- Log file (for the launcher): `/tmp/wordbook.log`.

---

## 11. Visual Design Language

### Palette (Morandi — desaturated, dusty, warm)
- `bg`: `#EFE9DD` — main warm white
- `sidebar`: `#E8E1D2` — slightly deeper warm
- `card`: `#F7F2E7` — cream card
- `shadow`: `#D6CDBC` — soft warm grey for shadows
- `border`: `#D9D1C2`
- `dot`: `#D6CDBC` — dot grid color (same as shadow, faint)
- `text_main`: `#4D4233` — warm dark
- `text_sub`: `#877C6D` — warm grey
- `text_hint`: `#B0A795` — faint
- `accent`: `#C9A578` — primary sand orange
- `accent_dark`: `#A8895F`, `accent_light`: `#EDDFC4`
- Wrong-count tiers: `#A8B89C` (sage) → `#B5A78F` (warm grey) → `#C9A578` (sand) → `#B88B7A` (clay/rose)
- Heatmap depth: `#E5DDCD` → `#E0CFAA` → `#C9A578` → `#A8895F` → `#80683F`

### Typography
- Everything is **Georgia** (serif).
- Title: 22 pt bold
- Card word: 15 pt bold
- Body: 13 pt
- Hint / example: 10 pt italic

### Buttons (claymorphism)
All primary action buttons are **custom canvas-drawn rounded rectangles**:
- 12–14 px corner radius
- Soft drop shadow underneath (drawn as a darker rounded rect at +3 px offset)
- On hover: lighten background ~10%
- On press: shadow collapses to +1 px, fill darkens slightly
- Sand `accent` for primary, cream for secondary

### Dot grid background
A faint grid of 1-px-radius dots in `#D6CDBC`, spaced 22 px, painted on the scroll canvas (behind the cards). Repaints on resize. Gives a notebook-paper feel; the cream cards float above it.

### Rounded floating popup
The capture popup uses `overrideredirect=True` + the underlying `NSWindow.setLevel_(25)`. Inside, a canvas paints two soft shadow layers (light warm grey at +8 px, dusty taupe at +5 px) under a card with a 2-px sand `accent_dark` border. Visually it reads as a floating Apple-style popover.

---

## 12. Auto-speak

The instant a word is captured (popup creation), the app calls TTS to speak the word in a clear English voice. Implementation:
- Primary: **Microsoft Edge Neural TTS** via the `edge-tts` Python package (free, no API key, very natural).
- Voice: `en-US-AriaNeural`.
- Falls back to `pyttsx3` (offline, macOS NSSpeechSynthesizer) if edge-tts fails.
- Cached per-word as mp3 in a tmp dir, played with macOS `afplay`. Second click of the same word is instant.

The ♪ button in word cards and the Hear word link in spell check use the same `speak()` function.

---

## 13. Behavioral contract (sequence diagrams)

### Capture, double-click in Safari

```
user double-clicks "ephemeral" in Safari
  ↓
mouse listener detects: 2 left clicks within 0.45 s, distance < 5 px
  ↓
  snapshot frontmost app (= Safari)
  send Cmd+C
  read clipboard, restore previous clipboard
  validate: letters only, ≤ 4 words → "ephemeral"
  process_word: translate → "短暂的", detect POS → "adj"
  ↓
on the Tk main thread:
  store pending word + capture coordinates + prev app
  call _show_capture_popup
  ↓
_show_capture_popup:
  speak("ephemeral")              ← TTS in background thread
  create Toplevel, overrideredirect, build canvas content
  position near (capture_x, capture_y), clamp to screen
  find the underlying NSWindow (matched by title)
  setLevel_(25), setBecomesKeyOnlyIfNeeded_(True),
  setFloatingPanel_(True), setHidesOnDeactivate_(False),
  setCollectionBehavior_(CanJoinAllSpaces | Transient | IgnoresCycle)
  reactivate prev_app (Safari)   ← user's highlight survives
  orderFrontRegardless           ← popup stays on top
  toggle overrideredirect off/on ← force Tk to repaint (macOS .app bug)
  reapply setLevel_(25) + orderFrontRegardless
  bind Escape + 15-s auto-dismiss
```

### Spell mastery

```
user in spell window types "ephemeral" + Enter
  ↓
  if first_try_correct (no wrong attempts this word):
    record_spell_correct(1)        ← today's heatmap count + 1
    mark_mastered(word_id)         ← mastered = 1, mastered_at = now,
                                     status = 'default'
    show per-letter sage feedback
    feedback label: "Mastered ✓"
  else:
    record_spell_correct(1)
    show per-letter sand feedback
    feedback label: "Correct (keep practising)"
  ↓
  speak("ephemeral")
  Next → / Finish button advances; Finish triggers celebration
```

---

## 14. Data model

```
words
  id           INTEGER PRIMARY KEY
  word         TEXT NOT NULL
  translation  TEXT
  pos          TEXT          -- 'noun' | 'verb' | 'adj' | 'adv'
  example      TEXT
  note         TEXT
  status       TEXT DEFAULT 'default'   -- 'default' | 'practice'
  mastered     INTEGER DEFAULT 0        -- 0/1
  wrong_count  INTEGER DEFAULT 0
  mastered_at  TEXT
  date_added   TEXT DEFAULT (date('now', 'localtime'))
  created_at   TEXT DEFAULT (datetime('now', 'localtime'))

spell_log
  date   TEXT PRIMARY KEY    -- 'YYYY-MM-DD', localtime
  count  INTEGER DEFAULT 1   -- total correct spellings that day
```

`add_word(word, translation, pos, example=None, note=None)` is idempotent for unmastered words — calling it with an existing daily word will not duplicate; it will update example/note if they were null. Mastered words don't suppress new entries (the user can "re-learn" a previously mastered word as a fresh daily entry).

---

## 15. Pedagogical thinking

- **Cost of capture is near zero.** The user shouldn't have to switch apps, type the word, or click through menus. Double-click + autospeak + 2-button popup is the entire interaction.
- **Mastery should be hard to fake.** First-try-correct in spell check is the only path. If they need multiple tries, they're not done yet.
- **Difficulty is remembered.** A word's `wrong_count` follows it into the archive so the user can revisit the words that gave them trouble.
- **Quiet encouragement, not gamification.** No XP, no leaderboards, no streaks-or-lose-everything dread. A subtle quote, a heatmap, a streak counter. The streak isn't a punishment — it's a quiet observation.
- **Beauty matters.** A learning tool the user opens daily should feel like a notebook they want to write in. Hence the Morandi palette, the dot grid, the soft shadows, the gentle confetti.

---

## 16. Technical constraints (lessons from building this)

1. **macOS hotkey requires Accessibility permission.** The .app's binary (or the Python interpreter if running from source) must be added to System Settings → Privacy & Security → Accessibility.

2. **macOS 15 SIGTRAP issue.** pynput's keyboard listener calls `TSMGetInputSourceProperty` from a worker thread, which trips `dispatch_assert_queue` under the hardened runtime that launchd applies to `.app` bundles. Result: instant SIGTRAP crash on `.app` double-click — but works fine from terminal. Workaround: the `.app` launcher script uses `osascript` to ask Terminal.app to run the python process (`nohup ./venv/bin/python main.py & disown`, then close the Terminal window). Python ends up in Terminal's relaxed runtime context, survives the window closing.

3. **macOS PyObjC lazy-import thread race.** `HIServices.AXIsProcessTrusted` can `KeyError` when accessed first from pynput's worker thread on pyobjc 12.x. The app pre-touches it on the main thread before starting the listener.

4. **PyInstaller-bundled Tk doesn't paint borderless windows when the owning app isn't active.** The NSWindow exists, has correct frame, isVisible=True, alpha=1.0 — but is blank. Fix: after positioning, toggle `overrideredirect` False→True→update_idletasks → reapply NSWindow level. Forces a repaint.

5. **Floating popup must NOT activate the WordBook process.** Calling `popup.focus_force()` / `lift()` would steal focus from Safari and cancel the user's highlight. Instead: `setBecomesKeyOnlyIfNeeded_(True)` + `setFloatingPanel_(True)` + reactivate the previously-frontmost app via `NSWorkspace.frontmostApplication()` snapshot.

6. **TTS choice.** `pyttsx3` produces robotic macOS NSSpeechSynthesizer output. Switched to `edge-tts` (Microsoft neural voices, free, no API key). Cache mp3 per word, play with `afplay`.

7. **DB location.** Must live in `~/Library/Application Support/WordBook/`, not next to the source code. The `.app` bundle's resource directory is read-only on signed builds.

8. **Mousewheel scrolling on macOS.** `event.delta` is ±1 (small) on macOS but ±120 on Windows. Handle both.

---

## 17. Stack

- **Language:** Python 3.11+
- **GUI:** Tkinter (`tk.Canvas`-heavy for custom rounded buttons, shadows, dot grid)
- **DB:** SQLite (stdlib)
- **Translation:** `deep_translator` → GoogleTranslator (free, unofficial)
- **TTS:** `edge-tts` (primary) + `pyttsx3` (fallback)
- **Hotkey / mouse capture:** `pynput`
- **Clipboard:** `pyperclip`
- **macOS native bridge:** `pyobjc` (AppKit, HIServices, AppKit.NSWorkspace, AppKit.NSApp.windows().setLevel_/setBecomesKeyOnlyIfNeeded_/setFloatingPanel_/setCollectionBehavior_/orderFrontRegardless)

---

## 18. Out of scope (intentionally)

- Multi-language support beyond English → Simplified Chinese.
- Cloud sync / accounts / multi-device.
- Spaced repetition scheduling (could be a later v2 — using `wrong_count` and `mastered_at` to surface forgotten words).
- Word definitions beyond a single translation (no full dictionary).
- Image / audio attachments per word.
- Sentence-level capture (currently capped at 4 words).
- Importing word lists from external sources.

---

## 19. Concrete acceptance criteria

The app is considered "working" when:

1. Double-clicking an English word in Safari while WordBook is running causes a small floating popup to appear next to the cursor within 1 second, with the word, POS, and a Chinese translation, while Safari remains the foreground app and the user's text highlight in Safari is preserved.
2. The word is spoken aloud automatically using a natural-sounding English voice.
3. Clicking "Add daily" on the popup adds the word to the Daily view; it appears at the top of the Daily list.
4. The popup auto-dismisses after 15 s if the user does nothing.
5. Flagging a Daily word with ✎ and then completing Spell Check, spelling it correctly on the first try, removes it from Daily and adds it to All Words with a `✓` badge and a sage left rim.
6. Spelling a word wrong once and then correctly leaves it in Daily with `wrong_count = 1`.
7. Calendar view shows a heatmap whose tile colors deepen with the number of correct spellings on that day; the current streak and longest streak are accurate.
8. The capture popup, all card hovers, all RoundedButton hovers, and the celebration confetti all render in the Morandi palette — no vivid colors.
