#!/usr/bin/env python3
"""Check that every logic scenario in specs/ is claimed by a test, and vice versa.

The project's central claim is that a spec is the contract: every `## Logic scenarios` entry is
paired with the test that asserts it. Until this script existed that pairing was honour-system, and
it had already slipped — four of the eight scenarios in the worked example had no test at all, which
nothing noticed because nothing was looking.

The link is two markers, one on each side:

  * In a spec, a line `@L1` directly above the scenario's `Given`. `@D1` for device scenarios.
  * In a test, a comment containing `spec: <spec-slug> L1` — several ids separated by commas, and a
    scenario may be claimed by more than one test.

The slug is the spec's filename without `.md`. What gets enforced:

  1. Every logic scenario is claimed by at least one test. This is the gate.
  2. Every claim names a scenario that exists — so renaming a scenario cannot silently orphan a test.
  3. Every scenario carries a tag, or the gate could be dodged by not tagging.
  4. Ids are unique within a spec.
  5. No test claims a device scenario. A `## Device scenarios` entry needs a real `D2`, so a JVM
     test claiming one is asserting something other than what the spec says — the category error the
     two-section format exists to prevent.

Device scenarios are deliberately *not* required to have tests. They are the half no test covers,
and this script prints them as the manual checklist instead.

Usage:
    python3 tools/check-specs.py                                # your own plugin: specs/ + plugin/src/
    python3 tools/check-specs.py --module examples/program-summary
"""

import re
import sys
from pathlib import Path

# Defaults point at the fork's own plugin. `--module examples/program-summary` points them at a
# self-contained example instead: the specs and the tests that claim them move together, so an
# example's scenarios are never the fork's obligation and deleting examples/ leaves nothing behind.
DEFAULT_SPEC_DIR = Path("specs")
DEFAULT_SOURCE_ROOT = Path("plugin/src")
TEST_SOURCE_SETS = ("commonTest", "androidHostTest")
# Not specs: one is the format's own documentation, the other the blank to copy. Both talk *about*
# Given/When/Then, so parsing them as specs would invent scenarios that do not exist.
NOT_A_SPEC = {"README.md", "TEMPLATE.md"}

HTML_COMMENT = re.compile(r"<!--.*?-->", re.S)
TAG_LINE = re.compile(r"^@([LD]\d+)$")
# The same marker anywhere else on a line: a cross-reference in prose ("see `@L5`"). Two of these
# pointed at scenarios that never existed, because nothing was looking — the exact failure the
# test-claim gate was written for, one level up.
INLINE_REFERENCE = re.compile(r"@([LD]\d+)")
SECTION = re.compile(r"^##\s+(.+?)\s*$")
SCENARIO_START = re.compile(r"^Given\b")
CLAIM = re.compile(r"spec:\s*([A-Za-z0-9._-]+)\s+([LD]\d+(?:\s*,\s*[LD]\d+)*)")


class Spec:
    def __init__(self, slug):
        self.slug = slug
        self.logic = {}       # id -> line number
        self.device = {}      # id -> line number
        self.references = {}  # id -> line number, for @Lx mentioned in prose rather than tagging
        self.problems = []

    def ids(self):
        return set(self.logic) | set(self.device)


def parse_spec(path):
    """Read one spec into its tagged scenarios, collecting format problems as it goes."""
    spec = Spec(path.stem)
    # Comments carry the template's guidance, including the words "Given / When / Then". Blanked
    # rather than deleted so reported line numbers still match the file on disk.
    text = HTML_COMMENT.sub(lambda m: "\n" * m.group(0).count("\n"), path.read_text())

    section = None
    pending = None          # (id, line) of the tag most recently seen
    pending_used = False

    for number, raw in enumerate(text.splitlines(), start=1):
        line = raw.strip()

        heading = SECTION.match(line)
        if heading:
            if pending and not pending_used:
                spec.problems.append(
                    f"{path}:{pending[1]}: tag @{pending[0]} is not followed by a scenario"
                )
            section = heading.group(1).lower()
            pending, pending_used = None, False
            continue

        tag = TAG_LINE.match(line)
        if not tag:
            for found in INLINE_REFERENCE.finditer(line):
                spec.references.setdefault(found.group(1), number)
        if tag:
            if pending and not pending_used:
                spec.problems.append(
                    f"{path}:{pending[1]}: tag @{pending[0]} is not followed by a scenario"
                )
            pending, pending_used = (tag.group(1), number), False
            continue

        if not SCENARIO_START.match(line):
            continue

        if section not in ("logic scenarios", "device scenarios"):
            continue  # A Given outside either section is prose, not a scenario.

        if pending is None or pending_used:
            spec.problems.append(f"{path}:{number}: scenario has no @id tag above its Given")
            continue

        scenario_id, tag_line = pending
        pending_used = True

        wanted = "L" if section == "logic scenarios" else "D"
        if not scenario_id.startswith(wanted):
            spec.problems.append(
                f"{path}:{tag_line}: @{scenario_id} is under '## {section}', "
                f"so its id must start with {wanted}"
            )
            continue

        bucket = spec.logic if wanted == "L" else spec.device
        if scenario_id in spec.ids():
            spec.problems.append(f"{path}:{tag_line}: @{scenario_id} is used more than once")
            continue
        bucket[scenario_id] = number

    if pending and not pending_used:
        spec.problems.append(
            f"{path}:{pending[1]}: tag @{pending[0]} is not followed by a scenario"
        )
    return spec


