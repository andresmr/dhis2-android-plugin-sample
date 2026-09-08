# DHIS2 Android plugin sample

A reference plugin for the [DHIS2 Android Capture App](https://github.com/dhis2/dhis2-android-capture-app),
and a harness for building plugins from written specifications.

A plugin is a small Android library that implements `Dhis2Plugin`, is packaged as a signed zip
bundle, and is rendered inside the Capture App from a server-side configuration. This repository is
the worked example: `ProgramOverviewPlugin` reads a tracker programme through the DHIS2 Android SDK
and renders a card on the home screen — enrolment and event counts, a few recent people, and one
write.

> **Status: proof of concept.** The plugin API, the bundle format and the injection points may still
> change. `plugin-sdk` is not published yet, so it has to be built into your Maven Local from a
> Capture App checkout before this project will configure — see *Build it* below.

## Layout

```
plugin/    the plugin — the only thing that ships
app/       a development harness; signs in to a real server and renders the plugin
specs/     feature specifications, the input to the build pipeline
verify.sh  the definition of done
```

## Build it

Needs JDK 17, an Android SDK with `platforms;android-37.0` and `build-tools;36.1.0`, and the
bundled Gradle 9.5.1 wrapper.

First, publish the plugin API into your local Maven repository from a checkout of the
[Capture App](https://github.com/dhis2/dhis2-android-capture-app) on the branch carrying the plugin
system (`poc/plugin-system` at the time of writing):

```bash
./gradlew :plugin-sdk:publishToMavenLocal :plugin-sdk-gradle:publishToMavenLocal
```

Then, here:

```bash
./verify.sh
```

That runs the unit tests, builds the signed bundle, checks the bundle carries nothing the host
already owns, and prints the bundle's checksum along with a `plugin-config.json` ready to post to a
server. `./verify.sh --cold` repeats it from an empty Gradle home, which catches stale local state.

## Run it against real data

`app/` is a harness, not a preview: it instantiates `D2`, signs in, downloads metadata and tracker
data, and renders the plugin's real entry point. Put credentials in `local.properties`, which is
gitignored:

```properties
dhis2.serverUrl=<your server>            # from an emulator, 10.0.2.2 is the host machine
dhis2.username=<your username>
dhis2.password=<your password>
dhis2.programUid=                        # optional; blank picks the first tracker programme
```

```bash
./gradlew :app:installDebug
```

The first run downloads metadata and takes several minutes; each step is named on screen. Use a
development server — the harness writes as well as reads.

**The harness is not the Capture App.** It will not tell you about the non-scrolling slot and its
height budget, the class-loader reload, Compose resource resolution, DI isolation, or androidx
Compose version skew. Those need the real host; `CLAUDE.md` lists them.

## Build a feature from a spec

The point of this repository is the loop, not the card. A feature starts as a file in `specs/`,
written in Given/When/Then, and is built by an agent following
[`.claude/skills/plugin-from-spec`](.claude/skills/plugin-from-spec/SKILL.md):

1. **Restate and stop.** Every scenario is paired with the test that will assert it, then the
   pipeline waits for approval. A misread spec costs a paragraph here and an afternoon later.
2. **Fold the answers back in.** Whatever the questions settled is written into the spec and
   committed first, alone. The test of that phase: re-running the pipeline on the committed spec
   should ask nothing new — a spec that only works alongside the conversation that produced it is
   not finished.
3. **Red, then green.** Failing tests from the `Then` clauses, then the implementation, in the
   order the layers depend on each other.
4. **Verify, then hand back.** `./verify.sh`, and a report saying what is proven by tests and what
   still needs a device.

Start from [`specs/TEMPLATE.md`](specs/TEMPLATE.md).
[`specs/README.md`](specs/README.md) explains the format, including the one thing that shapes it:
a JVM test cannot construct a `D2`, so scenarios split into those a fake repository can arrange and
those needing a real server. The format keeps them apart so a spec never carries an acceptance
criterion nothing can check.

## Architecture

Three layers, and deliberately no use-case layer — a plugin is small enough that one would only
forward calls.

```
PluginUiState  ←  PluginViewModel  ←  PluginRepository  ←  D2PluginRepository
  commonMain        commonMain          commonMain            androidMain
```

`D2PluginRepository` is the only file that touches the SDK. That is what keeps the state, the UI and
their tests in `commonMain`, runnable on the JVM against a fake — and it means the SDK surface is one
file to change when the plugin API narrows what it exposes.

## Install it in the Capture App

`./verify.sh` leaves the signed bundle and a ready-to-post `plugin-config.json` in
`plugin/build/outputs/plugin-bundle/`. Serve that directory over HTTP and post the config to your
server's dataStore under `dhis2AndroidPlugins/config` — the dataStore is the only source of plugin
configuration, there is no in-app fallback. `CLAUDE.md` has the full loop, including why the port
matters and when to bump `pluginVersion`.

Bundles are signed with your local debug key, so a checksum only matches the bundle you actually
built. A real publisher signs with their own key through `pluginBundle { signing { … } }`.

## More

[`CLAUDE.md`](CLAUDE.md) is the working reference: the layer rules, the build constraints that cost
real debugging, the DHIS2 design system, the on-device test loop, and the backlog. It is written for
whoever — or whatever — is editing this repository next.
