package com.teamscale.report.cobertura

import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.jacoco.EmptyReportException
import com.teamscale.report.jacoco.dump.Dump
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jacoco.core.data.ExecutionData
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfo
import org.jacoco.core.internal.data.CRC64
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files

/** Tests that the Cobertura report generator turns binary execution data into a Cobertura report. */
class CoberturaReportGeneratorTest {

	@TempDir
	lateinit var outputDirectory: File

	@Test
	fun `writes the covered classes with their source file`() {
		val report = runGenerator(ignoreUncoveredClasses = false).readText()

		assertThat(report).contains("""<!DOCTYPE coverage SYSTEM "http://cobertura.sourceforge.net/xml/coverage-04.dtd">""")
		// The test classes are in the default package, so the file name is the whole source file path.
		assertThat(report).contains("""<class name="TestClass" filename="TestClass.java"""")
		assertThat(report).contains("""<method name="foo" signature="()V"""")
		assertThat(report).contains("""hits="1"""")
	}

	@Test
	fun `omits uncovered classes if requested`() {
		val report = runGenerator(ignoreUncoveredClasses = true).readText()

		assertThat(report).contains("TestClass")
		assertThat(report).doesNotContain("TestClassTwo")
	}

	@Test
	fun `rejects a report without any coverage`() {
		assertThatThrownBy {
			runGenerator(ignoreUncoveredClasses = false, filter = ClasspathWildcardIncludeFilter("some.package.*", null))
		}.isExactlyInstanceOf(EmptyReportException::class.java)
	}

	private fun runGenerator(
		ignoreUncoveredClasses: Boolean,
		filter: ClasspathWildcardIncludeFilter = ClasspathWildcardIncludeFilter("*", null)
	): File {
		val outputFile = File(outputDirectory, "cobertura-coverage.xml")
		CoberturaReportGenerator(
			listOf(CLASS_FILE_FOLDER),
			filter,
			EDuplicateClassFileBehavior.FAIL,
			ignoreUncoveredClasses,
			Mockito.mock()
		).convertSingleDumpToReport(createDump(), outputFile)
		return outputFile
	}

	/** Creates a dump that marks all probes of `TestClass` as executed. */
	private fun createDump(): Dump {
		val classId = CRC64.classId(Files.readAllBytes(File(CLASS_FILE_FOLDER, "TestClass.class").toPath()))
		val store = ExecutionDataStore()
		store.put(ExecutionData(classId, "TestClass", booleanArrayOf(true, true, true)))
		return Dump(SessionInfo("session-id", 124L, 125L), store)
	}

	companion object {
		/**
		 * The compiled test classes are shared with the JaCoCo report generator tests, which is why they live in that
		 * test's data folder rather than in one of this test's own.
		 */
		private val CLASS_FILE_FOLDER =
			File("test-data/com.teamscale.report.jacoco/ignore-uncovered-classes")
	}
}
