package com.teamscale.jacoco.agent.util

import com.teamscale.jacoco.agent.upload.IUploader
import com.teamscale.report.jacoco.CoverageFile
import java.io.IOException

/**
 * Simulates an upload by storing coverage [java.io.File] in a list. The
 * "uploaded" Files can then be retrieved with
 * [InMemoryUploader.uploadedFiles]
 */
class InMemoryUploader : IUploader {
	val uploadedFiles = mutableListOf<CoverageFile>()

	override fun upload(coverageFile: CoverageFile) {
		uploadedFiles.add(coverageFile)
		try {
			coverageFile.delete()
		} catch (_: IOException) {
			// Do nothing as not being able to delete the file is not important for tests
		}
	}

	override fun describe() = "in memory uploader"
}
