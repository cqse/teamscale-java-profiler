package com.teamscale.jacoco.agent.options

import com.teamscale.jacoco.agent.commit_resolution.git_properties.CommitInfo
import java.util.*

/** Class encapsulating the Teamscale project and git commitInfo an upload should be performed to.  */
data class ProjectAndCommit(
	@JvmField val project: String?,
	@JvmField val commitInfo: CommitInfo?
)
