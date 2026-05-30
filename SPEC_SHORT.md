# WordBook — Short Description

Build a macOS desktop vocabulary app for Chinese-speaking English learners, in Python + Tkinter.

The signature interaction: while reading anywhere on macOS (Safari, Notes, etc.), the user **double-clicks an English word** and a small floating card appears next to the cursor showing the word, its part of speech, and a Chinese translation. The word is **spoken aloud automatically** (Microsoft Edge neural TTS). Two buttons: "Add daily" or "+ Example". The card must float on top of every other app **without stealing focus** from the user's current app — their text highlight must survive.

A word starts in **Daily** (active learning). The user flags it for **Practice** with a ✎ button. The **Spell Check** mode (Duolingo-inspired) drills practice words: shows only the Chinese translation, user types the English, gets per-letter feedback in soft sage/clay. If they spell it correctly on the first try (zero wrong attempts), the word becomes **Mastered** and moves to the **All Words** archive. Every wrong attempt increments `wrong_count`, which later tints the archived card in muted Morandi tones — sage (clean) → warm grey → dusty amber → clay-rose ("this one fought back").

A **Calendar** view shows a GitHub-style heatmap of daily spell-correctness, plus a rotating motivational quote and stats (current streak, longest streak, this month, total active days).

**Visual language: Morandi-inspired warm white background with a faint dot grid (notebook-paper feel), cream cards with soft shadows, rounded sand-orange buttons (claymorphism), Georgia serif throughout.** No vivid greens, reds, or blues anywhere — everything desaturated and dusty.

**Other features:** add words manually with auto-translated (and editable) translation, attach a short note to any word, batch select / delete / promote-to-practice, export current view to TXT/CSV, gentle confetti animation in Morandi colors on spell-session completion.

Reference points: Apple Notes, the Claude app on Mac, the Duolingo spell screen, a paper dot-grid notebook.

Data: SQLite at `~/Library/Application Support/WordBook/wordbook.db`.

Stack: Python 3.11+, Tkinter, SQLite, `deep_translator`, `edge-tts`, `pyttsx3` (fallback), `pynput` (global hotkey + mouse capture), `pyobjc` (macOS NSWindow level + floating panel config so the popup floats over other apps without activating).
