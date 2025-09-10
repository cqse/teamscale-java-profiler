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
