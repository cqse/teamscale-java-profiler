package com.teamscale.tia

import com.teamscale.test.commons.SystemTestUtils.buildMavenProcess
import com.teamscale.test.commons.SystemTestUtils.runMaven
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Paths

/**
 * Test class to check if multiple maven plugins can be started with dynamic port allocation.
 */
class TiaMavenMultipleJobsTest {
	/**
	 * Starts multiple Maven processes and checks that the ports are dynamically set and the servers are correctly
	 * started.
	 */
	@Test
	@Throws(Exception::class)
	fun testMavenTia() {
		val workingDirectory = "maven-dump-local-project"

		// Clean once before testing parallel execution and make sure that the cleaning
		// process is finished before testing.
		runMaven(workingDirectory, "clean")

		// run three verify processes in parallel without waiting
		repeat(3) {
			buildMavenProcess(workingDirectory, "verify").build().start()
		}

		// and one more that we wait for to terminate
		runMaven(workingDirectory, "verify")

		val configFile = Paths.get(workingDirectory, "target", "tia", "agent.log")
		val configContent = configFile.toFile().readText()
		Assertions.assertThat(configContent).isNotEmpty().doesNotContain("Could not start http server on port")
	}
}
