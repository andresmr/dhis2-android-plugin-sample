#!/usr/bin/env python3
"""Turn this template into your plugin. Run once, per fork.

Invoked through `./init.sh`, which is the name a forker should have to remember.

Flags first, prompts as a fallback, and **never a prompt when stdin is not a TTY** — an agent that
meets a hung prompt looks exactly like a hung build, so a missing value there is an error naming the
flag instead.

What it does, in order, and why the order matters:

  1. Writes plugin.json with `initialised: false`, so a crash leaves a machine-readable statement of
     what was being attempted rather than a silently half-renamed tree.
  2. Moves the Kotlin directories, git-aware, each through a temporary sibling.
  3. Renames the entry-point file.
  4. Rewrites the remaining references, longest token first.
  5. Writes local.properties.example, and local.properties if it is absent.
  6. Deletes every build directory. Not `./gradlew clean` — the build may not configure mid-rename,
     and stale Compose Resources output is the likeliest first-run failure there is.
  7. Flips `initialised: true`, runs ./verify.sh, stages everything, and stops without committing.

Idempotent throughout: re-running after a crash resumes, because each step checks its destination
first and the rewrite is a no-op once the old tokens are gone.
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from identity import (  # noqa: E402
    CONFIGURED_INJECTION_POINTS,
    RESERVED,
    SUPPORTED_INJECTION_POINTS,
    TEMPLATE,
    derive,
    is_pristine,
    validate,
)

ROOT = Path(__file__).resolve().parent.parent

EXIT_OK, EXIT_USAGE, EXIT_PREFLIGHT, EXIT_PRISTINE, EXIT_PARTIAL = 0, 1, 2, 3, 4

# Rewritten in place. Everything else is either binary, generated, or deliberately left alone.
REWRITE_SUFFIXES = {
    ".kt", ".kts", ".xml", ".md", ".py", ".pro", ".properties", ".json", ".sh", ".yml", ".yaml",
}

# LICENSE is a legal statement no tool should edit. identity.py holds the template constants the
# rewrite is driven by, and init-plugin.py is the rewriter.
REWRITE_EXCLUDED = ("LICENSE", "tools/identity.py", "tools/init-plugin.py")

SKIPPED_DIRS = {".git", "build", ".gradle", ".idea", ".kotlin"}

PLUGIN_SOURCE_SETS = ("commonMain", "androidMain", "commonTest", "androidHostTest")

BUILD_DIRS = ("build", "plugin/build", "app/build", ".kotlin")


# ─────────────────────────────────────────────────────────────── shell helpers


def run(command, check=True, capture=True):
    return subprocess.run(
        command, cwd=ROOT, check=check, text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
    )


def git_available():
    try:
        run(["git", "rev-parse", "--is-inside-work-tree"])
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False


def say(message=""):
    print(message, file=sys.stderr)


def die(message, code=EXIT_USAGE):
    say("\n\033[31m✗ %s\033[0m" % message)
    sys.exit(code)


# ─────────────────────────────────────────────────────────────── preflight


def check_worktree_clean(args):
    if args.allow_dirty or not git_available():
        return
    dirty = run(["git", "status", "--porcelain"]).stdout.strip()
    if dirty:
        die(
            "the working tree is not clean, and `git reset --hard` is the only recovery this tool "
            "offers if something goes wrong halfway.\n\n%s\n\n  Commit or stash first, or pass "
            "--allow-dirty if you are sure." % dirty,
            EXIT_PREFLIGHT,
        )


def plugin_sdk_version():
    """The plugin-sdk version the build asks for, from the version catalogue."""
    catalog = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
    found = re.search(r'^\s*pluginSdk\s*=\s*"([^"]+)"', catalog, re.M)
    return found.group(1) if found else None


def maven_local():
    override = os.environ.get("MAVEN_REPO_LOCAL")
    return Path(override) if override else Path.home() / ".m2" / "repository"


def check_maven_local():
    """plugin-sdk and plugin-sdk-gradle must be in Maven Local, at the version asked for.

    Nothing in this repository *configures* without them, and the native failure —
    `Plugin [id: 'org.dhis2.mobile.plugin-bundle'] was not found` — never mentions DHIS2. Version
    skew is worse than absence: it surfaces as an unrelated dependency-resolution error.
    """
    version = plugin_sdk_version()
    if version is None:
        return
    base = maven_local() / "org/dhis2/mobile"
    missing = [
        name for name in ("plugin-sdk", "plugin-sdk-gradle")
        if not (base / name / version).is_dir()
    ]
    if not missing:
        return

    present = []
    for name in ("plugin-sdk", "plugin-sdk-gradle"):
        directory = base / name
        if directory.is_dir():
            found = sorted(p.name for p in directory.iterdir() if p.is_dir())
            present.append("%s (%s)" % (name, ", ".join(found) if found else "nothing"))
        else:
            present.append("%s (not installed)" % name)

    die(
        "%s %s is not in your Maven Local.\n\n"
        "  This project will not even CONFIGURE without it — the plugin modules apply\n"
        "  id(\"org.dhis2.mobile.plugin-bundle\") from ~/.m2.\n\n"
        "  In a DHIS2 Capture App checkout, on the branch carrying the plugin system\n"
        "  (poc/plugin-system at the time of writing):\n\n"
        "      ./gradlew :plugin-sdk:publishToMavenLocal :plugin-sdk-gradle:publishToMavenLocal\n\n"
        "  Both, always: plugin-sdk-gradle is what pulls in the matching plugin-sdk, and a stale\n"
        "  copy of it surfaces here as an unrelated dependency-resolution error.\n\n"
        "  Looked in: %s\n"
        "  Found:     %s"
        % (" and ".join(missing), version, base, "; ".join(present)),
        EXIT_PREFLIGHT,
    )


def android_sdk_dir():
    local = ROOT / "local.properties"
    if local.is_file():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                return Path(line.split("=", 1)[1].strip())
    for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(name):
            return Path(os.environ[name])
    return None


def pinned_build_tools():
    text = (ROOT / "plugin/build.gradle.kts").read_text(encoding="utf-8")
    found = re.search(r'pluginBuildToolsVersion\s*=\s*"([^"]+)"', text)
    return found.group(1) if found else None


def check_android_sdk():
    """The build-tools version the bundle pins, because d8 decides the DEX bytes."""
    sdk = android_sdk_dir()
    if sdk is None:
        die(
            "no Android SDK found. Set sdk.dir in local.properties, or ANDROID_HOME.\n"
            "  (There is no local.properties yet — this tool writes one, but it needs to know "
            "where your SDK is first.)",
            EXIT_PREFLIGHT,
        )
    version = pinned_build_tools()
    if version and not (sdk / "build-tools" / version).is_dir():
        die(
            "build-tools %s is not installed at %s.\n\n"
            "  It is pinned in plugin/build.gradle.kts, because d8 decides the DEX bytes and a\n"
            "  different version moves the bundle's checksum.\n\n"
            "      sdkmanager --install \"build-tools;%s\""
            % (version, sdk / "build-tools" / version, version),
            EXIT_PREFLIGHT,
        )


# ─────────────────────────────────────────────────────────────── state


def read_plugin_json():
    path = ROOT / "plugin.json"
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except ValueError:
        return None


def state():
    """pristine / partial / initialised, from plugin.json alone."""
    data = read_plugin_json()
    if data is None:
        return "partial", None
    initialised = bool(data.get("template", {}).get("initialised"))
    if is_pristine(data) and not initialised:
        return "pristine", data
    if not initialised:
        return "partial", data
    return "initialised", data


# ─────────────────────────────────────────────────────────────── input


def pascal_case(text):
    return "".join(part[:1].upper() + part[1:] for part in re.split(r"[^A-Za-z0-9]+", text) if part)


def kebab_case(text):
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", text.lower())).strip("-")


def ask(prompt, default, flag):
    """The value, from a prompt — or from the documented default when there is no terminal.

    Without a TTY a prompt would hang, and a hung prompt is indistinguishable from a hung build.
    So a value with a default is simply taken; only a value with none is an error, and it names
    the flag to pass.
    """
    if not sys.stdin.isatty():
        if default:
            return default
        die(
            "%s is required, and stdin is not a terminal so there is nothing to prompt.\n"
            "  Pass %s — see ./init.sh --help." % (prompt, flag)
        )
    suffix = " [%s]" % default if default else ""
    answer = input("  %s%s: " % (prompt, suffix)).strip()
    return answer or default


def collect(args):
    name = args.name or ask("Plugin name, as a human would say it", "", "--name")
    if not name:
        die("--name is required.")

    package = args.package or ask(
        "Kotlin package",
        "org.myorg.%s" % re.sub(r"[^a-z0-9]", "", kebab_case(name)),
        "--package",
    )
    plugin_id = args.plugin_id or ask("Plugin id (reverse-domain)", package, "--plugin-id")
    entry_point = args.entry_point or ask(
        "Entry-point class", pascal_case(name) + "Plugin", "--entry-point"
    )
    version = args.version or ask("Version", "0.1.0", "--version")
    slug = args.slug or ask(
        "Project slug (Gradle rootProject.name)", kebab_case(name), "--slug"
    )

    data = {
        "$schema": "tools/plugin.schema.json",
        "name": name,
        "slug": slug,
        "pluginId": plugin_id,
        "package": package,
        "entryPoint": entry_point,
        "version": version,
        # Carried through the rename, never rewritten from a literal. These are the one part of
        # plugin.json the build reads and nothing in the tree mirrors, so they are exactly what an
        # author sets by hand before running this — and an init that overwrote them would silently
        # retarget a data-set plugin at the home screen.
        "injectionPoints": injection_points(args),
        # Omitted entirely when empty rather than written as {}: an absent key is "nothing to
        # configure", which is what an additive-only plugin means.
        **({"slotConfig": slot_config} if (slot_config := slot_configuration(args)) else {}),
        "conventions": {
            "sdkAllowed": [
                "plugin/src/androidMain/kotlin/{packagePath}/{entryPoint}.kt",
                "plugin/src/androidMain/kotlin/{packagePath}/data/**/*.kt",
            ],
            "repositoryInterfaces": [
                "plugin/src/commonMain/kotlin/{packagePath}/repository/*.kt",
            ],
            "boundedComposables": [
                "plugin/src/commonMain/kotlin/{packagePath}/ui/PluginCard.kt",
            ],
        },
        "template": {
            "source": "dhis2/dhis2-android-plugin-sample",
            "formatVersion": 1,
            "initialised": False,
        },
    }

    problems = validate(data)
    if problems:
        die("those values will not work:\n" + "\n".join("  - %s" % p for p in problems))

    return derive(dict(data))


def existing():
    """The plugin.json already here, or {} — a fresh clone always has one, a retry may not."""
    try:
        return json.loads((ROOT / "plugin.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def injection_points(args):
    """The slots to declare: the flags, else whatever plugin.json already says, else the home slot.

    The default is the additive slot because it is the one that needs no configuration — a fork
    that says nothing gets something that renders.
    """
    if args.injection_point:
        return list(dict.fromkeys(args.injection_point))
    return existing().get("injectionPoints") or ["HOME_ABOVE_PROGRAM_LIST"]


def slot_configuration(args):
    """`slotConfig`, from the flags or carried through unchanged.

    `--injection-point DATA_SET_INSTANCE_CONTENT` with no `--data-set-uid` writes an empty list
    rather than nothing: the slot is then declared and visibly unconfigured, which is what
    `tools/check-identity.py` reports. Silently omitting it would look configured and render nothing.
    """
    points = injection_points(args)
    if not args.injection_point:
        carried = existing().get("slotConfig") or {}
        return {slot: value for slot, value in carried.items() if slot in points}

    if "DATA_SET_INSTANCE_CONTENT" not in points:
        return {}
    return {"DATA_SET_INSTANCE_CONTENT": {"dataSetUids": list(dict.fromkeys(args.data_set_uid))}}


# ─────────────────────────────────────────────────────────────── the work


def write_plugin_json(target, initialised):
    """Write plugin.json, dropping the derived keys — they are computed, never stored."""
    data = {key: value for key, value in target.items()
            if key not in ("packagePath", "entryPointFqcn", "resourcePackage",
                           "harnessApplicationId")}
    data["template"]["initialised"] = initialised
    (ROOT / "plugin.json").write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


def move(source, destination, use_git, plan):
    """A git-aware directory move, through a temporary sibling.

    The temp hop is not ceremony. It is required whenever the destination is an ancestor of the
    source (org.acme.thing -> org.acme), a descendant of it (org.acme -> org.acme.core), or differs
    only in case — which on APFS, case-insensitive by default, a direct rename treats as a no-op.
    """
    if not source.is_dir():
        return                      # already moved; re-running after a crash lands here
    plan.append("move %s -> %s" % (source.relative_to(ROOT), destination.relative_to(ROOT)))
    if plan.dry_run:
        return

    temporary = source.parent / (".init-move-tmp-%s" % source.name)

    # The destination's parent is created *after* the source has moved aside, never before. For a
    # rename into a descendant — org.acme.thing -> org.acme.thing.core — the destination's parent
    # IS the source, so creating it first and then moving the source away deletes the path just
    # prepared. That failure leaves a .init-move-tmp-* directory behind, which is how it was found.
    if use_git:
        run(["git", "mv", str(source), str(temporary)])
        destination.parent.mkdir(parents=True, exist_ok=True)
        run(["git", "mv", str(temporary), str(destination)])
    else:
        shutil.move(str(source), str(temporary))
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(temporary), str(destination))


def prune_empty(directory, stop_at):
    """Remove now-empty package directories, bottom-up. Git does not track directories, so
    without this the old tree survives on disk and every find-based tool reports a package that
    no longer exists."""
    current = directory
    while current != stop_at and current.is_dir():
        try:
            current.rmdir()
        except OSError:
            return
        current = current.parent


class Plan(list):
    def __init__(self, dry_run):
        super().__init__()
        self.dry_run = dry_run


def move_sources(candidates, target, use_git, plan):
    """Move each source set's package directory to the target's path.

    The source is whichever candidate package directory actually **exists on disk**, not whatever
    plugin.json says. That distinction is what makes a resumed run work: plugin.json is written
    first, so after an interrupted run it already describes the destination, and trusting it would
    make every move a no-op while the files sat at the old path.
    """
    for source_set in PLUGIN_SOURCE_SETS:
        root = ROOT / "plugin/src" / source_set / "kotlin"
        if not root.is_dir():
            continue
        destination = root / target["packagePath"]
        for candidate in candidates:
            source = root / candidate["packagePath"]
            if source == destination or not source.is_dir():
                continue
            move(source, destination, use_git, plan)
            if not plan.dry_run:
                prune_empty(source.parent, root)
            break


def rename_entry_point(candidates, target, use_git, plan):
    """Rename the entry-point file, again from whichever name is really on disk."""
    directory = ROOT / "plugin/src/androidMain/kotlin" / target["packagePath"]
    destination = directory / ("%s.kt" % target["entryPoint"])
    if destination.is_file():
        return
    source = None
    for candidate in candidates:
        attempt = directory / ("%s.kt" % candidate["entryPoint"])
        if attempt.is_file() and attempt != destination:
            source = attempt
            break
    if source is None:
        return
    plan.append("rename %s -> %s.kt" % (source.relative_to(ROOT), target["entryPoint"]))
    if plan.dry_run:
        return
    if use_git:
        run(["git", "mv", str(source), str(destination)])
    else:
        shutil.move(str(source), str(destination))


def substitutions(sources, target):
    """Ordered longest-first, so a fully-qualified name is replaced before its package prefix.

    There is deliberately no bare `template` -> `x` rule. Every token here is fully qualified, a
    PascalCase class name, or a hyphenated slug — because the word on its own appears in prose, in
    fixture values and in a forker's own vocabulary, and rewriting those would be corruption rather
    than renaming.
    """
    pairs = []
    for previous in sources:
        if previous["package"] == target["package"] \
                and previous["entryPoint"] == target["entryPoint"] \
                and previous["slug"] == target["slug"] \
                and previous["name"] == target["name"]:
            continue
        pairs.extend([
            (previous["entryPointFqcn"], target["entryPointFqcn"]),
            (previous["package"], target["package"]),
            (previous["packagePath"], target["packagePath"]),
            (previous["entryPoint"], target["entryPoint"]),
            (previous["slug"], target["slug"]),
            (previous["name"], target["name"]),
        ])
    # Longest first, and de-duplicated keeping the first occurrence.
    seen = set()
    ordered = []
    for old, new in sorted(pairs, key=lambda pair: -len(pair[0])):
        if old and old != new and old not in seen:
            seen.add(old)
            ordered.append((old, new))
    return ordered


def rewritable_files():
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or path.suffix not in REWRITE_SUFFIXES:
            continue
        relative = path.relative_to(ROOT).as_posix()
        if any(part in SKIPPED_DIRS for part in path.relative_to(ROOT).parts):
            continue
        if relative.startswith(REWRITE_EXCLUDED):
            continue
        yield path


def rewrite(pairs, plan):
    if not pairs:
        return
    for path in rewritable_files():
        try:
            # newline="" so a Windows fork does not have its whole tree silently converted to LF
            # as a side effect of a package rename.
            with open(path, "r", encoding="utf-8", newline="") as handle:
                text = original = handle.read()
        except (UnicodeDecodeError, OSError):
            continue
        hits = 0
        for old, new in pairs:
            count = text.count(old)
            if count:
                hits += count
                text = text.replace(old, new)
        if text != original:
            plan.append("rewrite %s (%d)" % (path.relative_to(ROOT), hits))
            if not plan.dry_run:
                with open(path, "w", encoding="utf-8", newline="") as handle:
                    handle.write(text)


def write_local_properties(plan):
    example = ROOT / "local.properties.example"
    if not example.is_file():
        plan.append("write local.properties.example")
        if not plan.dry_run:
            example.write_text(LOCAL_PROPERTIES_EXAMPLE, encoding="utf-8")

    local = ROOT / "local.properties"
    if local.is_file():
        return                              # never touch one that already exists
    plan.append("write local.properties")
    if plan.dry_run:
        return
    sdk = android_sdk_dir()
    text = LOCAL_PROPERTIES_EXAMPLE
    if sdk is not None:
        text = re.sub(r"^sdk\.dir=.*$", "sdk.dir=%s" % sdk, text, count=1, flags=re.M)
    local.write_text(text, encoding="utf-8")


LOCAL_PROPERTIES_EXAMPLE = """\
# Copy to local.properties — which is gitignored, and must never be committed — and fill in.

