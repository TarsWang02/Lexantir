# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Per-word interaction in the Today's Article reading room: double-click **any**
  word to translate it (reusing the system-wide capture popup), with a simulated
  sand-gold selection highlight that is light/dark-theme aware and clears on a
  blank-space click.
- Designed README, project governance files, issue/PR templates, and CI.

### Changed
- App icon and menu-bar icons refreshed; `WordBook.icns` regenerated.

## [1.0.0] — 2026-05-30

First working build (Java/JavaFX rewrite of the original Python/Tkinter app).

### Added
- System-wide capture: double-click / drag-select / `⌘⇧D` → floating translation
  popup with auto-TTS, multiple meanings, and "Add daily" / "+ Example".
- Daily / Unfinished / All Words lists with the 4 AM → 4 AM session model.
- Spell Check (Duolingo-style) with first-try-correct mastery and Morandi confetti.
- Calendar view: GitHub-style heatmap, streaks, and a rotating quote.
- DeepSeek "Today's Article" (CLIL) generation reusing all of the day's words.
- Themes (Light / Dark / Follow-System) and i18n (English / 简体中文 / 日本語).
- Menu-bar background agent; SQLite store; manual add, notes, batch ops, export.

[Unreleased]: https://github.com/TarsWang02/Lexantir/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/TarsWang02/Lexantir/releases/tag/v1.0.0
