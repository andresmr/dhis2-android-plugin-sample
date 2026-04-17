# Release ProGuard rules for this library module itself.
# Keep the plugin entry point class name and public members — the host resolves
# it reflectively via Class.forName(PluginMetadata.entryPoint).
-keep class org.dhis2.pluginimplementationtest.MyPlugin { *; }
