package com.teamscale.jacoco.agent

import com.teamscale.jacoco.agent.options.TestAgentOptionsBuilder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI

class AgentHttpServerTest {
	private var agent: Agent? = null
	private val baseUri: URI
	private val httpServerPort = 8081
	private val defaultCommitMessage = "Some Message"
	private val defaultPartition = "Some Partition"

	init {
		baseUri = URI("http://localhost:$httpServerPort")
	}

	/** Starts the http server to control the agent  */
	@BeforeEach
	@Throws(Exception::class)
	fun setup() {
		val options = TestAgentOptionsBuilder()
			.withHttpServerPort(httpServerPort)
			.withTeamscaleMessage(defaultCommitMessage)
			.withTeamscalePartition(defaultPartition)
			.create()

		agent = Agent(options, null)
	}

	/** Stops the http server  */
	@AfterEach
	fun teardown() {
		agent!!.stopServer()
	}

	/** Test overwriting the commit message  */
	@Test
	@Throws(Exception::class)
	fun testOverridingMessage() {
		val newMessage = "New Message"

		putText("/message", newMessage)

		val teamscaleServer = agent!!.options.teamscaleServer
		Assertions.assertThat(teamscaleServer.message).isEqualTo(newMessage)
	}

	/** Test reading the commit message  */
	@Test
	@Throws(Exception::class)
	fun testGettingMessage() {
		val receivedMessage = getText("/message")

		Assertions.assertThat(receivedMessage).isEqualTo(defaultCommitMessage)
	}

	/** Test overwriting the partition  */
	@Test
	@Throws(Exception::class)
	fun testOverridingPartition() {
		val newPartition = "New Partition"

		putText("/partition", newPartition)

		val teamscaleServer = agent!!.options.teamscaleServer
		Assertions.assertThat(teamscaleServer.partition).isEqualTo(newPartition)
	}

	/** Test reading the partition  */
	@Test
	@Throws(Exception::class)
	fun testGettingPartition() {
		val receivedPartition = getText("/partition")

		Assertions.assertThat(receivedPartition).isEqualTo(defaultPartition)
	}

	/** Test reading the commit to which the agent will upload.  */
	@Test
	@Throws(Exception::class)
	fun testGettingCommit() {
		var receivedCommit = getText("/commit")
		Assertions.assertThat(receivedCommit).isEqualTo("{\"type\":\"REVISION\",\"value\":null}")
		receivedCommit = getJson("/commit")
		Assertions.assertThat(receivedCommit).isEqualTo("{\"type\":\"REVISION\",\"value\":null}")
	}

	/** Test reading the revision to which the agent will upload.  */
	@Test
	@Throws(Exception::class)
	fun testGettingRevision() {
		var receivedRevision = getText("/revision")
		Assertions.assertThat(receivedRevision).isEqualTo("{\"type\":\"REVISION\",\"value\":null}")
		receivedRevision = getJson("/revision")
		Assertions.assertThat(receivedRevision).isEqualTo("{\"type\":\"REVISION\",\"value\":null}")
	}

	@Throws(Exception::class)
	private fun putText(endpointPath: String, newValue: String) {
		val client = OkHttpClient()
		val textPlainMediaType = "text/plain; charset=utf-8".toMediaTypeOrNull()
		val endpointUrl = "$baseUri$endpointPath".toHttpUrlOrNull()
		val content = newValue.toByteArray()
		val request = Request.Builder()
			.url(endpointUrl!!)
			.method("PUT", content.toRequestBody(textPlainMediaType, 0, content.size))
			.build()
		client.newCall(request).execute()
	}

	@Throws(Exception::class)
	private fun getText(endpointPath: String): String {
		val client = OkHttpClient()
		val endpointUrl = "$baseUri$endpointPath".toHttpUrlOrNull()
		val request = Request.Builder()
			.url(endpointUrl!!)
			.build()
		val response = client.newCall(request).execute()
		return response.body.string()
	}

	@Throws(Exception::class)
	private fun getJson(endpointPath: String): String {
		val client = OkHttpClient()
		val endpointUrl = "$baseUri$endpointPath".toHttpUrlOrNull()
		val request = Request.Builder()
			.url(endpointUrl!!).header("Accept", javax.ws.rs.core.MediaType.APPLICATION_JSON)
			.build()
		val response = client.newCall(request).execute()
		return response.body.string()
	}
}
