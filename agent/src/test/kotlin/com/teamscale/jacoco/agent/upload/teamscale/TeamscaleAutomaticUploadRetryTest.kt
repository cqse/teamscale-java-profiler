package com.teamscale.jacoco.agent.upload.teamscale

import com.teamscale.client.CommitDescriptor.Companion.parse
import com.teamscale.client.TeamscaleServer
import com.teamscale.jacoco.agent.options.TestAgentOptionsBuilder
import com.teamscale.jacoco.agent.upload.UploadTestBase
import com.teamscale.jacoco.agent.upload.UploaderException
import okhttp3.mockwebserver.MockResponse
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Tests that the automatic reupload of previously unsuccessful coverage uploads
 * works for Teamscale.
 */
class TeamscaleAutomaticUploadRetryTest : UploadTestBase() {
	/**
	 * Makes a failing upload attempt and then automatically retries to upload the
	 * leftover coverage on disk.
	 */
	@Test
	@Throws(UploaderException::class)
	fun testAutomaticUploadRetry() {
		val server = TeamscaleServer()
		server.url = mockWebServer!!.url(serverUrl)
		server.project = "Fooproject"
		server.partition = "Test"
		server.commit = parse("master:HEAD")
		server.userName = "Foo"
		server.userAccessToken = "Token"
		uploader = TeamscaleUploader(server)
		mockWebServer!!.enqueue(MockResponse().setResponseCode(400))
		// This is expected to fail and leave the coverage on disk.
		uploader!!.upload(coverageFile!!)
		Assertions.assertThat(Files.exists(Paths.get(coverageFile.toString()))).isEqualTo(true)
		val options = TestAgentOptionsBuilder().withTeamscaleMessage("Foobar")
			.withTeamscalePartition("Test").withTeamscaleProject("project").withTeamscaleUser("User")
			.withTeamscaleUrl(mockWebServer!!.url(serverUrl).toString()).withTeamscaleAccessToken("foobar123")
			.withTeamscaleRevision("greatrevision").create()
		startAgentAfterUploadFailure(options)
		// A deleted coverage file tells us that the upload was successful.
		Assertions.assertThat(Files.notExists(Paths.get(coverageFile.toString()))).isEqualTo(true)
	}
}
