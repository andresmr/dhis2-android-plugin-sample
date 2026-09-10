# CLAUDE.md — DHIS2 Android plugin sample

Produces a **signed zip bundle** plugin for the DHIS2 Android Capture App. Everything needed to
build, test and package a plugin is here, with one exception: `plugin-sdk` and `plugin-sdk-gradle`
are not published, so they have to be built into Maven Local from a Capture App checkout before this
project will configure. See *Local testing flow* below, and `README.md` for the install path.

## Start here

1. **Specs live in `specs/`.** One file per feature, Given/When/Then. `specs/README.md` defines the
   format; `specs/example-program-summary.md` is a complete worked example describing the plugin in
   this repo today.
2. **Build a feature from a spec** with `/plugin-from-spec specs/<file>.md`. It restates the spec and
   stops for approval before writing code, then goes red → green → verified.
3. **`./verify.sh` is the definition of done.** The spec ↔ test gate, unit tests, signed bundle, a
   check on the bundle's zip layout, and the ready-to-post dataStore config. The gate is what makes
   "a spec is the contract" true rather than aspirational: every `@L*` scenario must be claimed by a
   test comment `spec: <slug> <id>`, and `tools/check-specs.py` fails the run when one is not. See
   `specs/README.md` for the convention. `--cold` additionally re-resolves every
   dependency from a fresh Gradle home, which catches stale local state — it keeps Maven Local,
   because that is where `plugin-sdk` lives, and it is not a clean-machine check. `verify.sh` says
   so itself; there is no clean-machine check here.
4. **Work happens on a branch, never on the default one.** The pipeline cuts `spec/<slug>` from the
   spec's filename before it edits anything, and commits the implementation only after you have
   reviewed and tried the result — including the device checklist, which is the half no test covers.
   The PR it opens is a draft, so that checklist is evidence a reviewer sees rather than takes on
   trust.
5. **Answering its questions changes the spec, not just the chat.** Whatever gets settled at the
   approval gate is folded back into `specs/<file>.md` and committed first, on its own. The test is
   that re-running the pipeline on the committed spec asks nothing new — a spec that only works
   alongside the conversation that produced it is not finished.

What no automated check here can cover: any read or write against DHIS2. A JVM unit test cannot
construct a `D2` — it needs an Android `Context`, a database and an HTTP stack — so those live under
`## Device scenarios` in a spec and are walked by hand. Keeping SDK access behind `PluginRepository`
is what keeps everything else automatable.

To walk them quickly, `./gradlew :app:installDebug` runs the plugin against a real server with real
data — see *Development harness* below, including the shorter list of things only the Capture App
can tell you.

## Layout

```
dhis2-android-plugin-sample/
├── specs/    # Feature specifications — the input to /plugin-from-spec
├── verify.sh # The definition of done
├── app/      # Android application — dev-only harness against a real server.
│   └── src/main/java/…/
│       ├── MainActivity.kt   # renders the plugin's entry point; also holds the @Previews
│       ├── harness/          # HarnessSession (D2 + login + sync), HarnessPluginContext,
│       │                     # PluginHost (in HarnessPluginHost.kt) — the host's Koin container
│       └── ui/theme/         # Studio template theme
│             # Uses CMP 1.10.3 (same Compose version as :plugin + Capture App).
│             # A stagePluginAssets task copies :plugin's composeResources into
│             # :app's assets at build time.
└── plugin/   # Kotlin Multiplatform + android.kotlin.multiplatform.library + CMP.
    ├── src/commonMain/kotlin/…/
    │   ├── model/        # ProgramSummary.kt — ProgramSummary, EnrolledPerson,
    │   │                 # LabelledValue, WriteTarget. Plain data, no SDK types.
    │   ├── repository/   # PluginRepository interface
    │   └── ui/           # PluginUiState, PluginViewModel, PluginCard
    ├── src/commonTest/       # ViewModel/UI tests against a fake repository — JVM, no device
    ├── src/androidHostTest/  # Tests of androidMain's top-level mapping and error translation,
    │                         # built from real SDK values. Also JVM — see Architecture.
    └── src/androidMain/kotlin/…/
        ├── ProgramOverviewPlugin.kt   # entry point: provideKoinModule + content, nothing else
        └── data/         # D2PluginRepository — the only file that sees the SDK
```

Both test source sets run under one Gradle task, `:plugin:testAndroidHostTest`.

Only `:plugin`'s output is shipped. `:app` is not.

## Architecture

Three layers, and no more than three. The Capture App itself also has a use-case layer; this project
deliberately does not — a plugin is small enough that a use case per action would be a file that only
forwards a call, and the point of this sample is to be read start to finish by someone who has never
seen it.

```
UiState  ←  ViewModel  ←  PluginRepository (interface)  ←  D2PluginRepository
commonMain   commonMain      commonMain                        androidMain
```

