# Program summary card

> Worked example, written after the fact from the plugin already in this repo — so you can read the
> spec and the code side by side. A real spec comes first; this one did not.
>
> Note where the lines fall: the logic scenarios name **state** (`Loaded`, `Failed`, counts, whether
> the repository was re-asked), because that is what a JVM test can assert. The exact strings on
> screen are device scenarios — nothing here renders the card.

## Intent

A health worker opening the app wants to know, without navigating anywhere, whether the program they
work in has data on this device. The plugin is never told which programme that is — the dataStore
config names only which code to run, so the plugin resolves a tracker programme from the server's own
metadata rather than carrying a UID. What it reports: how many people are enrolled, how many events exist, and who was
seen recently.

This sample reads only. It once carried a button that created an event, to show that a plugin can
write as well as read — but the plugin API hands over `D2` unrestricted, so a write is the same SDK
call the app itself makes and demonstrating it taught nothing the read does not. It is in the git
history if the next iteration, which narrows that access, needs somewhere to start.

## Logic scenarios

@L1
Given the repository returns a summary for "Child Programme" with 36 enrolled and 71 events
When the card loads
Then the summary state is Loaded, reporting the program name "Child Programme", 36 enrolled and
71 events
And the loaded summary names the programme the repository resolved, so nothing above the
repository has to be told which programme to show

@L2
Given the repository returns a summary with 3 recent people, each labelled from the attributes the
programme marks `displayInList`, in the programme's own sort order
When the card loads
Then the loaded summary carries those 3 people in that order, each under a label a human recognises
And no person is labelled with a UID

@L3
Given the repository returns a summary with no recent people
When the card loads
Then the loaded summary carries no people

@L4
Given the repository fails to load the summary with the message "Program not found"
When the card loads
Then the summary state is Failed with that message

@L5
Given the repository returns a summary with 5 recent people
When the card loads
Then the loaded summary carries only 3 — the shared display budget — because the host's column does
not scroll, and that promise is made to the host rather than by the query

@L6
Given the repository returns a summary with 2 recent people
When the card loads
Then the loaded summary carries both, unchanged

## Device scenarios

@D1
Given a server with "Child Programme" synced to the device
When I open the home screen
Then the counts match what the tracker list shows, rendered as "36 tracked entity instance(s)
available offline" and "71 event(s) in this program"

@D2
Given 36 enrolled and 3 shown
When I read the card
Then it shows 3 people by the name the programme lists them under — the same name the app's own
tracker list shows for each — then "… and 33 more". That number is `enrolledCount` minus the rows
shown, so it counts everyone not listed, not just the recent people

@D3
Given a summary with no recent people
When I read the card
Then no rows and no "and more" line are shown

@D4
Given the device has synced metadata again since the plugin loaded
When I return to the home screen
Then the card renders normally, with no `ClassCastException`

@D5
Given a programme whose enrolled people have no value for any attribute it marks `displayInList`
When I read the card
Then each row shows the org unit's name, and never a UID

@D6
Given a programme with several hundred enrolments
When I open the home screen
Then the card appears without a visible delay, because only the three rows shown are resolved with
their attribute values
<!-- No JVM test can observe the shape of a query. What enforces this mechanically is the build's
     `cap-before-enrichment` rule, from plugin-sdk-gradle. -->

## Metadata needs

- A tracker program with enrollments and events — `Child Programme` in the DHIS2 demo database works,
  and has both.
- The programme is whichever tracker programme sorts first by name, since the plugin resolves rather
  than names one. On the DHIS2 demo database that is not necessarily `Child Programme`.
- The programme must mark at least one tracked entity attribute `displayInList`, with a sort order,
  or every row falls through to the org unit name — which is correct behaviour but makes `@D2`
  untestable. `Child Programme` marks first and last name.
- For `@D7`, at least one enrolled person with no value for any of those attributes.

## UI budget

Capped at 320 dp — it sits above the host's program list and must not push it off screen. The counts
and program name are always visible. The recent-people list is capped at three rows with an
"… and N more" line rather than growing, and the card bounds itself with `heightIn(max = …)` plus
`verticalScroll` so any overflow scrolls inside the plugin instead of pushing the host's content away.

That cap of three is **one constant, shared**, because three different callers make three different
promises with it:

- `PluginViewModel` keeps the host's non-scrolling column intact. This is the promise to the host,
  and the only one a JVM test can reach — see `@L9`.
- `PluginCard` is the backstop for a `@Preview` or a harness that bypasses the ViewModel.
- `D2PluginRepository` avoids resolving rows nobody will see. An efficiency measure, not a promise.

Two unshared private threes is how the ViewModel came to enforce none of it.
