# AGENTS.md — DHIS2 Android plugin template

**This is the working reference for this repository, whichever agent or person is reading it.**
`CLAUDE.md`, `.cursorrules` and `.github/copilot-instructions.md` are one-line pointers here; there
is one copy, so there is nothing to drift.

A **template** for building plugins for the DHIS2 Android Capture App. A plugin is a small Android
library implementing `Dhis2Plugin`, packaged as a **signed zip bundle**, rendered inside the Capture
App from a server-side configuration. Fork this, run `./init.sh`, and write specs.

Everything needed to build, test and package a plugin is here, with one exception: `plugin-sdk` and
`plugin-sdk-gradle` are not published, so they have to be built into Maven Local from a Capture App
checkout — branch `poc/plugin-system` — before this project will even *configure*. See *Local
testing flow* below, and `README.md` for the install path.

## Start here

**If `./init.sh --check` says "pristine template", the repository has not been initialised.** That
comes first, before any feature work: follow `docs/workflows/initialise-plugin.md`. It settles the
plugin's name, package, id and entry point, rewrites the tree, and ends with a green `./verify.sh`.

Then:

1. **`plugin.json` is the single source of truth for this plugin's identity.** The Gradle build
   reads it in `settings.gradle.kts`; `tools/check-rules.py` and `tools/check-identity.py` read it
   through `tools/identity.py`. **Do not repeat the package, the id or the entry point anywhere
   else**, and do not hand-edit *those*: `./init.sh --force` renames a fork.
   `tools/check-identity.py` fails the build when the file and the tree disagree, which matters
   because nothing else can see that failure — the build stays green, the bundle is signed, and the
   host fails at load with `ClassNotFoundException`.

   `injectionPoints` and `slotConfig` are the opposite case and **are** yours to edit. Nothing in
   the tree mirrors them, so nothing can disagree with them; they are how you say which host slot
   this plugin renders in, and `./init.sh` carries whatever they say through the rename rather than
   overwriting it. See *Host slots*.
2. **Specs live in `specs/`.** One file per feature, Given/When/Then. `specs/README.md` defines the
   format, `specs/TEMPLATE.md` is the skeleton, and `specs/first-card.md` and
   `specs/data-set-body.md` are the seed's own — one per host slot, both meant to be replaced.
3. **Build a feature from a spec** by following `docs/workflows/plugin-from-spec.md`. It restates
   the spec and stops for approval before writing code, then goes red → green → verified. It is
   plain prose: follow it by hand, or hand it to any agent. In Claude Code it is also
   `/plugin-from-spec`.
4. **`./verify.sh` is the definition of done.** The spec ↔ test gate, the identity gate, the rule
   gates, unit tests, the signed bundle, a check on the bundle's zip layout, and the ready-to-post
   dataStore config. The spec gate is what makes "a spec is the contract" true rather than
   aspirational: every `@L*` scenario must be claimed by a test comment `spec: <slug> <id>`, and
   `tools/check-specs.py` fails the run when one is not. See `specs/README.md` for the convention.
   `--cold` re-resolves every
   dependency from a fresh Gradle home, which catches stale local state — it keeps Maven Local,
   because that is where `plugin-sdk` lives, and it is not a clean-machine check. `verify.sh` says
   so itself; there is no clean-machine check here.
5. **Work happens on a branch, never on the default one.** The pipeline cuts `spec/<slug>` from the
   spec's filename before it edits anything, and commits the implementation only after you have
   reviewed and tried the result — including the device checklist, which is the half no test covers.
   The PR it opens is a draft, so that checklist is evidence a reviewer sees rather than takes on
   trust.
6. **Answering its questions changes the spec, not just the chat.** Whatever gets settled at the
   approval gate is folded back into `specs/<file>.md` and committed first, on its own. The test is
   that re-running the pipeline on the committed spec asks nothing new — a spec that only works
   alongside the conversation that produced it is not finished.

What no automated check here can cover: any read or write against DHIS2. A JVM unit test cannot
construct a `D2` — it needs an Android `Context`, a database and an HTTP stack — so those live under
`## Device scenarios` in a spec and are walked by hand. Keeping SDK access behind the repository
interface is what keeps everything else automatable.

To walk them quickly, `./gradlew :app:installDebug` runs the plugin against a real server with real
data — see *Development harness* below, including the shorter list of things only the Capture App
can tell you.

## Layout

