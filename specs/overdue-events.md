# Overdue events

> The first feature specified before its code, and how the harness got validated.
>
> Three scenarios here were tightened *after* the first run: "no resolvable name" turned out to be
> satisfiable by reading whichever attribute came back first, which labelled three overdue people
> "Female", "Female", "Male". A spec that does not say what a name is does not get one. That was a
> spec gap before it was a code gap, and it is the kind the phase-01 gate can only catch if the
> spec is specific enough to argue with.

## Intent

A health worker starts the day wanting to know who has missed a visit. Today that means opening the
program, sorting a list, and reading dates. The card should answer it on the home screen: how many
events in this program are past their due date and still not done, and which few are worst, so
follow-up can start without navigating anywhere.

Overdue is the program's own judgement, not the plugin's — the SDK already models it as an event
status, and the plugin reports it rather than deciding it.

## Logic scenarios

Given the repository returns 3 overdue events
When the card loads
Then the overdue state is Loaded with a total of 3

Given the repository returns no overdue events
When the card loads
Then the overdue state is Loaded with a total of 0
And it carries no rows, so the card can say the program is up to date

Given the repository returns 12 overdue events and the card shows at most 3
When the card loads
Then the overdue state is Loaded with a total of 12
And it carries 3 rows
And it carries a remaining count of 9

Given the repository returns 2 overdue events and the card shows at most 3
When the card loads
Then it carries 2 rows
And it carries a remaining count of 0 — the card renders no "and more" line from that, which is a
device scenario below

Given the repository returns overdue events due on the 3rd, the 1st and the 2nd
When the card loads
Then the rows are ordered oldest due date first

Given the repository fails to load overdue events with the message "Overdue lookup failed"
When the card loads
Then the overdue state is Failed with that message
And the program summary is still Loaded, because one section failing is not a card-level failure

Given the summary loads and the overdue lookup is still running
When the card is composed
Then the summary state is Loaded and the overdue state is Loading

Given an overdue event whose tracked entity has values for the program's display attributes
When the card loads
Then that row's label is those values, in the program's configured order — the label the app itself
lists that person under, not whichever attribute happens to come back first

Given an overdue event whose tracked entity has no value for any of the program's display attributes
When the card loads
Then that row carries the org unit name as its label

Given an overdue event whose org unit is not on this device either
When the card loads
Then that row carries a recognisable placeholder, never a raw UID

Given a loaded card and a successful event write
When the write completes
Then the overdue total is re-read, because adding an event can change it

## Device scenarios

Given a server with a tracker program carrying at least one overdue event
When I open the home screen
Then the overdue count matches what the program's own event list reports as overdue

Given the card shows 3 overdue rows and a "and N more" line
When I read the rows
Then each shows the person's name as the app lists it — not a gender, a date of birth, or any other
attribute — with a due date, and none shows a raw UID

Given a program with no overdue events
When I open the home screen
Then the card says the program is up to date, and shows no empty list

Given the overdue section is collapsed at rest
When I expand it
Then the rows appear and the host's program list below is still reachable

Given a user whose org unit scope excludes the overdue events
When I open the home screen
Then the count is 0 rather than an error

## Metadata needs

- A tracker program with at least one program stage that has due dates — `Child Programme` in the
  DHIS2 demo database has them.
- At least four events in that program past their due date and not completed, so the cap at 3 and the
  "and N more" line are both exercised. The demo database has overdue events for `Child Programme`;
  if not, set an event's due date into the past and leave it incomplete.
- At least one overdue event whose tracked entity has no display attribute value, for the fallback
  row. If none exists, blank the display attribute on one enrollment.
- One program with **no** overdue events, for the up-to-date case.
- A second user whose org unit scope excludes those events, for the last device scenario.

## UI budget

The overdue section is **collapsed by default** and shows only its count line at rest — roughly
24 dp added to the existing card. Expanded it adds at most 3 rows plus the "and N more" line, around
96 dp, and it never grows beyond that regardless of the total.

This matters more than it sounds: the existing card already sits near its 260 dp budget in a
non-scrolling host column, so an overdue list that grew with the data would push the app's own
program list off screen where it cannot be scrolled back. The cap at 3 rows is the budget, not a
display preference.
