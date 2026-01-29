package com.teamscale.client

import com.teamscale.test.commons.SystemTestUtils
import com.teamscale.test.commons.SystemTestUtils.dumpCoverage
import com.teamscale.test.commons.TeamscaleMockServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import systemundertest.SystemUnderTest

/**
 * Runs the system under test and then forces a dump of the agent to a [RedirectMockServer]. This sends redirects
 * to our [TeamscaleMockServer]. Checks that the agent respects and follows these redirects.
 */
class HttpRedirectSystemTest {
	@Test
	@Throws(Exception::class)
	fun systemTest() {
		System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog")
		System.setProperty("org.eclipse.jetty.LEVEL", "OFF")

		val redirectMockServer = RedirectMockServer(REDIRECT_PORT, SystemTestUtils.TEAMSCALE_PORT)
		val teamscaleMockServer = TeamscaleMockServer(SystemTestUtils.TEAMSCALE_PORT).acceptingReportUploads()

		SystemUnderTest().foo()
		dumpCoverage(SystemTestUtils.AGENT_PORT)

		assertThat(teamscaleMockServer.getOnlySession("part").getReports()).hasSize(1)
		checkCustomUserAgent(teamscaleMockServer)

		redirectMockServer.shutdown()
		teamscaleMockServer.shutdown()
	}

	private fun checkCustomUserAgent(teamscaleMockServer: TeamscaleMockServer) {
		val collectedUserAgents = teamscaleMockServer.collectedUserAgents
		assertThat(collectedUserAgents).allMatch { it.matches(Regex("Teamscale Java Profiler/\\d+\\.\\d+.*")) }
	}

	companion object {
		private val REDIRECT_PORT = Integer.getInteger("redirectPort")
	}
}
