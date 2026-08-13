package com.teamscale.jacoco.agent.testimpact

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.matches
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.slf4j.LoggerFactory
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

	/** Regression test for TS-46939. */
	@Test
	@Throws(Exception::class)
	fun shouldAutomaticallyEndPreviousTestAsInconclusiveWhenNewTestStartsWithoutEnd() {
		val options = mockOptions(false)
		val strategy = CoverageToTeamscaleStrategy(controller, options, reportGenerator)

		whenever(reportGenerator.convert(any<File>())).thenReturn(getDummyTestwiseCoverage("a", "b"))

		// Test "a" is started but never ended. Starting "b" must auto-end "a" instead of dropping it.
		strategy.testStart("a")
		// Test "a" needs to take some time so that we can assert its duration is non-zero.
		Thread.sleep(5)
		strategy.testStart("b")
		strategy.testEnd("b", TestExecution("b", 0L, ETestExecutionResult.PASSED))
		strategy.testRunEnd(false)

		val reportCaptor = argumentCaptor<String>()
		verify(client).uploadReport(
			eq(EReportFormat.TESTWISE_COVERAGE),
			reportCaptor.capture(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull()
		)
		val report = reportCaptor.firstValue

		assertThat(report).contains("\"uniformPath\":\"a\"", "\"result\":\"INCONCLUSIVE\"")
		assertThat(report).contains("\"uniformPath\":\"b\"", "\"result\":\"PASSED\"")
		val durationA = Regex("\"uniformPath\":\"a\"[^}]*?\"duration\":([0-9.E-]+)")
			.find(report)!!.groupValues[1].toDouble()
		assertThat(durationA).isGreaterThan(0.0)
	}

	/**
	 * A test that is still running when the test run ends must not be dropped from the report. This happens e.g. if we
	 * rejected its /test/end request for not containing a result and the caller never repeated it.
	 */
	@Test
	@Throws(Exception::class)
	fun shouldEndARunningTestAsInconclusiveWhenTheTestRunEnds() {
		val strategy = CoverageToTeamscaleStrategy(controller, mockOptions(false), reportGenerator)

		whenever(reportGenerator.convert(any<File>())).thenReturn(getDummyTestwiseCoverage("a"))

		// Test "a" is started but never ended.
		strategy.testStart("a")
		strategy.testRunEnd(false)

		val reportCaptor = argumentCaptor<String>()
		verify(client).uploadReport(
			eq(EReportFormat.TESTWISE_COVERAGE),
			reportCaptor.capture(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull(),
			anyOrNull()
		)

		assertThat(reportCaptor.firstValue).contains(
			"\"uniformPath\":\"a\"", "\"result\":\"INCONCLUSIVE\"", "the test run ended"
		)
	}

	/** We must not report the duration of a previous test as the duration of a test we never saw starting. */
	@Test
	@Throws(Exception::class)
	fun shouldNotDeriveADurationForATestThatWasNeverStarted() {
		val strategy = CoverageToTeamscaleStrategy(controller, mockOptions(false), reportGenerator)

		strategy.testStart("a")
		// Test "a" needs to take some time so that a duration wrongly derived for "b" would be non-zero.
		Thread.sleep(5)
		strategy.testEnd("a", TestExecution("a", 0L, ETestExecutionResult.PASSED))

		val executionOfB = TestExecution("b", 0L, ETestExecutionResult.PASSED)
		strategy.testEnd("b", executionOfB)

		assertThat(executionOfB.durationSeconds).isEqualTo(0.0)
	}

	/**
	 * A caller may end the same test twice, e.g. by repeating a /test/end request whose response it never received.
	 * The duration we derived during the first request must survive that, since the repeated request usually contains
	 * no duration either.
	 */
	@Test
	@Throws(Exception::class)
	fun shouldKeepTheDerivedDurationWhenATestIsEndedTwice() {
		val strategy = CoverageToTeamscaleStrategy(controller, mockOptions(false), reportGenerator)

		strategy.testStart("mytest")
		// The test needs to take some time so that we can assert that its duration is non-zero.
		Thread.sleep(5)
		val firstExecution = TestExecution("mytest", 0L, ETestExecutionResult.PASSED)
		strategy.testEnd("mytest", firstExecution)

		val repeatedExecution = TestExecution("mytest", 0L, ETestExecutionResult.PASSED)
		strategy.testEnd("mytest", repeatedExecution)

		assertThat(repeatedExecution.durationSeconds)
			.isGreaterThan(0.0)
			.isEqualTo(firstExecution.durationSeconds)
	}

	@Test
	fun shouldNotWarnAboutAMissingTestStartWhenATestIsEndedTwice() {
		val log = captureLog {
			it.testStart("mytest")
			it.testEnd("mytest", TestExecution("mytest", 0L, ETestExecutionResult.PASSED))
			it.testEnd("mytest", TestExecution("mytest", 0L, ETestExecutionResult.PASSED))
		}

		assertThat(log.messagesAt(Level.WARN)).noneSatisfy {
			assertThat(it).contains("/test/start")
		}
	}

	@Test
	fun shouldWarnWhenNoTestStartWasReceived() {
		val log = captureLog {
			it.testEnd("mytest", TestExecution("mytest", 0L, ETestExecutionResult.PASSED))
		}

		assertThat(log.messagesAt(Level.WARN)).anySatisfy {
			assertThat(it).contains("No /test/start request was received for test 'mytest'")
		}
	}

	@Test
	fun shouldNotWarnAboutTheDurationIfTheCallerProvidedOne() {
		val log = captureLog {
			it.testEnd("mytest", TestExecution("mytest", 1500L, ETestExecutionResult.PASSED))
		}

		assertThat(log.messagesAt(Level.WARN)).noneSatisfy {
			assertThat(it).contains("/test/start")
		}
	}

	@Test
	fun shouldNotWarnAboutTheDurationIfTheCallerExplicitlyReportedZeroDuration() {
		val execution = JsonUtils.deserialize<TestExecution>(
			"""{"uniformPath":"mytest","result":"SKIPPED","duration":0}"""
		)

		val log = captureLog { it.testEnd("mytest", execution) }

		assertThat(log.messagesAt(Level.WARN)).noneSatisfy {
			assertThat(it).contains("/test/start")
		}
	}

	/** Runs the given actions against a strategy and returns the events it logged. */
	private fun captureLog(action: (CoverageToTeamscaleStrategy) -> Unit): List<ILoggingEvent> {
		val strategy = CoverageToTeamscaleStrategy(controller, mockOptions(false), reportGenerator)
		val logger = LoggerFactory.getLogger(strategy.javaClass) as Logger
		val appender = ListAppender<ILoggingEvent>().apply { start() }
		val previousLevel = logger.level
		logger.level = Level.DEBUG
		logger.addAppender(appender)
		val events = try {
			action(strategy)
			appender.list.toList()
		} finally {
			logger.detachAppender(appender)
			logger.level = previousLevel
			appender.stop()
		}
		assertThat(events)
			.describedAs("The strategy must have logged something, otherwise we are not capturing its log events")
			.isNotEmpty()
		return events
	}

	private fun List<ILoggingEvent>.messagesAt(level: Level) =
		filter { it.level == level }.map { it.formattedMessage }

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
		/** Returns a dummy coverage builder for a test with the given name that covers a few lines of Main.java.  */
		fun getDummyTestCoverage(test: String): TestCoverageBuilder {
			val testCoverageBuilder = TestCoverageBuilder(test)
			val fileCoverageBuilder = FileCoverageBuilder("src/main/java", "Main.java")
			fileCoverageBuilder.addLineRange(1, 4)
			testCoverageBuilder.add(fileCoverageBuilder)
			return testCoverageBuilder
		}

		/** Returns a dummy testwise coverage object for the given test names that each cover a few lines of Main.java.  */
		fun getDummyTestwiseCoverage(vararg tests: String): TestwiseCoverage {
			val testwiseCoverage = TestwiseCoverage()
			tests.forEach { testwiseCoverage.add(getDummyTestCoverage(it)) }
			return testwiseCoverage
		}
	}
}