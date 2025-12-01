package com.teamscale.test_impacted.engine.options

import com.teamscale.client.CommitDescriptor
import com.teamscale.client.TeamscaleClient
import com.teamscale.test_impacted.engine.ImpactedTestEngineConfiguration
import com.teamscale.test_impacted.engine.TestDataWriter
import com.teamscale.test_impacted.engine.TestEngineRegistry
import com.teamscale.test_impacted.engine.executor.ImpactedTestsProvider
import com.teamscale.test_impacted.engine.executor.ImpactedTestsSorter
import com.teamscale.test_impacted.engine.executor.NOPTestSorter
import com.teamscale.test_impacted.engine.executor.TeamscaleAgentNotifier
import com.teamscale.tia.client.ITestwiseCoverageAgentApi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.logging.Level
import kotlin.io.path.createDirectories

/**
 * Represents configurable options for initializing and running a test engine.
 * This class is used to determine the execution behavior, including impacted tests,
 * test coverage, and specific Test Engine configurations.
 *
 * @property enabled Whether the engine is enabled and hence should execute any tests
 * @property serverOptions Configuration options for connecting to the Teamscale server.
 *                          Required when `runImpacted` is `true`.
 * @property partition The partition name that identifies the specific test execution context.
 *                      Mandatory when creating an impacted tests provider.
 * @property repository The repository name, used for impacted test identification.
 * @property runImpacted Flag indicating whether only impacted tests should be executed.
 * @property runAllTests Flag indicating whether all tests should be executed, ignoring impacted tests.
 * @property includeAddedTests Flag indicating whether newly added tests should be included when running impacted tests.
 * @property includeFailedAndSkipped Flag indicating whether previously failed or skipped tests should be included in test runs.
 * @property baseline The branch or commit identifier for the baseline.
 * @property baselineRevision The revision string associated with the baseline.
 * @property endCommit Descriptor for the branch and timestamp of the ending commit. Either `endCommit` or `endRevision` is required.
 * @property endRevision The end revision string. Either `endRevision` or `endCommit` is required.
 * @property includedTestEngineIds A set of test engine IDs to explicitly include in the test run.
 * @property excludedTestEngineIds A set of test engine IDs to explicitly exclude from the test run.
 * @param reportDirectoryPath The filesystem path where test reports will be saved. Must be writable during initialization.
 * @param testCoverageAgentUrls A list of URLs pointing to test-wise coverage agents used during test execution.
 * @property logLevel The log level for the impacted test engine. Defaults to INFO.
 * @property logFilePath The filesystem path where the impacted test engine log file will be written.
 *                       If null, no file logging is configured.
 */
class TestEngineOptions(
	val enabled: Boolean,
	private val serverOptions: ServerOptions? = null,
	val partition: String? = null,
	private val repository: String? = null,
	private val runImpacted: Boolean = DEFAULT_RUN_IMPACTED,
	private val runAllTests: Boolean = false,
	private val includeAddedTests: Boolean = DEFAULT_INCLUDE_ADDED_TESTS,
	private val includeFailedAndSkipped: Boolean = DEFAULT_INCLUDE_FAILED_AND_SKIPPED,
	private val baseline: String? = null,
	private val baselineRevision: String? = null,
	private val endCommit: CommitDescriptor? = null,
	private val endRevision: String? = null,
	private val includedTestEngineIds: Set<String> = emptySet(),
	private val excludedTestEngineIds: Set<String> = emptySet(),
	reportDirectoryPath: String? = null,
	testCoverageAgentUrls: List<String> = emptyList(),
	val logLevel: Level = Level.INFO,
	val logFilePath: String? = null,
) {

	private var reportDirectory = reportDirectoryPath?.let { File(it) }
	private var testwiseCoverageAgentApis =
		testCoverageAgentUrls.mapNotNull { it.toHttpUrlOrNull()?.let(ITestwiseCoverageAgentApi::createService) }

	companion object {
		private const val DEFAULT_RUN_IMPACTED = true
		private const val DEFAULT_INCLUDE_ADDED_TESTS = true
		private const val DEFAULT_INCLUDE_FAILED_AND_SKIPPED = true
	}

	init {
		if (enabled) {
			if (runImpacted) {
				require(endCommit != null || endRevision != null) { "End commit must be set via endCommit or endRevision." }
				requireNotNull(serverOptions) { "Server options must be set." }
			}
			requireNotNull(reportDirectory) { "Report directory must be set." }

			reportDirectory?.let {
				if (!it.isDirectory || !it.canWrite()) {
					it.toPath().createDirectories()
				}
			}
		}
	}

	/**
	 * Provides the configuration for the impacted test engine based on the defined options.
	 *
	 * @throws IllegalStateException If the report directory is not set.
	 * @return An instance of [ImpactedTestEngineConfiguration] containing all the necessary components.
	 */
	val testEngineConfiguration: ImpactedTestEngineConfiguration
		get() {
			requireNotNull(reportDirectory) { "Report directory must be set." }
			val testSorter = createTestSorter()
			val teamscaleAgentNotifier = createTeamscaleAgentNotifier()
			val testEngineRegistry = TestEngineRegistry(includedTestEngineIds, excludedTestEngineIds)
			val testDataWriter = TestDataWriter(reportDirectory!!)
			return ImpactedTestEngineConfiguration(
				testDataWriter,
				testEngineRegistry,
				testSorter,
				teamscaleAgentNotifier
			)
		}

	private fun createTestSorter() =
		if (!runImpacted) NOPTestSorter() else ImpactedTestsSorter(createImpactedTestsProvider())

	private fun createImpactedTestsProvider(): ImpactedTestsProvider {
		requireNotNull(serverOptions) { "Server options must be set." }
		requireNotNull(reportDirectory) { "Report directory must be set." }
		requireNotNull(partition) { "Partition must be set." }
		val client = TeamscaleClient(
			serverOptions.url,
			serverOptions.userName,
			serverOptions.userAccessToken,
			serverOptions.project,
			File(reportDirectory, "server-request.txt")
		)
		return ImpactedTestsProvider(
			client, baseline, baselineRevision, endCommit, endRevision, repository, partition,
			runAllTests, includeAddedTests, includeFailedAndSkipped
		)
	}

	private fun createTeamscaleAgentNotifier() =
		TeamscaleAgentNotifier(testwiseCoverageAgentApis, runImpacted && !runAllTests)
}
