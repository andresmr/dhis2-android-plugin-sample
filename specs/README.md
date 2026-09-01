# Specs

One file per feature. A spec is the thing that gets agreed *before* code exists, and the thing that
survives the session that wrote it. Write it here, review it, then hand it to
`/plugin-from-spec specs/<your-file>.md`.

`example-program-summary.md` is a complete worked example, reverse-engineered from the plugin that
ships in this repo today. Copy it as a starting point.

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

A scenario belongs here when its Given can be arranged by handing the ViewModel a fake repository.
That covers most of what a plugin actually gets wrong: mapping, counting, empty states, error text,
what survives a failure.

```
Given the repository returns a summary with 3 events
When the card loads
Then the card shows "3 event(s) in this program"
```

### `## Device scenarios`

Given / When / Then for anything that needs a real DHIS2 read or write. **These cannot be
automated** — see *Why the split* below — so they become a manual checklist the pipeline prints at
the end, and they are the only scenarios a human has to walk through.

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

A `Then` is an assertion, so it has to name something observable — a string on screen, a count, a
state. "Then the data is correct" cannot become a test. "Then the card shows `Write refused` and the
previous summary is still visible" can, and it also happens to describe a real bug this plugin
had.
