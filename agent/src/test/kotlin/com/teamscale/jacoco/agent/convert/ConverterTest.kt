package com.teamscale.jacoco.agent.convert

import com.teamscale.client.FileSystemUtils.readFileUTF8
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.net.URISyntaxException
import java.nio.file.Files

/** Basic smoke test for the converter.  */
class ConverterTest {
	/**
	 * Ensures that running the converter on valid input does not yield any errors and produces a coverage XML report.
	 */
	@Test
	@Throws(Exception::class)
	fun testSmokeTest(@TempDir tempDir: File?) {
		val execFile = File(javaClass.getResource("coverage.exec")!!.toURI())
		val classFile = File(javaClass.getResource("TestClass.class")!!.toURI())
		val outputFile = File(tempDir, "coverage.xml")

		val arguments = ConvertCommand()
		arguments.inputFiles = mutableListOf(execFile.absolutePath)
		arguments.outputFile = outputFile.absolutePath
		arguments.classDirectoriesOrZips = mutableListOf(classFile.absolutePath)

		Converter(arguments).runJaCoCoReportGeneration()

		val xml = readFileUTF8(outputFile)
		Assertions.assertThat(xml).isNotEmpty().contains("<package").contains("<sourcefile").contains("<counter")
			.contains("TestClass")
	}

	@Test
	@Throws(Exception::class)
	fun testNestedJar(@TempDir tempDir: File?) {
		val execFile = File(javaClass.getResource("coverage.exec")!!.toURI())
		val classFile = File(javaClass.getResource("TestClass.jar.zip")!!.toURI())
		val outputFile = File(tempDir, "coverage.xml")

		val arguments = ConvertCommand()
		arguments.inputFiles = mutableListOf(execFile.absolutePath)
		arguments.outputFile = outputFile.absolutePath
		arguments.classDirectoriesOrZips = mutableListOf(classFile.absolutePath)

		Converter(arguments).runJaCoCoReportGeneration()

		val xml = readFileUTF8(outputFile)
		Assertions.assertThat(xml).isNotEmpty().contains("<package").contains("<sourcefile").contains("<counter")
			.contains("TestClass")
	}

	/**
	 * Ensures that running the converter on valid input does not yield any errors and produces a coverage XML report.
	 */
	@Test
	@Throws(Exception::class)
	fun testTestwiseCoverageSmokeTest(@TempDir tempDir: File?) {
		val inputDir = File(tempDir, "input")
		inputDir.mkdir()
		copyResourceTo("coverage-testwise.exec", inputDir)
		copyResourceTo("test-list.json", inputDir)
		copyResourceTo("test-execution.json", inputDir)
		val classFile = File(javaClass.getResource("classes.zip")!!.toURI())
		val outputFile = File(tempDir, "testwise-coverage.json")

		val arguments = ConvertCommand()
		arguments.inputFiles = mutableListOf(inputDir.absolutePath)
		arguments.outputFile = outputFile.absolutePath
		arguments.classDirectoriesOrZips = mutableListOf(classFile.absolutePath)

		Converter(arguments).runTestwiseCoverageReportGeneration()

		val json = readFileUTF8(File(tempDir, "testwise-coverage-1.json"))
		Assertions.assertThat(json)
			.contains("\"uniformPath\" : \"[engine:junit-vintage]/[runner:org.conqat.lib.cqddl.CQDDLTest]/[test:testFunctions(org.conqat.lib.cqddl.CQDDLTest)]\"")
			.contains("\"uniformPath\" : \"[engine:junit-vintage]/[runner:org.conqat.lib.cqddl.CQDDLTest]/[test:testDirectObjectInsertion(org.conqat.lib.cqddl.CQDDLTest)]\"")
			.contains("\"uniformPath\" : \"[engine:junit-vintage]/[runner:org.conqat.lib.cqddl.CQDDLTest]/[test:testKeyAbbreviations(org.conqat.lib.cqddl.CQDDLTest)]\"")
			.contains("\"uniformPath\" : \"[engine:junit-vintage]/[runner:org.conqat.lib.cqddl.CQDDLTest]/[test:testKeyAbbreviations(org.conqat.lib.cqddl.CQDDLTest)]\"")
			.contains("\"result\" : \"PASSED\"").contains("\"duration\" : 1234")
			.contains("\"coveredLines\" : \"33,46-47")
	}

	@Throws(URISyntaxException::class, IOException::class)
	private fun copyResourceTo(name: String, targetDir: File?) {
		val execFile = File(javaClass.getResource(name)!!.toURI())
		Files.copy(execFile.toPath(), File(targetDir, name).toPath())
	}
}
