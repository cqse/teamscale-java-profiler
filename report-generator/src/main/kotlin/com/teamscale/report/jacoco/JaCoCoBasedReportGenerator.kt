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
 * JaCoCo's execution data only contains per-class boolean arrays indicating which probes fired. It does not contain
 * any structural information (method names, line numbers, branch locations). The class files in
 * [codeDirectoriesOrArchives] provide that structural information: JaCoCo re-analyzes them to reconstruct which probes
 * correspond to which lines and branches, then merges this with the execution data to produce a meaningful coverage
 * report.
 *
 * Since the same class can appear in multiple archives (for example, a dependency bundled in two WARs), this class
 * detects such duplicates via [EnhancedCoverageVisitor] and handles them according to [duplicateClassFileBehavior].
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
	/**
	 * Supplier that creates a fresh coverage visitor for each dump. This is a supplier rather than
	 * a plain instance because [EnhancedCoverageVisitor] and the coverage visitor must share the
	 * same lifecycle: both are created per [convertSingleDumpToReport] call. If the coverage visitor
	 * were reused across dumps, it would still carry class IDs from a previous dump, and a class
	 * that reappears with a different CRC64 (for example, after an application server hot-reload)
	 * would crash inside JaCoCo's CoverageBuilder instead of being handled by our duplicate
	 * detection in [EnhancedCoverageVisitor].
	 */
	private val coverageVisitorSupplier: () -> Visitor,
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
		val coverageVisitor = coverageVisitorSupplier()
		analyzeStructureAndAnnotateCoverage(mergedStore, coverageVisitor)
		coverageFile.outputStream.use { outputStream ->
			createReport(outputStream, dump.info, mergedStore, coverageVisitor)
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

	/**
	 * Creates an XML report based on the given session and coverage data.
	 *
	 * @param coverageVisitor The visitor that was populated during [analyzeStructureAndAnnotateCoverage] for this dump.
	 *   Passed as a parameter (rather than being a field) because a fresh instance is created per dump via
	 *   [coverageVisitorSupplier].
	 */
	@Throws(IOException::class)
	protected abstract fun createReport(
		output: OutputStream,
		sessionInfo: SessionInfo?,
		store: ExecutionDataStore,
		coverageVisitor: Visitor
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
	private fun analyzeStructureAndAnnotateCoverage(store: ExecutionDataStore, coverageVisitor: Visitor) {
		val visitor = EnhancedCoverageVisitor(coverageVisitor)
		codeDirectoriesOrArchives.forEach { file ->
			FilteringAnalyzer(store, visitor, locationIncludeFilter, logger)
				.analyzeAll(file)
		}
	}

	/**
	 * Wrapper around the actual coverage visitor (typically JaCoCo's [org.jacoco.core.analysis.CoverageBuilder] or
	 * [com.teamscale.report.compact.TeamscaleCompactCoverageBuilder]) that intercepts duplicate, non-identical class
	 * files before they reach the wrapped visitor. Without this, duplicates would hit `CoverageBuilder` directly,
	 * which always throws `IllegalStateException` regardless of the configured [duplicateClassFileBehavior].
	 */
	private inner class EnhancedCoverageVisitor(
		private val coverageVisitor: Visitor
	) : ICoverageVisitor {

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
