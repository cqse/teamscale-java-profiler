package com.teamscale.report.testwise.jacoco

import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.jacoco.dump.Dump
import com.teamscale.report.testwise.jacoco.cache.AnalyzerCache
import com.teamscale.report.testwise.jacoco.cache.ProbesCache
import com.teamscale.report.testwise.model.builder.TestCoverageBuilder
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.jacoco.core.data.ExecutionDataStore
import java.io.File
import java.util.function.Consumer

/**
 * Helper class for analyzing class files, reading execution data, and converting them to coverage data.
 */
open class CachingExecutionDataReader(
	private val logger: ILogger,
	private val classesDirectories: Collection<File>,
	private val locationIncludeFilter: ClasspathWildcardIncludeFilter,
	private val duplicateClassFileBehavior: EDuplicateClassFileBehavior
) {
	private val probeCache: ProbesCache by lazy {
		ProbesCache(logger, duplicateClassFileBehavior)
	}

	/**
	 * Analyzes class directories and creates a lookup of probes to methods.
	 */
	fun analyzeClassDirs() {
		if (classesDirectories.isEmpty()) {
			logger.warn(
				"No class directories provided — testwise coverage will be calculated without caching."
			)
			return
		}
		val analyzer = AnalyzerCache(probeCache, locationIncludeFilter, logger)
		val classCount = classesDirectories
			.filter { it.exists() }
			.sumOf { analyzeDirectory(it, analyzer) }

		validateAnalysisResult(classCount)
	}

	/**
	 * Analyzes the specified directory, logging errors if any occur.
	 */
	private fun analyzeDirectory(classDir: File, analyzer: AnalyzerCache) =
		runCatching { analyzer.analyzeAll(classDir) }
			.onFailure { e ->
				logger.error(
					"Failed to analyze class files in $classDir! " +
							"Maybe the folder contains incompatible class files. Coverage for class files " +
							"in this folder will be ignored.", e
				)
			}
			.getOrDefault(0)

	/**
	 * Builds a consumer for coverage data.
	 */
	fun buildCoverageConsumer(
		locationIncludeFilter: ClasspathWildcardIncludeFilter,
		nextConsumer: Consumer<TestCoverageBuilder>
	) = DumpConsumer(logger, locationIncludeFilter, nextConsumer)

	/**
	 * Logs errors if no classes were analyzed or if the filter excluded all files.
	 */
	private fun validateAnalysisResult(classCount: Int) {
		val directoryList = classesDirectories.joinToString(",") { it.path }
		when {
			classCount == 0 -> logger.error(
				"No class files found in directories: $directoryList." +
						" Verify that 'class-dir' points to compiled output and not the source tree."
			)
			probeCache.isEmpty -> logger.error(
				"None of the $classCount class files found in the given directories match the configured include/exclude patterns! $directoryList"
			)
		}
	}

	/**
	 * Consumer for processing [Dump] objects and passing them to [TestCoverageBuilder].
	 *
	 * @param logger The logger to use for logging.
	 * @param locationIncludeFilter The filter to use for including locations.
	 * @param nextConsumer The consumer to pass the generated [TestCoverageBuilder] to.
	 */
	inner class DumpConsumer(
		private val logger: ILogger,
		private val locationIncludeFilter: ClasspathWildcardIncludeFilter,
		private val nextConsumer: Consumer<TestCoverageBuilder>
	) : Consumer<Dump> {
		override fun accept(dump: Dump) {
			val testId = dump.info.id.takeIf { it.isNotEmpty() } ?: return logger.debug(
				"Session with empty name detected, possibly indicating intermediate coverage."
			)
			runCatching { buildCoverage(testId, dump.store, locationIncludeFilter) }
				.onSuccess(nextConsumer::accept)
				.onFailure { e ->
					logger.error(
						"Failed to generate coverage for test $testId." +
								" This test's coverage will be missing from the report. Check the agent log for the cause.",
						e
					)
				}
		}

		/**
		 * Builds coverage for a given test and store.
		 */
		private fun buildCoverage(
			testId: String,
			executionDataStore: ExecutionDataStore,
			locationIncludeFilter: ClasspathWildcardIncludeFilter
		): TestCoverageBuilder {
			val testCoverage = TestCoverageBuilder(testId)
			executionDataStore.contents.forEach { executionData ->
				probeCache.getCoverage(executionData, locationIncludeFilter)?.let {
					testCoverage.add(it)
				}
			}
			probeCache.flushLogger()
			return testCoverage
		}
	}
}
