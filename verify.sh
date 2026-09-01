#!/usr/bin/env bash
#
# The definition of done for this project.
#
# A script rather than a list in prose, for two reasons: an agent that has to run one command cannot
# quietly skip half of it, and "done" then means the same thing in every session.
#
# Usage:
#   ./verify.sh            # tests + bundle
#   ./verify.sh --cold     # same, but from a fresh Gradle home and an empty local Maven repo
#
# --cold is the only run that proves this project builds somewhere other than this machine. It is
# slow (a few minutes; it re-downloads everything) and worth it after touching settings.gradle.kts
# or vendor/.

set -euo pipefail

cd "$(dirname "$0")"

BUNDLE_DIR="plugin/build/outputs/plugin-bundle"
# Expanded below as ${GRADLE_ARGS[@]+...}: macOS ships bash 3.2, where `set -u` treats an empty
# array as unbound and aborts. The += form keeps the args properly quoted.
GRADLE_ARGS=()

if [[ "${1:-}" == "--cold" ]]; then
  COLD_ROOT="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$COLD_ROOT'" EXIT
  GRADLE_ARGS+=("-g" "$COLD_ROOT/gradle-home" "-Dmaven.repo.local=$COLD_ROOT/m2")
  echo "→ cold run: fresh Gradle home, empty local Maven repo"
  echo
fi

step() { printf '\n\033[1m→ %s\033[0m\n' "$1"; }
fail() { printf '\n\033[31m✗ %s\033[0m\n' "$1" >&2; exit 1; }

# ---------------------------------------------------------------- 1. unit tests

step "Unit tests (commonTest, JVM — no device)"
./gradlew ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"} :plugin:testAndroidHostTest

# ---------------------------------------------------------------- 2. the bundle

step "Plugin bundle"
./gradlew ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"} :plugin:buildPluginBundle

ZIP="$(find "$BUNDLE_DIR" -maxdepth 1 -name '*.zip' -print -quit 2>/dev/null || true)"
[[ -n "$ZIP" ]] || fail "No bundle produced in $BUNDLE_DIR"

# ---------------------------------------------------------------- 3. nothing the host owns

# The bundle's classes.dex must carry this module's classes and nothing else. A second copy of a
# host-provided class is what produces ClassCastException / NoSuchMethodError at load time, and it
# is invisible until a device rejects it. The Gradle plugin checks the AAR; this checks the artefact
# that actually ships.
step "Bundle contains only this plugin's classes"
if command -v unzip >/dev/null 2>&1; then
  ENTRIES="$(unzip -Z1 "$ZIP")"
  # Anything outside android/ (plus the signature block) means the layout changed underneath us.
  UNEXPECTED="$(printf '%s\n' "$ENTRIES" | grep -vE '^(android/|META-INF/)' || true)"
  [[ -z "$UNEXPECTED" ]] || fail "Unexpected entries in the bundle:"$'\n'"$UNEXPECTED"

  printf '%s\n' "$ENTRIES" | grep -q '^android/classes.dex$' \
    || fail "Bundle has no android/classes.dex"
  echo "  layout OK — $(printf '%s\n' "$ENTRIES" | wc -l | tr -d ' ') entries, all under android/ or META-INF/"
else
  echo "  skipped: unzip not on PATH"
fi

# ---------------------------------------------------------------- 4. what to do with it

step "Ready to install"
SHA_FILE="$ZIP.sha256"
echo "  bundle    $ZIP"
if [[ -f "$SHA_FILE" ]]; then
  echo "  checksum  $(cat "$SHA_FILE")"
else
  echo "  checksum  $(shasum -a 256 "$ZIP" | cut -d' ' -f1)  (computed; no .sha256 beside the bundle)"
fi

CONFIG="$BUNDLE_DIR/plugin-config.json"
if [[ -f "$CONFIG" ]]; then
  echo
  echo "  plugin-config.json — the dataStore entry. Version, checksum and downloadUrl are all"
  echo "  filled in, the URL assuming an emulator and a static server on port 8081; change it"
  echo "  only for a physical device or another port. POST to dhis2AndroidPlugins/config:"
  echo
  sed 's/^/    /' "$CONFIG"
fi

printf '\n\033[32m✓ verified\033[0m\n'
echo
echo "Not covered by any of the above: every scenario under '## Device scenarios' in the spec."
echo "Those need a real DHIS2 login — the plugin API hands over a D2, which cannot be constructed"
echo "outside a running app, so no test here can exercise a read or a write. Walk them by hand."
