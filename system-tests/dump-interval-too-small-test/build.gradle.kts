plugins {
	com.teamscale.`kotlin-convention`
	com.teamscale.`system-test-convention`
}

tasks.test {
	teamscaleAgent(mapOf("interval" to "1", "debug" to logFilePath))
}
