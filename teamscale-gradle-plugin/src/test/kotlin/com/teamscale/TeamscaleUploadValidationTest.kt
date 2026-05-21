package com.teamscale

import com.teamscale.plugin.fixtures.TeamscaleConstants
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for validation logic in TeamscaleUpload task.
 */
class TeamscaleUploadValidationTest : TeamscalePluginTestBase() {

	@BeforeEach
	fun init() {
		rootProject.withSingleProject()
		rootProject.defaultProjectSetup()
	}

	@Test
	fun `upload fails with empty revision`() {
		rootProject.excludeFailingTests()
		rootProject.defineTestTasks()

		// Configure teamscale upload with empty revision
		rootProject.buildFile.appendText(
			"""
				
			teamscale {
				server {
					url = "${teamscaleMockServer.url}"
					userName = "${TeamscaleConstants.USER}"
					userAccessToken = "${TeamscaleConstants.ACCESS_TOKEN}"
					project = "test-project"
				}
				commit {
					revision = ""
				}
			}
			
			tasks.register('uploadTestResults', com.teamscale.TeamscaleUpload) {
				partition = "test-results"
				message = "Test upload"
				from unitTest
			}
			""".trimIndent()
		)

		val result = runExpectingError("uploadTestResults")
		assertThat(result.output).contains("To upload to Teamscale you must specify the commit to which the data should be uploaded to via teamscale.commit")
	}

	@Test
	fun `upload fails with missing access token`() {
		rootProject.excludeFailingTests()
		rootProject.defineTestTasks()

		// Configure teamscale upload with missing access token
		rootProject.buildFile.appendText(
			"""
				
			teamscale {
				server {
					url = "${teamscaleMockServer.url}"
					userName = "${TeamscaleConstants.USER}"
					userAccessToken = ""
					project = "test-project"
				}
				commit {
					revision = "test-revision"
				}
			}
			
			tasks.register('uploadTestResults', com.teamscale.TeamscaleUpload) {
				partition = "test-results"
				message = "Test upload"
				from unitTest
			}
			""".trimIndent()
		)

		val result = runExpectingError("uploadTestResults")
		assertThat(result.output).contains("Teamscale server 'userAccessToken' must not be empty.")
	}
}
