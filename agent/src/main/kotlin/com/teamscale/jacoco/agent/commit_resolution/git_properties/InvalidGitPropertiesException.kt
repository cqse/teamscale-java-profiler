package com.teamscale.jacoco.agent.commit_resolution.git_properties

/**
 * Thrown in case a git.properties file is found but it is malformed.
 */
class InvalidGitPropertiesException : Exception {
	internal constructor(s: String, throwable: Throwable?) : super(s, throwable)
	constructor(s: String) : super(s)
}
