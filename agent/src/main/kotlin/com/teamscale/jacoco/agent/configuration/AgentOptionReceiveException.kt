package com.teamscale.jacoco.agent.configuration

/** Thrown when retrieving the profiler configuration from Teamscale fails.   */
class AgentOptionReceiveException : Exception {
	constructor(message: String?) : super(message)
	constructor(message: String?, cause: Throwable?) : super(message, cause)
}