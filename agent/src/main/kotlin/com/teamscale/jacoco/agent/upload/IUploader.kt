package com.teamscale.jacoco.agent.upload

import com.teamscale.report.jacoco.CoverageFile

/** Uploads coverage reports.  */
interface IUploader {
	/**
	 * Uploads the given coverage file. If the upload was successful, the coverage
	 * file on disk will be deleted. Otherwise the file is left on disk and a
	 * warning is logged.
	 */
	fun upload(coverageFile: CoverageFile)

	/** Human-readable description of the uploader.  */
	fun describe(): String
}