```
your-plugin/
├── plugin.json   # Identity: name, package, plugin id, entry point, version, conventions.
│                 # The build and the gates both read it. Nothing else repeats it.
├── init.sh       # One-time setup of a fork. Wraps tools/init-plugin.py.
├── verify.sh     # The definition of done: every gate + tests + the signed bundle.
├── specs/        # Feature specifications — the input to docs/workflows/plugin-from-spec.md
├── docs/workflows/   # initialise-plugin.md, plugin-from-spec.md. Plain prose, any agent.
├── tools/
│   ├── identity.py        # Reads plugin.json. Shared by every gate below.
│   ├── check-identity.py  # Does the tree agree with plugin.json? Also init's state detector.
│   ├── check-specs.py     # Every @L scenario claimed by a test.
│   └── check-rules.py     # This plugin's own architecture rules, from plugin.json's conventions.
├── app/          # Android application — dev-only harness against a real server. Never shipped.
│   ├── src/main/java/org/dhis2/mobile/plugin/harness/
│   │   ├── MainActivity.kt   # loads the entry point by FQCN, exactly as the host does
│   │   ├── HarnessSession.kt # D2 + login + metadata + slot resolution, each step named on screen
│   │   ├── HarnessSlot.kt    # which slot, and its arguments — from plugin.json, not a constant
│   │   ├── HarnessPluginContext.kt   # a real Dhis2PluginContext, all of it from BuildConfig
│   │   ├── HarnessPluginHost.kt      # PluginHost — reproduces the host's private Koin container
│   │   └── ui/theme/         # Studio template theme
│   └── src/test/             # the harness's own pure logic — slot choice, and nothing past it
│             # Its Kotlin package is frozen and template-owned; only applicationId follows the
│             # fork, so two forks' harnesses coexist on one device. Uses CMP 1.10.3 (the same
│             # Compose version as the plugin modules and the Capture App). A stagePluginAssets
│             # task copies the hosted module's composeResources into :app's assets at build time.
└── plugin/       # YOUR plugin. Kotlin Multiplatform + android.kotlin.multiplatform.library + CMP.
    ├── src/commonMain/kotlin/…/
    │   ├── model/        # Plain data, no SDK types
    │   ├── repository/   # The repository interface — the seam the whole design rests on
    │   └── ui/           # PluginUiState, PluginViewModel, PluginCard
    ├── src/commonTest/       # ViewModel/UI tests against a fake repository — JVM, no device
    ├── src/androidHostTest/  # Tests of androidMain's top-level mapping and error translation,
    │                         # built from real SDK values. Also JVM — see Architecture.
    └── src/androidMain/kotlin/…/
        ├── <EntryPoint>.kt   # entry point: provideKoinModule + content + slotFor, nothing else
        ├── slots/            # one file per host slot — the default you land on at each
        ├── ui/Previews.kt    # @Previews of the card, beside the card
        └── data/             # D2PluginRepository — the only file that sees the SDK
```

Both of `:plugin`'s test source sets run under one Gradle task, `:plugin:testAndroidHostTest`; the
harness's own run under `:app:testDebugUnitTest`. `./verify.sh` runs both.

Only `:plugin`'s output is shipped. `:app` is the development harness and is not.

**What `:plugin` contains today is a seed** — one small working default per host slot, so that
whichever slot your `plugin.json` declares, you land on something that renders and reads. It exists
so `./verify.sh` has something real to check and so the three layers are visible rather than
described. Replace it; it is meant to be deleted.

**One plugin per fork.** There is one `plugin.json`, one `:plugin`, one set of specs. `MainActivity`
still loads the entry point reflectively, by the name `plugin.json` gives — that reflective load is
the only check of the entry-point contract that does not need a device, and it is worth keeping for
that alone.

**Experiments go on a branch.** An experiment against a *different* `plugin-sdk` — `poc/scoped-sdk`
here, which targets the Capture App's `poc/plugin-system-scopedSDK` — cannot be a module in this
build at all. Be aware of what that costs: `poc/scoped-sdk` renamed its package by hand and quietly
lost `specs/`, `tools/` and `verify.sh` doing it. Rebase such a branch onto `main` rather than
letting it drift.

## Architecture

Three layers, and no more than three. The Capture App itself also has a use-case layer; this project
deliberately does not — a plugin is small enough that a use case per action would be a file that only
forwards a call, and the point of a template is to be read start to finish by someone who has never
seen it.

