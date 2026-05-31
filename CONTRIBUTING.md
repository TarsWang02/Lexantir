# Contributing to Lexantir

Thanks for your interest! Lexantir is currently a **proprietary, single-maintainer
project** (see [LICENSE](LICENSE)), but feedback, bug reports, and ideas are very
welcome.

## Ways to help

- 🐞 **Report a bug** — open an issue using the *Bug report* template. Include your
  macOS version, what you did, and what you expected.
- 💡 **Suggest a feature** — open an issue using the *Feature request* template.
- 🧪 **Test builds** — see the build steps below and tell us what breaks.

> Code contributions (pull requests) are not generally accepted while the project
> is proprietary. If you'd like to collaborate, open an issue first to discuss.

## Building locally

Requires **JDK 23** (built/tested on Zulu 23, arm64).

```bash
cd wordbook-java
export JAVA_HOME=~/.sdkman/candidates/java/current

mvn -q compile      # fast feedback
mvn -q javafx:run   # dev run
./package-mac.sh    # full signed bundle → /Applications
```

First launch needs **Accessibility + Input Monitoring** permission
(System Settings → Privacy & Security) for global capture.

## Project conventions

- Imperative JavaFX, no FXML. `MainWindow` is the central controller.
- Colors/fonts go through `Theme`; user-facing strings through `I18n.t(...)`.
- The DeepSeek API key lives in the local SQLite DB only — **never commit secrets**.
- `created_at` is stored in UTC — compare against `Database.sessionCutoffUtc()`.

## Commit messages

Short, present-tense summaries with a type prefix where it helps
(`feat:`, `fix:`, `docs:`, `refactor:`, `chore:`).
