#!/usr/bin/env python3
"""Enforce the architecture rules that are this sample's own.

Two of the five checks that used to live here moved upstream into `plugin-sdk-gradle`, where every
plugin project gets them by applying the bundle plugin instead of copying a script: the SDK kept out
of shared source, and host-provided dependencies declared `compileOnly`. A third rule joined them
there, cap-before-enrichment, which nothing here ever checked. Run them with
`./gradlew :plugin:checkPluginConventions` — `./verify.sh` does.

What stays here is what upstream has no business knowing: `PluginRepository` and `PluginCard` are
types this sample invented, and the plugin system should not grow an interface for plugin authors to
implement just so a rule can look for it. That is the line — a convention of the *plugin system*
belongs in the build; a convention of *this plugin* belongs in this repo.

Original note, still true of what is left:

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


def check_sdk_is_in_one_file(failures):
    """The sample's own choice: the SDK appears in exactly the two files we nominated.

    The *generic* half of this — never in commonMain or commonTest — moved upstream to
    `checkPluginConventions`, because it is a property of the plugin system rather than of this
    repo. What is left is a list of filenames only this repo can have an opinion about.
    """
    sdk = re.compile(r"\borg\.hisp\.dhis\b")
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


def main():
    failures = []
    checks = (
        check_sdk_is_in_one_file,
        check_repository_returns_result,
        check_card_bounds_its_height,
    )
    for check in checks:
        check(failures)

    if failures:
        print("  architecture rules broken:")
        for failure in failures:
            print(f"    {failure}")
        return 1

    print(
        f"  {len(checks)} sample rule(s) checked: SDK in one file, repository returns Result, "
        f"card bounds its height  (the plugin system's own rules: checkPluginConventions)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
