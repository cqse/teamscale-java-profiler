package com.teamscale.config

import com.teamscale.client.EnvironmentVariableChecker
import org.eclipse.jgit.api.Git
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.util.logging.Logger

/**
 * Provider that tries to determine the repository revision either
 * from the environment variables or from a checked-out Git repository.
 */
abstract class GitRevisionValueSource : ValueSource<String, GitRevisionValueSource.Parameters> {

	/** Parameters for the GitRevisionValueSource. */
	interface Parameters : ValueSourceParameters {
		/** The project directory in which the plugin was applied. */
		val projectDirectory: DirectoryProperty
	}

	companion object {
		private val LOGGER = Logger.getLogger("GitRevisionValueSource")
	}

	override fun obtain(): String? {
		EnvironmentVariableChecker.findCommit()?.let {
			return it
		}

		try {
			val git = Git.open(parameters.projectDirectory.get().asFile)
			return git.repository.refDatabase.findRef("HEAD").objectId.name
		} catch (e: Exception) {
			LOGGER.info { "Failed to auto-detect git revision from checked out repository! " + e.stackTraceToString() }
			return null
		}
	}
}
