#!/usr/bin/env python3
"""Does the source tree agree with plugin.json?

plugin.json now drives the Android namespace, the bundle's `entryPoint`, the Compose Resources
package and the harness's applicationId. Nothing in Gradle checks that the *Kotlin* agrees with it.
Change `package` by hand and the build stays green, the bundle is produced and signed, and the host
fails at load with ClassNotFoundException — the one failure mode nothing else here can see, because
it lives in the gap between a string in a config file and a class on a classpath.

It is also the detector `./init.sh` uses, which is why the states are exit codes rather than prose:

    0  initialised, and the tree agrees
    3  pristine — still the template, nothing renamed yet
    4  partially initialised, or inconsistent

Run directly for the answer, or `./init.sh --check` for the same thing with a friendlier preamble.
"""

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from identity import (  # noqa: E402
    SUPPORTED_INJECTION_POINTS,
    TEMPLATE,
    is_pristine,
    load,
    validate,
)

OK, PRISTINE, INCONSISTENT = 0, 3, 4

PLUGIN_SOURCE_SETS = (
    Path("plugin/src/commonMain/kotlin"),
    Path("plugin/src/androidMain/kotlin"),
    Path("plugin/src/commonTest/kotlin"),
    Path("plugin/src/androidHostTest/kotlin"),
)

# Where a leftover template token is expected rather than wrong: the worked example is *about* the
# sample and rewriting it would make it describe a plugin that does not exist, and the licence is a
# legal statement no tool should edit.
EXEMPT_PREFIXES = ("examples/", "specs/examples/", "LICENSE", "tools/identity.py")

SCANNED_SUFFIXES = {
    ".kt", ".kts", ".xml", ".md", ".py", ".pro", ".properties", ".json", ".sh", ".yml", ".yaml",
}

SKIPPED_DIRS = {".git", "build", ".gradle", ".idea", ".kotlin", ".m2"}

PACKAGE_LINE = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)", re.M)


def scanned_files(root=Path(".")):
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.suffix not in SCANNED_SUFFIXES:
            continue
        if any(part in SKIPPED_DIRS for part in path.parts):
            continue
        if path.as_posix().startswith(EXEMPT_PREFIXES):
            continue
        yield path


def check_entry_point(problems, identity):
    """The class the bundle's `entryPoint` names must exist, and must be a Dhis2Plugin."""
    path = Path(
        "plugin/src/androidMain/kotlin/%s/%s.kt"
        % (identity["packagePath"], identity["entryPoint"])
    )
    if not path.is_file():
        problems.append(
            "%s does not exist, but plugin.json's entryPoint names %s. The bundle would be built "
            "and signed, and the host would fail at load with ClassNotFoundException."
            % (path, identity["entryPointFqcn"])
        )
        return

    source = path.read_text(encoding="utf-8")
    declared = PACKAGE_LINE.search(source)
    if not declared or declared.group(1) != identity["package"]:
        problems.append(
            "%s declares package %s, but plugin.json says %s."
            % (path, declared.group(1) if declared else "nothing", identity["package"])
        )
    if not re.search(r"\bclass\s+%s\b" % re.escape(identity["entryPoint"]), source):
        problems.append("%s does not declare `class %s`." % (path, identity["entryPoint"]))
    if "Dhis2Plugin" not in source:
        problems.append(
            "%s does not mention Dhis2Plugin. The host instantiates the entry point and casts it; "
            "a class that does not implement the interface fails at load." % path
        )


def check_packages_match_paths(problems, identity):
    """Every Kotlin file under :plugin sits at the directory its package declaration implies."""
    for source_set in PLUGIN_SOURCE_SETS:
        if not source_set.is_dir():
            continue
        for path in sorted(source_set.rglob("*.kt")):
            declared = PACKAGE_LINE.search(path.read_text(encoding="utf-8"))
            if not declared:
                problems.append("%s declares no package." % path)
                continue
            package = declared.group(1)
            if not package.startswith(identity["package"]):
                problems.append(
                    "%s declares package %s, which is outside plugin.json's %s."
                    % (path, package, identity["package"])
                )
                continue
            expected = source_set / package.replace(".", "/")
            if path.parent != expected:
                problems.append(
                    "%s declares package %s, so it belongs in %s." % (path, package, expected)
                )


def check_no_template_residue(problems, identity):
    """Nothing outside the examples still carries the template's own names."""
    if is_pristine(identity):
        return
    tokens = {
        TEMPLATE["package"]: "the template's Kotlin package",
        TEMPLATE["entryPoint"]: "the template's entry-point class",
        TEMPLATE["slug"]: "the template's project slug",
    }
    for path in scanned_files():
        text = path.read_text(encoding="utf-8", errors="replace")
        for token, what in tokens.items():
            if token in text:
                problems.append(
                    "%s still contains %s (%s). ./init.sh missed it, or it was reintroduced by "
                    "hand." % (path, token, what)
                )


def check_injection_points(problems, identity):
    """A field the bundle cannot honour would be a claim nothing checks."""
    points = identity.get("injectionPoints") or SUPPORTED_INJECTION_POINTS
    unsupported = [p for p in points if p not in SUPPORTED_INJECTION_POINTS]
    if unsupported:
        problems.append(
            "plugin.json's injectionPoints names %s. plugin-sdk-gradle's DataStoreSnippet "
            "hardcodes %s and PluginBundleExtension exposes no injectionPoints property, so the "
            "generated plugin-config.json would say otherwise. Add the property upstream first."
            % (", ".join(unsupported), SUPPORTED_INJECTION_POINTS)
        )


def main():
    identity = load(strict=False)

    problems = validate(identity)
    if problems:
        print("  plugin.json is not usable:")
        for problem in problems:
            print("    %s" % problem)
        return INCONSISTENT

    initialised = bool(identity.get("template", {}).get("initialised"))
    pristine = is_pristine(identity)

    if pristine and not initialised:
        print(
            "  pristine template — %s, not yet initialised. Run ./init.sh to make it yours."
            % identity["entryPointFqcn"]
        )
        # Still worth checking: the template's own tree has to be consistent, or every fork starts
        # from something broken.
        check_entry_point(problems, identity)
        check_packages_match_paths(problems, identity)
        check_injection_points(problems, identity)
        if problems:
            print("  ...but the template itself is inconsistent:")
            for problem in problems:
                print("    %s" % problem)
            return INCONSISTENT
        return PRISTINE

    check_entry_point(problems, identity)
    check_packages_match_paths(problems, identity)
    check_no_template_residue(problems, identity)
    check_injection_points(problems, identity)

    if not initialised:
        problems.append(
            "plugin.json says template.initialised is false, so ./init.sh did not finish. "
            "Re-run the same command to resume, or `git reset --hard && git clean -fd` to go "
            "back to the template."
        )

    if problems:
        print("  the tree and plugin.json disagree:")
        for problem in problems:
            print("    %s" % problem)
        return INCONSISTENT

    print(
        "  tree agrees with plugin.json: %s, entry point %s"
        % (identity["pluginId"], identity["entryPointFqcn"])
    )
    return OK


if __name__ == "__main__":
    sys.exit(main())
