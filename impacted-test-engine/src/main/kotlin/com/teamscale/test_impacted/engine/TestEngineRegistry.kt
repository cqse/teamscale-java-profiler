package com.teamscale.test_impacted.engine

import com.teamscale.test_impacted.commons.LoggerUtils.createLogger
import org.junit.platform.commons.util.ClassLoaderUtils
import org.junit.platform.engine.TestEngine
import java.util.*

/**
 * A registry for managing and accessing available [TestEngine] implementations.
 * This class utilizes a filtering mechanism based on included and excluded test engine IDs
 * to determine the available engines in the current environment.
 *
 * @param includedTestEngineIds Set of test engine IDs to explicitly include.
 * @param excludedTestEngineIds Set of test engine IDs to explicitly exclude.
 */
open class TestEngineRegistry(
	includedTestEngineIds: Set<String>,
	excludedTestEngineIds: Set<String>
) : Iterable<TestEngine> {
	private val testEnginesById: Map<String, TestEngine>
	private val logger = createLogger()

	init {
		var otherTestEngines = loadOtherTestEngines(excludedTestEngineIds)

		logger.fine("Loaded ${otherTestEngines.size} test engines after excluding: $excludedTestEngineIds")
		logger.fine("Found test engines: ${otherTestEngines.map { it.id }}")

		// If there are no test engines set we don't need to filter but simply use all other test engines.
		if (includedTestEngineIds.isNotEmpty()) {
			logger.fine("Filtering by included test engine IDs: $includedTestEngineIds")
			val beforeFilterCount = otherTestEngines.size
			otherTestEngines = otherTestEngines.filter { testEngine ->
				includedTestEngineIds.contains(testEngine.id)
			}
			logger.fine("Filtered from $beforeFilterCount to ${otherTestEngines.size} test engines")
		}

		testEnginesById = otherTestEngines.associateBy { it.id }
		logger.fine("Final test engines to be used: ${testEnginesById.keys}")
	}

	/**
	 * Uses the [ServiceLoader] to discover all [TestEngine]s but the [ImpactedTestEngine] and the
	 * excluded test engines.
	 */
	private fun loadOtherTestEngines(excludedTestEngineIds: Set<String>) =
		ServiceLoader.load(
			TestEngine::class.java, ClassLoaderUtils.getDefaultClassLoader()
		).filter {
			ImpactedTestEngine.ENGINE_ID != it.id && !excludedTestEngineIds.contains(it.id)
		}

	/** Returns the [TestEngine] for the engine id or null if none is present.  */
	fun getTestEngine(engineId: String) = testEnginesById[engineId]

	override fun iterator() =
		testEnginesById.values.sortedBy { it.id }.iterator()
}
