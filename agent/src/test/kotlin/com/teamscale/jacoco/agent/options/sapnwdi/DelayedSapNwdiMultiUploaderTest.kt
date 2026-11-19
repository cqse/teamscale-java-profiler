package com.teamscale.jacoco.agent.options.sapnwdi

import com.teamscale.client.CommitDescriptor
import com.teamscale.jacoco.agent.upload.IUploader
import com.teamscale.report.jacoco.CoverageFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.function.BiFunction

class DelayedSapNwdiMultiUploaderTest {
	@Test
	fun reportsNoUploadBeforeAnyCommitIsKnown(@TempDir tmpDir: File) {
		val multiUploader = DelayedSapNwdiMultiUploader(BiFunction { _, _ -> uploaderReporting(true) })

		assertThat(multiUploader.upload(coverageFileIn(tmpDir)))
			.`as`("A dump that no application can take should not count as an upload")
			.isFalse()
	}

	@Test
	fun reportsUploadWhenOneOfSeveralApplicationsUploaded(@TempDir tmpDir: File) {
		val failingApplication = SapNwdiApplication("com.example.Failing", "failing")
		val uploadingApplication = SapNwdiApplication("com.example.Uploading", "uploading")
		val multiUploader = DelayedSapNwdiMultiUploader(BiFunction { _, application ->
			uploaderReporting(application == uploadingApplication)
		})
		multiUploader.setCommitForApplication(CommitDescriptor("master", 1), failingApplication)
		multiUploader.setCommitForApplication(CommitDescriptor("master", 2), uploadingApplication)

		assertThat(multiUploader.upload(coverageFileIn(tmpDir)))
			.`as`("One successful upload among several targets should count as an upload")
			.isTrue()
	}

	@Test
	fun reportsNoUploadWhenEveryApplicationFailed(@TempDir tmpDir: File) {
		val multiUploader = DelayedSapNwdiMultiUploader(BiFunction { _, _ -> uploaderReporting(false) })
		multiUploader.setCommitForApplication(CommitDescriptor("master", 1), SapNwdiApplication("com.example.A", "a"))
		multiUploader.setCommitForApplication(CommitDescriptor("master", 2), SapNwdiApplication("com.example.B", "b"))

		assertThat(multiUploader.upload(coverageFileIn(tmpDir)))
			.`as`("Failed uploads to every target should not count as an upload")
			.isFalse()
	}

	private fun coverageFileIn(directory: File) =
		CoverageFile(File(directory, "jacoco.xml").apply { createNewFile() })

	private fun uploaderReporting(hasUploaded: Boolean) = object : IUploader {
		override fun upload(coverageFile: CoverageFile) = hasUploaded

		override fun describe() = "uploader reporting $hasUploaded"
	}
}
