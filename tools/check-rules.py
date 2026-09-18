#!/usr/bin/env python3
"""Enforce the architecture rules that are this plugin's own.

Two of the five checks that used to live here moved upstream into `plugin-sdk-gradle`, where every
plugin project gets them by applying the bundle plugin instead of copying a script: the SDK kept out
of shared source, and host-provided dependencies declared `compileOnly`. A third rule joined them
there, cap-before-enrichment, which nothing here ever checked. Run them with
`./gradlew :plugin:checkPluginConventions` — `./verify.sh` does.

What stays here is what upstream has no business knowing: the repository seam is a shape this
project chose, and the plugin system should not grow an interface for plugin authors to implement
just so a rule can look for it. That is the line — a convention of the *plugin system* belongs in the
build; a convention of *this plugin* belongs here.

A third rule used to live here: that a named card capped its own height. It is gone, and
deliberately. How much room a plugin may take is the *host's* business — it knows which slot is
being rendered and what surrounds it — so making every plugin author police it was asking them to
know the host's layout. A plugin should be free to design what it likes; see `AGENTS.md`'s
*Host slots*.

Where the remaining files live is not hardcoded: `plugin.json`'s `conventions` says, in globs, and
`./init.sh` rewrites nothing in this file. `sdkAllowed` is a list of patterns rather than filenames,
so a second repository under `data/` is allowed without editing a script while anything *else*
reaching for `D2` is still caught.

Original note, still true of what is left:

AGENTS.md says it plainly about `androidMain`: "when a rule matters for correctness and its only
enforcement sits [somewhere no test can reach], it is not enforced, it is hoped for." That applies
to AGENTS.md itself. A rule whose only enforcement is a paragraph asking nicely is decoration, and
an audit found three such rules that the code had quietly stopped following.

So the rules that are mechanical are checked here, and the rest are labelled in AGENTS.md as not
enforced, which is at least honest.

What is deliberately *not* here: anything needing to understand the code rather than find a token.
"Prefer layout APIs without default arguments" and "count in SQL, enrich only the N" are real rules
that no grep can settle; they stay prose, and say so.
"""

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from identity import load, paths_matching  # noqa: E402

PLUGIN = Path("plugin/src")
ANDROID_MAIN = PLUGIN / "androidMain"

BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")


def code_of(path):
    """A file's source with comments blanked, so a rule quoted in a KDoc is not read as a breach.

    Newlines are preserved so reported line numbers still match the file.
    """
    text = path.read_text(encoding="utf-8")
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


def allowed_by(key, identity):
    """Every file matching one of `conventions.<key>`'s globs."""
    found = set()
    for pattern in identity.get("conventions", {}).get(key, []):
        found.update(paths_matching(pattern, identity))
    return found


def check_sdk_is_in_one_file(failures, identity):
    """This plugin's own choice: the SDK appears only where `conventions.sdkAllowed` says it may.

    The *generic* half of this — never in commonMain or commonTest — moved upstream to
    `checkPluginConventions`, because it is a property of the plugin system rather than of one
    repository. What is left is a list only this repository can have an opinion about.
    """
    # `org.hisp.dhis.android`, not `org.hisp.dhis`. The DHIS2 design system is
    # `org.hisp.dhis.mobile.ui.designsystem` — also host-provided, also compileOnly, but it is UI and
    # belongs in commonMain like any other Compose import. The looser pattern flagged a file whose
    # only sin was importing DHIS2Theme, which would have taught every plugin author that using the
    # design system breaks a rule.
    sdk = re.compile(r"\borg\.hisp\.dhis\.android\b")
    allowed = allowed_by("sdkAllowed", identity)
    for path in kotlin_under(ANDROID_MAIN):
        if lines_matching(path, sdk) and path not in allowed:
            failures.append(
                "%s: a new file sees the SDK. Keep `D2` behind the repository interface, or add "
                "this path to conventions.sdkAllowed in plugin.json and say why in the commit"
                % path
            )


def check_repository_returns_result(failures, identity):
    """Architecture rule 4: a repository returns `Result`, never throws.

    An exception escaping into the host composition takes the enclosing screen with it, and Compose
    cannot express an error boundary around a composable call.
    """
    paths = allowed_by("repositoryInterfaces", identity)
    if not paths:
        failures.append(
            "no repository interface matched %s — the interface is the seam the whole design "
            "rests on, so an empty match is a finding rather than a pass"
            % ", ".join(identity.get("conventions", {}).get("repositoryInterfaces", []))
        )
        return
    for path in sorted(paths):
        for number, line in lines_matching(path, re.compile(r"\bfun\b")):
            if "Result<" not in line:
                failures.append(
                    "%s:%d: every repository function returns Result — %s" % (path, number, line)
                )


def main():
    identity = load()
    failures = []
    checks = (
        check_sdk_is_in_one_file,
        check_repository_returns_result,
    )
    for check in checks:
        check(failures, identity)

    if failures:
        print("  architecture rules broken:")
        for failure in failures:
            print("    %s" % failure)
        return 1

    print(
        "  %d rule(s) checked from plugin.json's conventions: SDK where declared, repository "
        "returns Result  (the plugin system's own rules: checkPluginConventions)" % len(checks)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
