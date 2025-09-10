package com.teamscale.tia

import com.teamscale.client.JsonUtils
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestwiseCoverageReport
import com.teamscale.test.commons.SystemTestUtils
import com.teamscale.test.commons.SystemTestUtils.coverage
import com.teamscale.test.commons.SystemTestUtils.getReportFileNames
import com.teamscale.test.commons.SystemTestUtils.runMavenTests
import com.teamscale.test.commons.TeamscaleMockServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.io.IOException
import java.nio.file.Files

/**
 * Runs several Maven projects' Surefire tests that have the agent attached and one of our JUnit run listeners enabled.
 * Checks that this produces a correct coverage report.
 */
class TiaMavenDumpToFileSystemTest {
	private lateinit var teamscaleMockServer: TeamscaleMockServer

	@BeforeEach
	@Throws(Exception::class)
	fun startFakeTeamscaleServer() {
		teamscaleMockServer = TeamscaleMockServer(SystemTestUtils.TEAMSCALE_PORT).disallowingStateChanges()
	}

	@AfterEach
	fun stopFakeTeamscaleServer() {
		teamscaleMockServer.shutdown()
	}

	@Test
	@Throws(Exception::class)
	fun testMavenTia() {
		runMavenTests(MAVEN_PROJECT_NAME)

		val unitTestReport = parseDumpedCoverageReport("tia")
		assertThat(unitTestReport.tests).hasSize(2)
		assertThat(unitTestReport.partial).isFalse()
		assertAll({
			assertThat(unitTestReport.tests)
				.extracting<String> { it.uniformPath }
				.containsExactlyInAnyOrder("bar/UnitTest/utBla()", "bar/UnitTest/utFoo()")
			assertThat(unitTestReport.tests)
				.extracting<ETestExecutionResult> { it.result }
				.containsExactlyInAnyOrder(ETestExecutionResult.PASSED, ETestExecutionResult.PASSED)
			assertThat(unitTestReport.tests)
				.extracting<String> { it.coverage }
				.containsExactly("SUT.java:3,6-7", "SUT.java:3,10-11")
		})

		val integrationTestReport = parseDumpedCoverageReport("tia-integration")
		assertThat(integrationTestReport.tests).hasSize(2)
		assertAll({
			assertThat(integrationTestReport.tests)
				.extracting<String> { it.uniformPath }
				.containsExactlyInAnyOrder("bar/IntegIT/itBla()", "bar/IntegIT/itFoo()")
			assertThat(integrationTestReport.tests)
				.extracting<ETestExecutionResult> { it.result }
				.containsExactlyInAnyOrder(ETestExecutionResult.PASSED, ETestExecutionResult.PASSED)
			assertThat(integrationTestReport.tests)
				.extracting<String> { it.coverage }
				.containsExactly("SUT.java:3,6-7", "SUT.java:3,10-11")
		})
	}

	@Throws(IOException::class)
	private fun parseDumpedCoverageReport(folderName: String): TestwiseCoverageReport {
		val files = getReportFileNames(MAVEN_PROJECT_NAME, folderName)
		return JsonUtils.deserialize<TestwiseCoverageReport>(
			String(Files.readAllBytes(files.first()))
		)
	}

	companion object {
		private const val MAVEN_PROJECT_NAME = "maven-dump-local-project"
	}
}
