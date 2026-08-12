plugins {
	// Ships in the same artifact as com.gradleup.nmcp, which buildSrc puts on the classpath for the
	// com.teamscale.publish convention plugin, so this must not repeat the version.
	id("com.gradleup.nmcp.aggregation")
}

// group and version are set for every project from settings.gradle.kts

/**
 * The projects that publish Maven artifacts, i.e. those applying the `com.teamscale.publish` convention
 * plugin. Listed explicitly because looking them up in the other projects would break project isolation.
 */
val publishedProjects = listOf(
	":agent",
	":impacted-test-engine",
	":report-generator",
	":teamscale-client",
	":teamscale-gradle-plugin",
	":teamscale-maven-plugin",
	":tia-client",
	":tia-runlisteners",
)

// Installs all Maven artifacts to your local Maven repository
tasks.register("publishToMavenLocal") {
	dependsOn(publishedProjects.map { "$it:publishToMavenLocal" })
}

// Collects the publications of all projects below into a single deployment and uploads it to Maven Central
// via the Central Portal. Publishing this way, rather than by cross-configuring the projects from here, is
// what keeps the release path compatible with project isolation.
nmcpAggregation {
	centralPortal {
		// The user token generated at https://central.sonatype.com/account, not the portal login itself.
		username = providers.gradleProperty("sonatypeUsername")
		password = providers.gradleProperty("sonatypePassword")
		// Release the deployment as soon as the portal has validated it. Use USER_MANAGED to stop after
		// validation and release by hand from the portal instead.
		publishingType = "AUTOMATIC"
	}
}

dependencies {
	// The Gradle plugin is released through the Gradle Plugin Portal, so it is not part of the deployment.
	// The com.teamscale.publish convention plugin leaves it out on the producing side for the same reason.
	(publishedProjects - ":teamscale-gradle-plugin").forEach {
		nmcpAggregation(project(it))
	}
}

