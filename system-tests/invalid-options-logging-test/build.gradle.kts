plugins {
	com.teamscale.`kotlin-convention`
	com.teamscale.`system-test-convention`
}

tasks.jar {
	manifest.attributes["Main-Class"] = "systemundertest.SystemUnderTest"
}

tasks.test {
	environment("AGENT_JAR", agentJar)
	environment("SYSTEM_UNDER_TEST_JAR", tasks.jar.get().outputs.files.singleFile)
	dependsOn(tasks.jar)
}
