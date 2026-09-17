// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
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
// …and prove it held.
//
// A force that silently stops applying — a group renamed upstream, a `strictly` constraint winning,
// someone deleting the block above — puts the build straight back where it started, with no symptom
// until a device. So the resolved versions are read back and checked, which is the difference
// between a rule that is enforced and one that is hoped for.
// ─────────────────────────────────────────────────────────────────────────────

val checkComposeAlignment by tasks.registering {
    group = "verification"
    description = "Every androidx Compose artifact resolves to the version the host provides."

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
    }
}
