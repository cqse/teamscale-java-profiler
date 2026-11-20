package com.teamscale.jacoco.agent.upload

import com.teamscale.report.jacoco.CoverageFile

/** Uploads coverage reports.  */
interface IUploader {
	/**
	 * Uploads the given coverage file. If the upload was successful, the coverage
	 * file on disk will be deleted. Otherwise the file is left on disk and a
	 * warning is logged.
	 *
	 * @return Whether the coverage reached a server. False for an uploader that keeps the coverage on the local disk,
	 * and for one that caches it until its commit is known.
	 */
	fun upload(coverageFile: CoverageFile): Boolean

	/** Human-readable description of the uploader.  */
	fun describe(): String
}
