package com.teamscale.client

import com.teamscale.test.commons.ProcessUtils
import com.teamscale.test.commons.TeamscaleMockServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Ensures that the teamscale.properties file is successfully located and used to retrieve the configuration from
 * Teamscale.
 */
class TeamscaleProfilerConfigurationSystemTest {
	@Test
	@Throws(Exception::class)
	fun systemTestRetrieveConfig() {
		val profilerConfiguration = ProfilerConfiguration().apply {
			configurationId = "my-config"
			configurationOptions = "teamscale-partition=foo\nteamscale-project=p\nteamscale-commit=master:12345"
		}
		val teamscaleMockServer = TeamscaleMockServer(FAKE_TEAMSCALE_PORT).acceptingReportUploads()
			.withProfilerConfiguration(profilerConfiguration)

		val agentJar = System.getenv("AGENT_JAR")
		val sampleJar = System.getenv("SYSTEM_UNDER_TEST_JAR")
		val result = ProcessUtils.execute("java", "-javaagent:$agentJar=config-id=my-config", "-jar", sampleJar)
		println(result.stderr)
		println(result.stdout)
		assertThat(result.exitCode).isEqualTo(0)
		assertThat(teamscaleMockServer.onlySession.partition).isEqualTo("foo")

		teamscaleMockServer.shutdown()

		assertThat(teamscaleMockServer.profilerEvents.toSet()).`as`(
			"We expect a sequence of interactions with the mock. Note that unexpected interactions can be caused by old agent instances that have not been killed properly."
		).containsExactly(
			"Profiler registered and requested configuration my-config",
			"Profiler 123 sent logs",
			"Profiler 123 sent heartbeat",
			"Profiler 123 unregistered"
		)
	}

	/**
	 * Tests that the system under test does start up normally after the 2 minutes of timeout elapsed when Teamscale is
	 * not available.
	 */
	@Test
	@Throws(Exception::class)
	fun systemTestLenientFailure() {
		val agentJar = System.getenv("AGENT_JAR")
		val sampleJar = System.getenv("SYSTEM_UNDER_TEST_JAR")
		val result = ProcessUtils.execute("java", "-javaagent:$agentJar=config-id=some-config", "-jar", sampleJar)
		println(result.stderr)
		println(result.stdout)
		assertThat(result.exitCode).isEqualTo(0)
		assertThat(result.stdout).contains("Production code")
	}

	/**
	 * Reproduces the customer-reported scenario (dmTech) where enabling debug logging caused the
	 * `teamscale-access-token` to appear in clear text in the debug logs that are forwarded back to Teamscale via
	 * [com.teamscale.jacoco.agent.logging.LogToTeamscaleAppender]. Asserts that the raw token never reaches the
	 * server and that the obfuscated form does, proving the obfuscation is wired up at the parser-level debug logs.
	 */
	@Test
	@Throws(Exception::class)
	fun systemTestDebugLogsObfuscateAccessToken() {
		val profilerConfiguration = ProfilerConfiguration().apply {
			configurationId = "my-config"
			configurationOptions = "teamscale-partition=foo\nteamscale-project=p\nteamscale-commit=master:12345"
		}
		val teamscaleMockServer = TeamscaleMockServer(FAKE_TEAMSCALE_PORT).acceptingReportUploads()
			.withProfilerConfiguration(profilerConfiguration)

		val agentJar = System.getenv("AGENT_JAR")
		val sampleJar = System.getenv("SYSTEM_UNDER_TEST_JAR")
		val agentOptions = "config-id=my-config,teamscale-server-url=http://localhost:$FAKE_TEAMSCALE_PORT," +
				"teamscale-user=fake,teamscale-access-token=$SECRET_ACCESS_TOKEN,debug=true"
		val result = ProcessUtils.execute("java", "-javaagent:$agentJar=$agentOptions", "-jar", sampleJar)
		println(result.stderr)
		println(result.stdout)
		assertThat(result.exitCode).isEqualTo(0)

		teamscaleMockServer.shutdown()

		val forwardedLogs = teamscaleMockServer.collectedLogMessages.joinToString("\n") { "${it.severity} ${it.message}" }
		assertThat(forwardedLogs)
			.`as`("Sanity check: the parser-level debug log must be forwarded for this test to be meaningful")
			.contains("Parsing options:")
		assertThat(forwardedLogs)
			.`as`("Forwarded debug logs must not contain the raw access token in clear text")
			.doesNotContain(SECRET_ACCESS_TOKEN)
		assertThat(forwardedLogs)
			.`as`("Forwarded debug logs must show the access token in obfuscated form")
			.contains("************" + SECRET_ACCESS_TOKEN.takeLast(4))
	}

	companion object {
		/** These ports must match what is configured for the -javaagent line in this project's build.gradle.  */
		private const val FAKE_TEAMSCALE_PORT = 64100

		/** A clearly-marked secret used only by the obfuscation test so any leak in the assertion output is obvious. */
		private const val SECRET_ACCESS_TOKEN = "topSecretToken12345"
	}
}
