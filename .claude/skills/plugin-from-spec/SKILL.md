---
name: plugin-from-spec
description: Build a DHIS2 Android plugin feature from a written specification in specs/. Use when the user points at a spec file, asks to implement a spec, or describes a feature as Given/When/Then. Runs spec → approval gate → failing tests → implementation → verification.
---

# Plugin from spec

Turn a spec in `specs/` into a working, verified plugin feature.

Invoked as `/plugin-from-spec specs/<file>.md`. If no file is named, list what is in `specs/` and ask
which one — do not guess, and do not start from a spec you inferred from the conversation. The spec
is the contract; if it does not exist as a file yet, write it first and get it approved as phase 01.

Read `CLAUDE.md` before anything else. It carries the architecture, the layer rules, and the build
traps, and it is not repeated here.

---

## Phase 00 — Orient

Read the spec and `CLAUDE.md`. Read the existing code in the layer you are about to touch — this is a
small project and reading `PluginUiState`, `PluginViewModel` and `PluginRepository` costs almost
nothing and prevents inventing a second way to do what already has one.

`specs/README.md` defines the section headings. If the spec is missing a section, say so now rather
than discovering it in phase 03.

## Phase 01 — Restate, then stop

**Change no files in this phase.**

Produce, in the conversation:

1. A table with one row per **logic scenario**: the scenario, and the name of the test that will
   assert it.
2. A table with one row per **device scenario**: the scenario, and the manual step it becomes.
3. Assumptions you are making that the spec does not state.
4. Questions the spec leaves genuinely open — ones where two readings lead to different code. Not a
   list of everything unstated.
5. Anything in the spec you think is wrong, in one or two sentences. A spec written before the code
   is often wrong in a small way; saying so now is cheap.

Then **stop and wait for approval.** Do not proceed to phase 02 in the same turn, even if the spec
looks unambiguous. This gate is the cheapest correction point in the whole pipeline: a misreading
costs a paragraph here and an afternoon later.

If the user approves with changes, restate the changed rows before continuing.

## Phase 02 — Red

Write the tests for the logic scenarios in `plugin/src/commonTest/`, following the existing
`PluginViewModelTest` — a fake `PluginRepository`, `runTest`, `advanceUntilIdle()` then assert on
`state.value`.

Do not assert by counting emissions (`skipItems(n)`). Emission counts change whenever anything else
in the load path changes, and every test coupled to them breaks together for no real reason. This
has already happened once in this project.

Run them. **Show them failing**, and check the failure is the one you expect — a test that fails
because of a typo in a fixture is not yet evidence of anything. If a test passes before the
implementation exists, it is asserting nothing; fix it now.

## Phase 03 — Green

Implement in this order, which is the direction the dependencies point:

```
model → PluginRepository (interface) → PluginUiState → PluginViewModel → PluginCard → D2PluginRepository
```

- Everything except `D2PluginRepository` and `MyPlugin` goes in `commonMain`.
- `D2PluginRepository` is the **only** file allowed to mention `D2`. If you find yourself wanting the
  SDK further up, the design has gone wrong — push the call down and return plain data.
- Repositories return `Result`, never throw. An exception escaping into the host composition takes
  the whole host screen with it.
- Composables take plain data and callbacks, never a `Dhis2PluginContext`.
- Respect the spec's UI budget. The host slot does not scroll.

Prefer DHIS2 design-system components over raw Material 3 so the plugin looks like the app it renders
inside — see the *Design system* section of `CLAUDE.md` for the reference URLs and the
`compileOnly` rule.

Run the tests until green. If a test needs changing to pass, say why in the report — a test edited to
match the implementation is worth a sentence, because that is how a spec quietly stops being the
contract.

## Phase 04 — Verify

```bash
./verify.sh
```

**Not done until this exits clean.** It runs the unit tests, builds the signed bundle, checks the
bundle carries nothing the host owns, and prints the checksum and the dataStore config.

Then bump `pluginVersion` in `plugin/build.gradle.kts`. The Capture App caches bundles by
`{id}-{version}.zip`, so shipping at an unchanged version means a device keeps running the old code —
which reads exactly like the change not working.

If you touched `settings.gradle.kts` or `vendor/`, run `./verify.sh --cold` too. That is the only run
that proves the project still builds anywhere but this machine.

## Phase 05 — Hand back

Report, in this order:

1. **What is proven.** The logic scenarios, each with its passing test.
2. **What is not.** The device scenarios, as a numbered checklist someone can walk through. State
   plainly that these are unverified — the plugin API hands over a `D2`, which cannot be constructed
   outside a running app, so no automated test here can exercise a read or a write.
3. **The metadata the device scenarios need**, from the spec's `## Metadata needs`.
4. **The install steps** — `verify.sh` already printed the bundle path, its checksum and the
   `plugin-config.json`; point at them and note that `downloadUrl` still needs setting.
5. **Anything you changed that the spec did not ask for**, and why.

Do not claim a device scenario works because the logic scenario behind it passes. They are different
claims and the whole format exists to keep them apart.

---

## Rules that apply throughout

- **Never commit.** Make the changes, run the checks, report. Committing is the user's call.
- **Do not weaken a test to make it pass.** If the spec and the implementation disagree, that is a
  finding for the report, not something to smooth over.
- **Report failures with their output.** If `verify.sh` fails, say what failed and paste the relevant
  lines. A summary that says "mostly working" is worse than useless.
- **`vendor/maven/` is a temporary bridge.** If a build failure looks like a stale plugin API — a
  method that should exist but does not — read `vendor/maven/README.md` before assuming your code is
  wrong.
