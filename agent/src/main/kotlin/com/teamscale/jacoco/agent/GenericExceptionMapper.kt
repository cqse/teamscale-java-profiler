package com.teamscale.jacoco.agent

import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

/**
 * Generates a [javax.ws.rs.core.Response] for an exception.
 */
@Provider
class GenericExceptionMapper : ExceptionMapper<Throwable?> {
	override fun toResponse(e: Throwable?): Response =
		Response.status(Response.Status.INTERNAL_SERVER_ERROR).apply {
			type(MediaType.TEXT_PLAIN_TYPE)
			entity("Message: ${e?.message}")
		}.build()
}