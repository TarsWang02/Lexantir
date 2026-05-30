# WordBook — Project Guide (CLAUDE.md)

## What this is
A macOS desktop vocabulary app for English learners, **rewritten from the original
Python/Tkinter app to Java/JavaFX** (same features & visual design, new architecture).

**Core thesis (the product's heart):** this is *not* a flashcard app — it's a
"learn-a-skill-in-the-language" engine (CLIL). You capture English words anywhere on
your system as you work/read; the app then uses an LLM to infer the *domain* of those
words and generate a short reading passage that re-uses **all of today's words**, so you
reinforce them in real, personally-relevant context. Capture → infer domain → generate
article → re-read = the loop.

Signature feature: highlight/double-click any English word **anywhere on the system** →
floating popup with translation (+ multiple meanings) + auto-TTS + "Add daily" / "+ Example".

---

## Build, run, package

> ⚠️ **Bash cwd resets between tool calls.** Always `cd /Users/organge/wordbook/wordbook-java`
> inside any mvn/package command, or it fails with "no POM in this directory".
> ⚠️ macOS has **no GNU `timeout`** — use background+kill patterns for timed runs.

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current   # JDK 23 (Zulu, arm64) via sdkman

cd /Users/organge/wordbook/wordbook-java
mvn -q compile            # compile only (fast feedback)
mvn -q javafx:run         # dev run (NOTE: runs as a plain 'java' process, not the signed
                          #   bundle — computer-use screenshot allowlist won't see it)
./package-mac.sh          # the real deal: clean package → sign → install to /Applications
```

`package-mac.sh` does: `mvn clean package` (shaded fat jar) → `jpackage --type app-image`
(`--icon packaging/WordBook.icns`) → set `LSUIElement=true` in Info.plist (menu-bar agent,
no Dock icon) → **code-sign with the stable self-signed identity** → copy to
`/Applications/WordBook.app`.

### Code signing (important for permissions)
- Signed with a **stable self-signed cert** named **`WordBook Self Sign`** (in the login
  keychain). Created once via openssl→p12 (`-legacy` for macOS compatibility)→`security import`.
- Why it matters: macOS ties **Accessibility / Input Monitoring** grants (TCC) to the
  signing identity. Ad-hoc signing changes the CDHash every build → grants reset every
  rebuild. A stable identity keeps the grants across rebuilds. **Do not delete that cert.**
- If the cert is missing, the script falls back to ad-hoc (and warns).

---

## Tech stack & layout
- **JDK 23**, **JavaFX 23.0.1**, **Maven** (`pom.xml`). Imperative JavaFX, no FXML.
- Deps: `javafx-controls`, `javafx-media`, `org.xerial:sqlite-jdbc:3.47.1.0`,
  `com.github.kwhat:jnativehook:2.2.2`, `com.google.code.gson:gson:2.11.0`,
  `net.java.dev.jna:jna:5.14.0`.
- Build plugins: `javafx-maven-plugin` (dev run), `maven-shade-plugin` (fat jar),
  `maven-compiler-plugin` (release 21). jpackage from the JDK builds the native bundle.
- Entry: `com.wordbook.Main` (plain launcher, does NOT extend Application) → `com.wordbook.App`.

### Source map (`src/main/java/com/wordbook/`)
- `App.java` — JavaFX Application; loads theme+lang, builds `MainWindow`, installs tray,
  `Platform.setImplicitExit(false)`, close→hide (background agent).
- `Main.java` — launcher.
- `ui/MainWindow.java` — **the monolithic controller** (BorderPane). Sidebar, header, all
  views (Daily/Unfinished/All/Reading/Calendar/Settings), capture popup, Today's-article flow.
- `ui/Theme.java` — switchable light/dark palette (mutable static fields) + fonts + helpers.
- `ui/I18n.java` — en/zh/ja string table (`I18n.t("key")`).
- `ui/Icons.java` — loads `/icons/ui/*.png`, **recolours** them to a theme colour (alpha
  mask + SRC_ATOP `ColorInput`) so one icon works in light/dark/active/danger.
- `ui/TrayService.java` — AWT `SystemTray` menu-bar icon (Open / Quit).
- `ui/components/` — `Card`, `FlatButton` (Duolingo 3D, supports `.icon()`), `IconButton`
  (supports `.icon()`), `Pill`, `DotGridPane`, `SpellCheckButton`.
- `ui/modals/` — `AddWordModal`, `NoteEditor`, `SpellCheckModal`, `ArticleGenDialog`
  (article settings), `AchievementCard` (portrait reward card).
- `service/Translator.java` — Google free translate endpoint; `Sense`/`ProcessedWord`
  records; `translateRich`/`senseLines`/`serializeSenses`.
- `service/TtsService.java` — TTS (see Known Issues — currently basic `say`).
- `service/DeepSeekService.java` — LLM client (article gen / domain clustering / quote).
- `capture/GlobalCapture.java` — JNativeHook global mouse/key listener + osascript ⌘C.
- `platform/MacNative.java` — JNA→Cocoa: front-most app pid + activate (popup foreground yield).
- `db/Database.java` — SQLite layer. `model/Word.java`, `model/Article.java`.
- `data/Articles.java` — old sample reading articles (**now unused**, see TODOs).
- `src/main/resources/styles/app.css` — themed via looked-up colours + `.dark` class.
- `src/main/resources/icons/` — `menubar_icon_{dark,light}.png`, `ui/*.png` (nav/action/celebrate).
- `packaging/` — `WordBook.icns`, `appicon_src.png`.

### Database  (`~/Library/Application Support/WordBook/wordbook.db` — same path as the old Python app)
- `words(id, word, translation, pos, example, example_translation, note, status,
  mastered, unfinished, wrong_count, mastered_at, date_added, created_at, senses)`
  - `senses` = JSON list of `{pos, terms[]}` (multiple meanings).
  - `created_at` is stored in **UTC** (legacy default) — always compare against a **UTC**
    cutoff (see `Database.sessionCutoffUtc()`).
- `spell_log(date, count)` — drives the heatmap/streaks.
- `meta(key, value)` — settings: `theme`, `lang`, `deepseek_api_key`.
- `articles(id, date, domain, difficulty, type, content, created_at, read_at, user_note)`
  — generated-article library.

---

## Daily session model
A "day" runs **4:00 AM → next 4:00 AM** (local). `getTodaySessionWords()` = words created
in the current session (any mastery state). At each 4 AM boundary, un-mastered words from a
**previous** session roll into **Unfinished** (`rolloverDailyPool()`), and the rollover is
**deferred while a Spell Check is open** (`spellActive` guard). The rollover runs once per
boundary (tracked by `lastSweepSession`), is self-healing, and uses the UTC cutoff.

Word lists:
- **Daily Pool** = today's session words; un-mastered pinned top, **mastered sunk to bottom**
  (green rim + ✓ badge). Includes words mastered *today*.
- **Unfinished** = `mastered=0 AND unfinished=1` (rolled over from past days).
- **All Words** = `mastered=1` (archive).

---

## Completed features
- Full Python→Java port; all original views & Morandi design preserved.
- **Global capture** (JNativeHook double-click / drag / ⌘⇧D) → translate → floating
  transparent always-on-top popup (auto-TTS, Add daily / + Example, multiple meanings).
  `MacNative` hands the foreground back so the main window doesn't surface on popup.
- **Multiple meanings (polysemy):** single Google request with `dt=t&dt=bd` returns a
  bilingual dictionary; senses stored in `words.senses` (JSON) and shown on cards, the
  capture popup, and spell check. A one-time background backfill filled existing words.
- **Spell Check:** quizzes **flagged (⚐→⚑) words on the current page** (page-scoped).
  Letter feedback always shows the **correct** word with wrong letters in red; feedback
  persists (cleared on retype / next word, not on a timer). Confetti celebration.
- **Theme system:** Light / Dark ("warm black") / Follow-System. `Theme.apply()` swaps a
  mutable palette; CSS uses looked-up colours toggled by a `.dark` class on each scene root;
  full UI rebuild on change. Persisted in `meta.theme`.
- **i18n:** English / 简体中文 / 日本語. First-run chooser; switch in Settings; persisted in
  `meta.lang`. Spell-check Chinese prompt uses Songti (宋体) for a softer look.
- **Custom icons:** app icon (`.icns`), menu-bar icons (light/dark auto by system), and
  in-app PNG icons (nav / card actions / toolbar / celebration), all **theme-tinted** via
  `Icons.java` (works in light & dark).
- **Background menu-bar agent:** Dock icon hidden (`LSUIElement`), top menu-bar icon with
  Open / Quit; closing the window **hides** it (global capture keeps running). No login item.
- **DeepSeek "Today's Article" (CLIL generation):**
  - Reading Room defaults to a **✦ Today's Article** tab (sample articles + right-hand
    instructions/theory panel were removed).
  - Flow: **Generate** → `ArticleGenDialog` (AI-clustered domain suggestions + custom field,
    difficulty 简单/中等/进阶, article type, output-language hook=English) → passage generated.
  - Uses **all of today's words (incl. mastered)**, capped at **40** (un-mastered prioritized);
    off-domain words woven in via metaphor; **every** word must appear.
  - Target words rendered **static sand-gold + bold + faint highlight** (no hover needed,
    cross-platform) via a `TextFlow` renderer.
  - A Chinese **导读** (reading guide) line is generated and shown above the passage.
  - A **MY NOTES** box (saved per article). **Done** button (in the *target* language) →
    marks read → **portrait achievement card** (iOS-proportioned: domain image + domain
    quote + this-week heatmap).
  - Articles saved to the `articles` table. API key in `meta.deepseek_api_key` (NOT in source).

### External services (all keyless/free except DeepSeek)
- **Translation:** `translate.googleapis.com/translate_a/single` (free, no key).
- **DeepSeek:** model **`deepseek-v4-flash`** (a *reasoning* model — read
  `choices[0].message.content`, ignore `reasoning_content`; give generous `max_tokens`).
  OpenAI-compatible at `https://api.deepseek.com`. Cost ≈ a few hundred tokens per article
  (fractions of a cent).
- **TTS:** macOS CLI (see Known Issues).

---

## Key design decisions
- **Stable self-signed code signing** so Accessibility/Input-Monitoring grants survive rebuilds.
- **Theme tinting of monochrome icons** (alpha mask) instead of shipping per-theme icon sets.
- **`-fx-background: <BG colour>` (never `transparent`) on the main ScrollPane** — a
  transparent value poisons modena's text-colour laddering and renders all card text white.
  (This was a real bug; do not reintroduce it.)
- **Senses stored in DB** (not re-fetched per render) so polysemy shows everywhere cheaply.
- **Article emphasis is static** (not hover) for cross-platform + mobile compatibility.
- **DeepSeek key & all settings live in `meta`** (local DB), never hard-coded.
- Capture/clipboard/keystroke use **macOS CLI tools** (`pbpaste`/`pbcopy`/`osascript`) +
  JNI-free JNA-Cocoa only where needed — chosen over AWT Robot to avoid AWT↔JavaFX conflicts.

---

## Known issues / TODOs
- **TTS is still the basic robotic macOS `say`.** `edge-tts` was removed when the Python
  `venv` was cleaned up, and no enhanced voice is installed. User **deferred** this ("read
  先不动"). Plan when resumed: prefer a **local macOS enhanced/premium voice** (`say -v`,
  fast + natural + offline) with a voice picker in Settings + prefetch; abstract `TtsService`
  behind an interface so each platform (Win SAPI/WinRT, iOS/Android native) plugs in later.
- **Achievement-card image is a placeholder gradient.** User will provide a curated CC0
  image set to **bundle** (lazy-loaded; bundle-size not memory is the cost). Then add
  domain→image matching. A self-hosted image server is the longer-term "product" option.
  (Decided against per-user image generation and raw image search — cost / moderation risk.)
- **Domain quotes** from the LLM can occasionally be fabricated/misattributed (low stakes).
- **No per-word tap interaction** on article target words yet (only static highlight).
- **No article-library browsing UI** (only today's latest is shown; articles are persisted).
- **Output language is English-only** (the hook exists in the gen dialog & DB; 导读 is Chinese).
- **Dead code:** `data/Articles.java` and `MainWindow.buildArticle/buildInstructions/buildTheory`
  are unused after the Reading Room redesign — safe to delete eventually.
- **Mobile (iOS/Android) not started.** Would need **Gluon Mobile** (to reuse this Java code)
  or a rewrite; `GlobalCapture`, `TtsService`, clipboard, and `MacNative` are macOS-specific
  and need per-platform implementations. Article gen / translation / UI logic port cleanly.
- **API key** is stored in plaintext in the local DB (fine for a personal app). It was pasted
  in chat — rotate it if this transcript is ever shared.
- **Re-summon after closing the window:** use the **menu-bar icon → Open** (re-opening the
  app from Finder while it's a running agent may not re-show the window).

---

## Gotchas cheat-sheet
- `cd` into `wordbook-java` for every mvn/package command (cwd resets).
- No GNU `timeout` on macOS.
- `created_at` is UTC → compare with `Database.sessionCutoffUtc()`.
- Don't set the main ScrollPane's `-fx-background` to transparent (white-text bug).
- `deepseek-v4-flash` returns reasoning tokens — set high `max_tokens`, read `content`.
- Verify external facts (e.g. model names) against the real API, not memory.
