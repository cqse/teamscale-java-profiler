package com.teamscale.jacoco.agent

/**
 * Raises a dump interval below one hour to one hour, once the agent uploaded coverage under it.
 *
 * Users set a short interval to see a first upload soon. They often forget to raise it again. An agent that only
 * writes to the local disk keeps a short interval. No server receives its coverage.
 */
class DumpIntervalGuard(
	/** The minutes between two interval dumps, as the `interval` option sets them. */
	configuredIntervalInMinutes: Int,
	/** Whether the agent sends its coverage to a server, rather than only writing it to the local disk. */
	isUploading: Boolean
) {

	/** Whether the configured interval applies only until the first successful upload. */
	val isShortUploadInterval =
		isUploading && configuredIntervalInMinutes in 1 until MINIMUM_UPLOAD_INTERVAL_IN_MINUTES

	/** The minutes between two interval dumps. */
	var dumpIntervalInMinutes = configuredIntervalInMinutes
		private set

	/**
	 * Raises a short upload interval to one hour. Returns whether this call raised it. Only the first call for a
	 * short upload interval does.
	 */
	fun raiseAfterSuccessfulUpload(): Boolean {
		if (!isShortUploadInterval || dumpIntervalInMinutes == MINIMUM_UPLOAD_INTERVAL_IN_MINUTES) {
			return false
		}

		dumpIntervalInMinutes = MINIMUM_UPLOAD_INTERVAL_IN_MINUTES
		return true
	}

	companion object {
		/** The minutes between two interval dumps after the first successful upload under a shorter interval. */
		const val MINIMUM_UPLOAD_INTERVAL_IN_MINUTES = 60
	}
}
