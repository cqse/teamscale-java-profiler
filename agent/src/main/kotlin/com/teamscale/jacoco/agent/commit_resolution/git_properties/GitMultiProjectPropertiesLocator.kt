package com.teamscale.jacoco.agent.commit_resolution.git_properties

import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.upload.teamscale.DelayedTeamscaleMultiProjectUploader
import com.teamscale.jacoco.agent.util.DaemonThreadFactory
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Searches a Jar/War/Ear/... file for a git.properties file in order to enable upload for the commit described therein,
 * e.g. to Teamscale, via a [DelayedTeamscaleMultiProjectUploader]. Specifically, this searches for the
 * 'teamscale.project' property specified in each of the discovered 'git.properties' files.
 */
class GitMultiProjectPropertiesLocator(
	private val uploader: DelayedTeamscaleMultiProjectUploader,
	private val executor: Executor,
	private val recursiveSearch: Boolean,
	private val gitPropertiesCommitTimeFormat: DateTimeFormatter?
) : IGitPropertiesLocator {
	private val logger = getLogger(this)

	constructor(
		uploader: DelayedTeamscaleMultiProjectUploader,
		recursiveSearch: Boolean,
		gitPropertiesCommitTimeFormat: DateTimeFormatter?
	) : this(
		uploader, Executors.newSingleThreadExecutor(
			DaemonThreadFactory(
				GitMultiProjectPropertiesLocator::class.java,
				"git.properties Jar scanner thread"
			)
		), recursiveSearch, gitPropertiesCommitTimeFormat
	)

	/**
	 * Asynchronously searches the given jar file for git.properties files and adds a corresponding uploader to the
	 * multi-project uploader.
	 */
	override fun searchFileForGitPropertiesAsync(file: File, isJarFile: Boolean) {
		executor.execute { searchFile(file, isJarFile) }
	}

	/**
	 * Synchronously searches the given jar file for git.properties files and adds a corresponding uploader to the
	 * multi-project uploader.
	 */
	@VisibleForTesting
	fun searchFile(file: File, isJarFile: Boolean) {
		logger.debug("Searching file {} for multiple git.properties", file.toString())
		try {
			val projectAndCommits = GitPropertiesLocatorUtils.getProjectRevisionsFromGitProperties(
				file, isJarFile, recursiveSearch, gitPropertiesCommitTimeFormat
			)
			if (projectAndCommits.isEmpty()) {
				logger.debug("No git.properties file found in {}", file)
				return
			}

			projectAndCommits.forEach { projectAndCommit ->
				// this code only runs when 'teamscale-project' is not given via the agent properties,
				// i.e., a multi-project upload is being attempted.
				// Therefore, we expect to find both the project (teamscale.project) and the revision
				// (git.commit.id) in the git.properties file.
				if (projectAndCommit.project == null || projectAndCommit.commitInfo == null) {
					logger.debug(
						"Found inconsistent git.properties file: the git.properties file in {} either does not specify the" +
								" Teamscale project ({}) property, or does not specify the commit " +
								"({}, {} + {}, or {} + {})." +
								" Will skip this git.properties file and try to continue with the other ones that were found during discovery.",
						file, GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_PROJECT,
						GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_ID,
						GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_BRANCH,
						GitPropertiesLocatorUtils.GIT_PROPERTIES_GIT_COMMIT_TIME,
						GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_BRANCH,
						GitPropertiesLocatorUtils.GIT_PROPERTIES_TEAMSCALE_COMMIT_TIME
					)
					return@forEach
				}
				logger.debug(
					"Found git.properties file in {} and found Teamscale project {} and revision {}", file,
					projectAndCommit.project, projectAndCommit.commitInfo
				)
				uploader.addTeamscaleProjectAndCommit(file, projectAndCommit)
			}
		} catch (e: IOException) {
			logger.error("Error during asynchronous search for git.properties in {}", file, e)
		} catch (e: InvalidGitPropertiesException) {
			logger.error("Error during asynchronous search for git.properties in {}", file, e)
		}
	}
}
