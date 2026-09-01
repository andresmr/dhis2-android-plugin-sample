# Program summary card

> Worked example, written after the fact from the plugin already in this repo — so you can read the
> spec and the code side by side. A real spec comes first; see `overdue-events.md` for one that does.
>
> Note where the lines fall: the logic scenarios name **state** (`Loaded`, `Failed`, counts, whether
> the repository was re-asked), because that is what a JVM test can assert. The exact strings on
> screen are device scenarios — nothing here renders the card.

## Intent

A health worker opening the app wants to know, without navigating anywhere, whether the program they
work in has data on this device: how many people are enrolled, how many events exist, and who was
seen recently. The plugin also offers one write — adding an event — because a card that can only read
proves half of what the plugin API can do.

## Logic scenarios

Given the repository returns a summary for "Child Programme" with 36 enrolled and 71 events
When the card loads
Then the summary state is Loaded, reporting the program name "Child Programme", 36 enrolled and
71 events
And the repository was asked for exactly the configured program UID

Given the repository returns a summary with 3 recent people
When the card loads
Then the loaded summary carries those 3 people, each with its attribute labels resolved

Given the repository returns a summary with no recent people
When the card loads
Then the loaded summary carries no people

Given the repository fails to load the summary with the message "Program not found"
When the card loads
Then the summary state is Failed with that message

Given a loaded summary
When the write succeeds with event UID "abc123"
Then the write state is Succeeded with "abc123"
And the summary state is still Loaded

Given a loaded summary
When the write fails with the message "Write refused"
Then the write state is Failed with that message
And the summary state is still Loaded, because a failed write is not a card-level failure
And the repository was not asked to reload

Given a loaded summary
When the write succeeds
Then the repository is asked for the summary a second time, so the new event is counted

Given the repository returns a summary with no write target
When the card loads
Then the loaded summary carries no write target, so the card offers no write control

## Device scenarios

Given a server with "Child Programme" synced to the device
When I open the home screen
Then the counts match what the tracker list shows, rendered as "36 tracked entity instance(s)
available offline" and "71 event(s) in this program"

Given 36 enrolled and 3 shown
When I read the card
Then it shows the 3 rows under their attribute labels, then "… and 33 more"

Given a summary with no recent people
When I read the card
Then no rows and no "and more" line are shown

Given the card is showing an event count
When I tap the write control
Then the count increases by one and "Created event <uid>" appears

Given a user without write access to the program
When I tap the write control
Then the card shows the SDK's own refusal message and keeps the summary on screen

Given the device has synced metadata again since the plugin loaded
When I return to the home screen
Then the card renders normally, with no `ClassCastException`

## Metadata needs

- A tracker program with enrollments and events — `Child Programme` in the DHIS2 demo database works,
  and has both.
- The logged-in user needs data-write access to that program and its first program stage for the
  write scenarios, and a second user *without* it for the refusal scenario.
- At least one enrollment must resolve an org unit the user can write to, or no write target exists
  and the control is correctly hidden.
- Tracked entity attributes configured for display, so rows render under labels rather than UIDs.

## UI budget

Resting height around 260 dp — it sits above the host's program list and must not push it off
screen. The counts and program name are always visible. The recent-people list is capped at three
rows with an "… and N more" line rather than growing, and the card bounds itself with
`heightIn(max = …)` plus `verticalScroll` so any overflow scrolls inside the plugin instead of
pushing the host's content away.
