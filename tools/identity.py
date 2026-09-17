#!/usr/bin/env python3
"""Who this plugin is. The one place a Python tool learns it.

`settings.gradle.kts` reads the same `plugin.json`, so the build and the gates cannot disagree about
the package, the id or the entry point — which they could, and did, when the answer lived in five
files at once.

Derived values are computed here and never stored in the file. A package path written down beside
the package it comes from is a second copy, and two copies is how they drift apart.

Targets Python 3.8: the system interpreter on a current macOS is 3.9, so no `match`, no `tomllib`,
no PEP-604 unions.
"""

import json
import re
import sys
from pathlib import Path

REQUIRED = ("name", "slug", "pluginId", "package", "entryPoint", "version")

# What the un-initialised template ships as. `init-plugin.py` rewrites *from* these, and
# `check-identity.py` reports a tree still carrying them as pristine rather than broken.
TEMPLATE = {
    "name": "My DHIS2 plugin",
    "slug": "dhis2-android-plugin-template",
    "package": "org.dhis2.mobile.plugin.template",
    "entryPoint": "MyPlugin",
}

# plugin-sdk-gradle's DataStoreSnippet hardcodes the list it writes into plugin-config.json, and
# PluginBundleExtension has no injectionPoints property to override it. So any other value here
# would be a claim the bundle does not honour, and a grant nothing checks reads like a control.
SUPPORTED_INJECTION_POINTS = ["HOME_ABOVE_PROGRAM_LIST"]

PACKAGE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")
PLUGIN_ID = re.compile(r"^[a-z][a-z0-9]*(\.[a-z0-9][a-z0-9-]*)+$")
ENTRY_POINT = re.compile(r"^[A-Z][A-Za-z0-9]*$")
VERSION = re.compile(r"^\d+\.\d+\.\d+([-.][0-9A-Za-z.-]+)?$")
SLUG = re.compile(r"^[a-z0-9][a-z0-9-]*$")

# A package segment that is a Kotlin or Java keyword reaches Compose Resources as
# `packageOfResClass`, which then emits a generated `Res` class in a package needing backticks —
# and the failure is a codegen error that never mentions the package you typed.
RESERVED = {
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
    "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true",
    "try", "typealias", "typeof", "val", "var", "when", "while",
    "abstract", "assert", "boolean", "byte", "case", "catch", "char", "const", "default",
    "double", "enum", "extends", "final", "finally", "float", "goto", "implements", "import",
    "instanceof", "int", "long", "native", "new", "private", "protected", "public", "short",
    "static", "strictfp", "switch", "synchronized", "throws", "transient", "void", "volatile",
}


def validate(data):
    """Field-level problems, as a list of sentences. Empty means the values are usable."""
    problems = []

    for key in REQUIRED:
        if not data.get(key):
            problems.append('plugin.json is missing "%s".' % key)
    if problems:
        return problems

    if not PACKAGE.match(data["package"]):
        problems.append(
            'package "%s" is not a Kotlin package: lower-case segments separated by dots, at '
            "least two of them." % data["package"]
        )
    else:
        reserved = [s for s in data["package"].split(".") if s in RESERVED]
        if reserved:
            problems.append(
                'package "%s" uses the reserved word(s) %s as a segment. Compose Resources would '
                "emit a Res class in a package needing backticks, and the error it produces names "
                "neither." % (data["package"], ", ".join(sorted(reserved)))
            )

    if not PLUGIN_ID.match(data["pluginId"]):
        problems.append(
            'pluginId "%s" should be reverse-domain, e.g. org.myorg.my-plugin.' % data["pluginId"]
        )
    if not ENTRY_POINT.match(data["entryPoint"]):
        problems.append(
            'entryPoint "%s" should be a simple class name in PascalCase, with no package.'
            % data["entryPoint"]
        )
    if not VERSION.match(data["version"]):
        problems.append('version "%s" should look like 1.2.3.' % data["version"])
    if not SLUG.match(data["slug"]):
        problems.append(
            'slug "%s" should be kebab-case: it becomes rootProject.name, and a space there '
            "reaches Compose Resources as a backtick-escaped package." % data["slug"]
        )

    points = data.get("injectionPoints") or SUPPORTED_INJECTION_POINTS
    unsupported = [p for p in points if p not in SUPPORTED_INJECTION_POINTS]
    if unsupported:
        problems.append(
            "injectionPoints names %s, which the bundle cannot honour: plugin-sdk-gradle's "
            "DataStoreSnippet hardcodes %s and PluginBundleExtension has no injectionPoints "
            "property. Add it upstream first — a field that lies is worse than no field."
            % (", ".join(unsupported), SUPPORTED_INJECTION_POINTS)
        )

    return problems


def derive(data):
    """The four values computed from `package`, added in place."""
    data["packagePath"] = data["package"].replace(".", "/")
    data["entryPointFqcn"] = "%s.%s" % (data["package"], data["entryPoint"])
    data["resourcePackage"] = "%s.generated.resources" % data["package"]
    data["harnessApplicationId"] = "%s.harness" % data["package"]
    return data


def load(root=Path("."), strict=True):
    """Parse plugin.json, or exit with a message a human can act on."""
    path = Path(root) / "plugin.json"
    if not path.is_file():
        sys.exit(
            "plugin.json is missing, so nothing here knows what this plugin is called.\n"
            "  A fresh fork: run ./init.sh\n"
            "  Deleted by accident: git checkout plugin.json"
        )
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except ValueError as error:
        sys.exit("plugin.json is not valid JSON: %s" % error)

    if strict:
        problems = validate(data)
        if problems:
            sys.exit(
                "plugin.json is not usable:\n"
                + "\n".join("  - %s" % p for p in problems)
                + "\n  See tools/plugin.schema.json, or re-run ./init.sh --force."
            )

    return derive(data)


def is_pristine(data):
    """True when this is still the template rather than somebody's plugin."""
    return data.get("package") == TEMPLATE["package"]


def expand(pattern, identity):
    """`{packagePath}` and `{entryPoint}` in a conventions glob."""
    return pattern.format(**identity)


def _as_regex(pattern):
    """A glob to a regex, with the two cases `fnmatch` gets wrong for paths.

    `fnmatch` has no notion of a separator, so `*` there would happily span directories and `**`
    would mean nothing special. Both matter here: `data/*.kt` must not reach into a subdirectory,
    and `data/**/*.kt` must match `data/D2PluginRepository.kt` — a `**` that matches *zero*
    segments, which is the case naive implementations miss and the one this repo actually uses.
    """
    out = []
    index = 0
    while index < len(pattern):
        char = pattern[index]
        if pattern.startswith("**/", index):
            out.append("(?:[^/]+/)*")       # zero or more whole segments
            index += 3
        elif pattern.startswith("**", index):
            out.append(".*")
            index += 2
        elif char == "*":
            out.append("[^/]*")             # within one segment only
            index += 1
        elif char == "?":
            out.append("[^/]")
            index += 1
        else:
            out.append(re.escape(char))
            index += 1
    return re.compile("".join(out) + r"\Z")


def paths_matching(pattern, identity, root=Path(".")):
    """Files matching a conventions glob, as paths relative to `root`."""
    regex = _as_regex(expand(pattern, identity))
    root = Path(root)
    return sorted(
        path
        for path in root.rglob("*")
        if path.is_file() and regex.match(path.as_posix())
    )
