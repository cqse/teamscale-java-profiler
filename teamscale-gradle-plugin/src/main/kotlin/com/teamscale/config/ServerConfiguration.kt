package com.teamscale.config

import com.teamscale.client.TeamscaleClient
import com.teamscale.client.TeamscaleServiceGenerator
import com.teamscale.utils.BuildVersion
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import java.io.Serializable

abstract class ServerConfiguration : Serializable {
	/** The url of the Teamscale server. */
	abstract val url: Property<String>

	/** The project id for which artifacts should be uploaded. */
	abstract val project: Property<String>

	/** The username of the Teamscale user. */
	abstract val userName: Property<String>

	/** The access token of the user. */
	abstract val userAccessToken: Property<String>

	fun validate() {
		if (url.get().isBlank()) {
			throw GradleException(missingServerPropertyMessage("url", "https://teamscale.example.com/"))
		}
		if (project.get().isBlank()) {
			throw GradleException(missingServerPropertyMessage("project", "my-project-id"))
		}
		if (userName.get().isBlank()) {
			throw GradleException(missingServerPropertyMessage("userName", "alice"))
		}
		if (userAccessToken.get().isBlank()) {
			throw GradleException(missingServerPropertyMessage("userAccessToken", "<your-Teamscale-access-key>"))
		}
	}

	private fun missingServerPropertyMessage(property: String, example: String) =
		"Teamscale server '$property' must not be empty." +
				" Set it via 'teamscale { server { $property = \"$example\" } }' in your build.gradle.kts."

	fun toClient() = TeamscaleClient(
		url.get(), userName.get(), userAccessToken.get(), project.get(),
		userAgent = TeamscaleServiceGenerator.buildUserAgent("Teamscale Gradle Plugin", BuildVersion.pluginVersion)
	)
}
