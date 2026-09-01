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
	 * an enclosing `@ParameterizedClass`.
	 *
	 * The dumps of one test are neither adjacent nor necessarily in the same file, so a first pass over the files
	 * determines which tests were dumped repeatedly. Only the coverage of those has to be held back until all files
	 * have been read; the coverage of every other test is passed on as soon as its dump was read, so that the consumer
	 * can write it out instead of keeping the whole test run in memory.
	 */
	@Throws(IOException::class, CoverageGenerationException::class)
	open fun convertAndConsumePerTest(executionDataFiles: List<File>, consumer: Consumer<TestCoverageBuilder>) {
		val repeatedTestIds = findTestsWithMultipleDumps(executionDataFiles)
		val repeatedTestCoverage = TestwiseCoverage()
		executionDataFiles.forEach { executionDataFile ->
			convertAndConsume(executionDataFile) { coverage ->
				if (coverage.uniformPath in repeatedTestIds) {
					repeatedTestCoverage.add(coverage)
				} else {
					consumer.accept(coverage)
				}
			}
		}
		repeatedTestCoverage.tests.values.forEach(consumer::accept)
	}

	/** Returns the IDs of the tests for which the given *.exec files contain more than one dump. */
	@Throws(IOException::class)
	private fun findTestsWithMultipleDumps(executionDataFiles: List<File>): Set<String> {
		val seenTestIds = mutableSetOf<String>()
		val repeatedTestIds = mutableSetOf<String>()
		executionDataFiles.forEach { executionDataFile ->
			readSessionInfos(executionDataFile) { info ->
				if (info.id.isNotEmpty() && !seenTestIds.add(info.id)) {
					repeatedTestIds.add(info.id)
				}
			}
		}
		return repeatedTestIds
	}

	/**
	 * Passes the session infos in the given *.exec file to the given visitor. Only the session infos are of interest
	 * here, so the execution data itself is read but discarded.
	 */
	@Throws(IOException::class)
	private fun readSessionInfos(executionDataFile: File, sessionInfoVisitor: ISessionInfoVisitor) {
		BufferedInputStream(FileInputStream(executionDataFile)).use { input ->
			ExecutionDataReader(input).apply {
				setExecutionDataVisitor { }
				setSessionInfoVisitor(sessionInfoVisitor)
				read()
			}
		}
	}

	/**
	 * Converts the dumps in the given *.exec file to a report, passing on the coverage of each dump as soon as it was
	 * read. Use [convertAndConsumePerTest] unless the consumer can handle more than one result for the same test,
	 * since a test that was executed repeatedly produces one dump per execution.
	 */
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