plugins {
	com.teamscale.`kotlin-convention`
	com.teamscale.`system-test-convention`
	com.teamscale.`spawned-jvm-coverage`
}

dependencies {
	spawnedJvmCode(project(":teamscale-maven-plugin"))
	spawnedJvmCode(project(":teamscale-client"))
}

tasks.test {
	// install dependencies needed by the Maven test project
	usesMavenLocalPublicationsOf(
		":agent",
		":impacted-test-engine",
		":report-generator",
		":teamscale-client",
		":teamscale-maven-plugin",
	)
}
