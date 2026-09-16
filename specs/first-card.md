# First card

<!--
The seed's spec: two logic scenarios and two device scenarios. Enough that ./verify.sh has
something real to check on a fresh fork, small enough to delete on your first day.

Replace it rather than extending it. A worked example of a full spec, with its implementation
beside it, is examples/program-summary/specs/program-summary.md.
-->

## Intent

Someone opening the Capture App should be able to tell, without navigating anywhere, that this
plugin is alive and reading the DHIS2 database that is already on the device. The card reports how
many programmes have been synced. It is the smallest thing that is still a real read — no metadata
has to be configured for it to mean something, on any server.

## Logic scenarios

@L1
Given the repository returns a summary reporting 4 programs
When the card loads
Then the summary state is Loaded, reporting 4 programs, and the repository was asked exactly once

@L2
Given the repository fails to load the summary, with the message "No database"
When the card loads
Then the summary state is Failed carrying that message, and nothing is thrown

## Device scenarios

@D1
Given a server whose metadata has finished syncing to the device
When I open the home screen
Then the card shows the plugin's name and a programme count matching the app's own programme list

@D2
Given the plugin has been reloaded after a metadata sync
When I return to the home screen
Then the card renders normally, with no ClassCastException in the log

## Metadata needs

Any DHIS2 server the logged-in user can sync metadata from. No programme, data set or org-unit
configuration is required: the count is over whatever that user can see, so an empty result is a
real answer rather than a broken one.

## UI budget

Capped at 160 dp, well inside the host's budget. The host renders this above its own programme list
in a **non-scrolling** column, so height taken here is height taken from the app and anything past
the viewport is unreachable. `PluginCard` bounds itself with `heightIn(max = 160.dp)` plus
`verticalScroll`, which is also what `tools/check-rules.py` looks for.

Everything is visible at rest; nothing hides behind a toggle.
