# Consumer ProGuard rules.
# When :app (or any consumer) enables R8/ProGuard, keep the plugin entry point
# and its metadata symbol so reflective loading via PluginMetadata.entryPoint works.
-keep class org.dhis2.mobile.plugin.sample.ProgramOverviewPlugin { *; }
