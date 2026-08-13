plugins {
	alias(libs.plugins.mavenPluginDevelopment)
	com.teamscale.`java-convention`
	com.teamscale.coverage
	com.teamscale.publish
}

publishAs {
	readableName = "Teamscale Maven Plugin"
	description = "Maven Plugin for Teamscale"
}

mavenPlugin {
	helpMojoPackage = "com.teamscale.maven.help"
}

// This module is not shaded itself, but the bundled logback configuration is handed to the shaded
// agent, so it has to reference the relocated logback classes.
tasks.processResources {
	shadowLoggingPackages(usesShadowedPackages)
}

verifyShadowedLoggingConfigs(tasks.jar)

dependencies {
	runtimeOnly(project(":agent"))
	implementation(project(":report-generator"))
	implementation(project(":teamscale-client"))

	compileOnly(libs.maven.core)
	implementation(libs.maven.pluginApi)
	compileOnly(libs.maven.pluginAnnotations)

	implementation(libs.jgit)

	testImplementation(libs.assertj)
}
