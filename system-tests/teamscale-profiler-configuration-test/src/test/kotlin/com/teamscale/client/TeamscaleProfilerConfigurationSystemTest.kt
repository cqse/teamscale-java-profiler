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

	companion object {
		/** These ports must match what is configured for the -javaagent line in this project's build.gradle.  */
		private const val FAKE_TEAMSCALE_PORT = 64100
	}
}
