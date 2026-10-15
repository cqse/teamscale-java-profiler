package com.teamscale.tia

import com.teamscale.client.EnvironmentVariableChecker
import com.teamscale.client.JsonUtils
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestwiseCoverageReport
import com.teamscale.test.commons.SystemTestUtils
import com.teamscale.test.commons.SystemTestUtils.copyMavenProject
import com.teamscale.test.commons.SystemTestUtils.coverage
import com.teamscale.test.commons.SystemTestUtils.getReportFileNames
import com.teamscale.test.commons.SystemTestUtils.runMavenTests
import com.teamscale.test.commons.TeamscaleMockServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

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

		assertCoverageReportsDumped(MAVEN_PROJECT_NAME)
	}

	/**
	 * With `runImpacted=false`, the plugin must not require a commit or revision. So the build must succeed outside of
	 * a Git checkout and without CI environment variables (TS-48002).
	 */
	@Test
	@Throws(Exception::class)
	fun testMavenTiaWithoutCommit(@TempDir tmpDir: Path) {
		val projectPath = copyMavenProject(MAVEN_PROJECT_NAME, tmpDir)

		val result = runMavenTests(
			projectPath,
			removeEnvironmentVariables = EnvironmentVariableChecker.COMMIT_ENVIRONMENT_VARIABLES
		)

		assertThat(result.exitCode)
			.withFailMessage { "Maven failed:\n${result.stdout}\n${result.stderr}" }
			.isEqualTo(0)
		assertCoverageReportsDumped(projectPath)
	}

	private fun assertCoverageReportsDumped(projectPath: String) {
		val unitTestReport = parseDumpedCoverageReport(projectPath, "tia")
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

		val integrationTestReport = parseDumpedCoverageReport(projectPath, "tia-integration")
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
	private fun parseDumpedCoverageReport(projectPath: String, folderName: String): TestwiseCoverageReport {
		val files = getReportFileNames(projectPath, folderName)
		return JsonUtils.deserialize<TestwiseCoverageReport>(
			String(Files.readAllBytes(files.first()))
		)
	}

	companion object {
		private const val MAVEN_PROJECT_NAME = "maven-dump-local-project"
	}
}
