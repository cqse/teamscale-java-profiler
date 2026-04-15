package com.teamscale.jacoco.agent.util

import com.teamscale.jacoco.agent.util.AgentUtils.agentDirectory
import org.assertj.core.api.Assertions
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test Utilities
 */
object TestUtils {
	/**
	 * Deletes all contents inside the coverage folder inside the agent directory
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun cleanAgentCoverageDirectory() {
		val coverageDir = agentDirectory.resolve("coverage")
		if (Files.exists(coverageDir)) {
			Files.list(coverageDir).use { stream ->
				stream.forEach { path: Path? ->
					Assertions.assertThat(path!!.toFile().delete()).withFailMessage("Failed to delete " + path).isTrue()
				}
			}
			Files.delete(coverageDir)
		}
	}

	@get:Throws(IOException::class)
	val freePort: Int
		/** Returns a new free TCP port number  */
		get() {
			ServerSocket(0).use { socket ->
				socket.setReuseAddress(true)
				return socket.getLocalPort()
			}
		}
}