- **UiState** — a `sealed interface` per concern, holding plain data. No SDK types.
- **ViewModel** — exposes `StateFlow<PluginUiState>`, calls the repository, maps failures into
  state. Never touches `D2`.
- **Repository interface** — the plugin's own vocabulary, returning `Result` of plain models.
- **D2PluginRepository** — the *only* place `D2` appears. Labelling a tracked entity is **not** its
  job: `plugin-sdk`'s `TrackedEntityLabeller` owns that rule, because a tracked entity's attribute
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

1. **[build]** Put it in `commonMain` unless it needs a platform API. In practice only `ProgramOverviewPlugin` and
   `D2PluginRepository` belong in `androidMain`, because `D2` is the Android SDK.
2. **[build]** Composables take plain data and callbacks — never a `Dhis2PluginContext`. That is what lets
   `@Preview` render the real UI without a server. The harness no longer needs this — it builds a
   real context against a real `D2` (see *Development harness*) — but a `@Preview` still does, and
   it is the faster loop for pure UI work.
3. **[checked]** **Stay short.** The host renders the slot in a non-scrolling `Column` above its own program list,
   so height taken here is height taken from the host and anything past the viewport is unreachable.
   `PluginCard` caps itself with `heightIn(max = …)` + `verticalScroll`.
4. **[checked]** A repository returns `Result`, never throws. An exception escaping into the host composition takes
   the enclosing screen with it, and Compose cannot express an error boundary around a composable
   call. This means catching `Throwable`, not just `D2Error` — see `io()` and `catchingD2` in
   `D2PluginRepository.kt` (`io()` is private; `catchingD2` is the top-level one the tests reach).
   A repository that only catches the SDK's own error type still lets an unexpected null while
   mapping a result reach the host.
5. **[build, in part]** **Count in SQL; materialise only what you show.** The one shape a grep
   *can* settle is checked — enriching with `.with…()` and then capping with `take(` — by the
   build's `cap-before-enrichment` rule. The rest is judgement. `blockingCount()` is a `COUNT(*)`;
   `blockingGet()` materialises rows. `ProgramSummary` carries a total beside a capped list for this
   reason. The SDK has no synchronous row limit — `blockingGet`, `blockingCount`, and a LiveData-based
   `getPaged` — so `take(n)` after a `blockingGet` is as good as it gets for the rows.
6. **[prose]** `D2Error` carries no `message`. It is `data class D2Error(…) : Exception()` and passes nothing to
   the `Exception` constructor, so `Throwable.message` is **always null** — read `errorCode()` and
   `errorDescription()`, or every failure renders as the bare word "D2Error".

## Commands

```bash
./verify.sh                            # spec gate + tests + bundle — the definition of done
python3 tools/check-specs.py           # the spec ↔ test gate alone (fast)
python3 tools/check-rules.py           # this sample's own rules, marked [checked] below (fast)
./gradlew :plugin:checkPluginConventions   # the plugin system's rules, marked [build] below
./verify.sh --cold                     # same, from a fresh Gradle home (Maven Local kept)
./gradlew :plugin:buildPluginBundle    # signed zip → plugin/build/outputs/plugin-bundle/
./gradlew :plugin:testAndroidHostTest  # unit tests — commonTest AND androidHostTest, JVM, no device
./gradlew :app:installDebug            # harness against a real server, on emulator
```

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
3. **[prose]** **Matching `composeMultiplatform` is not enough.** The host declares CMP *and* androidx `compose`
   separately, depending on the latter directly and at a higher version, while CMP brings
   `foundation-layout` transitively at a lower one. Almost everything is identical, which is the
   trap: the first casualty is a *defaulted* overload whose `…$default` synthetic changed.
   `Modifier.weight(1f)` crashed the host with `NoSuchMethodError: weight$default` at composition.
   Prefer layout APIs without default arguments, and note `compose.foundation` is not even declared
   here — it arrives transitively, so its version floats.
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

**Not adopted yet — this section describes where the UI should go, not where it is.** `PluginCard`
uses raw Material 3 today, and `org.hisp.dhis.mobile:designsystem` is declared nowhere in this
build. Adopting it is in the backlog; what follows is how to do it.

A plugin should look like the app it renders inside. The Capture App carries
`org.hisp.dhis.mobile:designsystem` on its runtime classpath, so declare it **`compileOnly`** and the
real components arrive from the host's class loader — the same arrangement as Compose, for the same
reason (rule 1 below).

- Guide: <https://developers.dhis2.org/docs/mobile/mobile-ui/overview>
- API reference: <https://dhis2.github.io/dhis2-mobile-ui/api/-mobile%20-u-i/org.hisp.dhis.mobile.ui.designsystem.component/index.html>

Declared in `commonMain` — it is a Compose Multiplatform library, so it belongs beside the other
`compose.*` entries rather than in `androidMain`:

