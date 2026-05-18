package com.teamscale.jacoco.agent.options

/** Describes the two possible modes the agent can be started in.  */
enum class EMode {
	/**
	 * The default mode which produces JaCoCo XML coverage files on exit, in a defined interval or when triggered via an
	 * HTTP endpoint. Each dump produces a new file containing the all collected coverage.
	 */
	NORMAL,
	/**
	 * Testwise coverage mode in which the agent only dumps when triggered via an HTTP endpoint. Coverage is written as
	 * exec and appended into a single file.
	 */
	TESTWISE
}
