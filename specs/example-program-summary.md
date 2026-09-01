# Program summary card

> Worked example. This describes the plugin that already exists in this repo, written after the
> fact — so you can read the spec and the code side by side. A real spec is written first.

## Intent

A health worker opening the app wants to know, without navigating anywhere, whether the program they
work in has data on this device: how many people are enrolled, how many events exist, and who was
seen recently. The plugin also offers one write — adding an event — because a card that can only read
proves half of what the plugin API can do.

## Logic scenarios

Given the repository returns a summary for "Child Programme" with 36 enrolled and 71 events
When the card loads
Then it shows the program name "Child Programme"
And it shows "36 tracked entity instance(s) available offline"
And it shows "71 event(s) in this program"

Given the repository returns a summary with 36 enrolled and only 3 recent people
When the card loads
Then it lists those 3 people with their attribute labels
And it shows "… and 33 more"

Given the repository returns a summary with no recent people
When the card loads
Then it lists nobody
And it does not show a "… and more" line

Given the repository fails to load the summary with the message "Program not found"
When the card loads
Then it shows "Program not found"
And it shows no counts

Given a loaded summary is on screen
When the write succeeds with event UID "abc123"
Then it shows "Created event abc123"
And the summary is still on screen

Given a loaded summary is on screen
When the write fails with the message "Write refused"
Then it shows "Write refused"
And the summary is still on screen

Given a loaded summary is on screen
When the write succeeds
Then the summary is reloaded, so the event count reflects the new event

Given the summary has no write target
When the card loads
Then the write control is not offered

## Device scenarios

Given a server with "Child Programme" synced to the device
When I open the home screen
Then the enrolled and event counts match what the tracker list shows for that program

Given the card is showing an event count
When I tap the write control
Then the count increases by one and the new event UID is shown

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
