#!/usr/bin/env python3
"""Enforce the architecture rules that can be enforced.

CLAUDE.md says it plainly about `androidMain`: "when a rule matters for correctness and its only
enforcement sits [somewhere no test can reach], it is not enforced, it is hoped for." That applies
to CLAUDE.md itself. A rule whose only enforcement is a paragraph asking nicely is decoration, and
an audit found three such rules that the code had quietly stopped following.

So the rules that are mechanical are checked here, and the rest are labelled in CLAUDE.md as not
enforced, which is at least honest. Each check below names the rule it implements.

What is deliberately *not* here: anything needing to understand the code rather than find a token.
"Prefer layout APIs without default arguments" and "count in SQL, enrich only the N" are real rules
that no grep can settle; they stay prose, and say so.
"""

import re
import sys
from pathlib import Path

PLUGIN = Path("plugin/src")
COMMON_MAIN = PLUGIN / "commonMain"
COMMON_TEST = PLUGIN / "commonTest"
ANDROID_MAIN = PLUGIN / "androidMain"
BUILD_FILE = Path("plugin/build.gradle.kts")

BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")


def code_of(path):
    """A file's source with comments blanked, so a rule quoted in a KDoc is not read as a breach.

    Newlines are preserved so reported line numbers still match the file.
    """
    text = path.read_text()
    text = BLOCK_COMMENT.sub(lambda m: "\n" * m.group(0).count("\n"), text)
    return LINE_COMMENT.sub("", text)


def lines_matching(path, pattern):
    return [
        (number, line.strip())
        for number, line in enumerate(code_of(path).splitlines(), start=1)
        if pattern.search(line)
    ]


def kotlin_under(directory):
    return sorted(directory.rglob("*.kt")) if directory.is_dir() else []


def check_sdk_is_in_one_place(failures):
    """Architecture rule 1: only androidMain may see the SDK, because `D2` is the Android SDK.

    This is the load-bearing one. It is what keeps state, UI and their tests on the JVM, and what
    makes the next iteration — narrowing the plugin's SDK access — a one-file change.
    """
    sdk = re.compile(r"\borg\.hisp\.dhis\b")
    for directory in (COMMON_MAIN, COMMON_TEST):
        for path in kotlin_under(directory):
            for number, line in lines_matching(path, sdk):
                failures.append(
                    f"{path}:{number}: the DHIS2 SDK is only allowed in androidMain — {line}"
                )

    # And within androidMain, in exactly one file, so the surface stays one file to change.
    seeing = [
        path for path in kotlin_under(ANDROID_MAIN)
        if lines_matching(path, sdk)
    ]
    allowed = {
        ANDROID_MAIN / "kotlin/org/dhis2/mobile/plugin/sample/data/D2PluginRepository.kt",
        ANDROID_MAIN / "kotlin/org/dhis2/mobile/plugin/sample/ProgramOverviewPlugin.kt",
    }
    for path in seeing:
        if path not in allowed:
            failures.append(
                f"{path}: a new file sees the SDK. Keep `D2` behind PluginRepository, or add this "
                f"file to `allowed` in tools/check-rules.py and say why in the commit"
            )


def check_composables_take_plain_data(failures):
    """Architecture rule 2: a composable never takes a `Dhis2PluginContext`.

    That is what lets @Preview render the real UI with no server.
    """
    context = re.compile(r"\bDhis2PluginContext\b")
    for path in kotlin_under(COMMON_MAIN):
        for number, line in lines_matching(path, context):
            failures.append(
                f"{path}:{number}: commonMain must not reference Dhis2PluginContext — {line}"
            )


def check_repository_returns_result(failures):
    """Architecture rule 4: a repository returns `Result`, never throws.

    An exception escaping into the host composition takes the enclosing screen with it, and Compose
    cannot express an error boundary around a composable call.
    """
    path = COMMON_MAIN / "kotlin/org/dhis2/mobile/plugin/sample/repository/PluginRepository.kt"
    if not path.is_file():
        failures.append(f"{path}: missing — the interface is the seam the whole design rests on")
        return
    for number, line in lines_matching(path, re.compile(r"\bfun\b")):
        if "Result<" not in line:
            failures.append(
                f"{path}:{number}: every PluginRepository function returns Result — {line}"
            )


def check_card_bounds_its_height(failures):
    """Architecture rule 3: the host slot does not scroll, so the card bounds itself."""
    path = COMMON_MAIN / "kotlin/org/dhis2/mobile/plugin/sample/ui/PluginCard.kt"
    if not path.is_file():
        return
    code = code_of(path)
    for needed, why in (
        ("heightIn(", "caps its height, or it eats the host's program list"),
        ("verticalScroll(", "scrolls its overflow, or content past the cap is unreachable"),
    ):
        if needed not in code:
            failures.append(f"{path}: PluginCard {why} — no `{needed}` found")


def check_host_provided_deps_are_compile_only(failures):
    """Build rule 1: `compileOnly` everything host-provided, except compose.components.resources.

    Bundling a class the host already owns is what produces ClassCastException at load time. The one
    exception is the CMP plugin's opt-in signal for generating `Res`, which must be `implementation`
    or `Res.*` stops resolving.
    """
    if not BUILD_FILE.is_file():
        failures.append(f"{BUILD_FILE}: missing")
        return

    code = code_of(BUILD_FILE)
    # Only the commonMain dependencies block — commonTest deliberately uses real dependencies,
    # because a unit test has no host to borrow classes from.
    block = re.search(r"val commonMain by getting \{(.*?)\n        \}", code, re.S)
    if not block:
        failures.append(f"{BUILD_FILE}: cannot find the commonMain source set to check")
        return

    host_provided = re.compile(r"\b(compose|libs)\.")
    for raw in block.group(1).splitlines():
        line = raw.strip()
        if not host_provided.search(line):
            continue
        is_resources = "compose.components.resources" in line
        if is_resources:
            if not line.startswith("implementation("):
                failures.append(
                    f"{BUILD_FILE}: compose.components.resources must be `implementation` — it is "
                    f"the CMP plugin's opt-in signal for generating Res — got: {line}"
                )
        elif not line.startswith("compileOnly("):
            failures.append(
                f"{BUILD_FILE}: host-provided dependencies are compileOnly — got: {line}"
            )


def main():
    failures = []
    checks = (
        check_sdk_is_in_one_place,
        check_composables_take_plain_data,
        check_repository_returns_result,
        check_card_bounds_its_height,
        check_host_provided_deps_are_compile_only,
    )
    for check in checks:
        check(failures)

    if failures:
        print("  architecture rules broken:")
        for failure in failures:
            print(f"    {failure}")
        return 1

    print(f"  {len(checks)} rule(s) checked: SDK confined to androidMain, composables take plain "
          f"data, repository returns Result, card bounds its height, host deps compileOnly")
    return 0


if __name__ == "__main__":
    sys.exit(main())
