package com.teamscale.systemtest

import com.teamscale.client.EReportFormat
import com.teamscale.test.commons.SystemTestUtils
import com.teamscale.test.commons.SystemTestUtils.dumpCoverage
import com.teamscale.test.commons.TeamscaleMockServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import systemundertest.SystemUnderTest

/**
 * Tests that the agent handles class reloads (for example, JBoss reload) correctly. When a class is reloaded with
 * different bytecode, JaCoCo assigns a different class ID (CRC64). With a shared report generator, this would cause
 * an `IllegalStateException: Can't add different class with same name` because the `CoverageBuilder` still holds the
 * old class ID from a previous dump. The fix is to create a fresh report generator per dump so the `CoverageBuilder`
 * never carries state across dumps.
 *
 * The reload is simulated by loading a modified copy of the [SystemUnderTest] class through a custom classloader,
 * which causes JaCoCo to create execution data with a different class ID for the same class name.
 */
class ReloadDetectionSystemTest {

	@Test
	fun dumpAfterClassReloadDoesNotCrash() {
		val teamscaleMockServer = TeamscaleMockServer(SystemTestUtils.TEAMSCALE_PORT).acceptingReportUploads()

		// First dump: generate coverage
		SystemUnderTest().foo()
		dumpCoverage(SystemTestUtils.AGENT_PORT)

		// Simulate reload: load the same class with modified bytecode via a custom classloader.
		// This causes JaCoCo's transformer to instrument the new version, creating an execution data
		// entry with a different CRC64 for the same class name.
		loadModifiedSystemUnderTest()

		// Generate more coverage and dump again. Because the agent creates a fresh report generator
		// (and thus a fresh CoverageBuilder) per dump, the changed class ID does not cause a crash.
		SystemUnderTest().bar()
		dumpCoverage(SystemTestUtils.AGENT_PORT)

		val sessions = teamscaleMockServer.getSessions()
		assertThat(sessions).hasSize(2)
		for (session in sessions) {
			assertThat(session.getReports(EReportFormat.JACOCO)).isNotNull.isNotEmpty
		}

		teamscaleMockServer.shutdown()
	}

	/**
	 * Loads a modified version of [SystemUnderTest] using a fresh classloader. The bytecode is read from the
	 * classpath, and a byte is changed so that JaCoCo computes a different class ID (CRC64).
	 */
	private fun loadModifiedSystemUnderTest() {
		val resourcePath = "/" + SystemUnderTest::class.java.name.replace('.', '/') + ".class"
		val originalBytes = SystemUnderTest::class.java.getResourceAsStream(resourcePath)?.readBytes()
			?: error("Could not read class file for ${SystemUnderTest::class.java.name}")

		// Modify the source file name attribute in the constant pool to produce different bytecode
		// (and thus a different CRC64). The source file name is purely informational and does not
		// affect class loading or execution. We change ".kt" to ".kx".
		val modifiedBytes = originalBytes.copyOf()
		val marker = "SystemUnderTest.kt".toByteArray()
		val markerIndex = findSubArray(modifiedBytes, marker)
		check(markerIndex >= 0) { "Could not find source file name in class bytecode" }
		// Change the last byte of ".kt" to produce ".kx"
		modifiedBytes[markerIndex + marker.size - 1] = 'x'.code.toByte()

		val classLoader = object : ClassLoader(ClassLoader.getSystemClassLoader()) {
			fun defineModifiedClass(): Class<*> {
				return defineClass("systemundertest.SystemUnderTest", modifiedBytes, 0, modifiedBytes.size)
			}
		}
		classLoader.defineModifiedClass()
	}

	private fun findSubArray(haystack: ByteArray, needle: ByteArray): Int {
		outer@ for (i in 0..haystack.size - needle.size) {
			for (j in needle.indices) {
				if (haystack[i + j] != needle[j]) continue@outer
			}
			return i
		}
		return -1
	}
}
