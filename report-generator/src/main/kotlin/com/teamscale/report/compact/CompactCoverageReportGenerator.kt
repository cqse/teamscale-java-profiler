package com.teamscale.report.compact

import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.jacoco.EmptyReportException
import com.teamscale.report.jacoco.JaCoCoBasedReportGenerator
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfo
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Creates an XML report from binary execution data.
 *
 * @param codeDirectoriesOrArchives Directories and zip files that contain class files.
 * @param locationIncludeFilter Include filter to apply to all locations during class file traversal.
 * @param duplicateClassFileBehavior Whether to ignore non-identical duplicates of class files.
 * @param logger The logger.
 */
class CompactCoverageReportGenerator(
	private val codeDirectoriesOrArchives: Collection<File>,
	private val locationIncludeFilter: ClasspathWildcardIncludeFilter,
	private val duplicateClassFileBehavior: EDuplicateClassFileBehavior,
	private val logger: ILogger
) : JaCoCoBasedReportGenerator<TeamscaleCompactCoverageBuilder>(
	codeDirectoriesOrArchives,
	locationIncludeFilter,
	duplicateClassFileBehavior,
	true,
	logger,
	{ TeamscaleCompactCoverageBuilder() }
) {

	/** Creates an XML report based on the given session and coverage data.  */
	@Throws(IOException::class)
	override fun createReport(
		output: OutputStream,
		sessionInfo: SessionInfo?,
		store: ExecutionDataStore,
		coverageVisitor: TeamscaleCompactCoverageBuilder
	) {
		val compactReportData = coverageVisitor.buildReport()
		compactReportData.checkForEmptyReport()
		compactReportData.writeTo(output)
	}

	@Throws(EmptyReportException::class)
	private fun TeamscaleCompactCoverageReport.checkForEmptyReport() {
		if (this.coverage.all { it.fullyCoveredLines.isEmpty && it.partiallyCoveredLines?.isEmpty != false }) {
			throw EmptyReportException("The generated coverage report is empty. $MOST_LIKELY_CAUSE_MESSAGE")
		}
	}
}
