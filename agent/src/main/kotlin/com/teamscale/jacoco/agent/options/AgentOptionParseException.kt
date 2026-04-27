package com.teamscale.jacoco.agent.options

/**
 * Thrown if option parsing fails.
 */
class AgentOptionParseException : Exception {
	constructor(message: String?) : super(message)
	constructor(e: Exception) : super(e.message, e)
	constructor(message: String?, cause: Throwable?) : super(message, cause)
}