```
UiState  ←  ViewModel  ←  PluginRepository (interface)  ←  D2PluginRepository
commonMain   commonMain      commonMain                        androidMain
```

- **UiState** — a `sealed interface` per concern, holding plain data. No SDK types.
- **ViewModel** — exposes `StateFlow<PluginUiState>`, calls the repository, maps failures into
  state. Never touches `D2`.
- **Repository interface** — the plugin's own vocabulary, returning `Result` of plain models.
- **D2PluginRepository** — the *only* place `D2` appears, and `plugin.json`'s
  `conventions.sdkAllowed` is what says so; `tools/check-rules.py` fails when another file reaches
  for the SDK. Labelling a tracked entity is **not** its job: `plugin-sdk`'s `TrackedEntityLabeller` owns that rule, because a tracked entity's attribute
  values arrive in no order and the programme's `displayInList` configuration is what decides which
  ones make a name. Every plugin rendering a person needs it, so none should re-derive it — and no
  grep could ever check that they got it right, which is exactly why it is a function and not a rule. Its mapping and its error translation are
  top-level functions so they can be tested without a `D2`: see `plugin/src/androidHostTest/`, which
  builds real SDK values through their builders rather than mocking a fluent chain seven links deep.
  What stays untested is the query itself. Moves blocking calls off the main thread
  and translates `D2Error` into a message worth showing.

**Why the interface earns its keep.** It is the seam that lets the ViewModel and UI be unit-tested on
the JVM against a fake. It also keeps the SDK surface in one file, which matters because the next
iteration of this PoC narrows that access — and one file is a far smaller thing to change than a
plugin scattered with `d2.` calls.

**Rules.** Each is marked by what enforces it: **[build]** when `plugin-sdk-gradle`'s
`checkPluginConventions` does — so every plugin project inherits it and a fork cannot let it rot —
**[checked]** when this repo's own `tools/check-rules.py` does, and **[prose]** when nothing does. A
prose rule is a rule you have to remember; three of them had quietly stopped being true before
anything was looking, which is why the distinction is written down rather than assumed.

1. **[build]** Put it in `commonMain` unless it needs a platform API. In practice only the entry
   point, `D2PluginRepository` and the `@Preview`s belong in `androidMain` — `D2` is the Android SDK
   and `@Preview` is an Android annotation.
2. **[build]** Composables take plain data and callbacks — never a `Dhis2PluginContext`. That is what lets
   `@Preview` render the real UI without a server. The harness no longer needs this — it builds a
   real context against a real `D2` (see *Development harness*) — but a `@Preview` still does, and
   it is the faster loop for pure UI work.
3. **[checked]** **Stay short — at an additive slot.** At `HOME_ABOVE_PROGRAM_LIST` the host renders
   the plugin in a non-scrolling `Column` above its own program list, so height taken there is height
   taken from the host and anything past the viewport is unreachable. `PluginCard` caps itself with
   `heightIn(max = …)` + `verticalScroll`, and `conventions.boundedComposables` is what checks it.
   **A replacement slot is the opposite** — it owns the region it was given, so filling it is correct
   and scrolling is the plugin's job. `DataSetBodyPlaceholder` deliberately caps nothing and is
   deliberately absent from that list. See *Host slots*.
4. **[checked]** A repository returns `Result`, never throws. An exception escaping into the host composition takes
   the enclosing screen with it, and Compose cannot express an error boundary around a composable
   call. This means catching `Throwable`, not just `D2Error` — see `io()` and `catchingD2` in
   `D2PluginRepository.kt` (`io()` is private; `catchingD2` is the top-level one the tests reach).
   A repository that only catches the SDK's own error type still lets an unexpected null while
   mapping a result reach the host.
5. **[build, in part]** **Count in SQL; materialise only what you show.** `blockingCount()` is a
   `COUNT(*)`; `blockingGet()` materialises rows, and `.one()` is a `LIMIT 1`. Enriching with
   `.with…()` and then capping with `take(` is the one shape a grep can settle, and the build's
   `cap-before-enrichment` rule settles it; the rest is judgement. The SDK has no synchronous row
   limit, so `take(n)` after a `blockingGet` is as good as it gets for the rows.
6. **[prose]** `D2Error` carries no `message`. It is `data class D2Error(…) : Exception()` and passes nothing to
   the `Exception` constructor, so `Throwable.message` is **always null** — read `errorCode()` and
   `errorDescription()`, or every failure renders as the bare word "D2Error".

## Commands

