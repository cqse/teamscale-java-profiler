package com.teamscale.jacoco.agent.upload

import com.teamscale.report.jacoco.CoverageFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LocalDiskUploaderTest {
	@Test
	fun keepsCoverageOnDiskAndReportsNoUpload(@TempDir tmpDir: File) {
		val file = File(tmpDir, "jacoco.xml").apply { createNewFile() }

		val hasUploaded = LocalDiskUploader().upload(CoverageFile(file))

		assertThat(hasUploaded).`as`("Writing to the local disk should not count as an upload").isFalse()
		assertThat(file).`as`("The coverage should stay on the local disk").exists()
	}
}
