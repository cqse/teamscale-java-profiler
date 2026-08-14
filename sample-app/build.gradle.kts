plugins {
	com.teamscale.`java-convention`
	application
	com.teamscale.`agent-jar`
	alias(libs.plugins.gitProperties)
}

application {
	mainClass = "com.example.Main"
}

version = "unspecified"

dependencies {
	testImplementation(libs.junit4)
}

tasks.jar {
	manifest {
		attributes["Main-Class"] = "com.example.Main"
	}
}

gitProperties {
	keys = listOf("git.branch", "git.commit.id", "git.commit.time")
}

/**
 * Uses `jacocoagent.local.properties` if it exists, so credentials for a real Teamscale instance can be kept out of
 * version control (the file is git-ignored), and the committed `jacocoagent.properties` otherwise.
 */
val agentConfigFile =
	listOf("jacocoagent.local.properties", "jacocoagent.properties")
		.first { layout.projectDirectory.file(it).asFile.exists() }

tasks.named<JavaExec>("run") {
	classpath = files(tasks.jar, configurations.runtimeClasspath)
	// How long the application should keep running, e.g. `./gradlew :sample-app:run -PruntimeSeconds=300` to profile
	// for five minutes. Without it the application uses its own default of ten seconds.
	providers.gradleProperty("runtimeSeconds").orNull?.let { args(it) }
	teamscaleAgent(
		mapOf(
			"config-file" to agentConfigFile
		)
	)
}