```bash
./init.sh                              # one-time setup of a fork. --check, --dry-run, --force
./verify.sh                            # every gate + tests + bundle — the definition of done
./verify.sh --cold                     # same, from a fresh Gradle home (Maven Local kept)

python3 tools/check-identity.py        # does the tree agree with plugin.json? (fast)
python3 tools/check-specs.py           # the spec ↔ test gate alone (fast)
python3 tools/check-rules.py           # this plugin's own rules, marked [checked] below (fast)
./gradlew :plugin:checkPluginConventions   # the plugin system's rules, marked [build] below
./gradlew checkHostAlignment           # Compose + DHIS2 SDK match what the host provides

./gradlew :plugin:buildPluginBundle    # signed zip → plugin/build/outputs/plugin-bundle/
./gradlew :plugin:testAndroidHostTest  # unit tests — commonTest AND androidHostTest, JVM, no device
./gradlew :app:installDebug            # harness against a real server, on emulator
```

`./verify.sh` runs all of the gates in one command, which is the point of it: a list in prose can be
half-skipped, and "done" then means something different in every session.

## Rules (read before editing `plugin/build.gradle.kts`)

Same marking as above. Note rule 1 is now checked by reading the real source-set
configurations rather than by regexing this file, so a version-catalog alias or a convention plugin
cannot slip past it.

1. **[build] `compileOnly` everything host-provided, except `compose.components.resources`.**
   The Capture App provides Compose/Material3/plugin-sdk at runtime via
   `InMemoryDexClassLoader`'s parent delegation — bundling them causes DEX bloat
   and `ClassCastException`. **But** `compose.components.resources` must be
   `implementation` — it's the CMP plugin's opt-in signal to generate the `Res`
   accessor class. Swap it to `compileOnly` and `Res.*` imports stop resolving.
2. **[enforced by the build — it fails at resolution]** **The plugin compiles against the DHIS2 SDK, and `settings.gradle.kts` needs two extra
   repositories for it.** `Dhis2PluginContext.sdk` is `D2`, so the plugin-bundle Gradle plugin
   injects `org.hisp.dhis:android-core` at the host's version — never declare it yourself. It pulls
   `com.github.dhis2:sms-compression` from JitPack, and the host usually tracks SDK snapshots, so
   both JitPack and the snapshots repo must be in `dependencyResolutionManagement`. Without them the
   build fails at dependency *resolution*, with an error that never mentions the DHIS2 SDK.
3. **[checked]** **Matching `composeMultiplatform` is not enough.** The host declares CMP *and* androidx
   `compose` separately, depending on the latter directly and at a higher version — 1.10.6 against
   the 1.10.5 CMP resolves. Almost everything is identical, which is the trap: the first casualty is
   a *defaulted* overload whose `…$default` synthetic moved. `Modifier.weight(1f)` crashed the host
   with `NoSuchMethodError: weight$default` at composition.

   The root `build.gradle.kts` now forces `androidx.compose.{animation,foundation,runtime,ui}` to
   `libs.versions.androidxCompose` in **every** module, harness included, and
   `./gradlew checkHostAlignment` reads the resolved versions back and fails when the force stops
   applying. `material3` is deliberately excluded: androidx's sub-groups do not share one version
   line — material3 is on 1.4.x — so a force across `androidx.compose.*` would be wrong.

   What is still on you: `androidxCompose` in the version catalogue is a **hand-mirrored host fact**,
   the last one left. `HostToolchain` publishes Kotlin, the CMP version, compileSdk, jvmTarget,
   `plugin-sdk` and the DHIS2 SDK — but not this. Raising the host's `compose` means raising it here
   too, and nothing will tell you. Preferring layout APIs without default arguments is still the
   cheaper habit.
4. **[enforced by the build — AGP 9 refuses the mix]** **Use `kotlin.multiplatform` + `com.android.kotlin.multiplatform.library`,
   not `com.android.library`.** AGP 9 disallows mixing plain Android library
   with KMP.
5. **[prose]** **Set `compose.resources { packageOfResClass = "…" }` explicitly.** Without
   it CMP derives the package from the root project name (which has spaces →
   backtick-escaped imports).
6. **[prose]** **The plugin declares no identity.** Its id, version, entry point and injection points all live
   in the server dataStore config. `pluginBundle { pluginId; entryPoint }` only fills in the
   generated `plugin-config.json` for convenience — it reaches neither the bundle nor the host.
