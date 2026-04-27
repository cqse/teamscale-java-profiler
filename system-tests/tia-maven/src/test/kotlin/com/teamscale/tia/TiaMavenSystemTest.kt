package com.teamscale.tia

import com.teamscale.client.FileSystemUtils
import com.teamscale.client.JsonUtils
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestwiseCoverageReport
import com.teamscale.test.commons.SystemTestUtils
import com.teamscale.test.commons.SystemTestUtils.coverage
import com.teamscale.test.commons.SystemTestUtils.runMavenTests
import com.teamscale.test.commons.TeamscaleMockServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.nio.file.Paths

/**
 * Runs several Maven projects' Surefire tests that have the agent attached, and one of our JUnit run listeners enabled.
 * Checks that this produces a correct coverage report.
 */
class TiaMavenSystemTest {
	private lateinit var teamscaleMockServer: TeamscaleMockServer

	@BeforeEach
	@Throws(Exception::class)
	fun startFakeTeamscaleServer() {
		teamscaleMockServer = TeamscaleMockServer(SystemTestUtils.TEAMSCALE_PORT)
			.withAuthentication("build", "6lJKEvNHeTxGPhMAi4D84DWqzoSFL1p4")
			.acceptingReportUploads()
			.withImpactedTests(
				"org/example/UnitTest/utBla()",
				"org/example/UnitTest/utFoo()",
				"org/example/UnitB1Test/utBlub()",
				"org/example/UnitB1Test/utGoo()",
				"org/example/UnitB2Test/utBlub()",
				"org/example/UnitB2Test/utGoo()",
				$$"org/example/NestedTest$Child2/getDefaultParameter()",
				"org/example/IntegrationIT/itBla()",
				"org/example/IntegrationIT/itFoo()",
				"org/example/IntegrationB1IT/itBlub()",
				"org/example/IntegrationB1IT/itGoo()",
				"org/example/IntegrationB2IT/itBlub()",
				"org/example/IntegrationB2IT/itGoo()"
			)
	}

	@AfterEach
	fun stopFakeTeamscaleServer() {
		teamscaleMockServer.shutdown()
	}

	@Test
	@Throws(Exception::class)
	fun testMavenTia() {
		runMavenTests("maven-project", "-Dtia")

		assertThat(teamscaleMockServer.allAvailableTests).extracting("partition")
			.containsOnly("Unit Tests", "Integration Tests")

		assertThat(teamscaleMockServer.getSessions()).hasSize(2)
		val unitTestSession = teamscaleMockServer.getSession("Unit Tests")
		val integrationTestSession = teamscaleMockServer.getSession("Integration Tests")

		assertThat(integrationTestSession.getCommit()).matches("abcd1337:myRepoId, .*")
		assertThat(teamscaleMockServer.impactedTestCommits[0]).matches("abcd1337:myRepoId, .*")
		assertThat(teamscaleMockServer.impactedTestCommits[1]).matches("abcd1337:myRepoId, .*")

		val unitTestReport = unitTestSession.onlyTestwiseCoverageReport
		assertThat(unitTestReport.partial).isTrue()
		checkExpectedUnitTestCoverage(unitTestReport)
		checkExpectedIntegrationTestCoverage(integrationTestSession.onlyTestwiseCoverageReport)
	}

	/**
	 * Tests that nested JUnit test classes are handled correctly (TS-45522).
	 */
	@Test
	@Throws(Exception::class)
	fun testNestedClassUniformPaths() {
		runMavenTests("maven-project", "-Dtia")

		val nestedTestNames = teamscaleMockServer.allAvailableTests

		assertThat(nestedTestNames).extracting("testName").contains(
			$$"org/example/NestedTest$Child1/getDefaultParameter()",
			$$"org/example/NestedTest$Child2/getDefaultParameter()"
		)

		val unitTestReport = teamscaleMockServer.getSession("Unit Tests").onlyTestwiseCoverageReport
		val test2 = unitTestReport.tests
			.find { it.uniformPath.contains("NestedTest\$Child2") }!!
		assertThat(test2.result).isEqualTo(ETestExecutionResult.PASSED)

		assertThat(unitTestReport.tests).extracting<String> { it.uniformPath }.doesNotContain(
			$$"org/example/NestedTest$Child1/getDefaultParameter()"
		)
	}

	@Test
	@Throws(IOException::class)
	fun testPreferBranchAndTimestampOverRevisionWhenProvidedManually() {
		runMavenTests(
			"maven-project", "-DteamscaleRevision=abcd1337",
			"-DteamscaleTimestamp=master:HEAD", "-Dtia"
		)

		assertThat(teamscaleMockServer.impactedTestCommits[0]).matches("abcd1337:myRepoId, null")
		assertThat(teamscaleMockServer.impactedTestCommits[1]).matches("abcd1337:myRepoId, null")
		assertThat(teamscaleMockServer.getSession("Unit Tests").getCommit())
			.matches("abcd1337:myRepoId, null")
	}

