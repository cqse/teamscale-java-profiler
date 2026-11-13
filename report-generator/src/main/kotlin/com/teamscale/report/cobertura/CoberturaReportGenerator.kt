package com.teamscale.report.cobertura

import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.jacoco.EmptyReportException
import com.teamscale.report.jacoco.JaCoCoBasedReportGenerator
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.IBundleCoverage
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfo
import org.jacoco.core.internal.analysis.BundleCoverageImpl
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Creates a Cobertura XML report from binary execution data.
 *
 * @param codeDirectoriesOrArchives Directories and zip files that contain class files.
 * @param locationIncludeFilter Include filter to apply to all locations during class file traversal.
 * @param duplicateClassFileBehavior Whether to ignore non-identical duplicates of class files.
 * @param ignoreUncoveredClasses Whether to remove uncovered classes from the report.
 * @param logger The logger.
 */
class CoberturaReportGenerator(
	codeDirectoriesOrArchives: List<File>,
	locationIncludeFilter: ClasspathWildcardIncludeFilter,
	duplicateClassFileBehavior: EDuplicateClassFileBehavior,
	ignoreUncoveredClasses: Boolean,
	logger: ILogger
) : JaCoCoBasedReportGenerator<CoverageBuilder>(
	codeDirectoriesOrArchives,
	locationIncludeFilter,
	duplicateClassFileBehavior,
	ignoreUncoveredClasses,
	logger,
	{ CoverageBuilder() }
) {

	/** Creates a Cobertura report based on the given session and coverage data.  */
	@Throws(IOException::class)
	override fun createReport(
		output: OutputStream,
		sessionInfo: SessionInfo?,
		store: ExecutionDataStore,
		coverageVisitor: CoverageBuilder
	) {
		// The Cobertura format is class-based: every class element carries the name of the class and the source file
		// it was compiled from. The bundle is therefore built from the analyzed classes, unlike the JaCoCo XML report,
		// which only needs the source file coverage.
		val bundleCoverage = BundleCoverageImpl("dummybundle", coverageVisitor.classes, coverageVisitor.sourceFiles)
		bundleCoverage.checkForEmptyReport()
		CoberturaReportWriter(output).write(bundleCoverage, sessionInfo?.dumpTimeStamp ?: System.currentTimeMillis())
	}

	@Throws(EmptyReportException::class)
	private fun IBundleCoverage.checkForEmptyReport() {
		if (packages.isEmpty() || lineCounter.totalCount == 0) {
			throw EmptyReportException("The generated coverage report is empty. $MOST_LIKELY_CAUSE_MESSAGE")
		}
		if (lineCounter.coveredCount == 0) {
			throw EmptyReportException("The generated coverage report does not contain any covered source code lines. $MOST_LIKELY_CAUSE_MESSAGE")
		}
	}
}