def parse_claims(test_dirs):
    """Every `spec: <slug> <ids>` marker in the test sources, as (slug, id) -> [locations]."""
    claims = {}
    for directory in test_dirs:
        if not directory.is_dir():
            continue
        for path in sorted(directory.rglob("*.kt")):
            for number, line in enumerate(path.read_text().splitlines(), start=1):
                found = CLAIM.search(line)
                if not found:
                    continue
                slug, ids = found.group(1), found.group(2)
                for scenario_id in (part.strip() for part in ids.split(",")):
                    claims.setdefault((slug, scenario_id), []).append(f"{path}:{number}")
    return claims


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    module = Path(".")
    if argv[:1] == ["--module"]:
        if len(argv) < 2:
            sys.exit("--module needs a directory, e.g. --module examples/program-summary")
        module = Path(argv[1])
        argv = argv[2:]
    if argv:
        sys.exit("unexpected argument(s): %s" % " ".join(argv))

    # At the repo root the plugin under test is :plugin, so its sources are a directory down. A
    # module passed with --module *is* the plugin, so its sources sit directly inside it.
    spec_dir = module / DEFAULT_SPEC_DIR
    source_root = DEFAULT_SOURCE_ROOT if module == Path(".") else module / "src"
    test_dirs = tuple(source_root / name for name in TEST_SOURCE_SETS)

    if not spec_dir.is_dir():
        sys.exit(
            "%s does not exist. Specs live beside the module they describe: specs/ for your own "
            "plugin, examples/<name>/specs/ for an example." % spec_dir
        )

    specs = [
        parse_spec(path)
        for path in sorted(spec_dir.glob("*.md"))
        if path.name not in NOT_A_SPEC
    ]
    by_slug = {spec.slug: spec for spec in specs}
    claims = parse_claims(test_dirs)

    failures = [problem for spec in specs for problem in spec.problems]

    # 1. Every logic scenario is claimed.
    for spec in specs:
        for scenario_id, number in sorted(spec.logic.items()):
            if (spec.slug, scenario_id) not in claims:
                failures.append(
                    f"{spec_dir}/{spec.slug}.md:{number}: @{scenario_id} has no test — add a "
                    f"comment `spec: {spec.slug} {scenario_id}` to the test that asserts it"
                )

    # 2 and 5. Every claim names a logic scenario that exists.
    for (slug, scenario_id), locations in sorted(claims.items()):
        spec = by_slug.get(slug)
        where = ", ".join(locations)
        if spec is None:
            failures.append(f"{where}: claims spec '{slug}', which is not a file in {spec_dir}/")
        elif scenario_id in spec.device:
            failures.append(
                f"{where}: claims @{scenario_id}, a device scenario — those need a real D2, "
                f"so no JVM test can assert one"
            )
        elif scenario_id not in spec.logic:
            failures.append(
                f"{where}: claims @{scenario_id}, which {spec_dir}/{slug}.md does not define"
            )

    # 6. Every @Lx mentioned in a spec's prose names a scenario that spec actually defines.
    for spec in specs:
        for scenario_id, number in sorted(spec.references.items()):
            if scenario_id not in spec.ids():
                failures.append(
                    f"{spec_dir}/{spec.slug}.md:{number}: refers to @{scenario_id}, which this "
                    f"spec does not define"
                )

    if failures:
        print("  spec ↔ test link broken:")
        for failure in failures:
            print(f"    {failure}")
        return 1

    logic = sum(len(spec.logic) for spec in specs)
    device = sum(len(spec.device) for spec in specs)
    print(
        f"  {logic} logic scenario(s) across {len(specs)} spec(s), each claimed by a test; "
        f"{device} device scenario(s) left for the checklist"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
