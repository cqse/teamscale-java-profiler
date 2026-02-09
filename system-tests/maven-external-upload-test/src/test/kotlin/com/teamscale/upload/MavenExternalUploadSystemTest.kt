package com.teamscale.upload

import com.teamscale.client.EReportFormat
import com.teamscale.client.EnvironmentVariableChecker
import com.teamscale.client.FileSystemUtils
import com.teamscale.client.SystemUtils
import com.teamscale.test.commons.ProcessUtils
import com.teamscale.test.commons.SystemTestUtils
import com.teamscale.test.commons.SystemTestUtils.runMavenTests
import com.teamscale.test.commons.TeamscaleMockServer
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
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

	private fun runCoverageUploadGoal(
		projectPath: String,
		environment: Map<String, String>? = null,
		removeEnvironmentVariables: List<String> = emptyList()
	): ProcessUtils.ProcessResult? {
		val workingDirectory = File(projectPath)
		var executable = "./mvnw"
		if (SystemUtils.IS_OS_WINDOWS) {
			executable = Paths.get(projectPath, "mvnw.cmd").toUri().getPath()
		}
		try {
			val builder = ProcessUtils.processBuilder(executable, MAVEN_COVERAGE_UPLOAD_GOAL).directory(workingDirectory)
			if (environment != null) {
				builder.setEnvironmentVariables(environment)
			}
			builder.removeEnvironmentVariables(removeEnvironmentVariables)
			return builder.execute()
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
	 * When no commit or revision is provided, the plugin should auto-resolve the Git HEAD
	 * revision and use it for the upload (TS-44960).
	 */
	@Test
	@Throws(Exception::class)
	fun testAutoResolveGitRevision() {
		runMavenTests(AUTO_RESOLVE_REVISION_PROJECT_NAME)
		val result = runCoverageUploadGoal(AUTO_RESOLVE_REVISION_PROJECT_NAME)
		assertThat(result).isNotNull()
		assertThat(result!!.exitCode).isEqualTo(0)

		val session = teamscaleMockServer!!.getSession("Unit Tests")
		assertThat(session.getReports(EReportFormat.JACOCO)).hasSize(1)
		// Verify revision is set to a valid Git SHA (40 hex characters)
		assertThat(session.getRevision()).matches("[a-f0-9]{40}")
	}

	/**
	 * When no commit or revision is configured and no git repo is available, but a CI environment variable
	 * is set, the plugin should use the commit from that variable for the upload (TS-45104).
	 */
	@Test
	@Throws(IOException::class)
	fun testCiEnvironmentVariableCommitResolution(@TempDir tmpDir: Path) {
		val fakeCommit = "abc123def456abc123def456abc123def456abc1"
		FileSystemUtils.copyFiles(File("missing-commit-project"), tmpDir.toFile()) { true }
		tmpDir.resolve("mvnw").toFile().setExecutable(true)
		val projectPath = tmpDir.toAbsolutePath().toString()
		runMavenTests(projectPath)
		val result = runCoverageUploadGoal(
			projectPath,
			environment = mapOf("GITHUB_SHA" to fakeCommit)
		)
		assertThat(result).isNotNull()
		assertThat(result!!.exitCode).isEqualTo(0)

		val session = teamscaleMockServer!!.getSession("My Custom Unit Tests Partition")
		assertThat(session.getReports(EReportFormat.JACOCO)).hasSize(1)
		assertThat(session.getRevision()).isEqualTo(fakeCommit)
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
		val result = runCoverageUploadGoal(projectPath, removeEnvironmentVariables = EnvironmentVariableChecker.COMMIT_ENVIRONMENT_VARIABLES)
		assertThat(result).isNotNull()
		assertThat(result!!.exitCode).isNotEqualTo(0)
		assertThat(teamscaleMockServer!!.getSessions()).isEmpty()
		assertThat(result.stdout)
			.contains("There is no <revision> or <commit> configured in the pom.xml, no CI environment variable was found, and it was not possible to determine the current revision")
	}

	companion object {
		private var teamscaleMockServer: TeamscaleMockServer? = null

		private const val MAVEN_COVERAGE_UPLOAD_GOAL = "com.teamscale:teamscale-maven-plugin:upload-coverage"
		private const val NESTED_MAVEN_PROJECT_NAME = "nested-project"
		private const val FAILING_MAVEN_PROJECT_NAME = "failing-project"
		private const val AUTO_RESOLVE_REVISION_PROJECT_NAME = "auto-resolve-revision-project"

		@JvmStatic
		@AfterAll
		fun stopFakeTeamscaleServer() {
			teamscaleMockServer?.shutdown()
		}
	}
}
