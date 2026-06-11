package com.teamscale.jacoco.agent.options

import com.teamscale.client.EReportFormat
import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.compact.CompactCoverageReportGenerator
import com.teamscale.report.jacoco.JaCoCoBasedReportGenerator
import com.teamscale.report.jacoco.JaCoCoXmlReportGenerator
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import java.io.File

/**
 * The coverage report format the agent produces in NORMAL mode.
 *
 * Each entry bundles the downstream [EReportFormat] used during upload with the on-disk file naming
 * convention so that filename, format identifier, and zip-entry name cannot drift apart.
 */
enum class EAgentReportFormat(
	/** The [EReportFormat] identifier sent to the upload backend.  */
	@JvmField val reportFormat: EReportFormat,
	/** Filename prefix for files written to the output directory.  */
	@JvmField val fileNamePrefix: String,
	/** Filename extension (without leading dot) for files written to the output directory.  */
	@JvmField val fileExtension: String,
) {
	/** Teamscale Compact Coverage (JSON).  */
	TEAMSCALE_COMPACT_COVERAGE(EReportFormat.TEAMSCALE_COMPACT_COVERAGE, "compact-coverage", "json"),
	/** JaCoCo XML.  */
	JACOCO(EReportFormat.JACOCO, "jacoco", "xml");

	/** Creates the report generator that produces this format from JaCoCo binary execution data. */
	fun createGenerator(
		classDirectoriesOrArchives: List<File>,
		locationIncludeFilter: ClasspathWildcardIncludeFilter,
		duplicateClassFileBehavior: EDuplicateClassFileBehavior,
		ignoreUncoveredClasses: Boolean,
		logger: ILogger
	): JaCoCoBasedReportGenerator<*> = when (this) {
		TEAMSCALE_COMPACT_COVERAGE -> CompactCoverageReportGenerator(
			classDirectoriesOrArchives, locationIncludeFilter, duplicateClassFileBehavior, logger
		)
		JACOCO -> JaCoCoXmlReportGenerator(
			classDirectoriesOrArchives, locationIncludeFilter, duplicateClassFileBehavior, ignoreUncoveredClasses, logger
		)
	}

	companion object {
		/** Returns the format whose [fileExtension] matches the given extension (case-insensitive), or `null`. */
		@JvmStatic
		fun fromFileExtension(extension: String): EAgentReportFormat? =
			entries.firstOrNull { it.fileExtension.equals(extension, ignoreCase = true) }
	}
}
