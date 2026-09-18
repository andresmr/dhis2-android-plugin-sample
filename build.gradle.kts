// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// ─────────────────────────────────────────────────────────────────────────────
// Compile against the androidx Compose the HOST provides, not the one Compose Multiplatform brings.
//
// This is architecture rule 3 made mechanical. The Capture App declares androidx Compose directly,
// at a higher version than CMP resolves transitively — 1.10.6 against 1.10.5 as this is written. A
// plugin compiled against the lower one meets the higher one at runtime, and almost everything is
// identical, which is the trap: the first casualty is a *defaulted* overload whose `…$default`
// synthetic moved. `Modifier.weight(1f)` crashed the host with `NoSuchMethodError: weight$default`
// at composition, and nothing in any build noticed.
//
// Applied to every module, the harness included. The harness is what stands in for the host locally,
// so it has to provide what the host provides — otherwise it is the one skew the harness cannot
// reproduce, which is exactly what AGENTS.md used to say about it.
//
// material3 is deliberately NOT in this list: androidx's Compose sub-groups do not share one version
// line — material3 is on 1.4.x while ui/foundation/runtime/animation are on 1.10.x — so a force
// across `androidx.compose.*` would be wrong. We already match the host on material3 (1.4.0) and on
// CMP's own material3 (1.9.0) without help.
// ─────────────────────────────────────────────────────────────────────────────

val hostAndroidxCompose = libs.versions.androidxCompose.get()

/** The DHIS2 SDK. `:plugin` never declares it — the bundle plugin injects it from the host. */
val sdkGroup = "org.hisp.dhis"
val sdkName = "android-core"

/** The androidx Compose groups that move together on the host's `compose` version line. */
val hostComposeGroups = setOf(
    "androidx.compose.animation",
    "androidx.compose.foundation",
    "androidx.compose.runtime",
    "androidx.compose.ui",
)

subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group in hostComposeGroups) {
                useVersion(hostAndroidxCompose)
                because("the Capture App provides androidx Compose $hostAndroidxCompose at runtime")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// …and prove it held — for Compose, and for the DHIS2 SDK.
//
// A force that silently stops applying — a group renamed upstream, a `strictly` constraint winning,
// someone deleting the block above — puts the build straight back where it started, with no symptom
// until a device. So the resolved versions are read back and checked, which is the difference
// between a rule that is enforced and one that is hoped for.
//
// The SDK half is a different shape. :plugin never declares android-core — the bundle plugin injects
// it at the host's version, so :plugin's resolution is the host's answer as long as nothing else
// asks for a different one. (It injects a plain version, not `strictly`: declare android-core here
// at a higher version and ordinary conflict resolution would win, and this check would then blame
// the harness. Nothing declares it, so the two agree. Worth knowing before anyone adds a
// declaration.) The harness declares its own from the catalogue, and the two silently diverged by
// two weeks of snapshot builds: the harness constructed a D2 from an older SDK than the plugin was
// compiled against, which is precisely the class of problem the harness exists to catch. So rather
// than trust the catalogue, this compares the harness against :plugin and fails on any difference.
// ─────────────────────────────────────────────────────────────────────────────

val checkHostAlignment by tasks.registering {
    group = "verification"
    description = "Compose and the DHIS2 SDK resolve to what the host provides."

    // Resolved at execution time, not configuration time, so this task costs nothing until it runs.
    notCompatibleWithConfigurationCache("resolves configurations from every project at execution time")

    doLast {
        val wrong = mutableListOf<String>()
        var checked = 0

        subprojects.forEach { project ->
            project.configurations
                .filter { it.isCanBeResolved && it.name.endsWith("CompileClasspath", ignoreCase = true) }
                .forEach { configuration ->
                    val resolved = runCatching {
                        configuration.incoming.resolutionResult.allComponents
                    }.getOrNull() ?: return@forEach

                    resolved.forEach { component ->
                        val id = component.moduleVersion ?: return@forEach
                        if (id.group in hostComposeGroups) {
                            checked++
                            if (id.version != hostAndroidxCompose) {
                                wrong += "${project.path} · ${configuration.name}: " +
                                    "${id.group}:${id.name}:${id.version}"
                            }
                        }
                    }
                }
        }

        if (wrong.isNotEmpty()) {
            error(
                buildString {
                    appendLine("androidx Compose does not match the host's $hostAndroidxCompose:")
                    wrong.distinct().sorted().forEach { appendLine("    $it") }
                    appendLine()
                    appendLine("  The force in build.gradle.kts stopped applying. A plugin compiled against a")
                    appendLine("  different androidx Compose than the host provides fails at composition with")
                    appendLine("  NoSuchMethodError, and nothing before a device will tell you.")
                },
            )
        }

        logger.lifecycle(
            "  $checked androidx Compose artifact(s) resolve to $hostAndroidxCompose, " +
                "the version the host provides",
        )

        // ── the DHIS2 SDK ───────────────────────────────────────────────────────────────────────
        fun sdkVersionIn(project: Project?, configurationName: String): String? =
            project?.configurations?.findByName(configurationName)
                ?.takeIf { it.isCanBeResolved }
                ?.let { configuration ->
                    runCatching { configuration.incoming.resolutionResult.allComponents }
                        .getOrNull()
                        ?.mapNotNull { it.moduleVersion }
                        ?.firstOrNull { it.group == sdkGroup && it.name == sdkName }
                        ?.version
                }

        // :plugin's is authoritative — the bundle plugin injects it from the host, and nothing in
        // :plugin declares android-core to compete with it.
        val hostSdk = listOf("androidCompileClasspath", "androidHostTestCompileClasspath")
            .firstNotNullOfOrNull { sdkVersionIn(findProject(":plugin"), it) }

        val harnessSdk = sdkVersionIn(findProject(":app"), "debugRuntimeClasspath")

        when {
            // Never silently pass: a check that cannot see its subject is a check that is not
            // running, and saying so is the difference between a gate and decoration.
            hostSdk == null -> error(
                "Could not resolve $sdkGroup:$sdkName in :plugin, so the DHIS2 SDK could not be " +
                    "checked. Run this from the repository root as part of ./verify.sh.",
            )

            harnessSdk == null -> error(
                "Could not resolve $sdkGroup:$sdkName in the harness (:app), so the DHIS2 SDK " +
                    "could not be checked.",
            )

            hostSdk != harnessSdk -> error(
                buildString {
                    appendLine("The harness and the plugin disagree about the DHIS2 SDK:")
                    appendLine("    :plugin (injected by the bundle plugin, from the host)  $hostSdk")
                    appendLine("    :app    (declared in gradle/libs.versions.toml)         $harnessSdk")
                    appendLine()
                    appendLine("  The harness would build its D2 from a different SDK than the plugin is")
                    appendLine("  compiled against, which is the one thing the harness exists to de-risk.")
                    appendLine("  Set dhis2AndroidCore in gradle/libs.versions.toml to $hostSdk.")
                },
            )

            else -> logger.lifecycle("  DHIS2 SDK $hostSdk in both :plugin and the harness")
        }

        // ── Material, on both sides of the design system ─────────────────────────────────────────
        //
        // A plugin does not get to pick a Material version. The DHIS2 design system is built against
        // one, the host ships that pairing, and a plugin compiled against a different material3 than
        // the one rendering its components is the NoSuchMethodError trap one layer up — with the
        // twist that the symptom is a *component* misrendering rather than the plugin's own code
        // failing, which is far harder to attribute.
        //
        // Note what this does NOT do: read the requirement out of the design system's metadata. It
        // declares Material as `implementation`, so the edge is absent from every compile classpath
        // and only appears at runtime. What is checkable instead is the pairing that actually
        // matters — what :plugin compiles against, against what the harness (which pulls the design
        // system for real, and resolves its requirement along with it) runs.
        val material3Group = "org.jetbrains.compose.material3"

        fun material3In(project: Project?, configurationName: String): String? =
            project?.configurations?.findByName(configurationName)
                ?.takeIf { it.isCanBeResolved }
                ?.let { configuration ->
                    runCatching { configuration.incoming.resolutionResult.allComponents }
                        .getOrNull()
                        ?.mapNotNull { it.moduleVersion }
                        ?.firstOrNull { it.group == material3Group && it.name.startsWith("material3") }
                        ?.version
                }

        val compiledAgainst = material3In(findProject(":plugin"), "androidCompileClasspath")
        val renderedWith = material3In(findProject(":app"), "debugRuntimeClasspath")

        when {
            compiledAgainst == null || renderedWith == null -> error(
                "Could not resolve $material3Group:material3 on both sides, so Material is " +
                    "unchecked. Is the design system still declared in :plugin and the harness?",
            )

            compiledAgainst != renderedWith -> error(
                buildString {
                    appendLine("The plugin and the design system disagree about Material:")
                    appendLine("    :plugin compiles against              $compiledAgainst")
                    appendLine("    the harness renders the design system with  $renderedWith")
                    appendLine()
                    appendLine("  The design system's components would be drawn by a different Material than")
                    appendLine("  the plugin was compiled against. Align composeMultiplatform in")
                    appendLine("  gradle/libs.versions.toml with the design system's own, or move designSystem.")
                },
            )

            else -> logger.lifecycle(
                "  Material $compiledAgainst on both sides of the design system",
            )
        }
    }
}
