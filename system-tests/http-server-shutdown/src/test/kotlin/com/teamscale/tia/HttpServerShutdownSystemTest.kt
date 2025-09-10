package com.teamscale.tia

import com.teamscale.test.commons.ProcessUtils
import com.teamscale.test.commons.SystemTestUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * Runs the agent with the HTTP server enabled and makes sure that the profiled application is shut down. This ensures
 * that the HTTP server is not blocking application shutdown with non-daemon threads.
 */
class HttpServerShutdownSystemTest {
	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS) // if the test exceeds the timeout, the shutdown didn't succeed
	@Throws(Exception::class)
	fun testShutdown() {
		val agentJar = System.getenv("AGENT_JAR")
		val sampleJar = System.getenv("SAMPLE_JAR")
		val result = ProcessUtils.execute(
			"java",
			"-javaagent:$agentJar=http-server-port=${SystemTestUtils.AGENT_PORT}",
			"-jar", sampleJar
		)
		println(result.stderr)
		println(result.stdout)
		assertThat(result.exitCode).isEqualTo(0)
	}
}
