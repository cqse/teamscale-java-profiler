package com.teamscale.jacoco.agent.options.sapnwdi

import com.teamscale.client.CommitDescriptor
import com.teamscale.jacoco.agent.util.StubUploader
import com.teamscale.report.jacoco.CoverageFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.function.BiFunction

class DelayedSapNwdiMultiUploaderTest {
	@Test
	fun reportsNoUploadBeforeAnyCommitIsKnown(@TempDir tmpDir: File) {
		val multiUploader = DelayedSapNwdiMultiUploader(BiFunction { _, _ -> StubUploader(true) })

		assertThat(multiUploader.upload(coverageFileIn(tmpDir)))
			.`as`("A dump that no application can take should not count as an upload")
			.isFalse()
	}

	@Test
	fun uploadsToEveryApplicationAndReportsUploadWhenOneSucceeded(@TempDir tmpDir: File) {
		val uploading = StubUploader(true)
		val failing = StubUploader(false)
		val uploadingApplication = SapNwdiApplication("com.example.Uploading", "uploading")
		val multiUploader = DelayedSapNwdiMultiUploader(BiFunction { _, application ->
			if (application == uploadingApplication) uploading else failing
		})
		multiUploader.setCommitForApplication(CommitDescriptor("master", 1), uploadingApplication)
		multiUploader.setCommitForApplication(
			CommitDescriptor("master", 2), SapNwdiApplication("com.example.Failing", "failing")
		)
		val coverageFile = coverageFileIn(tmpDir)

		val hasUploaded = multiUploader.upload(coverageFile)

		assertThat(hasUploaded)
			.`as`("One successful upload among several targets should count as an upload")
			.isTrue()
		assertThat(failing.receivedFiles)
			.`as`("A target after a successful one should still receive the coverage")
			.containsExactly(coverageFile)
		assertThat(uploading.receivedFiles).containsExactly(coverageFile)
	}

	@Test
	fun reportsNoUploadWhenEveryApplicationFailed(@TempDir tmpDir: File) {
		val multiUploader = DelayedSapNwdiMultiUploader(BiFunction { _, _ -> StubUploader(false) })
		multiUploader.setCommitForApplication(CommitDescriptor("master", 1), SapNwdiApplication("com.example.A", "a"))
		multiUploader.setCommitForApplication(CommitDescriptor("master", 2), SapNwdiApplication("com.example.B", "b"))

		assertThat(multiUploader.upload(coverageFileIn(tmpDir)))
			.`as`("Failed uploads to every target should not count as an upload")
			.isFalse()
	}

	private fun coverageFileIn(directory: File) =
		CoverageFile(File(directory, "jacoco.xml").apply { createNewFile() })
}