# Required. plugin/build.gradle.kts resolves d8 and apksigner from it, not just AGP.
sdk.dir=/Users/you/Library/Android/sdk

# The development harness (:app) only. The :plugin module never reads any of these.
# Use a development server: the plugin reads, but the harness signs in as a real user and syncs a
# real database onto the device.
# Any server you can reach. From an emulator, your own machine is 10.0.2.2, not localhost.
dhis2.serverUrl=https://play.dhis2.org/dev
dhis2.username=admin
dhis2.password=district

# Optional. Which slot to render, when plugin.json declares more than one. Blank picks the most
# specific slot the plugin could actually be rendered at.
# harness.slot=HOME_ABOVE_PROGRAM_LIST
"""


def clean_build_output(plan):
    """Delete build directories outright, rather than `./gradlew clean`.

    The build may not configure mid-rename, and this has to work regardless. It is also the single
    most important non-obvious step: Gradle's up-to-date checks do not model "the package changed",
    so stale Compose Resources output leaves a generated `Res` class in a package nothing imports —
    or two of them.
    """
    for name in BUILD_DIRS:
        path = ROOT / name
        if path.exists():
            plan.append("delete %s" % name)
            if not plan.dry_run:
                shutil.rmtree(path, ignore_errors=True)


# ─────────────────────────────────────────────────────────────── reporting


def report(target, verified):
    say()
    say("\033[32m✓ Initialised as \"%s\"\033[0m" % target["name"])
    say()
    say("  plugin id     %s" % target["pluginId"])
    say("  package       %s" % target["package"])
    say("  entry point   %s" % target["entryPointFqcn"])
    say("  version       %s" % target["version"])
    say("  slots         %s" % ", ".join(target["injectionPoints"]))
    for slot, field in CONFIGURED_INJECTION_POINTS.items():
        if slot not in target["injectionPoints"]:
            continue
        configured = (target.get("slotConfig", {}).get(slot) or {}).get(field) or []
        say("                %s.%s = %s"
            % (slot, field, ", ".join(configured) if configured
               else "(none yet — it replaces nothing until you add one)"))
    say()
    if verified:
        say("  ./verify.sh passed. Everything is staged but NOT committed — review it, then:")
    else:
        say("  Everything is staged but NOT committed — review it, then:")
    say()
    say("      git diff --staged")
    say("      git commit -m \"chore: initialise from the DHIS2 plugin template as %s\""
        % target["name"])
    say()
    say("  Next:")
    say("    1. Fill in local.properties with your DHIS2 server, then ./gradlew :app:installDebug")
    say("       to run the plugin against real data.")
    say("    2. specs/first-card.md is the seed's spec. Write yours beside it and build it with")
    say("       docs/workflows/plugin-from-spec.md.")
    say()
    say("  Three things this tool will not decide for you:")
    say("    - `git remote` still points at the template. Repoint it before you push.")
    say("    - LICENSE still reads \"Copyright (c) 2026, University of Oslo\". If your fork is not")
    say("      part of that project, decide what its licence is.")
    say("    - An older harness may still be installed under its old applicationId.")


def report_json(target, verified):
    print(json.dumps({
        "status": "initialised",
        "name": target["name"],
        "slug": target["slug"],
        "pluginId": target["pluginId"],
        "package": target["package"],
        "entryPoint": target["entryPoint"],
        "entryPointFqcn": target["entryPointFqcn"],
        "version": target["version"],
        "injectionPoints": target["injectionPoints"],
        "slotConfig": target.get("slotConfig", {}),
        "verified": verified,
        "committed": False,
    }, indent=2))


# ─────────────────────────────────────────────────────────────── entry point


def parse_args(argv):
    parser = argparse.ArgumentParser(
        prog="./init.sh",
        description="Turn this template into your own DHIS2 Android plugin. Run once.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""\
examples:
  ./init.sh
      Prompt for each value, pre-filled from the ones before it.

  ./init.sh --name "Immunisation Coverage" --package org.myorg.immunisation \\
            --plugin-id org.myorg.immunisation-coverage --entry-point ImmunisationPlugin --yes
      Fully determined, no prompts. This is the form to use from an agent or a script.

  ./init.sh --name "Monthly Stock" --package org.myorg.stock \\
            --injection-point DATA_SET_INSTANCE_CONTENT --data-set-uid BfMAe6Itzgt --yes
      A plugin that replaces the body of a data set instance screen. Without the flags, whatever
      plugin.json already declares is carried through unchanged.

  ./init.sh --check     Report the state and change nothing. 0 initialised, 3 pristine, 4 partial.
  ./init.sh --dry-run   Print every move, rename and rewrite, and change nothing.
""",
    )
    parser.add_argument("--name", help="display name, as a human would say it")
    parser.add_argument("--package", help="Kotlin package, e.g. org.myorg.immunisation")
    parser.add_argument("--plugin-id", help="reverse-domain id for the dataStore config")
    parser.add_argument("--entry-point", help="entry-point class name, e.g. ImmunisationPlugin")
    parser.add_argument("--version", help="initial version (default 0.1.0)")
    parser.add_argument("--slug", help="Gradle rootProject.name (default: kebab-case of --name)")
    parser.add_argument("--dry-run", action="store_true", help="print the plan, change nothing")
    parser.add_argument("--check", action="store_true", help="report the state, change nothing")
    # Repeatable, and only for the fully-determined path. Interactively, plugin.json is the place
    # to say this: it is one file, already schema'd, and the only thing the build reads.
    parser.add_argument("--injection-point", action="append", default=[],
                        metavar="SLOT", choices=SUPPORTED_INJECTION_POINTS,
                        help="host slot to declare; repeat for several "
                             "(default: keep what plugin.json says)")
    parser.add_argument("--data-set-uid", action="append", default=[], metavar="UID",
                        help="data set the DATA_SET_INSTANCE_CONTENT slot applies to; repeatable")
    parser.add_argument("--json", action="store_true", help="machine-readable result on stdout")
    parser.add_argument("--yes", action="store_true", help="do not ask for confirmation")
    parser.add_argument("--force", action="store_true",
                        help="re-initialise an already-initialised fork (a rename)")
    parser.add_argument("--no-verify", action="store_true", help="skip the closing ./verify.sh")
    parser.add_argument("--allow-dirty", action="store_true",
                        help="proceed with an unclean working tree")
    parser.add_argument("--commit", action="store_true", help="commit the result as well")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(sys.argv[1:] if argv is None else argv)
    current, data = state()

    if args.check:
        if current == "pristine":
            say("pristine template — not yet initialised. Run ./init.sh to make it yours.")
            return EXIT_PRISTINE
        if current == "partial":
            say("partially initialised — a previous run of ./init.sh did not finish.")
            say("  Re-run the same command to resume, or `git reset --hard && git clean -fd`.")
            return EXIT_PARTIAL
        say("initialised as \"%s\" (%s)." % (data["name"], data["pluginId"]))
        return EXIT_OK

    if current == "initialised" and not args.force:
        say("Already initialised as \"%s\" (%s)." % (data["name"], data["pluginId"]))
        say("  Nothing to do. To rename, re-run with --force and the new values.")
        # Exit 0, not an error: an agent re-running init should not be blocked by a prior success.
        return EXIT_OK

    check_worktree_clean(args)
    if not args.dry_run:
        check_maven_local()
        check_android_sdk()

    target = collect(args)

    # Both the template's own identity and whatever this fork currently carries. Replacing a token
    # that is not present costs nothing, so applying both is strictly safer than choosing between
    # them: one pass covers a resumed first run and a later --force rename.
    sources = [derive(dict(TEMPLATE))]
    if data and not is_pristine(data):
        sources.append(derive(dict(data)))

    # Tried in turn against what is actually on disk, most recently written first. A fresh run finds
    # the template's paths; a resumed one finds them too, because the moves had not happened.
    candidates = list(reversed(sources))

    if not args.yes and not args.dry_run and sys.stdin.isatty():
        say()
        say("  About to rewrite this repository as:")
        say("    %s  (%s)" % (target["name"], target["pluginId"]))
        say("    %s" % target["entryPointFqcn"])
        if input("\n  Continue? [y/N] ").strip().lower() not in ("y", "yes"):
            die("cancelled; nothing was changed.", EXIT_OK)

    use_git = git_available()
    plan = Plan(args.dry_run)

    if not args.dry_run:
        write_plugin_json(target, initialised=False)
    plan.append("write plugin.json (initialised: false)")

    move_sources(candidates, target, use_git, plan)
    rename_entry_point(candidates, target, use_git, plan)
    rewrite(substitutions(sources, target), plan)
    write_local_properties(plan)
    clean_build_output(plan)

    if args.dry_run:
        say("\n  Plan (%d steps), nothing changed:\n" % len(plan))
        for entry in plan:
            say("    %s" % entry)
        say()
        return EXIT_OK

    write_plugin_json(target, initialised=True)

    verified = False
    if not args.no_verify:
        say("\n  Running ./verify.sh …\n")
        result = subprocess.run(["./verify.sh"], cwd=ROOT)
        verified = result.returncode == 0
        if not verified:
            say()
            say("\033[31m✗ ./verify.sh failed after initialisation.\033[0m")
            say("  The rewrite itself completed — plugin.json and the tree agree. This is a build")
            say("  problem, and its output is above. Nothing has been committed.")

    if use_git:
        run(["git", "add", "-A"])
        if args.commit:
            run(["git", "commit", "-q", "-m",
                 "chore: initialise from the DHIS2 plugin template as %s" % target["name"]])

    if args.json:
        report_json(target, verified)
    else:
        report(target, verified)

    return EXIT_OK if verified or args.no_verify else EXIT_PREFLIGHT


if __name__ == "__main__":
    try:
        sys.exit(main())
    except subprocess.CalledProcessError as error:
        # A traceback tells a forker nothing they can act on. The state is recoverable and the
        # recovery is one command, so say that instead.
        say()
        say("\033[31m✗ Initialisation failed while running: %s\033[0m"
            % " ".join(error.cmd))
        if error.stdout:
            say(error.stdout.rstrip())
        say()
        say("  Nothing was committed. plugin.json records the attempt, so re-running the same")
        say("  command resumes. To go back to the template instead:")
        say()
        say("      git reset --hard && git clean -fd")
        sys.exit(EXIT_PARTIAL)
