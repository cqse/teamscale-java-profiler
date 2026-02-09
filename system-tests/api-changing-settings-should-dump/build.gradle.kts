plugins {
	com.teamscale.`kotlin-convention`
	com.teamscale.`system-test-convention`
}

tasks.test {
	/** These ports must match what is configured in the SystemTest class. */
	teamscaleAgent(
		mapOf(
			"http-server-port" to "$agentPort",
			"teamscale-server-url" to "http://localhost:$teamscalePort",
			"teamscale-user" to "fake",
			"teamscale-access-token" to "fake",
			"teamscale-project" to "p",
			"teamscale-partition" to "partition_before_change",
			"teamscale-commit" to "master:12345",
			"includes" to "*SystemUnderTest*",
		)
	)
}
