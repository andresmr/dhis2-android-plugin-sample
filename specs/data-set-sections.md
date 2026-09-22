# Data set sections

## Intent

A health worker opens a data set instance — one data set, for one period, at one organisation unit.
Before entering anything, they need to know *which* period they are about to write into, and what
parts the form is divided into. So the plugin shows the period at the top, and below it the data
set's sections in a horizontal row, with the first one selected. Which section is selected is what
the rest of the form will follow once there is a form; for now it is the row itself that matters,
because a health worker who cannot see the period risks entering January's stock into February.

## Logic scenarios

The period is **not read from DHIS2**. The host hands it over in
`DataSetInstanceSlotArguments.periodId`, and the DHIS2 SDK has no display name for a period anyway —
`Period` carries only `periodId`, `periodType`, `startDate` and `endDate`. So the label is derived
from the period id, by a pure function in `commonMain`, and only the sections need the repository.

The state carries a **structured** period rather than a formatted string, so the text can live in
`composeResources` and be translated. `PeriodLabel.Month(2024, 1)` is what the state holds;
"January 2024" is what the composable renders.

Supported: Daily, Weekly, Monthly, Quarterly, Yearly. The other eighteen period types — bi-monthly,
six-monthly, the weekly variants, the six financial years — fall back to `PeriodLabel.Raw`, which
shows the period id exactly as the host gave it. A raw id on screen is worse than a formatted one
and better than a blank line; L2 is what keeps it from being a blank line.

@L1
Given the host opens an instance whose period id is one of a supported type
When the state settles
Then the period is the structured label for that id — `202401` is `Month(2024, 1)`, `20240115` is
`Day(2024, 1, 15)`, `2024W3` is `Week(2024, 3)`, `2024Q1` is `Quarter(2024, 1)` and `2024` is
`Year(2024)`

@L2
Given the host opens an instance whose period id is of an unsupported type, such as `2024April` or
`202402B`
When the state settles
Then the period is `Raw` carrying that id unchanged, and never empty

@L3
Given the repository returns three sections whose sort orders are out of order, one of them null
When the state settles
Then the sections are listed by sort order ascending, with the null-ordered one last

@L4
Given the repository returns no sections at all, which is what a data set without sections does
When the state settles
Then the state is loaded with an empty section list and no selection — not a failure, and not an
empty-state message. The period stands alone.

@L5
Given the repository returns sections
When the state settles
Then the first section in sort order is the selected one

@L6
Given the sections have loaded and the first is selected
When the user selects another section
Then that section is the selected one and the list of sections is unchanged

@L7
Given the repository fails to read the sections
When the state settles
Then the state is failed, carrying the message, and nothing is thrown into the host composition

## Device scenarios

@D1
Given a configured data set instance on a real server
When I open it
Then the period reads as a person would say it, and the data set's sections are below it in a
horizontal row

@D2
Given that screen
When I tap a section other than the first
Then the selection visibly moves to it

@D3
Given a data set with six or more sections, or with long section names
When I open an instance of it
Then the row scrolls horizontally and no name is clipped or truncated away to nothing

@D4
Given the Capture App, not the harness — `LocalSlotContentPadding` is zero in the harness
When I scroll to the bottom of the plugin's content
Then nothing is hidden under the host's floating save button

@D5
Given the same instance
When I compare the plugin's period against the host's own top bar
Then they name the same period

## Metadata needs

A data set that exists on the server, is assigned to at least one organisation unit inside the
logged-in user's data capture scope, and has a period type its data input periods leave open.

**It must have sections** — at least one, and for `@D3` at least six or one with a long name.
A data set with no sections exercises `@L4` but makes `@D1` unwalkable. Its UID goes in
`plugin.json`'s `slotConfig.DATA_SET_INSTANCE_CONTENT.dataSetUids`.

For `@D1` to read well, prefer a data set on a period type this spec formats — monthly is the
common case.

## UI budget

This is a replacement slot, so the plugin fills the region the host gives it and scrolls inside it,
and it applies `LocalSlotContentPadding`: the host's save button floats over the region, and content
that ignores the padding has a last row nobody can reach.

At rest: the period on one line, and one row of sections under it. Both are visible without
interaction — there is no toggle and nothing is hidden.

The section row is the part that grows with the data, and it is bounded horizontally rather than
vertically: it scrolls sideways and stays one row tall however many sections the data set has. It
never wraps to a second line, because a row that grows downward pushes the form that will eventually
sit below it off the screen.

## Decisions taken, so they are not re-proposed

- **The server's `tabsDirection` is ignored.** `DataSet.displayOptions.tabsDirection` is
  `HORIZONTAL`/`VERTICAL` in the SDK, and a data set may well say `VERTICAL`. This plugin renders
  horizontally regardless. Honouring it was considered and ruled out as more than this feature asks
  for; it would be its own spec.
- **A section-less data set shows nothing extra** — no "this data set has no sections" message. The
  period alone is the whole screen in that case.
- **The seed is replaced, not extended.** `PluginSummary`, `loadSummary` and `DataSetBodyPlaceholder`
  go, along with the four identifiers the placeholder drew. They were the template's own example.
