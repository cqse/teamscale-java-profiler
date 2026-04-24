package com.teamscale.report

/**
 * Behavior when two non-identical class files with the same package name are found.
 *
 * Note for use inside the agent: the agent must never crash the profiled application (see
 * `PreMain` class Javadoc). [FAIL] therefore only aborts the individual dump; `Agent.dumpReport`
 * catches the resulting exception and logs it. Outside the agent (for example, in the CLI
 * converter or the Gradle plugin) [FAIL] surfaces as an exception to the caller as usual.
 */
enum class EDuplicateClassFileBehavior {
	/** Completely ignores it.  */
	IGNORE,

	/** Prints a warning to the logger.  */
	WARN,

	/** Fails and aborts the current report generation. See class-level note on agent semantics.  */
	FAIL
}
