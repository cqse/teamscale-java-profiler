package com.teamscale.client

import com.teamscale.test.commons.SystemTestUtils
import com.teamscale.test.commons.SystemTestUtils.dumpCoverage
import com.teamscale.test.commons.TeamscaleMockServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import systemundertest.SystemUnderTest
import java.nio.file.Paths
import kotlin.io.path.readLines

/**
 * Runs the agent with an interval of one minute and forces a dump that uploads to our [TeamscaleMockServer]. Checks
 * that the agent warns about the short interval and raises it after the successful upload.
 */
class DumpIntervalTooShortSystemTest {
	@Test
	fun appliesShortIntervalUntilFirstSuccessfulUpload() {
		System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog")
		System.setProperty("org.eclipse.jetty.LEVEL", "OFF")
		TeamscaleMockServer(SystemTestUtils.TEAMSCALE_PORT)
			.withAuthentication("fake", "fake")
			.acceptingReportUploads()

		SystemUnderTest().foo()
		dumpCoverage(SystemTestUtils.AGENT_PORT)

		val log = LOG_FILE.readLines()
		assertThat(log.filter { it.contains("You configured an interval smaller than 1h.") })
			.`as`("An agent that uploads with a short interval should warn once at startup")
			.hasSize(1)
			.allMatch { it.contains("WARN") }
		assertThat(log)
			.`as`("The first successful upload should raise the interval to one hour")
			.anyMatch { it.contains("INFO") && it.contains("Increased the coverage dump interval from 1 to 60 minutes.") }
	}

	companion object {
		private val LOG_FILE = Paths.get("logTest").resolve("logs").resolve("teamscale-jacoco-agent.log")
	}
}
