# Release ProGuard rules for this library module itself.
# Keep the plugin entry point class name and public members — the host resolves it reflectively
# via Class.forName(PluginMetadata.entryPoint).
#
# Matched by interface rather than by name, for the same reason as consumer-rules.pro: it survives
# a rename, and it does not repeat what plugin.json already says.
-keep class * implements org.dhis2.mobile.plugin.sdk.Dhis2Plugin { *; }
