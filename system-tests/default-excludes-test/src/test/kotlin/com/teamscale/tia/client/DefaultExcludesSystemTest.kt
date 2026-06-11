package com.teamscale.tia.client

import com.teamscale.test.commons.SystemTestUtils
import com.teamscale.test.commons.SystemTestUtils.dumpCoverage
import com.teamscale.test.commons.TeamscaleMockServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import systemundertest.SystemUnderTest

/**
 * Runs the system under test and then forces a dump of the agent to our [TeamscaleMockServer]. Checks the
 * resulting report to ensure the default excludes are applied.
 *
 * This test also acts as the end-to-end smoke test for the agent's default report format (Teamscale Compact Coverage).
 */
class DefaultExcludesSystemTest {
	@Test
	@Throws(Exception::class)
	fun systemTest() {
		System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog")
		System.setProperty("org.eclipse.jetty.LEVEL", "OFF")

		val teamscaleMockServer = TeamscaleMockServer(SystemTestUtils.TEAMSCALE_PORT)
			.withAuthentication("fake", "fake")
			.acceptingReportUploads()

		SystemUnderTest().foo()
		dumpCoverage(SystemTestUtils.AGENT_PORT)

		val report = teamscaleMockServer.getSession("part").getCompactCoverageReport(0)
			?: error("Expected a Teamscale Compact Coverage report to be uploaded")
		val filePaths = report.coverage.map { it.filePath }
		assertThat(filePaths)
			.noneMatch { it.contains("shadow") || it.contains("junit") || it.contains("eclipse") }
			.noneMatch { it.contains("apache") || it.contains("javax") || it.contains("slf4j") }
			.noneMatch { it.contains("com/sun") }
			.anyMatch { it.contains("SystemUnderTest") }
			.anyMatch { it.contains("NotExcludedClass") }
	}
}