```kotlin
compileOnly("org.hisp.dhis.mobile:designsystem:<the version the host ships>")
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
import org.dhis2.mobile.plugin.sample.generated.resources.Res
import org.dhis2.mobile.plugin.sample.generated.resources.plugin_loading
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

## Development harness

`:app` signs in to a real DHIS2 and renders the real plugin against real data — no hand-written
samples. Configure it in `local.properties`, which is gitignored and never committed:

```properties
sdk.dir=<your Android SDK>               # required by :plugin's build-tools pin, not just by AGP
dhis2.serverUrl=<your server>            # from an emulator, 10.0.2.2 is the host machine
dhis2.username=<your username>
dhis2.password=<your password>
dhis2.programUid=                        # optional; blank picks the first tracker programme
```

`dhis2.programUid` selects what the **harness downloads**. Leave it blank and the harness picks the
first tracker programme ordered by name, which is exactly how `D2PluginRepository` resolves the one
it reports on — so the two agree by default. Name a different programme and they will not; that is
the one case `MainActivity`'s on-screen note is about.

Use a development server. The plugin reads only — but the harness logs in as a real user and syncs
a real database onto the device, which is not something to point at production.

Then `./gradlew :app:installDebug`. On first run it instantiates `D2`, logs in, downloads metadata
and then **tracker data** — metadata alone brings programmes and stages but no enrolments, and a
plugin rendering real structure over zero rows looks like a plugin bug. That first run takes minutes;
afterwards the database is on the device and startup is immediate. Every step is named on screen, so
a slow run is distinguishable from a stuck one.

It renders `ProgramOverviewPlugin.content()` itself, not just `PluginCard`, by reproducing the host's private Koin
container (`PluginHost`) — a harness whose DI differs from the host's proves the wrong thing.

**What it cannot tell you.** It is not the Capture App, and these need the real host:

- the non-scrolling slot and the height budget
- the class-loader reload and its `ClassCastException`
- Compose resource resolution through `FileSystemResourceReader`
- that plugin bindings cannot leak into the host's container
- androidx Compose version skew — `NoSuchMethodError` reproduces only against the host's versions

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
   creates the key, PUT updates it afterwards. It points the app at
   `http://10.0.2.2:8081/plugin-{version}.zip` (the bundle is named from the Gradle
   module, not from the config's `id`). The dataStore is the only source of plugin
   config; there is no in-app fallback. There is no data-scope field to set — the plugin gets the
   SDK unrestricted, so the config only names *which* code to run.
5. Install the Capture App and log in: `./gradlew :app:installDhis2Debug` in that checkout, which
   installs as `com.dhis2.debug`. Plugins load when the home screen opens.

For UI work without a server at all, the `@Preview`s in `MainActivity` render `PluginCard` against
sample state. For the plugin against real data, `./gradlew :app:installDebug` (see *Development
harness*).

## Entry-point contract

`ProgramOverviewPlugin` must:

- Implement `org.dhis2.mobile.plugin.sdk.Dhis2Plugin`.
- Live in `src/androidMain`, because `Dhis2PluginContext.sdk` is `D2` — the DHIS2 *Android* SDK.
- Live at the FQCN the dataStore config names as `entryPoint`. The plugin declares none of it.
- Have a public no-arg constructor — the host instantiates via reflection.

## Backlog

- **Adopt the DHIS2 design system.** `PluginCard` uses raw Material 3 and hardcoded hex colours;
  `org.hisp.dhis.mobile:designsystem` is not declared. See *Design system* above for how.
- **Move the last two local rules upstream, or accept that they stay local.** `tools/check-rules.py`
  still checks that `PluginRepository` returns `Result` and that `PluginCard` bounds its height. Both
  name types this sample invented, so upstream could only find them by growing an interface for
  plugin authors to implement — a real architectural imposition the plugin system does not currently
  make. Probably the right answer is that they stay here; worth revisiting if a second plugin
  repeats them.

- **Publish `plugin-sdk` and `plugin-sdk-gradle` to Maven Central.** Until then every developer has
  to build them from a Capture App checkout into their own Maven Local, which is the single biggest
  obstacle to someone else cloning this and getting anywhere — and the reason this repository has no
  CI: a runner cannot build it. Publishing these is what makes automated verification possible.
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
- Have the plugin-bundle Gradle plugin pin the host's androidx Compose version the way it already
  pins `plugin-sdk` and `android-core`, so rule 3 stops being a manual concern. Note the sub-groups
  do not share one version line (`material3` is on 1.4.x while `ui`/`foundation` are on 1.10.x), so a
  group-wide force is wrong.
- Add a `jvm("desktop")` target and a `desktop/plugin.jar` bundle subdir once
  a Desktop host exists.
- Per-publisher cert allow-list in the Capture App's `PluginVerifier`.
