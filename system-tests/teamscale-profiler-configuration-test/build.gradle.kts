import kotlin.io.path.writeText

plugins {
	com.teamscale.`kotlin-convention`
	com.teamscale.`system-test-convention`
}


tasks.jar {
	manifest.attributes["Main-Class"] = "systemundertest.SystemUnderTest"
}

tasks.test {
	/** This port must match what is configured in the SystemTest class. */
	val teamscalePort = 64100

	environment("AGENT_JAR", agentJar)
	environment("SYSTEM_UNDER_TEST_JAR", tasks.jar.get().outputs.files.singleFile)

	val teamscalePropertiesPath = agentJar.toPath().parent.parent.resolve("teamscale.properties")
	doFirst {
		teamscalePropertiesPath.writeText(
			"""
			url=http://localhost:$teamscalePort
			username=fake
			accesskey=fake
		""".trimIndent()
		)
	}
	doLast {
		teamscalePropertiesPath.toFile().delete()
	}
	dependsOn(tasks.jar)
}
