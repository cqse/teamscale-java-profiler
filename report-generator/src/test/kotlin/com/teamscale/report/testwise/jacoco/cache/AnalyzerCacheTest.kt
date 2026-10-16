package com.teamscale.report.testwise.jacoco.cache

import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jacoco.core.internal.data.CRC64
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Tests for the [AnalyzerCache] class.  */
class AnalyzerCacheTest {

	@TempDir
	private lateinit var tempDir: File

	private val classBytes = javaClass.getResourceAsStream("AnalyzerCacheTest.class")!!.use { it.readBytes() }
	private val innerJar = zip("com/teamscale/report/testwise/jacoco/cache/AnalyzerCacheTest.class" to classBytes)

	private val logger = Mockito.mock<ILogger>()
	private val probesCache = ProbesCache(logger, EDuplicateClassFileBehavior.IGNORE)
	private val analyzer = AnalyzerCache(probesCache, ClasspathWildcardIncludeFilter(null, null), logger)

	/**
	 * An archive may contain JARs, e.g. a WAR contains its libraries and some JARs contain other JARs (e.g. SVNKit).
	 * The analyzer addresses such a JAR as `app.war@WEB-INF/lib/inner.jar`, which is not a file on disk, so the cache
	 * must not try to read it from there.
	 */
	@ParameterizedTest
	@CsvSource("outer.jar, lib/inner.jar", "app.war, WEB-INF/lib/inner.jar")
	fun analyzesClassesInJarsInsideArchives(archiveName: String, jarPath: String) {
		tempDir.resolve(archiveName).writeBytes(zip(jarPath to innerJar))

		assertThat(analyzer.analyzeAll(tempDir)).isEqualTo(1)
		assertThat(probesCache.containsClassId(CRC64.classId(classBytes))).isTrue()
	}

	/** A nested JAR that cannot be read must be reported with its location, so that users can find it. */
	@Test
	fun reportsLocationOfUnreadableNestedJar() {
		val outerJar = zip("lib/inner.jar" to innerJar)
		// Cuts the outer JAR in the middle of the nested JAR's data
		tempDir.resolve("outer.jar").writeBytes(outerJar.copyOf(outerJar.size / 2))

		assertThatThrownBy { analyzer.analyzeAll(tempDir) }
			.isInstanceOf(IOException::class.java)
			.hasMessageContaining("outer.jar@lib/inner.jar")
	}

	private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
		val output = ByteArrayOutputStream()
		ZipOutputStream(output).use { zip ->
			entries.forEach { (name, content) ->
				zip.putNextEntry(ZipEntry(name))
				zip.write(content)
				zip.closeEntry()
			}
		}
		return output.toByteArray()
	}
}
