plugins {
	`kotlin-dsl`
}

repositories {
	gradlePluginPortal()
}

dependencies {
	implementation(plugin(libs.plugins.shadow))
	implementation(plugin(libs.plugins.kotlinJvm))

	implementation(libs.asm.core)
	implementation(libs.asm.commons)
}

// Helper function that transforms a Gradle Plugin alias from a
// Version Catalog into a valid dependency notation for buildSrc
// https://docs.gradle.org/current/userguide/version_catalogs.html#sec:buildsrc-version-catalog
fun plugin(plugin: Provider<PluginDependency>) =
	plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
