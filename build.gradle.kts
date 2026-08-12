plugins {
	alias(libs.plugins.nexusPublish)
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

nexusPublishing {
	repositories {
		// see https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#configuration
		sonatype {
			nexusUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/")
			snapshotRepositoryUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
		}
	}
}