	@Test
	@Throws(IOException::class)
	fun testBaselineRevisionIsPreferred() {
		runMavenTests(
			"maven-project", "-DbaselineRevision=rev1", "-DbaselineCommit=master:1234", "-Dtia"
		)

		assertThat(teamscaleMockServer.baselines).containsOnly("rev1, null")
	}

	@Test
	@Throws(IOException::class)
	fun testBaselineCommitIsUsed() {
		runMavenTests("maven-project", "-DbaselineCommit=master:1234", "-Dtia")

		assertThat(teamscaleMockServer.baselines).containsOnly("null, master:1234")
	}

	@Test
	@Throws(IOException::class)
	fun testDebugLogging() {
		runMavenTests("maven-project", "-DdebugLogging=true", "-Dtia")

		assertThat(File("maven-project/sub-project-A/target/tia/agent.log")).content()
			.contains("DEBUG com.teamscale.jacoco.agent.Agent - No explicit teamscale.properties file given.");
		assertThat(File("maven-project/sub-project-A/target/tia/engine.log")).content()
			.contains("[FINE] com.teamscale.test_impacted.engine.TestEngineRegistry: Found test engines: [junit-jupiter]")
	}

	/**
	 * Starts a maven process with the reuseForks flag set to "false". Checks if the coverage can be converted to a
	 * testwise coverage report afterward.
	 */
	@Test
	@Throws(Exception::class)
	fun testMavenTiaWithoutReuseForks() {
		runMavenTests("maven-project", "-Dtia", "-DreuseForks=false")
		val workingDirectory = File("maven-project")
		val testwiseCoverage = File(
			Paths.get(workingDirectory.absolutePath, "coverage", "target", "tia", "reports", "testwise-coverage-1.json")
				.toUri()
		)
		val testwiseCoverageReport = JsonUtils.deserialize<TestwiseCoverageReport>(
			FileSystemUtils.readFileUTF8(testwiseCoverage)
		)
		checkExpectedUnitTestCoverage(testwiseCoverageReport)
	}

	companion object {
		private fun checkExpectedUnitTestCoverage(testwiseCoverageReport: TestwiseCoverageReport?) {
			assertThat(testwiseCoverageReport).isNotNull()
			assertThat(testwiseCoverageReport!!.tests)
				.extracting<String> { it.uniformPath }
				.containsExactlyInAnyOrder(
					"org/example/UnitTest/utBla()",
					"org/example/UnitTest/utFoo()",
					"org/example/UnitB1Test/utBlub()",
					"org/example/UnitB1Test/utGoo()",
					"org/example/UnitB2Test/utBlub()",
					"org/example/UnitB2Test/utGoo()",
					$$"org/example/NestedTest$Child2/getDefaultParameter()"
				)
			assertThat(testwiseCoverageReport.tests)
				.extracting<ETestExecutionResult> { it.result }
				.allMatch { it == ETestExecutionResult.PASSED }
			assertThat(testwiseCoverageReport.tests)
				.extracting<String> { it.coverage }.containsExactlyInAnyOrder(
					"SUTA.java:7,10-11;UnitTest.java:10-11",
					"SUTA.java:7,14-15;UnitTest.java:15-16",
					"SUTB1.java:7,14-15;UnitB1Test.java:10-11",
					"SUTB1.java:7,10-11;UnitB1Test.java:15-16",  // Ensure that sources from the project itself (SUT2), but also other dependent projects is considered (SUT1)
					"SUTB1.java:7,14-15;SUTB2.java:7,14-15;UnitB2Test.java:10-12",
					"SUTB1.java:7,10-11;SUTB2.java:7,10-11;UnitB2Test.java:16-18",
					"NestedTest.java:20-21;SUTA.java:7,14-15"
				)
		}

		private fun checkExpectedIntegrationTestCoverage(integrationTestReport: TestwiseCoverageReport) {
			assertThat(integrationTestReport.tests)
				.extracting<String> { it.uniformPath }
				.containsExactlyInAnyOrder(
					"org/example/IntegrationIT/itBla()",
					"org/example/IntegrationIT/itFoo()",
					"org/example/IntegrationB1IT/itBlub()",
					"org/example/IntegrationB1IT/itGoo()",
					"org/example/IntegrationB2IT/itBlub()",
					"org/example/IntegrationB2IT/itGoo()"
				)
			assertThat(integrationTestReport.tests)
				.extracting<ETestExecutionResult> { it.result }
				.allMatch { it == ETestExecutionResult.PASSED }
			assertThat(integrationTestReport.tests)
				.extracting<String> { it.coverage }
				.containsExactly(
					"IntegrationIT.java:10-11;SUTA.java:7,10-11",
					"IntegrationIT.java:15-16;SUTA.java:7,14-15",
					"IntegrationB1IT.java:10-11;SUTB1.java:7,14-15",
					"IntegrationB1IT.java:15-16;SUTB1.java:7,10-11",
					"IntegrationB2IT.java:10-11;SUTB2.java:7,14-15",
					"IntegrationB2IT.java:15-16;SUTB2.java:7,10-11"
				)
		}
	}
}
