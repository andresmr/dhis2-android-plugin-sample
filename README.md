# DHIS2 Android plugin sample

A reference plugin for the [DHIS2 Android Capture App](https://github.com/dhis2/dhis2-android-capture-app).
`ProgramOverviewPlugin` reads a tracker programme through the DHIS2 Android SDK and renders a card on
the app's home screen — enrolment and event counts, a few enrolled people, and one write.

A plugin is a small Android library that implements `Dhis2Plugin`, is packaged as a signed zip
bundle, and is rendered inside the Capture App from a server-side configuration.

> **Status: proof of concept.** The plugin API, the bundle format and the injection points may still
> change. `plugin-sdk` is not published to Maven Central, so it has to be built into your Maven Local
> from a Capture App checkout before this project will even *configure* — that is step 1 below, and
> it is not optional.

This README is the install guide. For how the plugin is put together — the layer rules, the build
constraints, the design system, the backlog — read [`CLAUDE.md`](CLAUDE.md).

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
- **A DHIS2 server you can write to.** The harness signs in and creates an event, so use a
  development instance, never production.
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

### 2. Write `local.properties`

In **this** repository. The file is gitignored and never committed:

```properties
# Required — without it, configuration aborts with an explicit error.
sdk.dir=/Users/you/Library/Android/sdk

# Harness credentials, read into BuildConfig by app/build.gradle.kts.
dhis2.serverUrl=http://10.0.2.2:8080
dhis2.username=admin
dhis2.password=district
dhis2.programUid=
```

`dhis2.programUid` chooses only which programme the **harness** downloads tracker data for; blank
picks the first tracker programme. It does not choose what the plugin reads — the plugin currently
hardcodes the demo Child Programme (`IpHINAT79UW`). If the harness downloads one programme and the
plugin reads another, the card reports the programme as not found; the harness says so on screen.

### 3. Build and verify

```bash
./verify.sh
```

This is the definition of done. It runs the unit tests, builds the signed bundle, checks the zip's
layout (every entry under `android/` or `META-INF/`, and `android/classes.dex` present — the
class-level check that the bundle carries nothing the host already owns is the Gradle plugin's), and
prints the bundle path, its checksum, and a ready-to-post `plugin-config.json`.

`./verify.sh --cold` repeats it from an empty Gradle home, which catches stale local state. It keeps
Maven Local, because that is where `plugin-sdk` lives.

Output lands in `plugin/build/outputs/plugin-bundle/`.

### 4. Try it against real data, without the host

```bash
./gradlew :app:installDebug
```

`app/` is a development harness: it instantiates `D2`, signs in with the credentials from step 2,
downloads metadata and then tracker data, and renders the plugin's real entry point inside a
reproduction of the host's Koin container. The first run takes several minutes; every step is named
on screen, so a slow run is distinguishable from a stuck one. Afterwards the database is on the
device and startup is immediate.

This step is optional but much faster to iterate on than the full host. It is also not the Capture
App: see *Development harness* in [`CLAUDE.md`](CLAUDE.md) for the list of things only the real host
can tell you.

### 5. Serve the bundle

```bash
cd plugin/build/outputs/plugin-bundle && python3 -m http.server 8081
```

**8081, not 8080.** A local DHIS2 instance usually owns 8080 and would answer with its login
redirect instead of the zip, which on the device looks exactly like the plugin silently not loading.

### 6. Post the config to the server dataStore

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

### 7. Install the Capture App and log in

In your **Capture App** checkout:

```bash
./gradlew :app:installDhis2Debug
```

Log in against the same server. Plugins load when the home screen opens, and the card renders above
the programme list.

## Troubleshooting

**`Plugin [id: 'org.dhis2.mobile.plugin-bundle'] was not found`** — step 1 has not run, or ran in
the wrong checkout. If it did run, the copy in `~/.m2` may predate an API change: republish.

**`build-tools 36.1.0 is not installed`** — install it (see *Prerequisites*). Raising the pin in
`plugin/build.gradle.kts` is fine, but it moves the bundle's checksum and every machine building
this then needs the version you raised it to.

**Code changes are not showing on the device** — the Capture App caches bundles by
`{id}-{version}.zip`, so rebuilding at the same version reuses the old cache. Bump `version` in
`plugin/build.gradle.kts`, re-run `./verify.sh`, and post the new config.

**Checksum mismatch, or a bundle that will not verify** — bundles are signed with your local debug
key, so only the bundle *you* built matches the checksum *you* posted. A bundle built on another
machine will not match, even with the same build-tools: the signature block carries the signer's
certificate. A real publisher signs with their own key through `pluginBundle { signing { … } }`.

**The card says the programme was not found** — the harness and the plugin are looking at different
programmes; see step 2.

## Where to go next

- [`CLAUDE.md`](CLAUDE.md) — the working reference: the three layers and their rules, the build
  constraints that each cost real debugging, the DHIS2 design system, the on-device checklist, and
  the backlog.
- [`specs/README.md`](specs/README.md) and
  [`.claude/skills/plugin-from-spec`](.claude/skills/plugin-from-spec/SKILL.md) — features here start
  as a Given/When/Then spec in `specs/` and are built by an agent following that skill. Start from
  [`specs/TEMPLATE.md`](specs/TEMPLATE.md).
