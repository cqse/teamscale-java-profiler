package com.teamscale.jacoco.agent.options

/** Decides which [com.teamscale.jacoco.agent.testimpact.TestEventHandlerStrategyBase] is used in testwise mode.  */
enum class ETestwiseCoverageMode {
	/** Caches testwise coverage in-memory and uploads a report to Teamscale.  */
	TEAMSCALE_UPLOAD,
	/** Writes testwise coverage to disk as .json files.  */
	DISK,
	/** Writes testwise coverage to disk as .exec files.  */
	EXEC_FILE,
	/** Returns testwise coverage to the caller via HTTP.  */
	HTTP
}
