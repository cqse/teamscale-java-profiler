package com.teamscale.jacoco.agent.upload.teamscale

import com.teamscale.client.TeamscaleServer
import com.teamscale.jacoco.agent.commit_resolution.git_properties.CommitInfo
import com.teamscale.jacoco.agent.options.ProjectAndCommit
import com.teamscale.jacoco.agent.upload.DelayedMultiUploaderBase
import com.teamscale.jacoco.agent.upload.IUploader
import java.io.File

/** Wrapper for [TeamscaleUploader] that allows to upload the same coverage file to multiple Teamscale projects.  */
class DelayedTeamscaleMultiProjectUploader(
	private val teamscaleServerFactory: (String?, CommitInfo?) -> TeamscaleServer
) : DelayedMultiUploaderBase(), IUploader {
	@JvmField
	val teamscaleUploaders = mutableListOf<TeamscaleUploader>()

	/**
	 * Adds a teamscale project and commit as a possible new target to upload coverage to. Checks if the project and
	 * commit are already registered as an upload target and will prevent duplicate uploads.
	 */
	fun addTeamscaleProjectAndCommit(file: File, projectAndCommit: ProjectAndCommit) {
		val teamscaleServer = teamscaleServerFactory(
			projectAndCommit.project,
			projectAndCommit.commitInfo
		)

		if (teamscaleUploaders.any { it.teamscaleServer.hasSameProjectAndCommit(teamscaleServer) }) {
			logger.debug(
				"Project and commit in git.properties file {} are already registered as upload target. Coverage will not be uploaded multiple times to the same project {} and commit info {}.",
				file, projectAndCommit.project, projectAndCommit.commitInfo
			)
			return
		}
		teamscaleUploaders.add(TeamscaleUploader(teamscaleServer))
	}

	override val wrappedUploaders: MutableCollection<IUploader>
		get() = teamscaleUploaders.toMutableList()
}
