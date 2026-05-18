package com.teamscale.jacoco.agent.options

import com.teamscale.jacoco.agent.util.TestUtils.cleanAgentCoverageDirectory
import com.teamscale.report.util.CommandLineLogger
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** Tests the [AgentOptions].  */
class FilePatternResolverTest {
	@TempDir
	protected var testFolder: File? = null

	@BeforeEach
	@Throws(IOException::class)
	fun setUp() {
		File(testFolder, "file_with_manifest1.jar").createNewFile()
		File(testFolder, "plugins/inner").mkdirs()
		File(testFolder, "plugins/some_other_file.jar").createNewFile()
		File(testFolder, "plugins/file_with_manifest2.jar").createNewFile()
	}

	/** Tests path resolution with absolute path.  */
	@Test
	@Throws(IOException::class)
	fun testPathResolutionForAbsolutePath() {
		assertInputInWorkingDirectoryMatches(".", testFolder!!.absolutePath, "")
	}

	/** Tests path resolution with relative paths.  */
	@Test
	@Throws(IOException::class)
	fun testPathResolutionForRelativePath() {
		assertInputInWorkingDirectoryMatches(".", ".", "")
		assertInputInWorkingDirectoryMatches("plugins", "../file_with_manifest1.jar", "file_with_manifest1.jar")
	}

	/** Tests path resolution with patterns and relative paths.  */
	@Test
	@Throws(IOException::class)
	fun testPathResolutionWithPatternsAndRelativePaths() {
		assertInputInWorkingDirectoryMatches(".", "plugins/file_*.jar", "plugins/file_with_manifest2.jar")
		assertInputInWorkingDirectoryMatches(".", "*/file_*.jar", "plugins/file_with_manifest2.jar")
		assertInputInWorkingDirectoryMatches("plugins/inner", "..", "plugins")
		assertInputInWorkingDirectoryMatches("plugins/inner", "../s*", "plugins/some_other_file.jar")
	}

	/** Tests path resolution with patterns and absolute paths.  */
	@Test
	@Throws(IOException::class)
	fun testPathResolutionWithPatternsAndAbsolutePaths() {
		assertInputInWorkingDirectoryMatches(
			"plugins", testFolder!!.getAbsolutePath() + "/plugins/file_*.jar",
			"plugins/file_with_manifest2.jar"
		)
	}

	@Throws(IOException::class)
	private fun assertInputInWorkingDirectoryMatches(
		workingDir: String, input: String?,
		expected: String
	) {
		val workingDirectory = File(testFolder, workingDir)
		val actualFile: File = filePatternResolverWithDummyLogger.parsePath("option-name", input, workingDirectory)
			.toFile()
		val expectedFile = File(testFolder, expected)
		Assertions.assertThat(getNormalizedPath(actualFile)).isEqualByComparingTo(getNormalizedPath(expectedFile))
	}

	/** Tests path resolution with incorrect input.  */
	@Test
	fun testPathResolutionWithPatternErrorCases() {
		Assertions.assertThatThrownBy {
			filePatternResolverWithDummyLogger.parsePath(
				"option-name",
				"**.war",
				testFolder
			)
		}.isInstanceOf(IOException::class.java).hasMessageContaining(
			"Invalid path given for option option-name: **.war. The pattern **.war did not match any files in"
		)
	}

	@Test
	@Throws(IOException::class)
	fun resolveToMultipleFilesWithPattern() {
		val files = filePatternResolverWithDummyLogger
			.resolveToMultipleFiles("option-name", "**.jar", testFolder)
		Assertions.assertThat(files).hasSize(3)
	}

	@Test
	@Throws(IOException::class)
	fun resolveToMultipleFilesWithoutPattern() {
		val files = filePatternResolverWithDummyLogger
			.resolveToMultipleFiles("option-name", "plugins/file_with_manifest2.jar", testFolder)
		Assertions.assertThat(files).hasSize(1)
	}

	companion object {
		/** Resolves the path to its absolute normalized path.  */
		private fun getNormalizedPath(file: File) = file.getAbsoluteFile().toPath().normalize()

		private val filePatternResolverWithDummyLogger: FilePatternResolver
			get() = FilePatternResolver(CommandLineLogger())

		/**
		 * Delete created coverage folders
		 */
		@JvmStatic
		@AfterAll
		@Throws(IOException::class)
		fun teardown() {
			cleanAgentCoverageDirectory()
		}
	}
}
