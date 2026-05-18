package com.teamscale.jacoco.agent.testimpact

import com.teamscale.client.JsonUtils.serializeToJson
import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.report.testwise.model.TestExecution
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Helper class for writing a list of test executions to a file. This class ensures that we never have to hold all test
 * executions in memory but rather incrementally append to the output file. This ensures that we don't use unnecessary
 * amounts of memory during profiling.
 */
class TestExecutionWriter(private val testExecutionFile: File) {
	private val logger = getLogger(this)

	private var hasWrittenAtLeastOneExecution = false

	init {
		logger.debug("Writing test executions to {}", testExecutionFile)
	}

	/** Appends the given [TestExecution] to the test execution list file.  */
	@Synchronized
	@Throws(IOException::class)
	fun append(testExecution: TestExecution) {
		val json = testExecution.serializeToJson()

		RandomAccessFile(testExecutionFile, "rwd").use { file ->
			var textToWrite = "$json]"
			if (hasWrittenAtLeastOneExecution) {
				textToWrite = ",$textToWrite"
				// overwrite the trailing "]"
				file.seek(file.length() - 1)
			} else {
				textToWrite = "[$textToWrite"
			}
			file.write(textToWrite.toByteArray(StandardCharsets.UTF_8))
		}
		hasWrittenAtLeastOneExecution = true
	}
}
