# Data set body

<!--
The second of the seed's two specs, one per slot. This one covers DATA_SET_INSTANCE_CONTENT: the
slot where a plugin replaces the body of the host's data set instance screen.

What it covers now is only the plumbing: which slot the entry point and the harness choose, and
the rules a replacement slot renders under. What is *rendered* there is data-set-sections.md's,
which replaced the placeholder this spec was originally written for.
-->

## Intent

Someone who opens a data set the administrator has given to this plugin should see the plugin's own
screen where the host's table would be, with the host's own top bar, save button and bottom bar
still framing it. This spec covers how that screen is *reached* — that the entry point and the
harness both pick the data set slot when, and only when, the host is really rendering one. What the
screen then shows is data-set-sections.md's.

## Logic scenarios

@L1
Given the host provides no slot arguments
When the entry point decides what to render
Then it chooses the home slot, because a slot with nothing to say about what is on screen is the
whole screen

@L2
Given the host provides arguments naming a data set instance
When the entry point decides what to render
Then it chooses the data set slot, carrying those same arguments through unchanged

@L3
Given a plugin declaring both slots, with a data set UID configured
When the harness picks the slot to render
Then it picks the data set slot, because a replacement that is configured is the most specific slot
the plugin could actually be rendered at

@L4
Given a plugin declaring both slots, with no data set UID configured
When the harness picks the slot to render
Then it picks the home slot, because a replacement with no UIDs replaces nothing — the same rule the
host applies

## Device scenarios

@D1
Given plugin.json declares DATA_SET_INSTANCE_CONTENT with the UID of a data set on my server
When I run the harness
Then it names the data set, period, org unit and attribute option combo it resolved, and the plugin
renders against that instance rather than an empty screen

@D2
Given that UID edited to one no server has
When I run the harness
Then it fails with a message naming that UID and what to change, rather than showing an empty screen

@D3
Given the data set slot is being rendered
When I look at the bottom of the plugin's content
Then the last row sits clear of where the host's save button would float, because the plugin applied
LocalSlotContentPadding

## Metadata needs

A data set that exists on the server, is assigned to at least one organisation unit inside the
logged-in user's data capture scope, and has a period type its data input periods leave open. No
data values are required: the harness resolves the instance's coordinates from metadata. What the
screen itself needs is in data-set-sections.md.

## UI budget

A replacement **owns** the region the host gives it, so filling the space is correct and scrolling is
the plugin's job — whatever renders there caps nothing.

What it must respect is `LocalSlotContentPadding`: the host's save button floats over this region,
and content that ignores the padding has a last row nobody can reach.
