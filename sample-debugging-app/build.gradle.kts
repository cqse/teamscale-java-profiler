plugins {
	com.teamscale.`java-convention`
	application
	com.teamscale.`agent-jar`
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

/**
 * Uses `jacocoagent.local.properties` if it exists, so credentials for a real Teamscale instance can be kept out of
 * version control (the file is git-ignored), and the committed `jacocoagent.properties` otherwise.
 */
val agentConfigFile =
	listOf("jacocoagent.local.properties", "jacocoagent.properties")
		.first { layout.projectDirectory.file(it).asFile.exists() }

tasks.named<JavaExec>("run") {
	teamscaleAgent(
		mapOf(
			"config-file" to agentConfigFile
		)
	)
	dependsOn(":agent:shadowJar")
}
