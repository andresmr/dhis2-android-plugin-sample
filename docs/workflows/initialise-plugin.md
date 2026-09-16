# Initialise a plugin from this template

The first thing anyone does with a fork. It runs once, takes about a minute, and ends with a signed
bundle and a green `./verify.sh`.

**This is a document, not a tool.** Follow it by hand, or hand it to whatever agent you use — Claude
Code, Cursor, Copilot, Codex. Nothing in it depends on any of them. In Claude Code it is also
`/plugin-init`, which is a short wrapper around this file.

---

## Phase 00 — Check the prerequisite that blocks everything

`plugin-sdk` and `plugin-sdk-gradle` are not published to Maven Central yet, and **this project will
not even configure without them** — the plugin modules apply `id("org.dhis2.mobile.plugin-bundle")`
from Maven Local. Nothing else in this guide can be attempted first.

In a checkout of the **DHIS2 Android Capture App**, on the branch carrying the plugin system
(`poc/plugin-system` at the time of writing):

```bash
./gradlew :plugin-sdk:publishToMavenLocal :plugin-sdk-gradle:publishToMavenLocal
```

Both, always. `plugin-sdk-gradle` is what pulls in the matching `plugin-sdk`, and a stale copy of it
is invisible from this side: it surfaces as a dependency-resolution error that never mentions DHIS2.

`./init.sh` checks for both, at the version `gradle/libs.versions.toml` asks for, and says exactly
this if they are missing. You do not have to verify it by hand — but you do have to do it first.

## Phase 01 — Agree the four names

Ask, and do not guess. These are hard to change casually later — not impossible (`./init.sh --force`
renames a fork), but they end up in a server's dataStore configuration, so they are worth a minute.

| What | Example | Notes |
|---|---|---|
| **Name** | `Immunisation Coverage` | How a human says it. Becomes the card's title and the harness's label. |
| **Kotlin package** | `org.myorg.immunisation` | Lower-case, at least two segments, and no segment may be a Kotlin or Java keyword. `org.acme.in.plugin` is rejected — Compose Resources would emit a `Res` class in a package needing backticks, and the error names neither. |
| **Plugin id** | `org.myorg.immunisation-coverage` | Reverse-domain. This is what the DHIS2 administrator writes in the dataStore. Hyphens allowed here, unlike the package. |
| **Entry-point class** | `ImmunisationPlugin` | PascalCase, no package. The host instantiates it by name, so it must have a public no-argument constructor. |

Two more with sensible defaults, worth mentioning rather than asking about: the **version** (default
`0.1.0`) and the **slug** used as Gradle's `rootProject.name` (default: kebab-case of the name).

If you are an agent: confirm these back before running anything, then pass every one as a flag. Do
not rely on prompts — `./init.sh` will not prompt without a terminal, by design, because a hung
prompt is indistinguishable from a hung build.

## Phase 02 — Run it

```bash
./init.sh --name "Immunisation Coverage" \
          --package org.myorg.immunisation \
          --plugin-id org.myorg.immunisation-coverage \
          --entry-point ImmunisationPlugin \
          --version 0.1.0 \
          --yes
```

Without flags it prompts for each value, pre-filled from the ones before it. `--dry-run` prints
every move, rename and rewrite and changes nothing; worth doing first if you want to see the shape
of it.

What it does: writes `plugin.json`, moves the Kotlin directories, renames the entry-point file,
rewrites the remaining references, deletes `examples/` and every build directory, then runs
`./verify.sh` and stages everything **without committing**.

It stops short of three decisions that are not a tool's to make, and says so: your `git remote`
still points at the template, `LICENSE` still names the University of Oslo, and an older harness may
still be installed under its previous `applicationId`.

## Phase 03 — Point the harness at a server

`local.properties` is written for you if it was absent, and it is gitignored. Fill in:

```properties
sdk.dir=/Users/you/Library/Android/sdk
dhis2.serverUrl=http://10.0.2.2:8080     # from an emulator, 10.0.2.2 is your host machine
dhis2.username=admin
dhis2.password=district
```

**Use a development server.** The plugin only reads, but the harness signs in as a real user and
syncs a real database onto the device.

Then:

```bash
./gradlew :app:installDebug
```

The first run takes several minutes — it instantiates `D2`, logs in, downloads metadata and then
tracker data. Every step is named on screen, so a slow run is distinguishable from a stuck one.
Afterwards the database is on the device and startup is immediate.

This is also the credential check: wrong URL, wrong password or an unreachable server each show up
as a named failure on screen rather than as a blank card.

## Phase 04 — Confirm, then commit

```bash
git diff --staged
./init.sh --check          # exit 0, and prints your plugin's identity
```

Read the diff. Then commit it on its own — the rename is not part of your first feature:

```bash
git commit -m "chore: initialise from the DHIS2 plugin template as <name>"
```

## Phase 05 — Hand back

Report:

1. **The identity**: name, plugin id, package, entry-point FQCN, version.
2. **That `./verify.sh` passed**, and where the bundle and its `plugin-config.json` are.
3. **What is unproven.** `specs/first-card.md` carries two device scenarios that no JVM test can
   cover — a `D2` needs an Android `Context`, a database and an HTTP stack. Point at
   `./gradlew :app:installDebug`, and say plainly that the Capture App itself is still needed for
   the height budget, the class-loader reload, resource resolution and Compose version skew.
4. **The three decisions init left alone**, from its own closing report.
5. **What happens next**: write a spec in `specs/`, and build it with
   `docs/workflows/plugin-from-spec.md`. `specs/TEMPLATE.md` is the skeleton and `specs/README.md`
   is the format.

Then stop. Do not start writing a feature in the same session unless asked — initialising and
specifying are different conversations, and the second one deserves a fresh look at what the plugin
is actually for.

---

## Afterwards

**Renaming.** `./init.sh --force` with the new values. Never hand-edit `plugin.json` and the tree
separately: `tools/check-identity.py` exists because the build stays green when they disagree, and
the failure surfaces on a device as `ClassNotFoundException`.

**The seed.** `plugin/src` holds a small working plugin — model, repository interface, UiState,
ViewModel, card, `D2PluginRepository` — reading the programme count. It exists so `./verify.sh` has
something real to check and so the three layers are visible rather than described. Replace it. It is
meant to be deleted.

**The examples.** `./init.sh` removes `examples/`. If you want to keep the worked example around
while you learn the shape of things, pass `--keep-examples`, and set `harness.module` in
`local.properties` to run it in the harness. Delete it before you ship.
