# Specs

One file per feature. A spec is the thing that gets agreed *before* code exists, and the thing that
survives the session that wrote it. Write it here, review it, then hand it to
`/plugin-from-spec specs/<your-file>.md`.

The spec's filename becomes the branch — `specs/overdue-events.md` is built on `spec/overdue-events`
— so name the file after the feature, in words a reviewer would recognise.

**A spec is not finished when you hand it over; it is finished when the pipeline stops asking.**
Whatever gets settled while answering its questions is written back into the file and committed
before any code, so the spec that produced the implementation is the spec in the repo. If re-running
the same file would still need the conversation, something it learned is missing from it.

Start from `TEMPLATE.md` — it is the five headings with the guidance inline as comments you delete.

Two complete examples sit beside it:

- `example-program-summary.md` — the plugin that ships in this repo today, written after the fact, so
  the spec and the code can be read side by side.
- `overdue-events.md` — specified before its code exists, which is the normal direction.

## The sections

Use these headings, in this order. The pipeline reads them by name, so renaming one means it is not
read. An empty section is fine and is better than a missing one — it says "considered, nothing to
say" rather than "forgotten".

### `## Intent`

One paragraph, in the language of the person using the app, not the code. Why does a health worker
want this on their home screen? If this paragraph is hard to write, the feature is not ready to
specify.

### `## Logic scenarios`

Given / When / Then, one blank line between scenarios. These become tests in
`plugin/src/commonTest/` and run on the JVM with no device.

A scenario belongs here when **both** halves hold:

1. Its `Given` can be arranged by handing the ViewModel a fake `PluginRepository`.
2. Its `Then` names something the **UI state** exposes — a case of a sealed interface, a value, a
   count, a message — or a call the repository should or should not have received.

Point 2 is the one that catches people out, and it follows from how the tests work: they call
`advanceUntilIdle()` and assert on `state.value`. Nothing renders the card, so a `Then` about a
string on screen has nothing to assert against.

```
Given the repository returns a summary with 3 events
When the card loads
Then the summary state is Loaded, reporting 3 events
```

Together these still cover most of what a plugin actually gets wrong: mapping, counting, empty
states, error text, and what survives a failure.

### `## Device scenarios`

Given / When / Then for anything needing a real DHIS2 read or write, **and for what is actually
rendered**. Neither can be automated — see *Why the split* below — so they become a manual checklist
the pipeline prints at the end, and they are the only scenarios a human has to walk through.

Keep them few. Every scenario here is a step someone repeats by hand on every change.

```
Given a server with "Child Programme" synced and 36 enrollments
When I open the home screen
Then the enrolled count matches the tracker list
```

### `## Metadata needs`

What must already exist on the DHIS2 server for the device scenarios to be runnable: programs,
program stages, data sets, org units, whether the user needs write access. Be specific enough that
someone else could set the server up.

### `## UI budget`

The host renders the plugin in a **non-scrolling** column above its own program list, so height
taken here is height taken from the app, and anything past the viewport is unreachable rather than
scrollable. State the resting height, what is visible without interaction, and what hides behind a
toggle.

## Why the split between logic and device scenarios

`Dhis2PluginContext.sdk` is a `D2`, and there is no way to construct one outside a logged-in app.
A context therefore cannot be faked, which means no automated test can exercise the code path that
reads from DHIS2.

This is not a gap in the harness, it is a property of the API, and the format makes it explicit so
that:

- a spec never carries an acceptance criterion that nothing can check;
- the report at the end can say honestly which scenarios are proven and which are still open;
- the pressure lands where it belongs — on keeping SDK access behind
  `PluginRepository` so that *everything above it* is a logic scenario.

The practical consequence when writing a spec: if a scenario you want to automate keeps needing a
device, the design is probably reaching into the SDK too far up. Push the SDK call down into the
repository and describe the result as plain data, and the scenario moves.

## Writing a good Then

A `Then` is an assertion, so it has to name something observable. "Then the data is correct" cannot
become a test. "Then the write state is Failed and the summary state is still Loaded" can — and it
describes a real bug this plugin had.

For a **logic** scenario, observable means *present in the UI state*. If the value you want to assert
is one the card derives while rendering — "and N more", computed from two counts — you have two
options, in order of preference:

1. **Push the derived value into the state.** Have the state carry `N`. It becomes assertable, and
   the composable gets simpler. This is usually the right design anyway.
2. **Move the scenario to `## Device scenarios`** and phrase it there as the string on screen.

And never assert on how many times the state was emitted. Emission counts change whenever anything
else in the load path changes, and every test coupled to them then breaks together for no real
reason. That has already happened once here.
