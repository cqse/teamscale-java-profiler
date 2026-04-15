package com.teamscale.report.jacoco

import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.IBundleCoverage
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfo
import org.jacoco.core.internal.analysis.BundleCoverageImpl
import org.jacoco.report.xml.XMLFormatter
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Creates an XML report from binary execution data.
 *
 * @param codeDirectoriesOrArchives Directories and zip files that contain class files.
 * @param locationIncludeFilter Include filter to apply to all locations during class file traversal.
 * @param duplicateClassFileBehavior Whether to ignore non-identical duplicates of class files.
 * @param ignoreUncoveredClasses Whether to remove uncovered classes from the report.
 * @param logger The logger.
 */
class JaCoCoXmlReportGenerator(
	private val codeDirectoriesOrArchives: List<File>,
	private val locationIncludeFilter: ClasspathWildcardIncludeFilter,
	private val duplicateClassFileBehavior: EDuplicateClassFileBehavior,
	private val ignoreUncoveredClasses: Boolean,
	private val logger: ILogger
) : JaCoCoBasedReportGenerator<CoverageBuilder>(
	codeDirectoriesOrArchives,
	locationIncludeFilter,
	duplicateClassFileBehavior,
	ignoreUncoveredClasses,
	logger,
	{ CoverageBuilder() }
) {

	/** Creates an XML report based on the given session and coverage data.  */
	@Throws(IOException::class)
	override fun createReport(
		output: OutputStream,
		sessionInfo: SessionInfo?,
		store: ExecutionDataStore,
		coverageVisitor: CoverageBuilder
	) {
		val bundleCoverage = BundleCoverageImpl("dummybundle", emptyList(), coverageVisitor.sourceFiles)
		bundleCoverage.checkForEmptyReport()
		XMLFormatter().createVisitor(output).apply {
			visitInfo(listOf(sessionInfo), store.contents)
			visitBundle(bundleCoverage, null)
			visitEnd()
		}
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
