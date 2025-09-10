package com.teamscale.upload

import com.teamscale.client.EReportFormat
import com.teamscale.client.FileSystemUtils
import com.teamscale.test.commons.SystemTestUtils
import com.teamscale.test.commons.SystemTestUtils.runMavenTests
import com.teamscale.test.commons.TeamscaleMockServer
import org.apache.commons.lang3.SystemUtils
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.conqat.lib.commons.io.ProcessUtils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Runs several Maven projects' Surefire and Failsafe tests that produce coverage via the Jacoco Maven plugin. Checks
 * that the Jacoco reports are correctly uploaded to a Teamscale instance.
 */
class MavenExternalUploadSystemTest {
	@BeforeEach
	@Throws(Exception::class)
	fun startFakeTeamscaleServer() {
		if (teamscaleMockServer == null) {
			teamscaleMockServer = TeamscaleMockServer(SystemTestUtils.TEAMSCALE_PORT).acceptingReportUploads()
		}
		teamscaleMockServer?.reset()
	}

	private fun runCoverageUploadGoal(projectPath: String): ProcessUtils.ExecutionResult? {
		val workingDirectory = File(projectPath)
		var executable = "./mvnw"
		if (SystemUtils.IS_OS_WINDOWS) {
			executable = Paths.get(projectPath, "mvnw.cmd").toUri().getPath()
		}
		try {
			return ProcessUtils.execute(
				ProcessBuilder(executable, MAVEN_COVERAGE_UPLOAD_GOAL).directory(workingDirectory)
			)
		} catch (e: IOException) {
			Assertions.fail(e.toString())
		}
		return null
	}

	@Test
	@Throws(Exception::class)
	fun testMavenExternalUpload() {
		runMavenTests(NESTED_MAVEN_PROJECT_NAME)
		runCoverageUploadGoal(NESTED_MAVEN_PROJECT_NAME)
		val sessions = teamscaleMockServer!!.getSessions()
		assertThat(sessions).hasSize(2)
		assertThat(sessions.first().partition).isEqualTo("Integration Tests")
		assertThat(sessions.last().partition).isEqualTo("My Custom Unit Tests Partition")

		assertThat(sessions.first().getReports(EReportFormat.JACOCO)).hasSize(3)
		assertThat(sessions.last().getReports(EReportFormat.JACOCO)).hasSize(3)
	}

	@Test
	@Throws(IOException::class)
	fun testIncorrectJaCoCoConfiguration() {
		runMavenTests(FAILING_MAVEN_PROJECT_NAME)
		val result = runCoverageUploadGoal(FAILING_MAVEN_PROJECT_NAME)
		assertThat(result).isNotNull()
		assertThat(teamscaleMockServer!!.getSessions()).isEmpty()
		assertThat(result!!.stdout).contains(
			"Skipping upload for $FAILING_MAVEN_PROJECT_NAME as org.jacoco:jacoco-maven-plugin is not configured to produce XML reports"
		)
	}

	/**
	 * When no commit is given and no git repo is available, which is the usual fallback, a helpful error message should
	 * be shown (TS-40425).
	 */
	@Test
	@Throws(IOException::class)
	fun testErrorMessageOnMissingCommit(@TempDir tmpDir: Path) {
		FileSystemUtils.copyFiles(File("missing-commit-project"), tmpDir.toFile()) { true }
		tmpDir.resolve("mvnw").toFile().setExecutable(true)
		val projectPath = tmpDir.toAbsolutePath().toString()
		runMavenTests(projectPath)
		val result = runCoverageUploadGoal(projectPath)
		assertThat(result).isNotNull()
		assertThat(result!!.returnCode).isNotEqualTo(0)
		assertThat(teamscaleMockServer!!.getSessions()).isEmpty()
		assertThat(result.stdout)
			.contains("There is no <revision> or <commit> configured in the pom.xml and it was not possible to determine the current revision")
	}

	companion object {
		private var teamscaleMockServer: TeamscaleMockServer? = null

		private const val MAVEN_COVERAGE_UPLOAD_GOAL = "com.teamscale:teamscale-maven-plugin:upload-coverage"
		private const val NESTED_MAVEN_PROJECT_NAME = "nested-project"
		private const val FAILING_MAVEN_PROJECT_NAME = "failing-project"

		@JvmStatic
		@AfterAll
		fun stopFakeTeamscaleServer() {
			teamscaleMockServer?.shutdown()
		}
	}
}
