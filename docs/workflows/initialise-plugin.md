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

## Phase 01 — Agree the four names and the slot

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

**Then ask where it renders.** This is a real question with two answers, and it decides which of the
template's two defaults the fork lands on:

| Slot | What it is | Needs |
|---|---|---|
| `HOME_ABOVE_PROGRAM_LIST` | A card on the home screen, above the programme list. Additive — every plugin configured for it renders. | Nothing. This is the default. |
| `DATA_SET_INSTANCE_CONTENT` | The body of the data set instance screen, in place of the host's table. The host keeps its top bar, save button and bottom bar. | The UID of at least one data set it applies to. |

A plugin may declare both. It lives in `plugin.json` as `injectionPoints` and `slotConfig`, which is
also what reaches the dataStore config an administrator posts — and unlike the four names above,
these are **yours to edit by hand**, before or after init. Nothing in the source tree mirrors them,
so nothing can disagree with them. `./init.sh` carries whatever they say through the rename.

Both defaults stay in the tree whichever you pick: `plugin/src/androidMain/kotlin/…/slots/` has one
file per slot, and the one you do not use is a file you can delete once you are sure. See *Host
slots* in `AGENTS.md`.

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

For the data set slot, add `--injection-point` and `--data-set-uid` (both repeatable):

```bash
./init.sh --name "Monthly Stock" \
          --package org.myorg.stock \
          --plugin-id org.myorg.monthly-stock \
          --entry-point StockPlugin \
          --injection-point DATA_SET_INSTANCE_CONTENT \
          --data-set-uid BfMAe6Itzgt \
          --yes
```

Omit them and whatever `plugin.json` already declares is kept — so editing the file first works just
as well, and is the better route by hand.

Without flags it prompts for each value, pre-filled from the ones before it. `--dry-run` prints
every move, rename and rewrite and changes nothing; worth doing first if you want to see the shape
of it.

What it does: writes `plugin.json`, moves the Kotlin directories, renames the entry-point file,
rewrites the remaining references, deletes every build directory, then runs `./verify.sh` and stages
everything **without committing**. It reports the slots it recorded, and says plainly when a
replacement slot has no UIDs yet — that is a plugin that will render nothing.

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

# Optional: only when plugin.json declares more than one slot and you want the other one.
# harness.slot=HOME_ABOVE_PROGRAM_LIST
```

**Use a development server.** The plugin only reads, but the harness signs in as a real user and
syncs a real database onto the device.

Then:

```bash
./gradlew :app:installDebug
```

The first run takes several minutes — it instantiates `D2`, logs in and downloads metadata. Every
step is named on screen, so a slow run is distinguishable from a stuck one. Afterwards the database
is on the device and startup is immediate.

It then resolves the slot `plugin.json` declares. At the home slot there is nothing to resolve. At
`DATA_SET_INSTANCE_CONTENT` it finds the data set, period, organisation unit and attribute option
combo for the UID you configured, and names all four on screen — so "the harness picked the wrong
instance" and "the plugin is broken" cannot be confused. A UID no server has, or a data set assigned
to no unit you can capture for, is a named failure rather than an empty screen.

This is also the credential check: wrong URL, wrong password or an unreachable server each show up
as a named failure on screen rather than as a blank card.

Note the harness downloads **metadata only**. A plugin that reads rows of data will see none until
you add the download it needs.

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

1. **The identity**: name, plugin id, package, entry-point FQCN, version, and the slot or slots it
   declares — with their `slotConfig`, or the fact that a replacement slot has none yet.
2. **That `./verify.sh` passed**, and where the bundle and its `plugin-config.json` are.
3. **What is unproven.** The seed's specs carry device scenarios that no JVM test can
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
ViewModel, card, `D2PluginRepository` — reading the programme count, plus one renderer per slot
under `slots/`. It exists so `./verify.sh` has
something real to check and so the three layers are visible rather than described. Replace it. It is
meant to be deleted.

**Changing the slot later.** Edit `injectionPoints` and `slotConfig` in `plugin.json` and rebuild.
Unlike the four names, these have no counterpart in the source tree, so no gate can disagree with
them and `--force` is not involved. The other slot's default is already in `slots/`, waiting.
