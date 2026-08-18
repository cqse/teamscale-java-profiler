plugins {
	com.teamscale.`kotlin-convention`
	com.teamscale.`system-test-convention`
}

tasks.register<JavaExec>("runWithoutGradleWorker") {
	mainClass = "jul.test.SystemUnderTest"
	classpath = sourceSets["main"].runtimeClasspath
	systemProperty("java.util.logging.manager", "jul.test.CustomLogManager")
	teamscaleAgent(mapOf("mode" to "testwise", "tia-mode" to "http", "http-server-port" to "63900"))
}

tasks.test {
	dependsOn("runWithoutGradleWorker")
	teamscaleAgent(mapOf("debug" to logFilePath))
}
