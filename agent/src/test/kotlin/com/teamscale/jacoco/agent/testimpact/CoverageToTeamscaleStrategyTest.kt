package com.teamscale.jacoco.agent.testimpact

import com.teamscale.client.*
import com.teamscale.jacoco.agent.JacocoRuntimeController
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestwiseCoverage
import com.teamscale.report.testwise.model.builder.FileCoverageBuilder
import com.teamscale.report.testwise.model.builder.TestCoverageBuilder
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.matches
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import retrofit2.Response
import java.io.File
import java.io.IOException

@ExtendWith(MockitoExtension::class)
class CoverageToTeamscaleStrategyTest {

	@Mock
	private lateinit var client: TeamscaleClient

	@Mock
	private lateinit var reportGenerator: JaCoCoTestwiseReportGenerator

	@Mock
	private lateinit var controller: JacocoRuntimeController

	@TempDir
	lateinit var tempDir: File

	@Test
	@Throws(Exception::class)
	fun shouldRecordCoverageForTestsEvenIfNotProvidedAsAvailableTest() {
		val options = mockOptions(false)
		val strategy = CoverageToTeamscaleStrategy(controller, options, reportGenerator)

		val testwiseCoverage = getDummyTestwiseCoverage("mytest")
		whenever(reportGenerator.convert(any<File>())).thenReturn(testwiseCoverage)

		// we skip testRunStart and don't provide any available tests
		strategy.testStart("mytest")
		strategy.testEnd("mytest", TestExecution("mytest", 0L, ETestExecutionResult.PASSED))
		strategy.testRunEnd(false)

		verify(client).uploadReport(
			eq(EReportFormat.TESTWISE_COVERAGE),
			matches("\\Q{\"partial\":false,\"tests\":[{\"uniformPath\":\"mytest\",\"sourcePath\":\"mytest\",\"duration\":\\E[^,]*\\Q,\"result\":\"PASSED\",\"paths\":[{\"path\":\"src/main/java\",\"files\":[{\"fileName\":\"Main.java\",\"coveredLines\":\"1-4\"}]}]}]}\\E"),
			anyOrNull(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull()
		)
	}

	@ParameterizedTest
	@ValueSource(booleans = [true, false])
	@Throws(Exception::class)
	fun testValidCallSequence(useRevision: Boolean) {
		val clusters = listOf(
			PrioritizableTestCluster(
				"cluster",
				listOf(PrioritizableTest("mytest"))
			)
		)

		whenever(
			client.getImpactedTests(
				anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
				anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
			)
		).thenReturn(Response.success(clusters))

		val testwiseCoverage = getDummyTestwiseCoverage("mytest")
		whenever(reportGenerator.convert(any<File>())).thenReturn(testwiseCoverage)

		val options = mockOptions(useRevision)
		val strategy = CoverageToTeamscaleStrategy(controller, options, reportGenerator)

		strategy.testRunStart(
			listOf(ClusteredTestDetails("mytest", "mytest", "content", "cluster")),
			false,
			true,
			true,
			null,
			null
		)
		strategy.testStart("mytest")
		strategy.testEnd("mytest", TestExecution("mytest", 0L, ETestExecutionResult.PASSED))
		strategy.testRunEnd(true)

		verify(client).uploadReport(
			eq(EReportFormat.TESTWISE_COVERAGE),
			matches("\\Q{\"partial\":true,\"tests\":[{\"uniformPath\":\"mytest\",\"sourcePath\":\"mytest\",\"content\":\"content\",\"duration\":\\E[^,]*\\Q,\"result\":\"PASSED\",\"paths\":[{\"path\":\"src/main/java\",\"files\":[{\"fileName\":\"Main.java\",\"coveredLines\":\"1-4\"}]}]}]}\\E"),
			anyOrNull(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull()
		)
	}

	@Throws(IOException::class)
	private fun mockOptions(useRevision: Boolean): AgentOptions {
		val options = mock<AgentOptions>()
		whenever(options.createTeamscaleClient(true)).thenReturn(client)
		whenever(options.createNewFileInOutputDirectory(any(), any())).thenReturn(File(tempDir, "test"))

		val server = TeamscaleServer().apply {
			if (useRevision) {
				revision = "rev1"
			} else {
				commit = CommitDescriptor("branch", "12345")
			}
			url = "https://doesnt-exist.io".toHttpUrl()
			userName = "build"
			userAccessToken = "token"
			partition = "partition"
		}
		options.teamscaleServer = server

		return options
	}

	companion object {
		/** Returns a dummy testwise coverage object for a test with the given name that covers a few lines of Main.java.  */
		@JvmStatic
		fun getDummyTestwiseCoverage(test: String): TestwiseCoverage {
			val testCoverageBuilder = TestCoverageBuilder(test)
			val fileCoverageBuilder = FileCoverageBuilder("src/main/java", "Main.java")
			fileCoverageBuilder.addLineRange(1, 4)
			testCoverageBuilder.add(fileCoverageBuilder)
			val testwiseCoverage = TestwiseCoverage()
			testwiseCoverage.add(testCoverageBuilder)
			return testwiseCoverage
		}
	}
}