/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2017 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent.commandline

import com.teamscale.jacoco.agent.options.AgentOptionParseException
import java.io.IOException

/**
 * Interface for commands: argument parsing and execution.
 */
interface ICommand {
	/**
	 * Makes sure the arguments are valid. Must return all detected problems in the
	 * form of a user-visible message.
	 */
	@Throws(AgentOptionParseException::class, IOException::class)
	fun validate(): Validator

	/**
	 * Runs the implementation of the command. May throw an exception to indicate
	 * abnormal termination of the program.
	 */
	@Throws(Exception::class)
	fun run()
}