# NOTE: nothing in plugin/build.gradle.kts references this file today — the KMP Android
# library block declares neither proguardFiles nor consumerProguardFiles, and the bundle
# ships a DEX the host loads directly rather than an AAR anyone runs R8 over. Kept because
# the keep rule is still the right one the day either changes.
# Release ProGuard rules for this library module itself.
# Keep the plugin entry point class name and public members — the host resolves it reflectively
# via Class.forName(PluginMetadata.entryPoint).
#
# Matched by interface rather than by name, for the same reason as consumer-rules.pro: it survives
# a rename, and it does not repeat what plugin.json already says.
-keep class * implements org.dhis2.mobile.plugin.sdk.Dhis2Plugin { *; }
