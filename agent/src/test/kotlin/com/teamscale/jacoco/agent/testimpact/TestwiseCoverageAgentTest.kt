package com.teamscale.jacoco.agent.testimpact

import com.teamscale.client.ClusteredTestDetails
import com.teamscale.client.CommitDescriptor
import com.teamscale.client.EReportFormat
import com.teamscale.client.PrioritizableTest
import com.teamscale.client.PrioritizableTestCluster
import com.teamscale.client.TeamscaleClient
import com.teamscale.client.TeamscaleServer
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.jacoco.agent.options.ETestwiseCoverageMode
import com.teamscale.jacoco.agent.util.TestUtils
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.tia.client.RunningTest
import com.teamscale.tia.client.TestRun
import com.teamscale.tia.client.TestRunWithClusteredSuggestions
import com.teamscale.tia.client.TiaAgent
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.matches
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.File

@ExtendWith(MockitoExtension::class)
class TestwiseCoverageAgentTest {

	@Mock
	private lateinit var client: TeamscaleClient

	@Mock
	private lateinit var reportGenerator: JaCoCoTestwiseReportGenerator

	@TempDir
	lateinit var tempDir: File

	@Test
	@Throws(Exception::class)
	fun testAccessViaTiaClientAndReportUploadToTeamscale() {
		val availableTests = listOf(
			ClusteredTestDetails("test1", "test1", "content", "cluster"),
			ClusteredTestDetails("test2", "test2", "content", "cluster")
		)
		val impactedClusters = listOf(
			PrioritizableTestCluster(
				"cluster",
				listOf(PrioritizableTest("test2"))
			)
		)

		whenever(
			client.getImpactedTests(
				anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
				anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
			)
		).thenReturn(Response.success(impactedClusters))

		whenever(reportGenerator.convert(any<File>())).thenReturn(
			CoverageToTeamscaleStrategyTest.getDummyTestwiseCoverage("test2")
		)

		val port: Int
		synchronized(TestUtils::class.java) {
			port = TestUtils.freePort
			val options = mockOptions(port)
			whenever(options.createNewFileInOutputDirectory(anyOrNull(), anyOrNull()))
				.thenReturn(File(tempDir, "test"))
			TestwiseCoverageAgent(options, null, reportGenerator)
		}

		val agent = TiaAgent(false, "http://localhost:$port".toHttpUrl())

		val testRun = agent.startTestRun(availableTests)
		assertThat(testRun.prioritizedClusters).hasSize(1)
		assertThat(testRun.prioritizedClusters!!.first().tests).hasSize(1)

		val test = testRun.prioritizedClusters!!.first().tests!!.first()
		assertThat(test.testName).isEqualTo("test2")

		testRun.startTest(test.testName)
			.endTest(TestRun.TestResultWithMessage(ETestExecutionResult.PASSED, "message"))

		testRun.endTestRun(true)

		verify(client).uploadReport(
			eq(EReportFormat.TESTWISE_COVERAGE),
			matches("\\Q{\"partial\":true,\"tests\":[{\"uniformPath\":\"test1\",\"sourcePath\":\"test1\",\"content\":\"content\",\"paths\":[]},{\"uniformPath\":\"test2\",\"sourcePath\":\"test2\",\"content\":\"content\",\"duration\":\\E[^,]*\\Q,\"result\":\"PASSED\",\"message\":\"message\",\"paths\":[{\"path\":\"src/main/java\",\"files\":[{\"fileName\":\"Main.java\",\"coveredLines\":\"1-4\"}]}]}]}\\E"),
			anyOrNull(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull()
		)
	}

	@Test
	@Throws(Exception::class)
	fun testErrorHandling() {
		val errorBody = (FORBIDDEN_MESSAGE_PREFIX + MISSING_VIEW_PERMISSIONS).toResponseBody(PLAIN_TEXT)
		whenever(
			client.getImpactedTests(
				anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
				anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
			)
		).thenReturn(Response.error(403, errorBody))

		val port: Int
		synchronized(TestUtils::class.java) {
			port = TestUtils.freePort
			val options = mockOptions(port)
			TestwiseCoverageAgent(options, null, reportGenerator)
		}

		val agent = TiaAgent(false, "http://localhost:$port".toHttpUrl())
		assertThatCode { agent.startTestRunAssumingUnchangedTests() }
			.hasMessageContaining(MISSING_VIEW_PERMISSIONS)
	}

	private interface ITestwiseCoverageAgentApiWithoutBody {
		/**
		 * Version of testrun/start that doesn't have a body. This can't be triggered via the Java TIA client but is a
		 * supported version of the API for other clients.
		 */
		@POST("testrun/start")
		fun testRunStarted(
			@Query("include-non-impacted") includeNonImpacted: Boolean,
			@Query("baseline") baseline: Long?
		): Call<List<PrioritizableTestCluster>>
	}

	@Test
	@Throws(Exception::class)
	fun shouldHandleMissingRequestBodyForTestrunStartGracefully() {
		val impactedClusters = listOf(
			PrioritizableTestCluster(
				"cluster",
				listOf(PrioritizableTest("test2"))
			)
		)

		whenever(
			client.getImpactedTests(
				anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
				anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
			)
		).thenReturn(Response.success(impactedClusters))

		val port: Int
		synchronized(TestUtils::class.java) {
			port = TestUtils.freePort
			TestwiseCoverageAgent(mockOptions(port), null, reportGenerator)
		}

		val api = Retrofit.Builder()
			.addConverterFactory(JacksonConverterFactory.create())
			.baseUrl("http://localhost:$port")
			.build()
			.create(ITestwiseCoverageAgentApiWithoutBody::class.java)

		val response = api.testRunStarted(false, null).execute()

		assertThat(response.isSuccessful).describedAs(response.toString()).isTrue()
		val tests = response.body()
		assertThat(tests).isNotNull.hasSize(1)
		assertThat(tests!![0].tests).hasSize(1)
	}

	private fun mockOptions(port: Int): AgentOptions {
		val options = mock<AgentOptions>()
		whenever(options.createTeamscaleClient(true)).thenReturn(client)

		val server = TeamscaleServer().apply {
			commit = CommitDescriptor("branch", "12345")
			url = "https://doesnt-exist.io".toHttpUrl()
			userName = "build"
			userAccessToken = "token"
			partition = "partition"
		}

		options.apply {
			teamscaleServer = server
			httpServerPort = port
			testwiseCoverageMode = ETestwiseCoverageMode.TEAMSCALE_UPLOAD
		}

		whenever(options.createTeamscaleClient(true)).thenReturn(client)
		return options
	}

	companion object {
		private const val FORBIDDEN_MESSAGE_PREFIX = "HTTP Status Code: 403 Forbidden\nMessage: "
		private const val MISSING_VIEW_PERMISSIONS = "User doesn't have permission 'VIEW' on project x."
		private val PLAIN_TEXT = "plain/text".toMediaType()
	}
}