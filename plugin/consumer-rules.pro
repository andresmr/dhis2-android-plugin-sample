# NOTE: nothing in plugin/build.gradle.kts references this file today — the KMP Android
# library block declares neither proguardFiles nor consumerProguardFiles, and the bundle
# ships a DEX the host loads directly rather than an AAR anyone runs R8 over. Kept because
# the keep rule is still the right one the day either changes.
# Consumer ProGuard rules.
# When :app (or any consumer) enables R8/ProGuard, keep the plugin entry point and its public
# members so reflective loading via PluginMetadata.entryPoint works.
#
# Matched by interface rather than by name: the host only ever looks up a Dhis2Plugin, so this
# stays correct through a rename and covers a bundle that ships more than one entry point. A
# hardcoded FQCN here is a fifth copy of the identity that plugin.json owns.
-keep class * implements org.dhis2.mobile.plugin.sdk.Dhis2Plugin { *; }
