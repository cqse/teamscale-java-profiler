plugins {
	com.teamscale.`kotlin-convention`
	com.teamscale.`system-test-convention`
}

tasks.test {
	val redirectPort = ports.pickFreePort()
	systemProperty("redirectPort", redirectPort)

	teamscaleAgent(
		mapOf(
			"http-server-port" to "$agentPort",
			"teamscale-server-url" to "http://localhost:$redirectPort",
			"teamscale-user" to "fake",
			"teamscale-access-token" to "fake",
			"teamscale-project" to "p",
			"teamscale-partition" to "part",
			"teamscale-commit" to "master:12345",
			"includes" to "*"
		)
	)
}
