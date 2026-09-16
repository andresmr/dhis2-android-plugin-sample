See [`AGENTS.md`](AGENTS.md).

That file is the project's working reference, and it is not Claude-specific — the architecture, the
rules and the build traps are the same whichever tool is reading them. Keeping one copy is the point;
a second would drift.

The spec pipeline is [`docs/workflows/plugin-from-spec.md`](docs/workflows/plugin-from-spec.md), and
first-time setup is [`docs/workflows/initialise-plugin.md`](docs/workflows/initialise-plugin.md).
Both are plain prose, runnable by hand. In Claude Code they are also `/plugin-from-spec` and
`/plugin-init`.
