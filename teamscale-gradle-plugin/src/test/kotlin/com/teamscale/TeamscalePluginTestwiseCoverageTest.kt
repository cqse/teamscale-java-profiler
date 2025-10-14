package com.teamscale

import com.teamscale.client.EReportFormat
import com.teamscale.client.JsonUtils
import com.teamscale.plugin.fixtures.TestwiseCoverageReportAssert.Companion.assertThat
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestwiseCoverageReport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource


/**
 * Integration tests for the Teamscale Gradle plugin focusing on the testwise coverage generation.
 */
@ParameterizedClass
@ValueSource(strings = ["5.12.0", "5.13.4", "6.0.0"])
class TeamscalePluginTestwiseCoverageTest : TeamscalePluginTestBase() {

	@Parameter
	var junitVersion: String? = null

	@BeforeEach
	fun init() {
		rootProject.withSingleProject()
		rootProject.withDependencyResolutionManagement()
		rootProject.withTeamscalePlugin()
		rootProject.withJunitDependencies(junitVersion!!)
	}

	@Test
	fun `all unit tests produce coverage`() {
		rootProject.withServerConfig()
		rootProject.defineTestTasks()
		rootProject.defineUploadTask()

		val build = runExpectingError(
			"--continue",
			"clean",
			"unitTest",
			"-Dimpacted",
			"-DrunAllTests",
			"unitTestReportUpload"
		)
		assertThat(build.output).contains("FAILURE (21 tests, 14 successes, 1 failures, 6 skipped)")
			.doesNotContain("you did not provide all relevant class files")
		val testwiseCoverageReportFile =
			rootProject.buildDir.resolve("reports/testwise-coverage/unitTestReport.json")
		assertThat(testwiseCoverageReportFile).exists()

		assertFullCoverage(testwiseCoverageReportFile.readText())

		val session = teamscaleMockServer.getOnlySession("Unit Tests")
		assertFullCoverage(session.getOnlyReport(EReportFormat.TESTWISE_COVERAGE))
	}

	@Test
	fun `only impacted unit tests are executed`() {
		rootProject.withServerConfig()
		rootProject.defineTestTasks()
		rootProject.defineUploadTask()

		val build = run(
			"--continue",
			"clean",
			"unitTest",
			"-Dimpacted",
			"unitTestReportUpload"
		)
		assertThat(build.output).contains("SUCCESS (1 tests, 1 successes, 0 failures, 0 skipped)")
		val testwiseCoverageReportFile =
			rootProject.buildDir.resolve("reports/testwise-coverage/unitTestReport.json")
		assertThat(testwiseCoverageReportFile).exists()

		assertPartialCoverage(testwiseCoverageReportFile.readText())

		val session = teamscaleMockServer.getOnlySession("Unit Tests")
		assertPartialCoverage(session.getOnlyReport(EReportFormat.TESTWISE_COVERAGE))
	}

	@Test
	fun `unit tests without server config produce coverage`() {
		rootProject.defineTestTasks()

		val build = runExpectingError("clean", "unitTest")
		assertThat(build.output).contains("FAILURE (21 tests, 14 successes, 1 failures, 6 skipped)")
			.doesNotContain("you did not provide all relevant class files")
			.doesNotContain("WARNING: JAXBContext implementation could not be found. WADL feature is disabled.")
			.doesNotContain("WARNING: A class javax.activation.DataSource for a default provider")
		val testwiseCoverageReportFile =
			rootProject.buildDir.resolve("reports/testwise-coverage/unitTestReport.json")
		assertThat(testwiseCoverageReportFile).exists()

		val source = testwiseCoverageReportFile.readText()
		assertFullCoverage(source)
	}

	@Test
	fun `wrong include pattern produces error`() {
		rootProject.defineTestTasks("non.existent.package.*")

		val build = runExpectingError("clean", "unitTest")
		assertThat(build.output).contains("No coverage was recorded for any of the executed tests! Check your jacoco include/exclude patterns on the Test task.")
	}

	private fun assertFullCoverage(source: String) {
		val testwiseCoverageReport = JsonUtils.deserialize<TestwiseCoverageReport>(source)
		assertThat(testwiseCoverageReport)
			.hasPartial(false)
			.containsExecutionResult("com/example/project/IgnoredJUnit4Test/systemTest", ETestExecutionResult.SKIPPED)
			.containsExecutionResult("com/example/project/JUnit4Test/systemTest", ETestExecutionResult.PASSED)
			.containsExecutionResult(
				"com/example/project/JUnit5Test/withValueSource(String)",
				ETestExecutionResult.PASSED
			)
			.containsExecutionResult(
				"com/example/project/FailingRepeatedTest/testRepeatedTest()",
				ETestExecutionResult.FAILURE
			)
			.containsExecutionResult("com/example/project/FibonacciTest/test[4]", ETestExecutionResult.PASSED)
			.containsCoverage(
				"com/example/project/JUnit4Test/systemTest",
				"com/example/project/Calculator.java",
				"13,16,20-22"
			)
			// 19 Tests because JUnit 5 parameterized tests are grouped
			.hasSize(19)
	}

	private fun assertPartialCoverage(source: String) {
		val testwiseCoverageReport = JsonUtils.deserialize<TestwiseCoverageReport>(source)
		assertThat(testwiseCoverageReport)
			.hasPartial(true)
			.containsExecutionResult("com/example/project/JUnit4Test/systemTest", ETestExecutionResult.PASSED)
			.containsCoverage(
				"com/example/project/JUnit4Test/systemTest",
				"com/example/project/Calculator.java",
				"13,16,20-22"
			)
			.hasSize(19)
			.hasTestsWithCoverage(1)
	}
}
