package com.teamscale.report.jacoco

import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.jacoco.dump.Dump
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.jacoco.core.analysis.IClassCoverage
import org.jacoco.core.analysis.ICounter
import org.jacoco.core.analysis.ICoverageVisitor
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfo
import org.jacoco.core.tools.ExecFileLoader
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Base class for generating reports based on the binary JaCoCo exec dump files.
 *
 * It takes care of ignoring non-identical classes with the same fully qualified name and classes without coverage.
 *
 * @param codeDirectoriesOrArchives Directories and zip files that contain class files.
 * @param locationIncludeFilter Include filter to apply to all locations during class file traversal.
 * @param ignoreUncoveredClasses Whether to remove uncovered classes from the report.
 * @param logger The logger.
 */
abstract class JaCoCoBasedReportGenerator<Visitor : ICoverageVisitor>(
	private val codeDirectoriesOrArchives: Collection<File>,
	private val locationIncludeFilter: ClasspathWildcardIncludeFilter,
	private val duplicateClassFileBehavior: EDuplicateClassFileBehavior,
	private val ignoreUncoveredClasses: Boolean,
	private val logger: ILogger,
	/** The coverage visitor which will be called with all the data found in the exec files. */
	protected val coverageVisitor: Visitor,
) {

	/**
	 * Creates the report and writes it to a file.
	 *
	 * @return The file object of for the converted report or null if it could not be created
	 */
	@Throws(IOException::class, EmptyReportException::class)
	fun convertSingleDumpToReport(dump: Dump, outputFilePath: File): CoverageFile {
		val coverageFile = CoverageFile(outputFilePath)
		val mergedStore = dump.store
		analyzeStructureAndAnnotateCoverage(mergedStore)
		coverageFile.outputStream.use { outputStream ->
			createReport(outputStream, dump.info, mergedStore)
		}
		return coverageFile
	}

	/** Merges and converts multiple exec files into one testwise coverage report. */
	@Throws(IOException::class, EmptyReportException::class)
	fun convertExecFilesToReport(execFiles: Collection<File>, outputFilePath: File) {
		val loader = ExecFileLoader()
		for (jacocoExecutionData in execFiles) {
			loader.load(jacocoExecutionData)
		}

		val sessionInfo = loader.sessionInfoStore.getMerged("merged")
		convertSingleDumpToReport(Dump(sessionInfo, loader.executionDataStore), outputFilePath)
	}

	/** Creates an XML report based on the given session and coverage data.  */
	@Throws(IOException::class)
	protected abstract fun createReport(
		output: OutputStream,
		sessionInfo: SessionInfo?,
		store: ExecutionDataStore
	)

	/**
	 * Analyzes the structure of the class files in [codeDirectoriesOrArchives] and builds an in-memory coverage
	 * report with the coverage in the given store.
	 *
	 * We share a single [EnhancedCoverageVisitor] across all entries so that its duplicate-detection map tracks classes
	 * globally. Without this, the same class appearing in two different archives (for example, old and new WAR after an
	 * application server reload) would bypass our duplicate handling and hit `CoverageBuilder` directly, which always
	 * throws `IllegalStateException` regardless of the configured [duplicateClassFileBehavior].
	 */
	@Throws(IOException::class)
	private fun analyzeStructureAndAnnotateCoverage(store: ExecutionDataStore) {
		val visitor = EnhancedCoverageVisitor()
		codeDirectoriesOrArchives.forEach { file ->
			FilteringAnalyzer(store, visitor, locationIncludeFilter, logger)
				.analyzeAll(file)
		}
	}

	private inner class EnhancedCoverageVisitor : ICoverageVisitor {

		private val classIdByClassName: MutableMap<String, Long> = mutableMapOf()

		override fun visitCoverage(coverage: IClassCoverage) {
			if (ignoreUncoveredClasses && (coverage.classCounter.status and ICounter.FULLY_COVERED) == 0 || coverage.sourceFileName == null) {
				return
			}
			val prevCoverageId = classIdByClassName.put(coverage.name, coverage.id)
			if (prevCoverageId != null && prevCoverageId != coverage.id) {
				warnAboutDuplicateClassFile(coverage)
				return
			}
			coverageVisitor.visitCoverage(coverage)
		}

		private fun warnAboutDuplicateClassFile(coverage: IClassCoverage) {
			when (duplicateClassFileBehavior) {
				EDuplicateClassFileBehavior.IGNORE -> return
				EDuplicateClassFileBehavior.WARN -> {
					// we do not log the exception here as it does not provide additional valuable information
					// and may confuse users into thinking there's a serious
					// problem with the agent due to the stack traces in the log
					logger.warn(
						"Ignoring duplicate, non-identical class file for class ${coverage.name} compiled " +
								"from source file ${coverage.sourceFileName}. This happens when a class with the same " +
								"fully-qualified name is loaded twice but the two loaded class files are not identical. " +
								"A common reason for this is that the same library or shared code is included twice in " +
								"your application but in two different versions. The produced coverage for this class " +
								"may not be accurate or may even be unusable. To fix this problem, please resolve the " +
								"conflict between both class files in your application."
					)
					return
				}

				EDuplicateClassFileBehavior.FAIL -> error { "Can't add different class with same name: ${coverage.name}" }
			}
		}
	}

	companion object {
		/** Part of the error message logged when validating the coverage report fails.  */
		const val MOST_LIKELY_CAUSE_MESSAGE = "Most likely you did not configure the agent correctly." +
				" Please check that the includes and excludes options are set correctly so the relevant code is included." +
				" If in doubt, first include more code and then iteratively narrow the patterns down to just the relevant code." +
				" If you have specified the class-dir option, please make sure it points to a directory containing the" +
				" class files/jars/wars/ears/etc. for which you are trying to measure code coverage."

	}
}
