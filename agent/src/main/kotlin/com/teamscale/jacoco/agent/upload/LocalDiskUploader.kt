package com.teamscale.jacoco.agent.upload

import com.teamscale.report.jacoco.CoverageFile

/**
 * Dummy uploader which keeps the coverage file written by the agent on disk,
 * but does not actually perform uploads.
 */
class LocalDiskUploader : IUploader {
	override fun upload(coverageFile: CoverageFile) {
		// Don't delete the file here. We want to store the file permanently on disk in
		// case no uploader is configured.
	}

	override fun describe() = "configured output directory on the local disk"
}
