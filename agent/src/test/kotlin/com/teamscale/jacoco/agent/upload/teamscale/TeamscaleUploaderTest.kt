package com.teamscale.jacoco.agent.upload.teamscale

import com.teamscale.client.CommitDescriptor.Companion.parse
import com.teamscale.client.EReportFormat
import com.teamscale.client.TeamscaleServer
import com.teamscale.jacoco.agent.upload.UploadTestBase
import okhttp3.mockwebserver.MockResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TeamscaleUploaderTest : UploadTestBase() {
	@BeforeEach
	fun setUpTeamscaleUploader() {
		val server = TeamscaleServer()
		server.url = mockWebServer!!.url(serverUrl)
		server.project = "project"
		server.partition = "partition"
		server.commit = parse("master:HEAD")
		server.userName = "user"
		server.userAccessToken = "token"
		uploader = TeamscaleUploader(server, EReportFormat.JACOCO)
	}

	@Test
	fun reportsSuccessfulUpload() {
		mockWebServer!!.enqueue(MockResponse().setResponseCode(200))

		assertThat(uploader!!.upload(coverageFile!!))
			.`as`("An upload that Teamscale accepted should count as an upload")
			.isTrue()
	}

	@Test
	fun reportsFailedUpload() {
		mockWebServer!!.enqueue(MockResponse().setResponseCode(400))

		assertThat(uploader!!.upload(coverageFile!!))
			.`as`("An upload that Teamscale rejected should not count as an upload")
			.isFalse()
	}
}
