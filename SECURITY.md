# Security Policy

## Supported versions

This is an early-stage project; only the latest `main` is supported.

## Reporting a vulnerability

Please **do not** open a public issue for security problems.

Instead, use GitHub's private reporting:
**Security → Report a vulnerability** (Private Vulnerability Reporting) on this
repository. If that is unavailable, contact the maintainer privately via GitHub
(@TarsWang02).

Please include:
- a description of the issue and its impact,
- steps to reproduce (or a proof of concept),
- the app version / commit and your macOS version.

You'll get an acknowledgement as soon as possible, and credit in the changelog
if you'd like it once the issue is resolved.

## Notes for this project

- The DeepSeek API key is stored only in the user's **local** SQLite database
  (`~/Library/Application Support/WordBook/wordbook.db`) — never in source.
- The app requests **Accessibility / Input Monitoring** to provide system-wide
  word capture. It reads the current text selection on the capture gesture only,
  translates it, and restores the prior clipboard; it does not log keystrokes.
