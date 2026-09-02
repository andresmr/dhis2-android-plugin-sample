# DHIS2 Android plugin sample

A reference plugin for the [DHIS2 Android Capture App](https://github.com/dhis2/dhis2-android-capture-app),
and a harness for building plugins from written specifications.

A plugin is a small Android library that implements `Dhis2Plugin`, is packaged as a signed zip
bundle, and is rendered inside the Capture App from a server-side configuration. This repository is
the worked example: `ProgramOverviewPlugin` reads a tracker programme through the DHIS2 Android SDK
and renders a card on the home screen — enrolment and event counts, a few recent people, and one
write.

> **Status: proof of concept.** The plugin API, the bundle format and the injection points may still
> change. `vendor/maven/` holds pre-built copies of `plugin-sdk` and `plugin-sdk-gradle` because they
> are not published yet, which is why this project builds with no extra setup — see
> [`vendor/maven/README.md`](vendor/maven/README.md).

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

```bash
./verify.sh
```

That runs the unit tests, builds the signed bundle, checks the bundle carries nothing the host
already owns, and prints the bundle's checksum along with a `plugin-config.json` ready to post to a
server. `./verify.sh --cold` repeats it from an empty Gradle home and local Maven repository, which
is the only run that proves the project builds on a machine that has never seen it.

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

## Publish a bundle

Tag with the plugin's version:

```bash
git tag v1.5.0 && git push origin v1.5.0
```

The release workflow verifies, then attaches the bundle, its checksum and a `plugin-config.json`
whose `downloadUrl` already points at the release asset. Installing the plugin is posting that one
file to the server's dataStore.

Bundles built in CI are signed with a throwaway debug key, so their checksum will not match one
built locally — use the `plugin-config.json` from the release, not a local one. A real publisher
signs with their own key through `pluginBundle { signing { … } }`.

## More

[`CLAUDE.md`](CLAUDE.md) is the working reference: the layer rules, the build constraints that cost
real debugging, the DHIS2 design system, the on-device test loop, and the backlog. It is written for
whoever — or whatever — is editing this repository next.
