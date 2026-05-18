package com.teamscale.jacoco.agent.upload

import com.teamscale.report.jacoco.CoverageFile
import java.util.*

/**
 * Interface for all the uploaders that support an automatic upload retry
 * mechanism.
 */
interface IUploadRetry {
	/**
	 * Marks coverage files of unsuccessful coverage uploads so that they can be
	 * reuploaded at next agent start.
	 */
	fun markFileForUploadRetry(coverageFile: CoverageFile)

	/**
	 * Retries previously unsuccessful coverage uploads with the given properties.
	 */
	fun reupload(coverageFile: CoverageFile, properties: Properties)
}
