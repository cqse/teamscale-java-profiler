package com.teamscale.test_impacted.engine.executor

import com.teamscale.client.ClusteredTestDetails
import com.teamscale.client.PrioritizableTest
import com.teamscale.client.StringUtils.levenshteinDistance
import com.teamscale.test_impacted.commons.LoggerUtils.createLogger
import org.junit.platform.engine.UniqueId
import java.util.*

/**
 * Holds a list of test details that can currently be executed. Provides the ability to translate uniform paths returned
 * by the Teamscale server to unique IDs used in JUnit Platform.
 */
class AvailableTests {
	companion object {
		private val LOG = createLogger()
	}

	/**
	 * A mapping from the tests uniform path (Teamscale internal representation) to unique id (JUnit internal
	 * representation).
	 */
	private val uniformPathToUniqueIdMapping = mutableMapOf<String, UniqueId>()

	/** List of all test details.  */
	val testList = mutableListOf<ClusteredTestDetails>()

	/** Adds a new [com.teamscale.client.TestDetails] object and the according uniqueId.  */
	fun add(uniqueId: UniqueId, details: ClusteredTestDetails) {
		uniformPathToUniqueIdMapping[details.uniformPath] = uniqueId
		testList.add(details)
	}

	/**
	 * Converts the [PrioritizableTest] to the [UniqueId] returned by the [org.junit.platform.engine.TestEngine].
	 */
	fun convertToUniqueId(test: PrioritizableTest): Optional<UniqueId> {
		val clusterUniqueId = uniformPathToUniqueIdMapping[test.testName]
		if (clusterUniqueId == null) {
			LOG.severe {
				"Teamscale returned the impacted test '${test.testName}' which does not match any local test." +
						" This test will be skipped. Please report a bug."
			}
			LOG.severe { "The following local tests seem related:" }
			uniformPathToUniqueIdMapping.keys
				.sortedBy { test.testName.levenshteinDistance(it) }
				.take(5)
				.forEach { LOG.severe { " - $it" } }
		}
		return Optional.ofNullable(clusterUniqueId)
	}
}
