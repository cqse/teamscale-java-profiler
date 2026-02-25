/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2017 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent.commandline

import com.teamscale.client.StringUtils
import com.teamscale.jacoco.agent.util.Assertions

/**
 * Helper class to allow for multiple validations to occur.
 */
class Validator {
	/** The found validation problems in the form of error messages for the user.  */
	private val messages = mutableListOf<String>()

	/** Runs the given validation routine.  */
	fun ensure(validation: ExceptionBasedValidation) {
		try {
			validation.validate()
		} catch (e: Exception) {
			e.message?.let { messages.add(it) }
		} catch (e: AssertionError) {
			e.message?.let { messages.add(it) }
		}
	}

	/**
	 * Interface for a validation routine that throws an exception when it fails.
	 */
	fun interface ExceptionBasedValidation {
		/**
		 * Throws an [Exception] or [AssertionError] if the validation fails.
		 */
		@Throws(Exception::class, AssertionError::class)
		fun validate()
	}

	/**
	 * Checks that the given condition is `true` or adds the given error message.
	 */
	fun isTrue(condition: Boolean, message: String?) {
		ensure { Assertions.isTrue(condition, message) }
	}

	/**
	 * Checks that the given condition is `false` or adds the given error message.
	 */
	fun isFalse(condition: Boolean, message: String?) {
		ensure { Assertions.isFalse(condition, message) }
	}

	val isValid: Boolean
		/** Returns `true` if the validation succeeded.  */
		get() = messages.isEmpty()

	val errorMessage: String
		/** Returns an error message with all validation problems that were found.  */
		get() = "- ${messages.joinToString("${StringUtils.LINE_FEED}- ")}"
}
