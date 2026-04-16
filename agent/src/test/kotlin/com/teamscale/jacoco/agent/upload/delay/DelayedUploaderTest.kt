package com.teamscale.jacoco.agent.upload.delay

import com.teamscale.jacoco.agent.util.InMemoryUploader
import com.teamscale.report.jacoco.CoverageFile
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.stream.Collectors

class DelayedUploaderTest {
	@Test
	@Throws(IOException::class)
	fun shouldStoreToCacheIfCommitIsNotKnown(@TempDir outputPath: Path) {
		val coverageFilePath = outputPath
			.resolve(String.format("jacoco-%d.xml", ZonedDateTime.now().toInstant().toEpochMilli()))
		val coverageFile = CoverageFile(Files.createFile(coverageFilePath).toFile())

		val destination = InMemoryUploader()
		val store: DelayedUploader<Any> = DelayedUploader(outputPath) { _ -> destination }

		store.upload(coverageFile)

		Assertions.assertThat(Files.list(outputPath).collect(Collectors.toList()))
			.contains(coverageFilePath)
		Assertions.assertThat(destination.uploadedFiles).doesNotContain(coverageFile)
	}

	@Test
	@Throws(IOException::class)
	fun shouldStoreToDestinationIfCommitIsKnown(@TempDir outputPath: Path) {
		val coverageFilePath = outputPath
			.resolve(String.format("jacoco-%d.xml", ZonedDateTime.now().toInstant().toEpochMilli()))
		val coverageFile = CoverageFile(Files.createFile(coverageFilePath).toFile())

		val destination = InMemoryUploader()
		val store = DelayedUploader<String>( outputPath) { _ -> destination }

		store.setCommitAndTriggerAsynchronousUpload("a2afb54566aaa")
		store.upload(coverageFile)

		Assertions.assertThat(Files.list(outputPath).collect(Collectors.toList()))
			.doesNotContain(coverageFilePath)
		Assertions.assertThat(destination.uploadedFiles).contains(coverageFile)
	}

	@Test
	@Throws(Exception::class)
	fun shouldAsynchronouslyStoreToDestinationOnceCommitIsKnown(@TempDir outputPath: Path) {
		val coverageFilePath = outputPath
			.resolve(String.format("jacoco-%d.xml", ZonedDateTime.now().toInstant().toEpochMilli()))
		val coverageFile = CoverageFile(Files.createFile(coverageFilePath).toFile())

		val destination = InMemoryUploader()
		val executor = Executors.newSingleThreadExecutor()
		val store = DelayedUploader<String>(outputPath, executor) { _ -> destination }

		store.upload(coverageFile)
		store.setCommitAndTriggerAsynchronousUpload("a2afb54566aaa")
		executor.shutdown()
		executor.awaitTermination(5, TimeUnit.SECONDS)

		Assertions.assertThat<Path>(Files.list(outputPath).collect(Collectors.toList()))
			.doesNotContain(coverageFilePath)
		Assertions.assertThat<CoverageFile>(destination.uploadedFiles).contains(coverageFile)
	}
}