7. **[prose]** **A bundle is byte-identical only with the same `build-tools` *and* the same signing key.**
   `plugin/build.gradle.kts` pins `d8Executable`/`apksignerExecutable`, because the bundle plugin
   otherwise takes the newest installed and a different `d8` emits different DEX bytes. With that
   pinned, two machines produce an identical `classes.dex`, `MANIFEST.MF` and `.SF` — measured,
   not assumed.

   What still differs is `META-INF/*.RSA`, the signature block, because it carries the signer's
   certificate and each machine has its own debug key. That is inherent: a signed artefact's bytes
   depend on the key, and no pin changes it. So **a bundle built elsewhere will not match yours**,
   and a dataStore entry has to use the checksum of the bundle you actually serve. Same key plus
   same build-tools does reproduce exactly.

   Raising the pin is fine; expect the checksum to move, and every machine that builds this needs
   the `build-tools` version you raise it to.
8. **[prose]** **Bump the version to invalidate the device cache.** That is the Gradle `version` assignment near
   the top of `plugin/build.gradle.kts` — there is no `pluginVersion` property, and `pluginBundle { }`
   does not carry one. The Capture App caches by `{id}-{version}.zip`; rebuilding at the same version
   reuses the old cache. Symptom: "my code changes aren't showing."

## Design system

**Adopted.** `org.hisp.dhis.mobile:designsystem` is declared `compileOnly` in the plugin modules and
as a real dependency in the harness, the seed's `PluginCard` draws every colour, space, corner and
text style from it, and the harness wraps the plugin in `DHIS2Theme` exactly as the host does.

A plugin should look like the app it renders inside. The Capture App carries
`org.hisp.dhis.mobile:designsystem` on its runtime classpath, so declare it **`compileOnly`** and the
real components arrive from the host's class loader — the same arrangement as Compose, for the same
reason (rule 1 below).

- Guide: <https://developers.dhis2.org/docs/mobile/mobile-ui/overview>
- API reference: <https://dhis2.github.io/dhis2-mobile-ui/api/-mobile%20-u-i/org.hisp.dhis.mobile.ui.designsystem.component/index.html>

Declared in `commonMain` — it is a Compose Multiplatform library, so it belongs beside the other
`compose.*` entries rather than in `androidMain`:

```kotlin
compileOnly(libs.dhis2.mobile.designsystem)   // designSystem in the version catalogue
```

It resolves from the repositories already in `settings.gradle.kts` (the snapshots repo is what
serves it), and Gradle selects the `-android` variant automatically. Unlike `plugin-sdk` and
`android-core`, the bundle plugin does **not** pin this version for you — matching it to the host is
the author's job, which is what makes the skew warning below worth reading.

Consult the API reference when choosing a component rather than reaching for Material 3 directly —
it documents what exists (`Button`, `InputDateTime`, `InfoBar`, `ButtonStyle`, and the rest of
`org.hisp.dhis.mobile.ui.designsystem.component`).

Same skew caution as rule 3: the design system is on a snapshot, so a plugin compiled against an
older copy than the host ships can still meet `NoSuchMethodError` at composition. Prefer components
without defaulted parameters where there is a choice.

## Resources

```
plugin/src/commonMain/composeResources/
├── values/strings.xml           # default (English)
├── values-es/strings.xml        # Spanish (add more as values-{locale}/)
└── drawable/plugin_icon.xml
```

Access from code:

```kotlin
import <your.package>.generated.resources.Res
import <your.package>.generated.resources.plugin_loading
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

Text(stringResource(Res.string.plugin_loading))
Image(painter = painterResource(Res.drawable.plugin_icon), contentDescription = null)
```

Runtime resolution differs by host:

- **Capture App.** `PluginLoader` extracts the zip; `PluginSlot` provides a
  per-plugin `FileSystemResourceReader` via
  `CompositionLocalProvider(LocalResourceReader …)`. AssetManager is not
  involved.
- **Harness.** The `stagePluginAssets` task copies resources into
  `:app/build/generated/plugin-assets/composeResources/{package}/…` and
  registers the directory via AGP 9's Variant Sources API. CMP's default
  Android reader then finds them via `context.assets.open(…)`.

## Host slots

A slot is a place in the Capture App where a plugin's UI is rendered. `plugin-sdk` defines two, and
they behave in opposite ways — most of what is true of one is false of the other.

