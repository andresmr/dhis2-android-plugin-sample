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

`specs/README.md` defines the section headings and `specs/TEMPLATE.md` is the skeleton. If the spec
is missing a section, or still carries the template's `<!-- -->` guidance comments, say so now rather
than discovering it in phase 03.

## Phase 01 — Restate, then stop

**Change no files in this phase.**

Produce, in the conversation:

1. A table with one row per **logic scenario**: the scenario, the name of the test that will assert
   it, and **what in the UI state that test reads**. If you cannot name the state a scenario asserts
   against, it is not a logic scenario — the tests call `advanceUntilIdle()` and read `state.value`,
   so a `Then` about a string on screen has nothing to assert. Say so, and propose either pushing the
   derived value into the state or moving the scenario to the device checklist. Do not silently
   reinterpret it.
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

## Phase 01b — Branch

Only after approval, and before touching a single file.

```bash
git rev-parse --abbrev-ref HEAD          # confirm the starting point
git status --porcelain                   # must be empty
git checkout -b spec/<spec-file-slug>
```

- **Never work on the default branch.** If `git status` is not clean, stop and say so — do not stash,
  do not commit someone else's work in progress.
- Name the branch `spec/<slug>` after the spec file, not after your guess at the feature.
  `specs/overdue-events.md` → `spec/overdue-events`. The branch and the spec are the same unit of
  work, and the name should make that obvious a month later.
- Branch from wherever the session already is. In a worktree or a cloud session that may not be the
  default branch, and silently re-pointing it is worse than working from the wrong base visibly.

## Phase 01c — Fold the answers back into the spec

The first commit on the branch is the spec, before any code.

Everything settled in phase 01 lives in a conversation that ends when the session does. The spec is
what survives. If the answers stay in the chat, the next run of the same file asks the same questions
and is free to answer them differently — so the spec has to absorb them.

Edit `specs/<file>.md` so that:

- **Every question you asked is answered in the spec**, as a scenario or a sentence — not in a
  comment about what was decided, but as the requirement itself.
- **Every vague `Then` that caused a question is tightened** so it could not be satisfied by the
  reading you had to ask about. "Has no resolvable name" was satisfiable by reading any attribute at
  all; naming the program's display attributes is what makes it wrong to get wrong.
- **Assumptions that turned out incorrect are deleted**, not annotated.
- **Anything the human ruled out** is stated, if a future reader would otherwise propose it again.

Then show the diff, and commit it alone:

```bash
git add specs/<file>.md
git commit -m "spec: <feature> — fold in the phase 01 answers"
```

Alone, because a reviewer should be able to read *what we agreed the feature is* before *how it was
built*, and because a spec change that arrives mixed into an implementation commit is one nobody
reads.

**The test of whether this phase was done properly:** re-running `/plugin-from-spec` on the committed
spec should ask nothing new and arrive at the same design. If it would still need the conversation,
the spec is not finished — go back and put the missing decision in it. A spec that only works with
the chat that produced it is not a spec.

If phase 01 raised no questions and required no assumptions, say so and skip the commit rather than
manufacturing an edit.

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

- Everything except `D2PluginRepository` and `ProgramOverviewPlugin` goes in `commonMain`.
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
   `plugin-config.json`, with `downloadUrl` generated for an emulator on port 8081. Point at them, and
   flag that the URL needs changing only for a physical device or a different port.
5. **Anything you changed that the spec did not ask for**, and why.
6. **The branch name**, and that only the spec is committed so far — the implementation is not.

Do not claim a device scenario works because the logic scenario behind it passes. They are different
claims and the whole format exists to keep them apart.

Then **stop.** Only the phase-01c spec commit exists; the implementation is uncommitted on the
phase-01b branch for the human to read, run, and try on a device.

## Phase 06 — Commit and open a PR

**Only when the human has said the work is good.** This is a second gate, and it is as firm as the
one in phase 01: reviewing the device scenarios is the entire point of phases 04 and 05, and a commit
that lands before that review says the checklist was decoration.

If they come back with changes, apply them and return to phase 04. Repeat until they approve.

On approval:

1. **Commit** the implementation to the phase-01b branch. One commit unless the work genuinely
   separates, and separate from the phase-01c spec commit, which is already there. The message should
   name the spec it came from, what is proven by tests, and what was verified by hand.
2. **Push**, then open the PR:

   ```bash
   git push -u origin HEAD
   gh pr create --fill --draft
   ```

   Draft, because the device checklist is evidence a reviewer should see rather than take on trust.
   The PR body should carry the device scenarios and their outcome.

3. **If there is no `origin`,** stop and say so plainly — the commit is safely on the branch, and a
   PR needs a remote that does not exist yet. Do not create a repository, add a remote, or push
   anywhere on your own initiative: where this code lives is not a decision to make on someone's
   behalf. Tell them what is needed (`gh repo create`, or an existing remote to add) and leave it.
4. **If `gh` is missing or unauthenticated** — likely in a cloud session — commit and push if you can,
   then hand back the branch name and let them open the PR.

---

## Rules that apply throughout

- **Commit the spec in phase 01c, and the implementation only in phase 06 after approval — never on
  the default branch.** Phases 02 to 05 leave the code uncommitted on the phase-01b branch.
- **Do not weaken a test to make it pass.** If the spec and the implementation disagree, that is a
  finding for the report, not something to smooth over.
- **Report failures with their output.** If `verify.sh` fails, say what failed and paste the relevant
  lines. A summary that says "mostly working" is worse than useless.
- **`vendor/maven/` is a temporary bridge.** If a build failure looks like a stale plugin API — a
  method that should exist but does not — read `vendor/maven/README.md` before assuming your code is
  wrong.
