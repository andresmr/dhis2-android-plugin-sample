---
name: plugin-from-spec
description: Build a DHIS2 Android plugin feature from a written specification in specs/. Use when the user points at a spec file, asks to implement a spec, or describes a feature as Given/When/Then. Runs spec → approval gate → failing tests → implementation → verification.
---

# Plugin from spec

Follow [`docs/workflows/plugin-from-spec.md`](../../../docs/workflows/plugin-from-spec.md) exactly.
Read it now, and read `AGENTS.md` before touching anything.

The workflow lives there rather than here so that it works for any agent, and for a human with none.
This file is the Claude Code entry point and holds no instructions of its own — anything added here
would be a second copy, and the two would drift.

If the user named a spec file, start with it. If not, list `specs/` and ask which.