| | `HOME_ABOVE_PROGRAM_LIST` | `DATA_SET_INSTANCE_CONTENT` |
|---|---|---|
| Kind | Additive | Replacement |
| Where | Home screen, above the programme list | The body of the data set instance screen |
| Who else renders | Every registered plugin, one after another | Exactly one plugin wins |
| Height | The host's, and it does not scroll — **cap yours** | Yours; **fill it, and scroll inside it** |
| `LocalSlotContentPadding` | Zero; nothing floats over it | The save button's; **apply it** |
| `LocalSlotArguments` | `null` — the slot is the whole screen | `DataSetInstanceSlotArguments` |
| Needs configuration | No | Yes — `slotConfig.DATA_SET_INSTANCE_CONTENT.dataSetUids` |

**Where a slot is declared.** `plugin.json`'s `injectionPoints`, and for a replacement,
`slotConfig`:

```json
"injectionPoints": ["DATA_SET_INSTANCE_CONTENT"],
"slotConfig": {
  "DATA_SET_INSTANCE_CONTENT": {
    "dataSetUids": ["BfMAe6Itzgt"]
  }
}
```

That is the whole change — no Kotlin, nothing in `local.properties`. Rebuild and the harness renders
that slot, resolving the period, organisation unit and attribute option combo itself. Declare both
slots and it picks the replacement.

Both fields reach the generated `plugin-config.json`, which an administrator posts to the server
dataStore — and the dataStore is what the host actually reads. The plugin's *Kotlin* declares none
of it (see the entry-point contract), and neither file may be a second copy of the other.

**An empty `dataSetUids` is a kill switch, not a bug.** A replacement renders nowhere until it says
which objects it applies to, so emptying the list switches the plugin off without deleting its
dataStore entry. `tools/check-identity.py` says so out loud rather than failing, because on a device
the symptom — the host's own screen, unchanged — looks exactly like the plugin failing to load.

**Where a slot is *rendered*.** One file each, under `plugin/src/androidMain/kotlin/…/slots/`.
`MyPlugin.content()` reads `LocalSlotArguments`, passes it to `slotFor`, and delegates. Adding a
slot is adding a file; a slot your plugin does not declare is an unused file you can delete once you
are sure, not a branch you have to read past. Those files take a `Dhis2PluginContext`, which
architecture rule 2 forbids for composables — they are the entry point's own layer split up, not UI,
and the composables *they* call still obey the rule.

**Which slot the harness renders.** Not a constant anyone edits: `HarnessSlot.kt` derives it from
the same `plugin.json` the dataStore config came from, so the two cannot disagree. The rule is the
most specific slot the plugin could actually be rendered at — a replacement wins when it is declared
*and* configured, because an unconfigured one replaces nothing. A fork that declares both and wants
to look at the other one edits `plugin.json`, which is the same edit that would change what a device
renders.

**What the harness resolves for you.** A replacement needs four identifiers, and the harness finds
them from the data set UID `plugin.json` names — preferring a data set instance that really exists,
and otherwise assembling the coordinates from metadata. Every way that can fail is a named failure
on screen naming the UID and what to change, because an empty screen and a broken plugin look
identical.

## Development harness

`:app` signs in to a real DHIS2 and renders the real plugin against real data — no hand-written
samples. Configure it in `local.properties`, which is gitignored and never committed:

```properties
sdk.dir=<your Android SDK>               # required by :plugin's build-tools pin, not just by AGP
dhis2.serverUrl=<your server>
dhis2.username=<your username>
dhis2.password=<your password>
```

Use a development server: the harness logs in as a real user and syncs a real database onto the
device, which is not something to point at production.

Then `./gradlew :app:installDebug`. It instantiates `D2`, logs in, downloads metadata, and resolves
the slot to render — and it downloads **metadata only**. A plugin that reads rows of data will see
none until you add the download it needs; the harness deliberately does not guess which that is.

That first run takes minutes; afterwards the database is on the device and startup is immediate.
Every step is named on screen, so a slow run is distinguishable from a stuck one.

It renders the entry point's `content()` itself, not just the card, by reproducing the host's private
Koin container (`PluginHost`) — a harness whose DI differs from the host's proves the wrong thing.
And it *finds* that entry point the way the host does, with `Class.forName` on the FQCN from
`plugin.json`, so a class at the wrong name or without a public no-arg constructor fails here rather
than on a device.

**What it cannot tell you.** It is not the Capture App, and these need the real host:

