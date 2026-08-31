package com.teamscale.tia

import com.teamscale.client.EReportFormat
import com.teamscale.test.commons.SystemTestUtils
import com.teamscale.test.commons.SystemTestUtils.runGradle
import com.teamscale.test.commons.TeamscaleMockServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Runs tests in all submodules and expects the results of both in the upload.
 */
class TestwiseCoverageGradleSystemTest {
	private lateinit var teamscaleMockServer: TeamscaleMockServer

	@BeforeEach
	@Throws(Exception::class)
	fun startFakeTeamscaleServer() {
		teamscaleMockServer = TeamscaleMockServer(SystemTestUtils.TEAMSCALE_PORT)
			.acceptingReportUploads().withImpactedTests("com/example/app/MainTest/testMain()")
	}

	@AfterEach
	fun stopFakeTeamscaleServer() {
		teamscaleMockServer.shutdown()
	}

	@Test
	@Throws(Exception::class)
	fun testGradleAggregatedTestwiseCoverageUploadWithoutJVMTestSuite() {
		runGradle("gradle-project", "clean", "teamscaleSystemTestReportUpload")

		val testwiseReport = teamscaleMockServer.getOnlyTestwiseCoverageReport("System Tests")
		assertThat(testwiseReport.partial).isEqualTo(false)
		assertThat(testwiseReport.tests).extracting<String> { it.uniformPath }
			.containsExactly(
				"com/example/app/MainTest/testMain()",
				// Each test method of a @ParameterizedClass is one test, no matter how many parameter sets it ran with.
				"com/example/lib/CalculatorParameterizedTest/testAddIsCommutative()",
				"com/example/lib/CalculatorTest/testAdd()"
			)
		assertThat(testwiseReport.tests).allMatch { it.paths.isNotEmpty() }
	}

	@Test
	@Throws(Exception::class)
	fun testGradleAggregatedTestwiseCoverageUploadHasPartialFlagSet() {
		runGradle(
			"gradle-project", "clean",
			"systemTest", "-Dimpacted",
			"teamscaleSystemTestReportUpload"
		)

		val testwiseReport = teamscaleMockServer.getOnlyTestwiseCoverageReport("System Tests")
		assertThat(testwiseReport.partial).isEqualTo(true)
		assertThat(testwiseReport.tests).extracting<String> { it.uniformPath }
			.containsExactly(
				"com/example/app/MainTest/testMain()",
				"com/example/lib/CalculatorParameterizedTest/testAddIsCommutative()",
				"com/example/lib/CalculatorTest/testAdd()"
			)
		// Only the impacted test was executed, so it is the only one with coverage.
		assertThat(testwiseReport.tests.first().paths).isNotEmpty()
		assertThat(testwiseReport.tests.drop(1)).allMatch { it.paths.isEmpty() }
	}

	@Test
	@Throws(Exception::class)
	fun testGradleAggregatedCompactCoverageUploadWithoutJVMTestSuite() {
		val result = runGradle("gradle-project", "clean", "unitTest", "teamscaleUnitTestReportUpload")
		assertThat(result.isSuccess).isTrue()

		val session = teamscaleMockServer.getOnlySession("Unit Tests")
		assertThat(session.getReports()).hasSize(4)
		// One JUnit report per test class: MainTest, CalculatorTest and CalculatorParameterizedTest.
		assertThat(session.getReports(EReportFormat.JUNIT)).hasSize(3)

		val compactReport = session.getCompactCoverageReport(0)!!

		with(compactReport.coverage) {
			assertThat(first().filePath).isEqualTo("com/example/app/Main.java")
			assertThat(last().filePath).isEqualTo("com/example/lib/Calculator.java")
			assertThat(first().fullyCoveredLines).containsExactly(7, 8, 9)
			assertThat(last().fullyCoveredLines).containsExactly(3, 6, 16)
		}
	}

	@Test
	@Throws(Exception::class)
	fun testGradleAggregatedCompactCoverageUploadWithJVMTestSuite() {
		val result = runGradle("gradle-project", "clean", "teamscaleTestReportUpload")
		assertThat(result.isSuccess).isTrue()

		val session = teamscaleMockServer.getOnlySession("Default Tests")
		assertThat(session.getReports()).hasSize(4)
		// One JUnit report per test class: MainTest, CalculatorTest and CalculatorParameterizedTest.
		assertThat(session.getReports(EReportFormat.JUNIT)).hasSize(3)

		val compactReport = session.getCompactCoverageReport(0)!!
		assertThat(compactReport.coverage.first().filePath).isEqualTo("com/example/app/Main.java")
		assertThat(compactReport.coverage.last().filePath).isEqualTo("com/example/lib/Calculator.java")
		assertThat(compactReport.coverage.first().fullyCoveredLines).containsExactly(7, 8, 9)
		assertThat(compactReport.coverage.last().fullyCoveredLines).containsExactly(3, 6, 16)
	}

	@Test
	@Throws(Exception::class)
	fun testDebugLogging() {
		val result = runGradle("gradle-project", "clean", "systemTest", "-DdebugLogging=true", "-Dimpacted")
		assertThat(result.isSuccess).isTrue()

		assertThat(File("gradle-project/app/build/jacoco/systemTest/logs/teamscale-jacoco-agent.log")).content()
			.contains("DEBUG Agent - No explicit teamscale.properties file given.")
		assertThat(File("gradle-project/app/build/jacoco/systemTest/engine.log")).content()
			.contains("[FINE] com.teamscale.test_impacted.engine.TestEngineRegistry: Found test engines: [junit-jupiter]")
	}
}
