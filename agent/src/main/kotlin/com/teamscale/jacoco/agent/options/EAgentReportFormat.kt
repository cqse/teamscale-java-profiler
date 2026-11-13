package com.teamscale.jacoco.agent.options

import com.teamscale.client.EReportFormat
import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.cobertura.CoberturaReportGenerator
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
	JACOCO(EReportFormat.JACOCO, "jacoco", "xml"),
	/** Cobertura XML.  */
	COBERTURA(EReportFormat.COBERTURA, "cobertura", "xml");

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
		COBERTURA -> CoberturaReportGenerator(
			classDirectoriesOrArchives, locationIncludeFilter, duplicateClassFileBehavior, ignoreUncoveredClasses, logger
		)
	}

	companion object {
		/**
		 * Returns the format whose [fileExtension] matches the given extension (case-insensitive), or `null`.
		 *
		 * Extensions are not unique: [JACOCO] and [COBERTURA] both produce `.xml`, and [JACOCO] wins because it is
		 * declared first. Use [fromFileName] whenever the full file name is available, since the file name prefix
		 * tells the two apart.
		 */
		@JvmStatic
		fun fromFileExtension(extension: String): EAgentReportFormat? =
			entries.firstOrNull { it.fileExtension.equals(extension, ignoreCase = true) }

		/**
		 * Returns the format whose [fileNamePrefix] and [fileExtension] match the given name of a file written to the
		 * output directory, or `null` if the name follows no known convention.
		 */
		@JvmStatic
		fun fromFileName(fileName: String): EAgentReportFormat? =
			entries.firstOrNull {
				fileName.startsWith("${it.fileNamePrefix}-") &&
						fileName.endsWith(".${it.fileExtension}", ignoreCase = true)
			}
	}
}
