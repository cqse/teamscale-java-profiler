package com.teamscale.client

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/** A log entry to be sent to Teamscale */
class ProfilerLogEntry @JsonCreator constructor(
	/** The time of the event */
	@param:JsonProperty("timestamp") var timestamp: Long,

	/** Log message */
	@param:JsonProperty("message") var message: String,

	/** Details, for example, the stack trace */
	@param:JsonProperty("details") var details: String?,

	/** Event severity */
	@param:JsonProperty("severity") var severity: String
)
