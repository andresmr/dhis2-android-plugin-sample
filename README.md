# DHIS2 Android plugin template

A template for building plugins for the
[DHIS2 Android Capture App](https://github.com/dhis2/dhis2-android-capture-app). Fork it, run
`./init.sh`, and build features from written specifications.

A plugin is a small Android library that implements `Dhis2Plugin`, is packaged as a signed zip
bundle, and is rendered inside the Capture App from a server-side configuration.

What you get: a Kotlin Multiplatform plugin module with a three-layer architecture, a development
harness that signs in to a real DHIS2 and renders your plugin against real data, a spec format whose
scenarios are checked against your tests, and one command — `./verify.sh` — that means "done".

> **Status: proof of concept.** The plugin API, the bundle format and the injection points may still
> change. `plugin-sdk` is not published to Maven Central, so it has to be built into your Maven Local
> from a Capture App checkout before this project will even *configure* — that is step 1 below, and
> it is not optional.

- **Setting up a fork?** [`docs/workflows/initialise-plugin.md`](docs/workflows/initialise-plugin.md)
- **Building a feature?** [`docs/workflows/plugin-from-spec.md`](docs/workflows/plugin-from-spec.md)
- **Working on the code?** [`AGENTS.md`](AGENTS.md) — the layer rules, the build traps, the backlog.

Both workflows are plain prose. Follow them by hand, or hand them to whatever agent you use — Claude
Code, Cursor, Copilot, Codex. In Claude Code they are also `/plugin-init` and `/plugin-from-spec`.

This README is the install guide: what to install, and how to get a bundle onto a device.

## Prerequisites

- **Android SDK** with `platforms;android-37.0` and `build-tools;36.1.0`. Install them through
  Android Studio's SDK Manager, or:
  ```bash
  sdkmanager --install "platforms;android-37.0" "build-tools;36.1.0"
  ```
  The build-tools version is pinned in `plugin/build.gradle.kts`, because `d8` decides the DEX bytes
  and a different version moves the bundle's checksum. If it is missing, configuration fails with a
  message naming it.
- **A JDK** — any recent one, only to launch the Gradle wrapper. Gradle provisions its own JDK 21
  toolchain (`gradle/gradle-daemon-jvm.properties`), so you do not need 21 installed.
- **Gradle** — none to install; use the bundled `./gradlew` (9.5.1).
- **A DHIS2 server.** The plugin only reads, but the harness signs in and downloads metadata and
  tracker data, so use a development instance rather than production.
- **An emulator or a device.** From an emulator, `10.0.2.2` is your host machine.
- **A Capture App checkout** on the branch carrying the plugin system — `poc/plugin-system` at the
  time of writing. You need it twice: for step 1, and to install the host in step 7.

## Install, step by step

### 1. Publish the plugin API to Maven Local

In your **Capture App** checkout:

```bash
./gradlew :plugin-sdk:publishToMavenLocal :plugin-sdk-gradle:publishToMavenLocal
```

Both, always. `plugin-sdk-gradle` is the Gradle plugin that packages bundles and pulls in the
matching `plugin-sdk`; a stale copy of it is invisible from this side and surfaces as an unrelated
dependency-resolution error. Nothing in this repository configures until this succeeds, because
`plugin/build.gradle.kts` applies `id("org.dhis2.mobile.plugin-bundle")` from Maven Local.

Republish after any change to the plugin API — and note that a changed API under an unchanged
version just leaves a stale jar in `~/.m2`.

### 2. Make it yours

```bash
./init.sh
```

Asks for the plugin's name, Kotlin package, plugin id and entry-point class, then rewrites the
repository as yours: the sources move to your package, `plugin.json` records the identity that the
build and every gate read, and `./verify.sh` runs. Nothing is committed — review `git diff --staged`
first.

**Which host slot your plugin renders in is `plugin.json`'s `injectionPoints`**, and `./init.sh`
carries whatever it says through the rename rather than overwriting it — so set it before you run,
or pass `--injection-point` and `--data-set-uid`. The template ships targeting the home screen. See
*Host slots* in [`AGENTS.md`](AGENTS.md).

Pass the values as flags to skip the prompts; `./init.sh --help` lists them, and `--dry-run` shows
exactly what would change. Already done it? `./init.sh --check` says so.

Full walkthrough: [`docs/workflows/initialise-plugin.md`](docs/workflows/initialise-plugin.md).

### 3. Write `local.properties`

In **this** repository. Step 2 writes one for you from `local.properties.example` if it was absent;
it is gitignored and must never be committed.

```properties
# Required — without it, configuration aborts with an explicit error.
sdk.dir=/Users/you/Library/Android/sdk

# Harness credentials, read into BuildConfig by app/build.gradle.kts.
dhis2.serverUrl=http://10.0.2.2:8080
dhis2.username=admin
dhis2.password=district

# Optional. Which slot to render, when plugin.json declares more than one. Blank picks the most
# specific slot the plugin could actually be rendered at.
# harness.slot=HOME_ABOVE_PROGRAM_LIST
```

**Use a development server.** A plugin only reads, but the harness signs in as a real user and syncs
a real database onto the device.

Which slot the harness renders comes from `plugin.json`'s `injectionPoints` — the same field that
reaches the dataStore config — so the harness and a device cannot disagree about it. `harness.slot`
only overrides *which* of several declared slots you are working on, and naming one `plugin.json`
does not declare is refused. See *Host slots* in [`AGENTS.md`](AGENTS.md).

### 4. Build and verify

```bash
./verify.sh
```

This is the definition of done. It checks that every logic scenario in `specs/` has a test, that the
source tree agrees with `plugin.json`, and that the architecture rules hold; runs the unit tests;
builds the signed bundle, checks the zip's
layout (every entry under `android/` or `META-INF/`, and `android/classes.dex` present — the
class-level check that the bundle carries nothing the host already owns is the Gradle plugin's), and
prints the bundle path, its checksum, and a ready-to-post `plugin-config.json`.

`./verify.sh --cold` repeats it from an empty Gradle home, which catches stale local state. It keeps
Maven Local, because that is where `plugin-sdk` lives.

Output lands in `plugin/build/outputs/plugin-bundle/`.

### 5. Try it against real data, without the host

```bash
./gradlew :app:installDebug
```

`app/` is a development harness: it instantiates `D2`, signs in with the credentials from step 3,
downloads metadata, resolves the slot your `plugin.json` declares, and renders your plugin's real
entry point inside a reproduction of the host's Koin container. It finds that entry point the way the host does —
`Class.forName` on the name in `plugin.json` — so a wrong class name or a missing no-argument
constructor fails here, on your laptop, rather than on a device. The first run takes several minutes; every step is named
on screen, so a slow run is distinguishable from a stuck one. Afterwards the database is on the
device and startup is immediate.

This step is optional but much faster to iterate on than the full host. It is also not the Capture
App: see *Development harness* in [`AGENTS.md`](AGENTS.md) for the list of things only the real host
can tell you.

### 6. Serve the bundle

```bash
cd plugin/build/outputs/plugin-bundle && python3 -m http.server 8081
```

**8081, not 8080.** A local DHIS2 instance usually owns 8080 and would answer with its login
redirect instead of the zip, which on the device looks exactly like the plugin silently not loading.

### 7. Post the config to the server dataStore

The `plugin-config.json` beside the bundle already has `version`, `checksum`, `id`, `entryPoint` and
a `downloadUrl` pointing at `http://10.0.2.2:8081/…` — change the URL only for a physical device or
another port. The dataStore is the only source of plugin configuration; there is no in-app fallback.

```bash
curl -u admin:district -X POST \
  -H 'Content-Type: application/json' \
  --data @plugin/build/outputs/plugin-bundle/plugin-config.json \
  'http://localhost:8080/api/dataStore/dhis2AndroidPlugins/config'
```

`POST` creates the key; use `PUT` on the same URL to update it afterwards.

### 8. Install the Capture App and log in

In your **Capture App** checkout:

```bash
./gradlew :app:installDhis2Debug
```

Log in against the same server. Where the plugin appears is the slot its config names: at
`HOME_ABOVE_PROGRAM_LIST` it loads with the home screen and renders above the programme list; at
`DATA_SET_INSTANCE_CONTENT` it replaces the body of the data set instance screen, for the data sets
`slotConfig` lists, and nowhere else.

## Troubleshooting

**`Plugin [id: 'org.dhis2.mobile.plugin-bundle'] was not found`** — step 1 has not run, or ran in
the wrong checkout. If it did run, the copy in `~/.m2` may predate an API change: republish.

**`build-tools 36.1.0 is not installed`** — install it (see *Prerequisites*). Raising the pin in
`plugin/build.gradle.kts` is fine, but it moves the bundle's checksum and every machine building
this then needs the version you raised it to.

**Code changes are not showing on the device** — the Capture App caches bundles by
`{id}-{version}.zip`, so rebuilding at the same version reuses the old cache. Bump `version` in
**`plugin.json`** — not in `plugin/build.gradle.kts`, which reads it from there — then re-run
`./verify.sh` and post the new config.

**Checksum mismatch, or a bundle that will not verify** — bundles are signed with your local debug
key, so only the bundle *you* built matches the checksum *you* posted. A bundle built on another
machine will not match, even with the same build-tools: the signature block carries the signer's
certificate. A real publisher signs with their own key through `pluginBundle { signing { … } }`.

**`plugin.json and the source tree disagree`** — `plugin.json` was edited by hand, or an `./init.sh`
run did not finish. The message names what disagrees. To rename deliberately, use
`./init.sh --force`; to resume an interrupted run, re-run the same command.

**The plugin does not load on the device, with `ClassNotFoundException`** — the `entryPoint` in the
dataStore config names a class the bundle does not contain. `python3 tools/check-identity.py` catches
this before you post the config; `./verify.sh` runs it.

## Where to go next

- [`AGENTS.md`](AGENTS.md) — the working reference: the three layers and their rules, the build
  constraints that each cost real debugging, the DHIS2 design system, the on-device checklist, and
  the backlog.
- [`specs/README.md`](specs/README.md) — the spec format, and why logic and device scenarios are
  kept apart. Start from [`specs/TEMPLATE.md`](specs/TEMPLATE.md), and build what you write with
  [`docs/workflows/plugin-from-spec.md`](docs/workflows/plugin-from-spec.md).
- **Host slots** in [`AGENTS.md`](AGENTS.md) — the two places a plugin can render, how they differ,
  and how `plugin.json` decides which one you land on.
