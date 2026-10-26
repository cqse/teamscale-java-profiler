package com.teamscale.report.jacoco

import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.assertj.core.api.Assertions.assertThat
import org.jacoco.core.data.ExecutionDataStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Tests that [FilteringAnalyzer] filters class files by their class name, independent of their location. */
class FilteringAnalyzerTest {

	@TempDir
	private lateinit var tempDir: File

	private val classBytes = classBytes("FilteringAnalyzerTest.class")

	/**
	 * The location of a class file must not influence whether it is included: class directories may contain further
	 * directories above the package structure, class files need not be in a directory matching their package (e.g.
	 * in Kotlin projects) and archives may contain classes below e.g. `BOOT-INF/classes`.
	 */
	@ParameterizedTest
	@CsvSource(
		"com.teamscale.report.*, , true",
		"*com.teamscale.report.*, , true",
		"com.teamscale.report.jacoco.FilteringAnalyzerTest, , true",
		"org.other.*, , false",
		", com.teamscale.*, false",
		"com.teamscale.*, com.teamscale.report.jacoco.*, false",
	)
	fun filtersByClassNameIndependentOfLocation(includes: String?, excludes: String?, expectedIncluded: Boolean) {
		tempDir.resolve("build/classes/main/com/teamscale/report/jacoco/FilteringAnalyzerTest.class").write(classBytes)
		tempDir.resolve("flat/FilteringAnalyzerTest.class").write(classBytes)
		tempDir.resolve("app.jar").write(zip("BOOT-INF/classes/com/teamscale/report/jacoco/FilteringAnalyzerTest.class"))
		tempDir.resolve("app.war").write(zip("WEB-INF/classes/com/teamscale/report/jacoco/FilteringAnalyzerTest.class"))

		val analyzedClasses = analyze(ClasspathWildcardIncludeFilter(includes, excludes))

		assertThat(analyzedClasses).hasSize(if (expectedIncluded) 4 else 0)
	}

	/** Like JaCoCo, nested classes are matched with their `$`. */
	@Test
	fun matchesNestedClassesWithDollar() {
		tempDir.resolve("Outer.class").write(classBytes)
		tempDir.resolve("Nested.class").write(classBytes("FilteringAnalyzerTest\$Nested.class"))

		assertThat(analyze(ClasspathWildcardIncludeFilter("com.teamscale.report.jacoco.FilteringAnalyzerTest$*", null)))
			.containsExactly("com/teamscale/report/jacoco/FilteringAnalyzerTest\$Nested")
	}

	/** A class file too new for JaCoCo must be logged and skipped instead of failing the whole report. */
	@Test
	fun skipsUnsupportedClassFiles() {
		// Bytes 6 and 7 hold the class file's major version
		tempDir.resolve("Future.class").write(classBytes.copyOf().also { it[6] = 0x7F; it[7] = 0x7F })
		tempDir.resolve("Supported.class").write(classBytes)
		val logger = Mockito.mock<ILogger>()

		assertThat(analyze(ClasspathWildcardIncludeFilter(null, null), logger))
			.containsExactly("com/teamscale/report/jacoco/FilteringAnalyzerTest")
		Mockito.verify(logger).error(Mockito.contains("Unsupported class file"), Mockito.any(Throwable::class.java))
	}

	private fun analyze(filter: ClasspathWildcardIncludeFilter, logger: ILogger = Mockito.mock<ILogger>()): List<String> {
		val analyzedClasses = mutableListOf<String>()
		FilteringAnalyzer(ExecutionDataStore(), { analyzedClasses.add(it.name) }, filter, logger).analyzeAll(tempDir)
		return analyzedClasses
	}

	private fun File.write(content: ByteArray) {
		parentFile.mkdirs()
		writeBytes(content)
	}

	private fun classBytes(name: String) = javaClass.getResourceAsStream(name)!!.use { it.readBytes() }

	private fun zip(entryName: String): ByteArray {
		val output = ByteArrayOutputStream()
		ZipOutputStream(output).use { zip ->
			zip.putNextEntry(ZipEntry(entryName))
			zip.write(classBytes)
			zip.closeEntry()
		}
		return output.toByteArray()
	}

	/** A nested class to test the matching of nested class names. */
	private class Nested
}
