# Consumer ProGuard rules.
# When :app (or any consumer) enables R8/ProGuard, keep the plugin entry point and its public
# members so reflective loading via PluginMetadata.entryPoint works.
#
# Matched by interface rather than by name: the host only ever looks up a Dhis2Plugin, so this
# stays correct through a rename and covers a bundle that ships more than one entry point. A
# hardcoded FQCN here is a fifth copy of the identity that plugin.json owns.
-keep class * implements org.dhis2.mobile.plugin.sdk.Dhis2Plugin { *; }