- the additive slot's non-scrolling column and its height budget
- the class-loader reload and its `ClassCastException`
- Compose resource resolution through `FileSystemResourceReader`
- that plugin bindings cannot leak into the host's container
- ~~androidx Compose version skew~~ — largely covered now: the harness is forced to the same
  androidx Compose the host provides (build rule 3), so a `NoSuchMethodError` from that skew
  reproduces here. What it still cannot cover is a host that has moved on without this repo noticing

So the harness shrinks the device checklist; it does not empty it.

## Local testing flow

1. **Publish the plugin API to Maven Local first.** It is not on Maven Central yet, and this
   project will not even configure without it — the `id("org.dhis2.mobile.plugin-bundle")` line
   resolves from there. In a checkout of the Capture App on the branch carrying the plugin system:

   ```bash
   ./gradlew :plugin-sdk:publishToMavenLocal :plugin-sdk-gradle:publishToMavenLocal
   ```

   Both, always: the Gradle plugin is what pulls in the matching `plugin-sdk`, and a stale
   `plugin-sdk-gradle` is invisible from this side — it surfaces as an unrelated
   dependency-resolution error. Republish after any change to the plugin API, and remember a
   changed API under an unchanged version leaves a stale copy in `~/.m2`.
2. `./verify.sh`, or `./gradlew :plugin:buildPluginBundle` directly. `plugin-config.json` beside the bundle is
   the dataStore entry with `version`, `checksum`, `id` and `entryPoint` already filled
   in — the last two come from `pluginBundle { }` in `plugin/build.gradle.kts`.
3. `cd plugin/build/outputs/plugin-bundle && python3 -m http.server 8081`.
   Not 8080: a local DHIS2 instance usually owns it and answers with its login redirect
   instead of the bundle, which reads on device as the plugin silently not loading.
4. Post that JSON to the DHIS2 server dataStore (`dhis2AndroidPlugins/config`) — POST
   creates the key, PUT updates it afterwards. Its `downloadUrl` is a guess the Gradle
   plugin writes, so point it at wherever you served the zip; note the bundle is named
   `plugin-{version}.zip` from the Gradle module, not from the config's `id`. The
   dataStore is the only source of plugin config; there is no in-app fallback. There is no data-scope field to set — the plugin gets the
   SDK unrestricted, so the config only names *which* code to run.
5. Install the Capture App and log in: `./gradlew :app:installDhis2Debug` in that checkout, which
   installs as `com.dhis2.debug`. Plugins load when the home screen opens.

For UI work without a server at all, the `@Preview`s in `MainActivity` render `PluginCard` against
sample state. For the plugin against real data, `./gradlew :app:installDebug` (see *Development
harness*).

## Entry-point contract

The entry point — the class `plugin.json` names — must:

- Implement `org.dhis2.mobile.plugin.sdk.Dhis2Plugin`.
- Live in `src/androidMain`, because `Dhis2PluginContext.sdk` is `D2` — the DHIS2 *Android* SDK.
- Live at the FQCN the dataStore config names as `entryPoint`. The plugin's *Kotlin* declares none
  of it; `plugin.json` is what fills that field in the generated `plugin-config.json`, and
  `tools/check-identity.py` checks the class is really there.
- Have a public no-arg constructor — the host instantiates via reflection.

## Backlog

- **A way to pull template improvements into an existing fork.** `git remote add template …` plus a
  documented merge, probably. Only becomes a real need once someone has forked and the template has
  moved on, but that is exactly when it is too late to design.
- **Teach `plugin-sdk-gradle` to verify the entry point.** `plugin.json` drives
  `pluginBundle.entryPoint`, and nothing in Gradle checks the class exists; `tools/check-identity.py`
  covers this repository only. `BuildPluginBundleTask` already has a `ClassesJarInspector`, so it
  could assert the entry point is present in the DEX for *every* plugin project.
- **`LocalHostRefresh` is defined and unused.** `plugin-sdk` provides it; nothing here provides or
  consumes it. A replacement slot that *writes* values has no way to tell the host to re-read before
  its save button validates, so this becomes a real gap the moment a plugin does more than display.
- **Reach for design-system *components*, not only its tokens.** The seed uses `SurfaceColor`,
  `TextColor`, `Spacing` and `Radius` with a plain Material 3 `Card`. `BaseCard`, `ListCard`,
  `Button`, `Badge` and `InfoBar` exist in `…designsystem.component` and would be closer to the
  host still; `BaseCard` takes nine parameters and several defaults, so it is worth doing
  deliberately rather than by reflex (build rule 3).
