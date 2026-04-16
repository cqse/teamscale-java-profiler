package com.teamscale.jacoco.agent.options

import com.teamscale.client.CommitDescriptor.Companion.parse
import com.teamscale.client.TeamscaleServer
import com.teamscale.jacoco.agent.commit_resolution.git_properties.CommitInfo
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig
import com.teamscale.report.util.ILogger
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.mockito.Mockito

/**
 * Builds [AgentOptions] for test purposes
 */
class TestAgentOptionsBuilder {
	private var httpServerPort: Int? = null
	private val artifactoryConfig = ArtifactoryConfig()
	private val teamscaleServer = TeamscaleServer()

	/**
	 * Ensures that the [AgentOptions] are [built][create] with the given
	 * [HTTP server port][AgentOptions.httpServerPort].
	 */
	fun withHttpServerPort(httpServerPort: Int?): TestAgentOptionsBuilder {
		this.httpServerPort = httpServerPort
		return this
	}

	/**
	 * Ensures that the [AgentOptions] are [built][create] with the given
	 * [Teamscale partition][TeamscaleServer.partition].
	 */
	fun withTeamscalePartition(teamscalePartition: String?): TestAgentOptionsBuilder {
		this.teamscaleServer.partition = teamscalePartition
		return this
	}

	/**
	 * Ensures that the [AgentOptions] are [built][create] with the given
	 * [Teamscale message][TeamscaleServer.message].
	 */
	fun withTeamscaleMessage(teamscaleMessage: String?): TestAgentOptionsBuilder {
		this.teamscaleServer.message = teamscaleMessage
		return this
	}

	/**
	 * Ensures that the [AgentOptions] are [built][create] with the given
	 * [Teamscale project][TeamscaleServer.project].
	 */
	fun withTeamscaleProject(project: String?): TestAgentOptionsBuilder {
		this.teamscaleServer.project = project
		return this
	}

	/**
	 * Ensures that the [AgentOptions] are [built][create] with the given
	 * [Teamscale url][TeamscaleServer.url].
	 */
	fun withTeamscaleUrl(url: String): TestAgentOptionsBuilder {
		this.teamscaleServer.url = url.toHttpUrlOrNull()
		return this
	}

	/**
	 * Ensures that the [AgentOptions] are [built][create] with the given
	 * [Teamscale username][TeamscaleServer.userName].
	 */
	fun withTeamscaleUser(user: String?): TestAgentOptionsBuilder {
		this.teamscaleServer.userName = user
		return this
	}

	/**
	 * Ensures that the [AgentOptions] are [built][create] with the given
	 * [Teamscale access token][TeamscaleServer.userAccessToken].
	 */
	fun withTeamscaleAccessToken(accessToken: String?): TestAgentOptionsBuilder {
		this.teamscaleServer.userAccessToken = accessToken
		return this
	}

	/**
	 * Ensures that the [AgentOptions] are [built][create] with the given
	 * [Teamscale revision][TeamscaleServer.revision].
	 */
	fun withTeamscaleRevision(revision: String?): TestAgentOptionsBuilder {
		this.teamscaleServer.revision = revision
		return this
	}

	/**
	 * Adds minimal artifactory configs so that a ArtifactoryUploader can be built.
	 */
	fun withMinimalArtifactoryConfig(apiKey: String?, partition: String?, url: String): TestAgentOptionsBuilder {
		artifactoryConfig.apiKey = apiKey
		artifactoryConfig.partition = partition
		artifactoryConfig.url = url.toHttpUrlOrNull()
		artifactoryConfig.commitInfo = CommitInfo(
			"fake_revision",
			parse("somebranch:0")
		)
		return this
	}

	/**
	 * Builds the [AgentOptions].
	 */
	fun create(): AgentOptions {
		val agentOptions = AgentOptions(Mockito.mock(ILogger::class.java))
		agentOptions.teamscaleServer = teamscaleServer
		agentOptions.httpServerPort = httpServerPort
		agentOptions.artifactoryConfig = artifactoryConfig
		return agentOptions
	}
}
