#!/usr/bin/env bash
#
# Build WordBook.app — a self-contained native macOS bundle.
#
#   1. Maven builds a shaded fat jar (app + all deps + JavaFX natives).
#   2. jpackage wraps it with a bundled Java runtime into WordBook.app.
#
# The finished bundle is left at:  target/dist/WordBook.app
# Double-click it in Finder, or `open target/dist/WordBook.app`.
#
# First launch will ask for Accessibility permission
# (System Settings → Privacy & Security → Accessibility) — this is
# required for the global double-click / drag / ⌘⇧D word capture to work.
#
set -euo pipefail
cd "$(dirname "$0")"

# Use the JDK that ships jpackage (JDK 17+; this project is built/tested on 23).
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}"
echo "JAVA_HOME = $JAVA_HOME"

APP_NAME="WordBook"
APP_VERSION="1.0.0"
BUNDLE_ID="com.wordbook.app"

# Stable self-signed code-signing identity. Signing with the SAME identity on
# every build keeps the app's TCC "identity" constant, so the Accessibility /
# Input Monitoring permissions you grant once survive future rebuilds. If the
# identity is missing we fall back to ad-hoc (and you'd have to re-grant).
SIGN_ID="WordBook Self Sign"

echo "==> 1/4  Building shaded fat jar (mvn clean package)…"
mvn -q clean package -DskipTests

echo "==> 2/4  Staging jpackage input…"
rm -rf target/jpkg-input target/dist
mkdir -p target/jpkg-input target/dist
cp target/WordBook.jar target/jpkg-input/

echo "==> 3/4  Running jpackage…"
"$JAVA_HOME/bin/jpackage" \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --input target/jpkg-input \
  --main-jar WordBook.jar \
  --main-class com.wordbook.Main \
  --dest target/dist \
  --mac-package-identifier "$BUNDLE_ID" \
  --mac-package-name "$APP_NAME" \
  --icon packaging/WordBook.icns \
  --java-options "-Xmx512m"

APP="target/dist/$APP_NAME.app"

# Run as a menu-bar agent: hide the Dock icon (LSUIElement). Must happen BEFORE
# signing — editing Info.plist after codesign would invalidate the signature.
echo "==> Setting LSUIElement (menu-bar/background agent)…"
/usr/libexec/PlistBuddy -c "Add :LSUIElement bool true" "$APP/Contents/Info.plist" 2>/dev/null \
  || /usr/libexec/PlistBuddy -c "Set :LSUIElement true" "$APP/Contents/Info.plist"

echo "==> 4/4  Code-signing with stable identity…"
if security find-identity -p codesigning | grep -q "$SIGN_ID"; then
  # Sign inside-out: nested native libs / the launcher first, then the bundle.
  find "$APP/Contents" \( -name '*.dylib' -o -name '*.jnilib' \) -print0 2>/dev/null \
    | xargs -0 -I{} codesign --force --timestamp=none --sign "$SIGN_ID" {} 2>/dev/null || true
  codesign --force --timestamp=none --sign "$SIGN_ID" "$APP/Contents/MacOS/$APP_NAME" 2>/dev/null || true
  codesign --force --deep --timestamp=none --sign "$SIGN_ID" "$APP"
  echo "    signed as: $SIGN_ID"
  codesign --verify --deep --strict "$APP" && echo "    signature verified ✅"
else
  echo "    ⚠️  identity \"$SIGN_ID\" not found — leaving ad-hoc signature."
  echo "       (Permissions will reset on each rebuild until a stable identity exists.)"
fi

# Install into /Applications so the app has a permanent home. Because the
# signature identity is stable, re-installing over the old copy keeps the
# granted Accessibility / Input Monitoring permissions intact.
INSTALLED="/Applications/$APP_NAME.app"
echo "==> Installing to $INSTALLED …"
if rm -rf "$INSTALLED" 2>/dev/null && cp -R "$APP" "$INSTALLED" 2>/dev/null; then
  echo "    installed ✅"
  TARGET="$INSTALLED"
else
  echo "    ⚠️  couldn't write /Applications — using build copy instead."
  TARGET="$(pwd)/$APP"
fi

echo
echo "Done. App: $TARGET"
echo "Launch with: open \"$TARGET\""
