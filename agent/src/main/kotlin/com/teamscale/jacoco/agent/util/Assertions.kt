package com.teamscale.jacoco.agent.util

import org.jetbrains.annotations.Contract

/**
 * Simple methods to implement assertions.
 */
object Assertions {
	/**
	 * Checks if a condition is `true`.
	 *
	 * @param condition condition to check
	 * @param message   exception message
	 * @throws AssertionError if the condition is `false`
	 */
	@JvmStatic
	@Contract(value = "false, _ -> fail", pure = true)
	@Throws(AssertionError::class)
	fun isTrue(condition: Boolean, message: String?) {
		throwAssertionErrorIfTestFails(condition, message)
	}

	/**
	 * Checks if a condition is `false`.
	 *
	 * @param condition condition to check
	 * @param message   exception message
	 * @throws AssertionError if the condition is `true`
	 */
	@Contract(value = "true, _ -> fail", pure = true)
	@Throws(AssertionError::class)
	fun isFalse(condition: Boolean, message: String?) {
		throwAssertionErrorIfTestFails(!condition, message)
	}

	/**
	 * Throws an [AssertionError] if the test fails.
	 *
	 * @param test    test which should be true
	 * @param message exception message
	 * @throws AssertionError if the test fails
	 */
	private fun throwAssertionErrorIfTestFails(test: Boolean, message: String?) {
		if (!test) {
			throw AssertionError(message)
		}
	}
}