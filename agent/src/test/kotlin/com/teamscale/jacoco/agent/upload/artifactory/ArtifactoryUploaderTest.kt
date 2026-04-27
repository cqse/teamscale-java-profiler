package com.teamscale.jacoco.agent.upload.artifactory

import com.teamscale.client.CommitDescriptor
import com.teamscale.client.EReportFormat
import com.teamscale.jacoco.agent.commit_resolution.git_properties.CommitInfo
import com.teamscale.jacoco.agent.options.TestAgentOptionsBuilder
import com.teamscale.jacoco.agent.upload.UploadTestBase
import com.teamscale.jacoco.agent.upload.UploaderException
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class ArtifactoryUploaderTest : UploadTestBase() {
	/** Sets up the artifactory uploader with some basic credentials.  */
	@BeforeEach
	fun setupArtifactoryUploader() {
		val serverUrl = mockWebServer!!.url("/artifactory/")
		val artifactoryConfig = generateBasicArtifactoryConfig(serverUrl)
		artifactoryConfig.apiKey = "some_api_key"
		uploader = ArtifactoryUploader(artifactoryConfig, listOf(), EReportFormat.JACOCO)
	}

	/**
	 * Tests if the [ArtifactoryConfig.ARTIFACTORY_API_KEY_OPTION] is set, it will be used as authentication
	 * method against artifactory in the [ArtifactoryUploader.ARTIFACTORY_API_HEADER]
	 */
	@Test
	@Throws(InterruptedException::class)
	fun testUseApiKeyHeaderWhenOptionIsPresent() {
		mockWebServer!!.enqueue(MockResponse().setResponseCode(200))
		uploader!!.upload(coverageFile!!)
		val recordedRequest = checkNotNull(mockWebServer!!.takeRequest(5, TimeUnit.SECONDS))
		Assertions.assertThat(recordedRequest.getHeader(ArtifactoryUploader.ARTIFACTORY_API_HEADER))
			.`as`(("Artifactory API Header (${ArtifactoryUploader.ARTIFACTORY_API_HEADER}) not used when the option${ArtifactoryConfig.ARTIFACTORY_API_KEY_OPTION}is set."))
			.isNotNull()
	}

	/**
	 * Tests that an unsuccessful upload is automatically retried if the profiler is started.
	 */
	@Test
	@Throws(UploaderException::class)
	fun testAutomaticUploadRetry() {
		mockWebServer!!.enqueue(MockResponse().setResponseCode(400))
		uploader!!.upload(coverageFile!!)
		Assertions.assertThat(Files.exists(Paths.get(coverageFile.toString()))).isEqualTo(true)
		val options = TestAgentOptionsBuilder()
			.withMinimalArtifactoryConfig("fookey", "Test", mockWebServer!!.url(serverUrl).toString()).create()
		startAgentAfterUploadFailure(options)
		Assertions.assertThat(Files.notExists(Paths.get(coverageFile.toString()))).isEqualTo(true)
	}

	private fun generateBasicArtifactoryConfig(serverUrl: HttpUrl?): ArtifactoryConfig {
		val config = ArtifactoryConfig()
		config.commitInfo = CommitInfo("some_revision", CommitDescriptor("some_branch", 0))
		config.url = serverUrl
		return config
	}
}
