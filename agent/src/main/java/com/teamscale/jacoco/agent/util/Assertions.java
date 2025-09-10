package com.teamscale.jacoco.agent.util;

import org.jetbrains.annotations.Contract;

/**
 * This class provides simple methods to implement assertions.
 */
public class Assertions {

	/**
	 * Checks if a condition is <code>true</code>.
	 *
	 * @param condition condition to check
	 * @param message   exception message
	 * @throws AssertionError if the condition is <code>false</code>
	 */
	@Contract(value = "false, _ -> fail", pure = true)
	public static void isTrue(boolean condition, String message) throws AssertionError {
		throwAssertionErrorIfTestFails(condition, message);
	}

	/**
	 * Checks if a condition is <code>false</code>.
	 *
	 * @param condition condition to check
	 * @param message   exception message
	 * @throws AssertionError if the condition is <code>true</code>
	 */
	@Contract(value = "true, _ -> fail", pure = true)
	public static void isFalse(boolean condition, String message) throws AssertionError {
		throwAssertionErrorIfTestFails(!condition, message);
	}

	/**
	 * Throws an {@link AssertionError} if the test fails.
	 *
	 * @param test    test which should be true
	 * @param message exception message
	 * @throws AssertionError if the test fails
	 */
	private static void throwAssertionErrorIfTestFails(boolean test, String message) {
		if (!test) {
			throw new AssertionError(message);
		}
	}
}