- **Move the last two local rules upstream, or accept that they stay local.** `tools/check-rules.py`
  still checks that `PluginRepository` returns `Result` and that `PluginCard` bounds its height. Both
  name shapes this template chose, so upstream could only find them by growing an interface for
  plugin authors to implement — a real architectural imposition the plugin system does not currently
  make. Probably the right answer is that they stay here; worth revisiting if a second plugin
  repeats them.

- **Publish `plugin-sdk` and `plugin-sdk-gradle` to Maven Central.** Until then every developer has
  to build them from a Capture App checkout into their own Maven Local, which is the single biggest
  obstacle to someone forking this and getting anywhere — and the reason CI here runs only the
  Python gates: a runner cannot do the Gradle half. Publishing these is what would let CI build the
  bundle and run the tests, which is the difference between a contract that is enforced and one that
  is hoped for.
- **Get the DHIS2 SDK out of this template's build files entirely.** A plugin project should declare
  one DHIS2 dependency, `plugin-sdk`, and nothing else. Two changes in the Capture App, then one
  here:

  1. ~~`AndroidPluginWiring` should extend the test runtime classpath from `androidMain`'s
     `compileOnly`.~~ **Done** — `AndroidPluginWiring.wireHostTestRuntime` does it, from both
     `commonMain`'s and `androidMain`'s `compileOnly`, and `plugin/build.gradle.kts` no longer
     carries the workaround. `commonMain` mattered as much: `plugin-sdk` is declared there, which is
     what made a JVM test touching a plugin-sdk type die with `NoClassDefFoundError`.
  2. A `plugin-sdk-test` artefact with `api(android-core)`, for `:app` and test source sets — never
     for `:plugin`, which must keep the SDK `compileOnly` or the bundle inspector will flag it, as it
     already flags `koin-core` for being `api` in `plugin-sdk`.
  3. Then `:app` depends on `plugin-sdk-test` instead of `android-core`, and `dhis2AndroidCore`
     disappears from `gradle/libs.versions.toml`. The SDK version then lives in exactly one place —
     the Capture App's own catalog, reaching here through `HostToolchain`.

  Worth keeping two promises separate when designing `plugin-sdk-test`: putting SDK classes on a JVM
  test classpath (what the tests in `androidHostTest` need) is not the same as *initialising a real
  `D2`*, which needs an Android `Context` and a database and so only serves instrumented tests.
- Extract `buildPluginBundle` into a published Gradle plugin.
- Publish a `plugin-sdk-test` artefact. A *unit test* still has no way to construct a `D2` — it needs
  an Android `Context`, a database and an HTTP stack — so `commonTest` remains fake-repository
  territory. What changed is that an application module can: see `HarnessPluginContext`. An
  instrumented test in this repo could now drive a real `D2` against a test server, which is the
  route to shrinking the `## Device scenarios` half of every spec.
- Narrow the plugin's SDK access. This iteration hands over `D2` unrestricted; the next one restricts
  it to a server-declared subset, enforced inside the SDK rather than by the host.
- **Publish the host's androidx Compose version in `HostToolchain`.** The force and the gate exist
  here now (build rule 3), but the *number* is still copied by hand into
  `libs.versions.androidxCompose`, so a host that raises `compose` leaves every plugin silently
  compiling against the wrong one. `HostToolchain` already carries `KOTLIN`, `COMPOSE` (the CMP
  plugin version — not this one), `COMPILE_SDK`, `JVM_TARGET`, `PLUGIN_SDK_VERSION` and
  `DHIS2_SDK_VERSION`; one more constant closes it for every plugin project rather than this one.
  Note the sub-groups do not share a version line (`material3` is on 1.4.x while
  `ui`/`foundation`/`runtime`/`animation` are on 1.10.x), so it must be a specific value, not a
  group-wide force.

  **`HostToolchain` is `internal`**, so today a plugin author's build cannot read *any* of it — not
  the Compose version, not the SDK version. Making it public API, or surfacing it as a property on
  the `pluginBundle` extension, is the single change that would let every plugin project stop
  hand-mirroring host facts. `./gradlew checkHostAlignment` here is a workaround for its absence:
  it compares the harness against `:plugin`'s injected resolution, which is the host's answer
  arriving by the only route currently open.
- Add a `jvm("desktop")` target and a `desktop/plugin.jar` bundle subdir once
  a Desktop host exists.
- Per-publisher cert allow-list in the Capture App's `PluginVerifier`.
