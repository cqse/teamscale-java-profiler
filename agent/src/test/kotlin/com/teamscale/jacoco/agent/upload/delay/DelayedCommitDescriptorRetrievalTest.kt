package com.teamscale.jacoco.agent.upload.delay

import com.teamscale.jacoco.agent.commit_resolution.git_properties.CommitInfo
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitPropertiesLocatorUtils.getCommitInfoFromGitProperties
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitSingleProjectPropertiesLocator
import com.teamscale.jacoco.agent.util.InMemoryUploader
import com.teamscale.report.jacoco.CoverageFile
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.streams.asSequence

class DelayedCommitDescriptorRetrievalTest {
	@Test
	@Throws(Exception::class)
	fun locatorShouldTriggerUploadOfCachedXmls(@TempDir outputPath: Path) {
		val storeExecutor = Executors.newSingleThreadExecutor()
		val coverageFilePath = outputPath
			.resolve(String.format("jacoco-%d.xml", ZonedDateTime.now().toInstant().toEpochMilli()))
		val coverageFile = CoverageFile(Files.createFile(coverageFilePath).toFile())

		val destination = InMemoryUploader()
		val store = DelayedUploader(outputPath, storeExecutor) { _: CommitInfo? -> destination }

		val locatorExecutor = Executors.newSingleThreadExecutor()
		val locator = GitSingleProjectPropertiesLocator(
			store, true, null, locatorExecutor
		) { file, isJarFile, recursiveSearch, timeFormatter ->
			getCommitInfoFromGitProperties(
				file, isJarFile, recursiveSearch, timeFormatter
			)
		}

		store.upload(coverageFile)
		locator.searchFileForGitPropertiesAsync(File(javaClass.getResource("git-properties.jar")!!.toURI()), true)
		locatorExecutor.shutdown()
		locatorExecutor.awaitTermination(5, TimeUnit.SECONDS)
		storeExecutor.shutdown()
		storeExecutor.awaitTermination(5, TimeUnit.SECONDS)

		Assertions.assertThat(
			Files.list(outputPath).asSequence().any { it == coverageFilePath }
		).isFalse()
		Assertions.assertThat(destination.uploadedFiles.contains(coverageFile)).isTrue()
	}
}
