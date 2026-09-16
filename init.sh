#!/usr/bin/env bash
#
# One-time setup: turn this template into your plugin.
#
# A wrapper, so that the first command a forker runs is one word and lives beside ./verify.sh
# rather than somewhere under tools/.
#
# Usage:
#   ./init.sh                 # prompt for each value
#   ./init.sh --name "…" …    # fully determined; the form to use from an agent
#   ./init.sh --dry-run       # print the plan, change nothing
#   ./init.sh --check         # report the state, change nothing
#   ./init.sh --help          # every flag
set -euo pipefail
cd "$(dirname "$0")"
command -v python3 >/dev/null 2>&1 || {
  echo "python3 is required (the gates in tools/ are Python too)." >&2
  exit 1
}
exec python3 tools/init-plugin.py "$@"
