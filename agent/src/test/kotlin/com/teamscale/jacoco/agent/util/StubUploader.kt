package com.teamscale.jacoco.agent.util

import com.teamscale.jacoco.agent.upload.IUploader
import com.teamscale.report.jacoco.CoverageFile

/** Records the coverage files it receives and reports each upload as [hasUploaded] says. */
class StubUploader(private val hasUploaded: Boolean) : IUploader {
	/** The coverage files [upload] received, in order. */
	val receivedFiles = mutableListOf<CoverageFile>()

	override fun upload(coverageFile: CoverageFile): Boolean {
		receivedFiles += coverageFile
		return hasUploaded
	}

	override fun describe() = "stub uploader reporting $hasUploaded"
}
