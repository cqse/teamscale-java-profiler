package com.teamscale.jacoco.agent.upload

import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException

/**
 * Exception thrown from an uploader. Either during the upload or in the validation process.
 */
class UploaderException : Exception {
	/** Constructor  */
	constructor(message: String, e: Exception) : super(message, e)

	/** Constructor  */
	constructor(message: String) : super(message)

	/** Constructor  */
	constructor(message: String, response: Response<ResponseBody>) : super(createResponseMessage(message, response))

	companion object {
		private fun createResponseMessage(message: String, response: Response<ResponseBody>): String {
			try {
				val errorBodyMessage = response.errorBody()!!.string()
				return "$message (${response.code()}): \n$errorBodyMessage"
			} catch (_: IOException) {
				return message
			} catch (_: NullPointerException) {
				return message
			}
		}
	}
}