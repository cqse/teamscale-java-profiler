import kotlin.io.path.writeText

plugins {
	com.teamscale.`kotlin-convention`
	com.teamscale.`system-test-convention`
}

tasks.test {
	val teamscalePropertiesPath = agentJar.toPath().parent.parent.resolve("teamscale.properties")
	val teamscalePort = teamscalePort
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
		delete(teamscalePropertiesPath)
	}

	teamscaleAgent(
		mapOf(
			"http-server-port" to "$agentPort",
			"teamscale-project" to "p",
			"teamscale-partition" to "part",
			"teamscale-commit" to "master:12345",
			"includes" to "*"
		)
	)
}
