# <Feature name>

<!--
  Copy this file to specs/<your-feature>.md and fill it in. Keep the five headings and their order:
  the pipeline reads them by name, so a renamed section is one it does not read. An empty section is
  fine — it reads as "considered, nothing to say" rather than "forgotten".

  Delete these comments as you go. Two complete examples sit beside this file.
-->

## Intent

<!--
  One paragraph, in the words of the person using the app rather than the code. Why does a health
  worker want this on their home screen? If this is hard to write, the feature is not ready to
  specify yet.
-->

## Logic scenarios

<!--
  Given / When / Then, one blank line between scenarios. These become tests in
  plugin/src/commonTest/ and run on the JVM with no device.

  A scenario belongs here when both halves hold:

    1. Its GIVEN can be arranged by handing the ViewModel a fake PluginRepository.
    2. Its THEN names something the UI state exposes — a case of a sealed interface, a value, a
       count, a message — or a call the repository should or should not have received.

  Point 2 is the one that catches people out. The tests assert on state, not on pixels: they call
  advanceUntilIdle() and read state.value. "Then the card shows '3 overdue'" cannot be asserted
  here, because no JVM test renders the card. Two ways out, in order of preference:

    - Push the derived value into the state. If the card computes "and N more" from two numbers,
      have the state carry N. Then it is assertable, and the card gets simpler.
    - Move the scenario to Device scenarios, and phrase it there as the string on screen.

  Never assert on how many times the state was emitted. Emission counts change whenever anything
  else in the load path changes, and every test coupled to them breaks together for no reason. This
  has already happened once in this project.
-->

## Device scenarios

<!--
  Given / When / Then for anything needing a real DHIS2 read or write, and for what is actually
  rendered. These CANNOT be automated: Dhis2PluginContext.sdk is a D2, which cannot be constructed
  outside a logged-in app, so no test here can exercise the SDK or the composed UI.

  They become a manual checklist the pipeline prints at the end. Keep them few — each one is a step
  someone repeats by hand on every change.

  When unsure which section a scenario belongs to, put it here. A logic scenario that turns out to
  need a device is discovered after the approval gate has already been spent on it.
-->

## Metadata needs

<!--
  What must already exist on the DHIS2 server for the device scenarios to be runnable: programs,
  program stages, data sets, org units, and whether the logged-in user needs write access. Specific
  enough that someone else could set the server up from this list.
-->

## UI budget

<!--
  The host renders the plugin in a NON-SCROLLING column above its own program list. Height taken
  here is height the app loses, and anything past the viewport is unreachable rather than
  scrollable.

  State the resting height, what is visible without interaction, and what hides behind a toggle. If
  a section can grow with the data, say what bounds it.
-->
