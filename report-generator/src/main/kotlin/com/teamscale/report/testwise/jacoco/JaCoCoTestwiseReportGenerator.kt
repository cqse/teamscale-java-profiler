package com.teamscale.report.testwise.jacoco

import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.jacoco.dump.Dump
import com.teamscale.report.testwise.jacoco.CachingExecutionDataReader.*
import com.teamscale.report.testwise.jacoco.cache.CoverageGenerationException
import com.teamscale.report.testwise.model.TestwiseCoverage
import com.teamscale.report.testwise.model.builder.TestCoverageBuilder
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.jacoco.core.data.ExecutionData
import org.jacoco.core.data.ExecutionDataReader
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.IExecutionDataVisitor
import org.jacoco.core.data.ISessionInfoVisitor
import org.jacoco.core.data.SessionInfo
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.function.Consumer

/**
 * Creates an XML report for an execution data store. The report is grouped by session.
 *
 * The class files under test must be compiled with debug information otherwise no coverage will be collected.
 */
open class JaCoCoTestwiseReportGenerator(
	codeDirectoriesOrArchives: Collection<File>,
	private val locationIncludeFilter: ClasspathWildcardIncludeFilter,
	duplicateClassFileBehavior: EDuplicateClassFileBehavior,
	logger: ILogger
) {
	/** The execution data reader and converter. */
	private val executionDataReader = CachingExecutionDataReader(
		logger, codeDirectoriesOrArchives, locationIncludeFilter, duplicateClassFileBehavior
	)

	init {
		// This has to be unsafe as mockito does not support mocking final classes
		updateClassDirCache()
	}

	/** Updates the probe cache of the [ExecutionDataReader]. */
	open fun updateClassDirCache() {
		executionDataReader.analyzeClassDirs()
	}

	/** Converts the given dumps to a report. */
	@Throws(IOException::class, CoverageGenerationException::class)
	open fun convert(executionDataFile: File): TestwiseCoverage {
		val testwiseCoverage = TestwiseCoverage()
		val dumpConsumer = executionDataReader.buildCoverageConsumer(locationIncludeFilter, testwiseCoverage::add)
		readAndConsumeDumps(executionDataFile, dumpConsumer)
		return testwiseCoverage
	}

	/** Converts the given dump to a report. */
	@Throws(CoverageGenerationException::class)
	open fun convert(dump: Dump): TestCoverageBuilder? {
		val testCoverageBuilders = mutableListOf<TestCoverageBuilder>()
		executionDataReader
			.buildCoverageConsumer(locationIncludeFilter, testCoverageBuilders::add)
			.accept(dump)
		return testCoverageBuilders.singleOrNull()
	}

	/**
	 * Converts the given dumps to a report, merging the coverage of all dumps that belong to the same test before
	 * passing it on. A test produces more than one dump if it was executed repeatedly, e.g. once per parameter set of
	 * an enclosing `@ParameterizedClass`. In contrast to [convertAndConsume] this holds the coverage of all tests in
	 * memory, which is unavoidable because the dumps of one test are not adjacent.
	 */
	@Throws(IOException::class, CoverageGenerationException::class)
	open fun convertAndConsumePerTest(executionDataFiles: List<File>, consumer: Consumer<TestCoverageBuilder>) {
		val testwiseCoverage = TestwiseCoverage()
		executionDataFiles.forEach { testwiseCoverage.add(convert(it)) }
		testwiseCoverage.tests.values.forEach(consumer::accept)
	}

	/** Converts the given dumps to a report. */
	@Throws(IOException::class)
	open fun convertAndConsume(executionDataFile: File, consumer: Consumer<TestCoverageBuilder>) {
		val dumpConsumer = executionDataReader.buildCoverageConsumer(locationIncludeFilter, consumer)
		readAndConsumeDumps(executionDataFile, dumpConsumer)
	}

	/** Reads the dumps from the given *.exec file. */
	@Throws(IOException::class)
	private fun readAndConsumeDumps(executionDataFile: File, dumpConsumer: DumpConsumer) {
		BufferedInputStream(FileInputStream(executionDataFile)).use { input ->
			ExecutionDataReader(input).apply {
				val dumpCallback = DumpCallback(dumpConsumer)
				setExecutionDataVisitor(dumpCallback)
				setSessionInfoVisitor(dumpCallback)
				read()
				dumpCallback.processDump()
			}
		}
	}

	/** Collects execution information per session and passes it to the consumer . */
	private class DumpCallback(
		private val consumer: DumpConsumer
	) : IExecutionDataVisitor, ISessionInfoVisitor {
		/** The dump that is currently being read. */
		private var currentDump: Dump? = null

		/** The store to which coverage is currently written to. */
		private var store: ExecutionDataStore? = null

		override fun visitSessionInfo(info: SessionInfo) {
			processDump()
			ExecutionDataStore().let {
				currentDump = Dump(info, it)
				store = it
			}
		}

		override fun visitClassExecution(data: ExecutionData) {
			store?.put(data)
		}

		fun processDump() {
			currentDump?.let {
				consumer.accept(it)
				currentDump = null
			}
		}
	}
}