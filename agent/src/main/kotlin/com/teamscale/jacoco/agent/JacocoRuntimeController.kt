package com.teamscale.jacoco.agent

import com.teamscale.report.jacoco.dump.Dump
import org.jacoco.agent.rt.IAgent
import org.jacoco.core.data.ExecutionDataReader
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.ISessionInfoVisitor
import org.jacoco.core.data.SessionInfo
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Wrapper around JaCoCo's [RT] runtime interface.
 *
 *
 * Can be used if the calling code is run in the same JVM as the agent is attached to.
 */
class JacocoRuntimeController
/** Constructor.  */(
	/** JaCoCo's [RT] agent instance  */
	private val agent: IAgent
) {
	/** Indicates a failed dump.  */
	class DumpException(message: String?, cause: Throwable?) : Exception(message, cause)

	/**
	 * Dumps execution data and resets it.
	 *
	 * @throws DumpException if dumping fails. This should never happen in real life. Dumping should simply be retried
	 * later if this ever happens.
	 */
	@Throws(DumpException::class)
	fun dumpAndReset(): Dump {
		val binaryData = agent.getExecutionData(true)

		try {
			ByteArrayInputStream(binaryData).use { inputStream ->
				ExecutionDataReader(inputStream).apply {
					val store = ExecutionDataStore()
					setExecutionDataVisitor { store.put(it) }
					val sessionInfoVisitor = SessionInfoVisitor()
					setSessionInfoVisitor(sessionInfoVisitor)
					read()
					return Dump(sessionInfoVisitor.sessionInfo, store)
				}
			}
		} catch (e: IOException) {
			throw DumpException("should never happen for the ByteArrayInputStream", e)
		}
	}

	/**
	 * Dumps execution data to the given file and resets it afterwards.
	 */
	@Throws(IOException::class)
	fun dumpToFileAndReset(file: File) {
		val binaryData = agent.getExecutionData(true)

		FileOutputStream(file, true).use { outputStream ->
			outputStream.write(binaryData)
		}
	}


	/**
	 * Dumps execution data to a file and resets it.
	 *
	 * @throws DumpException if dumping fails. This should never happen in real life. Dumping should simply be retried
	 * later if this ever happens.
	 */
	@Throws(DumpException::class)
	fun dump() {
		try {
			agent.dump(true)
		} catch (e: IOException) {
			throw DumpException(e.message, e)
		}
	}

	/** Resets already collected coverage.  */
	fun reset() {
		agent.reset()
	}

	var sessionId: String?
		/** Returns the current sessionId.  */
		get() = agent.sessionId
		/**
		 * Sets the current sessionId of the agent that can be used to identify which coverage is recorded from now on.
		 */
		set(sessionId) {
			agent.setSessionId(sessionId)
		}

	/** Unsets the session ID so that coverage collected from now on is not attributed to the previous test.  */
	fun resetSessionId() {
		agent.sessionId = ""
	}

	/**
	 * Receives and stores a [org.jacoco.core.data.SessionInfo]. Has a fallback dummy session in case nothing is received.
	 */
	private class SessionInfoVisitor : ISessionInfoVisitor {
		/** The received session info or a dummy.  */
		var sessionInfo: SessionInfo = SessionInfo(
			"dummysession", System.currentTimeMillis(), System.currentTimeMillis()
		)

		/** {@inheritDoc}  */
		override fun visitSessionInfo(info: SessionInfo) {
			this.sessionInfo = info
		}
	}
}