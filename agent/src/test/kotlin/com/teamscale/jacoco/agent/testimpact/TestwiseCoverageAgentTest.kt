package com.teamscale.jacoco.agent.testimpact

import com.teamscale.client.*
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.jacoco.agent.options.ETestwiseCoverageMode
import com.teamscale.jacoco.agent.util.TestUtils
import com.teamscale.report.jacoco.dump.Dump
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.tia.client.ITestwiseCoverageAgentApi
import com.teamscale.tia.client.TestRun
import com.teamscale.tia.client.TiaAgent
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.matches
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.POST
import retrofit2.http.Path
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

	/** The agents started during the current test. Their HTTP servers must be stopped again, cf. [stopAgents]. */
	private val agents = mutableListOf<TestwiseCoverageAgent>()

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

		val agent = TiaAgent(false, startAgent(dumpsCoverage = true))

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

	/** The test result is mandatory, so a /test/end request without a body must be rejected. */
	@Test
	@Throws(Exception::class)
	fun shouldRejectATestEndRequestWithoutABody() {
		val url = startAgent()
		ITestwiseCoverageAgentApi.createService(url).testStarted("test1").execute()

		val response = apiWithoutBody(url).testFinished("test1").execute()

		assertThat(response.code()).isEqualTo(400)
		assertThat(response.errorBody()!!.string()).contains("The test result is missing for test 'test1'")
	}

	/** The test result is mandatory, so a /test/end request whose body has no result must be rejected. */
	@Test
	@Throws(Exception::class)
	fun shouldRejectATestEndRequestWithoutAResult() {
		val api = ITestwiseCoverageAgentApi.createService(startAgent())
		api.testStarted("test1").execute()

		val response = api.testFinished("test1", TestExecution(uniformPath = "test1")).execute()

		assertThat(response.code()).isEqualTo(400)
		assertThat(response.errorBody()!!.string()).contains("The test result is missing for test 'test1'")
	}

	/**
	 * In tia-mode=http, a test's coverage is only handed to the caller in the response to its /test/end request.
	 * Rejecting such a request must therefore not dump and discard the coverage: the caller must receive it when it
	 * repeats the request with a result.
	 */
	@Test
	@Throws(Exception::class)
	fun shouldNotDiscardTheCoverageWhenRejectingATestEndRequestInHttpMode() {
		whenever(reportGenerator.convert(any<Dump>())).thenReturn(
			CoverageToTeamscaleStrategyTest.getDummyTestCoverage("test1")
		)
		val url = startAgent(mode = ETestwiseCoverageMode.HTTP)
		val api = ITestwiseCoverageAgentApi.createService(url)

		api.testStarted("test1").execute()
		val rejectedResponse = apiWithoutBody(url).testFinished("test1").execute()

		assertThat(rejectedResponse.code()).isEqualTo(400)
		// the coverage must still be recorded, i.e. it must neither have been dumped nor discarded
		verify(reportGenerator, never()).convert(any<Dump>())

		val response = api.testFinished("test1", TestExecution("test1", 0L, ETestExecutionResult.PASSED)).execute()

		assertThat(response.isSuccessful).describedAs(response.toString()).isTrue()
		assertThat(response.body()!!.string()).contains(
			"\"uniformPath\":\"test1\"", "\"result\":\"PASSED\"", "\"fileName\":\"Main.java\""
		)
	}

	/**
	 * A caller that reacts to the rejection of its /test/end request by repeating it with a result must end up with the
	 * correct result and the duration the profiler derived for the test.
	 */
	@Test
	@Throws(Exception::class)
	fun shouldRecordTheRepeatedTestEndRequestAfterRejectingTheFirstOne() {
		whenever(reportGenerator.convert(any<File>())).thenReturn(
			CoverageToTeamscaleStrategyTest.getDummyTestwiseCoverage("test1")
		)
		val url = startAgent(dumpsCoverage = true)
		val api = ITestwiseCoverageAgentApi.createService(url)

		api.testStarted("test1").execute()
		// The test needs to take some time so that we can assert that its duration is non-zero.
		Thread.sleep(5)
		apiWithoutBody(url).testFinished("test1").execute()
		// The caller reacts to the rejection by repeating the request with a result, but without a duration.
		api.testFinished("test1", TestExecution("test1", 0L, ETestExecutionResult.PASSED)).execute()
		api.testRunFinished(true).execute()

		val report = argumentCaptor<String>()
		verify(client).uploadReport(
			eq(EReportFormat.TESTWISE_COVERAGE), report.capture(),
			anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
		)
		assertThat(report.firstValue)
			.contains("\"result\":\"PASSED\"")
			// the rejected request must not have caused a result of its own to be recorded
			.doesNotContain("INCONCLUSIVE")
		val duration = Regex("\"uniformPath\":\"test1\"[^}]*?\"duration\":([0-9.E-]+)")
			.find(report.firstValue)!!.groupValues[1].toDouble()
		assertThat(duration).isGreaterThan(0.0)
	}

	/**
	 * Starts an agent in testwise coverage mode and returns the URL its HTTP server listens on. The agent is shut down
	 * again after the test, cf. [stopAgents].
	 *
	 * @param dumpsCoverage whether the test ends a test, which makes the agent write an exec file.
	 * @param mode the tia-mode to configure for the agent.
	 */
	private fun startAgent(
		dumpsCoverage: Boolean = false,
		mode: ETestwiseCoverageMode = ETestwiseCoverageMode.TEAMSCALE_UPLOAD
	): HttpUrl {
		val port: Int
		synchronized(TestUtils::class.java) {
			port = TestUtils.freePort
			val options = mockOptions(port, mode)
			if (dumpsCoverage) {
				whenever(options.createNewFileInOutputDirectory(anyOrNull(), anyOrNull()))
					.thenReturn(File(tempDir, "test"))
			}
			val testExecutionWriter = TestExecutionWriter(File(tempDir, "test-execution.json"))
			agents.add(TestwiseCoverageAgent(options, testExecutionWriter, reportGenerator))
		}
		return "http://localhost:$port".toHttpUrl()
	}

	/**
	 * Shuts down the HTTP servers of all agents started during the test. Otherwise, each test would leave a listening
	 * socket and its request handling threads behind for the rest of the test JVM's lifetime.
	 */
	@AfterEach
	fun stopAgents() {
		agents.forEach { it.stopServer() }
		agents.clear()
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

		val agent = TiaAgent(false, startAgent())
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

		/**
		 * Version of test/end that doesn't have a body. This can't be triggered via the Java TIA client anymore, but
		 * other clients may still send such a request, which the agent must reject.
		 */
		@POST("test/end/{testUniformPath}")
		fun testFinished(
			@Path(value = "testUniformPath", encoded = true) testUniformPath: String
		): Call<ResponseBody>
	}

	/** Creates an API for requests that the Java TIA client cannot send, e.g. because they have no body. */
	private fun apiWithoutBody(url: HttpUrl) = Retrofit.Builder()
		.addConverterFactory(JacksonConverterFactory.create())
		.baseUrl(url)
		.build()
		.create(ITestwiseCoverageAgentApiWithoutBody::class.java)

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

		val response = apiWithoutBody(startAgent()).testRunStarted(false, null).execute()

		assertThat(response.isSuccessful).describedAs(response.toString()).isTrue()
		val tests = response.body()
		assertThat(tests).isNotNull.hasSize(1)
		assertThat(tests!![0].tests).hasSize(1)
	}

	private fun mockOptions(port: Int, mode: ETestwiseCoverageMode = ETestwiseCoverageMode.TEAMSCALE_UPLOAD): AgentOptions {
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
			testwiseCoverageMode = mode
		}

		return options
	}

	companion object {
		private const val FORBIDDEN_MESSAGE_PREFIX = "HTTP Status Code: 403 Forbidden\nMessage: "
		private const val MISSING_VIEW_PERMISSIONS = "User doesn't have permission 'VIEW' on project x."
		private val PLAIN_TEXT = "plain/text".toMediaType()
	}
}