package com.teamscale.jacoco.agent.options

import java.io.File
import java.io.IOException

/** Handles parsing a .txt file with classpath pattern separated by newlines. */
object ClasspathUtils {

	/** Replaces all txt files in the given list with the file names written in the txt file separated by new lines. */
	@Throws(IOException::class)
	fun resolveClasspathTextFiles(
		key: String,
		filePatternResolver: FilePatternResolver,
		patterns: List<String>
	): List<File> {
		val (txtFiles, classDirOrJarFiles) = patterns.flatMap { pattern ->
			filePatternResolver.resolveToMultipleFiles(key, pattern)
		}.partition { file ->
			file.name.endsWith(".txt")
		}

		val resolvedFromTxt = txtFiles.flatMap { txtFile ->
			resolveClassPathEntries(key, filePatternResolver, txtFile)
		}

		return classDirOrJarFiles + resolvedFromTxt
	}

	@Throws(IOException::class)
	private fun resolveClassPathEntries(
		key: String,
		filePatternResolver: FilePatternResolver,
		txtFile: File
	): List<File> {
		val filePaths = try {
			txtFile.readLines()
		} catch (e: IOException) {
			throw IOException(
				"Failed to read class path entries from the provided $txtFile in the `$key` option.", e
			)
		}

		return filePaths.flatMap { filePath ->
			filePatternResolver.resolveToMultipleFiles(key, filePath)
		}
	}
}