package com.teamscale.report.jacoco

import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import com.teamscale.test.zip
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jacoco.core.data.ExecutionDataStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito
import java.io.EOFException
import java.io.File
import java.io.IOException

/** Tests that [FilteringAnalyzer] filters class files by their class name, independent of their location. */
class FilteringAnalyzerTest {

	@TempDir
	private lateinit var tempDir: File

	private val classBytes = resourceBytes("FilteringAnalyzerTest.class")

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
		val locations = mapOf(
			"build/classes/main/com/teamscale/report/jacoco/FilteringAnalyzerTest.class" to classBytes,
			"flat/FilteringAnalyzerTest.class" to classBytes,
			"app.jar" to zip("BOOT-INF/classes/com/teamscale/report/jacoco/FilteringAnalyzerTest.class" to classBytes),
			"app.war" to zip("WEB-INF/classes/com/teamscale/report/jacoco/FilteringAnalyzerTest.class" to classBytes),
		)
		locations.forEach { (path, content) -> tempDir.resolve(path).write(content) }
		val filter = ClasspathWildcardIncludeFilter(includes, excludes)

		val includedLocations = locations.keys.filter { analyze(filter, file = tempDir.resolve(it)).isNotEmpty() }

		assertThat(includedLocations).isEqualTo(if (expectedIncluded) locations.keys.toList() else emptyList())
	}

	/** Like JaCoCo, nested classes are matched with their `$`. */
	@Test
	fun matchesNestedClassesWithDollar() {
		tempDir.resolve("Outer.class").write(classBytes)
		tempDir.resolve("Nested.class").write(resourceBytes("FilteringAnalyzerTest\$Nested.class"))

		assertThat(analyze(ClasspathWildcardIncludeFilter("com.teamscale.report.jacoco.FilteringAnalyzerTest$*", null)))
			.containsExactly("com/teamscale/report/jacoco/FilteringAnalyzerTest\$Nested")
	}

	/** A JAR with a shell script in front, e.g. a Spring Boot executable JAR, must be analyzed like a plain JAR. */
	@Test
	fun analyzesExecutableJars() {
		tempDir.resolve("app.jar").write(resourceBytes("/com/teamscale/report/util/spring-boot-executable-example.jar"))

		assertThat(analyze(ClasspathWildcardIncludeFilter("org.springframework.boot.loader.jar.*", null)))
			.contains("org/springframework/boot/loader/jar/Bytes")
			.allMatch { it.startsWith("org/springframework/boot/loader/jar/") }
	}

	/** A class file too new for JaCoCo must be logged and skipped instead of failing the whole report. */
	@Test
	fun skipsUnsupportedClassFiles() {
		tempDir.resolve("Future.class").write(withUnsupportedVersion(classBytes))
		tempDir.resolve("Supported.class").write(classBytes)
		val logger = Mockito.mock<ILogger>()

		assertThat(analyze(ClasspathWildcardIncludeFilter(null, null), logger))
			.containsExactly("com/teamscale/report/jacoco/FilteringAnalyzerTest")
		Mockito.verify(logger).error(Mockito.contains("Unsupported class file"), Mockito.any(Throwable::class.java))
	}

	/** A class file too new for JaCoCo must be skipped without an error if it is excluded, as the error recommends. */
	@Test
	fun skipsExcludedUnsupportedClassFilesWithoutError() {
		tempDir.resolve("Future.class").write(withUnsupportedVersion(classBytes))
		val logger = Mockito.mock<ILogger>()

		assertThat(analyze(ClasspathWildcardIncludeFilter(null, "com.teamscale.*"), logger)).isEmpty()
		Mockito.verify(logger).debug(Mockito.contains("Excluding class file"))
		Mockito.verifyNoMoreInteractions(logger)
	}

	/**
	 * A class file whose class name cannot be read must be logged and skipped instead of failing the whole report,
	 * since it may be excluded.
	 */
	@Test
	fun skipsCorruptClassFiles() {
		// Cuts the class file in its constant pool, which holds the class name
		tempDir.resolve("Corrupt.class").write(classBytes.copyOf(40))
		tempDir.resolve("Supported.class").write(classBytes)
		val logger = Mockito.mock<ILogger>()

		assertThat(analyze(ClasspathWildcardIncludeFilter(null, null), logger))
			.containsExactly("com/teamscale/report/jacoco/FilteringAnalyzerTest")
		Mockito.verify(logger).warn(Mockito.contains("Corrupt.class"), Mockito.any(Throwable::class.java))
	}

	/** An archive that JaCoCo cannot read must be reported with its location, the cause and a hint. */
	@Test
	fun reportsUnreadableArchives() {
		val jar = zip("com/teamscale/report/jacoco/FilteringAnalyzerTest.class" to classBytes)
		// Cuts the JAR in the middle of the class file's data
		tempDir.resolve("app.jar").write(jar.copyOf(jar.size / 2))

		assertThatThrownBy { analyze(ClasspathWildcardIncludeFilter(null, null)) }
			.isInstanceOf(IOException::class.java)
			.hasMessageContaining("app.jar@com/teamscale/report/jacoco/FilteringAnalyzerTest.class")
			.hasMessageContaining("Verify that the file is a readable")
			.hasCauseInstanceOf(EOFException::class.java)
	}

	private fun analyze(
		filter: ClasspathWildcardIncludeFilter,
		logger: ILogger = Mockito.mock<ILogger>(),
		file: File = tempDir
	): List<String> {
		val analyzedClasses = mutableListOf<String>()
		FilteringAnalyzer(ExecutionDataStore(), { analyzedClasses.add(it.name) }, filter, logger).analyzeAll(file)
		return analyzedClasses
	}

	private fun File.write(content: ByteArray) {
		parentFile.mkdirs()
		writeBytes(content)
	}

	private fun resourceBytes(name: String) = javaClass.getResourceAsStream(name)!!.use { it.readBytes() }

	/** Returns a copy of the given class file with a major version that is too new for JaCoCo. */
	private fun withUnsupportedVersion(classBytes: ByteArray): ByteArray {
		val unsupportedClassBytes = classBytes.copyOf()
		// Bytes 6 and 7 hold the class file's major version
		unsupportedClassBytes[6] = 0x7F
		unsupportedClassBytes[7] = 0x7F
		return unsupportedClassBytes
	}

	/** A nested class to test the matching of nested class names. */
	private class Nested
}
