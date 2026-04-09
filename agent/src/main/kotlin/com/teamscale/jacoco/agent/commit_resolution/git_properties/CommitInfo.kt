package com.teamscale.jacoco.agent.commit_resolution.git_properties

import com.teamscale.client.CommitDescriptor
import com.teamscale.client.StringUtils.isEmpty
import java.util.*

/** Hold information regarding a commit.  */
data class CommitInfo(
	/** The revision information (git hash).  */
	@JvmField var revision: String?,
	/** The commit descriptor.  */
	@JvmField var commit: CommitDescriptor?
) {
	/**
	 * If the commit property is set via the `teamscale.commit.branch` and `teamscale.commit.time`
	 * properties in a git.properties file, this should be preferred to the revision. For details see [TS-38561](https://cqse.atlassian.net/browse/TS-38561).
	 */
	@JvmField
	var preferCommitDescriptorOverRevision: Boolean = false

	override fun toString() = "$commit/$revision"

	/**
	 * Returns true if one of or both, revision and commit, are set
	 */
	val isEmpty: Boolean
		get() = revision.isNullOrEmpty() && commit == null
}
