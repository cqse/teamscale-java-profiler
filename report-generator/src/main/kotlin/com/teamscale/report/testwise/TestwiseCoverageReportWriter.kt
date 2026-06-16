package com.teamscale.report.testwise

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.teamscale.client.JsonUtils
import com.teamscale.report.testwise.model.TestInfo
import com.teamscale.report.testwise.model.builder.TestCoverageBuilder
import com.teamscale.report.testwise.model.factory.TestInfoFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.function.Consumer

/**
 * Writes out a [com.teamscale.report.testwise.model.TestwiseCoverageReport] one [TestInfo] after the other
 * so that we do not need to keep them all in memory during the conversion.
 */
class TestwiseCoverageReportWriter(
	/** Factory for converting [TestCoverageBuilder] objects to [TestInfo]s.  */
	private val testInfoFactory: TestInfoFactory, private val outputFile: File,
	/** After how many written tests a new file should be started.  */
	private val splitAfter: Int,
	/** Teh value of the partial flag. */
	private val partial: Boolean?
) : Consumer<TestCoverageBuilder>,
	AutoCloseable {
	/** Writer instance to where the [com.teamscale.report.testwise.model.TestwiseCoverageReport] is written to. */
	private var jsonGenerator: JsonGenerator? = null

	/** Number of tests written to the file.  */
	private var testsWritten: Int = 0

	/** Number of test files that have been written.  */
	private var testFileCounter: Int = 0

	init {
		startReport()
	}

	override fun accept(testCoverageBuilder: TestCoverageBuilder) {
		val testInfo = testInfoFactory.createFor(testCoverageBuilder)
		try {
			writeTestInfo(testInfo)
		} catch (e: IOException) {
			// Need to be wrapped in RuntimeException as Consumer does not allow to throw a checked Exception
			throw RuntimeException(
				"Writing test info for '${testInfo.uniformPath}' to the testwise report failed: ${e.message}." +
						" Check disk space and that the agent's 'out' directory is writable.",
				e
			)
		}
	}

	@Throws(IOException::class)
	override fun close() {
		testInfoFactory.createTestInfosWithoutCoverage().forEach { testInfo ->
			writeTestInfo(testInfo)
		}
		endReport()
	}

	@Throws(IOException::class)
	private fun startReport() {
		testFileCounter++
		val outputStream = Files.newOutputStream(getOutputFile(testFileCounter).toPath())
		jsonGenerator = JsonUtils.createFactory().createGenerator(outputStream).apply {
			prettyPrinter = DefaultPrettyPrinter()
			writeStartObject()
			partial?.let { writeBooleanField("partial", it) }
			writeFieldName("tests")
			writeStartArray()
		}
	}

	private fun getOutputFile(testFileCounter: Int): File {
		var name = outputFile.nameWithoutExtension

		name = "$name-$testFileCounter.json"
		return File(outputFile.getParent(), name)
	}

	@Throws(IOException::class)
	private fun writeTestInfo(testInfo: TestInfo?) {
		if (testInfo == null || testInfo.paths.isEmpty() && partial == true) {
			// Do not add skipped tests to the report if the partial flag is set
			return
		}
		if (testsWritten >= splitAfter) {
			endReport()
			testsWritten = 0
			startReport()
		}
		jsonGenerator?.writeObject(testInfo)
		testsWritten++
	}

	@Throws(IOException::class)
	private fun endReport() {
		jsonGenerator?.let {
			it.writeEndArray()
			it.writeEndObject()
			it.close()
		}
	}
}
