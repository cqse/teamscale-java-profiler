package com.teamscale.test_impacted.engine.executor

import com.teamscale.client.CommitDescriptor
import com.teamscale.client.PrioritizableTestCluster
import com.teamscale.client.TeamscaleClient
import com.teamscale.client.TestWithClusterId
import com.teamscale.test_impacted.commons.LoggerUtils.createLogger
import retrofit2.Response
import java.io.IOException
import java.util.logging.Level

/**
 * Provides functionality to query impacted tests from Teamscale based on various configurable conditions.
 *
 * @property client The Teamscale client used to communicate with the Teamscale server.
 * @property baseline The baseline identifier for comparison, if applicable.
 * @property baselineRevision The specific baseline revision, if applicable.
 * @property endCommit The commit descriptor indicating the end of the revision range.
 * @property endRevision The specific end revision, if applicable.
 * @property repository The repository name to be used for querying impacted tests.
 * @property partition The partition key used to categorize tests for querying.
 * @property includeNonImpacted Whether to include non-impacted tests in the results.
 * @property includeAddedTests Whether to include tests that were newly added.
 * @property includeFailedAndSkipped Whether to include failed and skipped tests in the impact analysis.
 */
open class ImpactedTestsProvider(
	private val client: TeamscaleClient,
	private val baseline: String?,
	private val baselineRevision: String?,
	private val endCommit: CommitDescriptor?,
	private val endRevision: String?,
	private val repository: String?,
	val partition: String,
	private val includeNonImpacted: Boolean,
	private val includeAddedTests: Boolean,
	private val includeFailedAndSkipped: Boolean
) {
	/** Queries Teamscale for impacted tests.  */
	fun getImpactedTestsFromTeamscale(
		availableTestDetails: List<TestWithClusterId>
	): List<PrioritizableTestCluster>? {
		try {
			LOG.info { "Getting impacted tests..." }
			val response = client
				.getImpactedTests(
					availableTestDetails, baseline, baselineRevision, endCommit, endRevision, repository,
					listOf(partition), includeNonImpacted, includeAddedTests, includeFailedAndSkipped
				)

			if (response.isSuccessful) {
				val testClusters = response.body()
				if (testClusters != null && testCountIsPlausible(testClusters, availableTestDetails)) {
					return testClusters
				}
				LOG.severe("""
					Teamscale was not able to determine impacted tests:
					${response.body()}
					The test engine will fall back to executing all tests.
					Verify the configured project, branch/timestamp, and partition exist in Teamscale.
					""".trimIndent())
			} else {
				LOG.severe(
					"Retrieval of impacted tests failed: ${response.code()} ${response.message()}" +
							"\n${getErrorBody(response)}" +
							"\nThe test engine will fall back to executing all tests."
				)
			}
		} catch (e: IOException) {
			LOG.log(
				Level.SEVERE, e
			) { "Retrieval of impacted tests failed." }
		}
		return null
	}

	/**
	 * Checks that the number of tests returned by Teamscale matches the number of available tests when running with
	 * [includeNonImpacted].
	 */
	private fun testCountIsPlausible(
		testClusters: List<PrioritizableTestCluster>,
		availableTestDetails: List<TestWithClusterId>
	): Boolean {
		val returnedTests = testClusters.sumOf {
			it.tests?.size?.toLong() ?: 0
		}
		if (!includeNonImpacted) {
			LOG.info { "Received $returnedTests impacted tests of ${availableTestDetails.size} available tests." }
			return true
		}
		if (returnedTests == availableTestDetails.size.toLong()) {
			return true
		} else {
			LOG.severe {
				"Retrieved $returnedTests tests from Teamscale, but expected ${availableTestDetails.size}." +
						" The test selection will fall back to executing all tests." +
						" This usually indicates a mismatch between the local test set and what Teamscale knows" +
						" — verify the project/branch/timestamp configuration."
			}
			return false
		}
	}

	companion object {
		private val LOG = createLogger()

		@Throws(IOException::class)
		private fun getErrorBody(response: Response<*>): String {
			response.errorBody().use { error ->
				if (error != null) {
					return error.string()
				}
			}
			return ""
		}
	}
}
