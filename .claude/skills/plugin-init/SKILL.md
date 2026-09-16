---
name: plugin-init
description: One-time setup of a fork of the DHIS2 Android plugin template — agree the plugin's name, package, id and entry point, run ./init.sh, and get to a green verify. Use when the repository has not been initialised yet, when ./init.sh --check reports "pristine template", or when the user asks to start a new plugin.
---

# Initialise this plugin

Follow [`docs/workflows/initialise-plugin.md`](../../../docs/workflows/initialise-plugin.md)
exactly. Read it now.

The workflow lives there rather than here so that it works for any agent, and for a human with none.
This file is the Claude Code entry point and holds no instructions of its own.

Two things worth knowing before you start reading:

- `./init.sh --check` says whether this has already been done: exit 0 initialised, 3 pristine,
  4 partially initialised.
- Pass every value as a flag. `./init.sh` will not prompt without a terminal, by design.
