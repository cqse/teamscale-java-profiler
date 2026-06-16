package com.teamscale.jacoco.agent.options

import com.teamscale.client.AntPatternUtils
import com.teamscale.client.FileSystemUtils.normalizeSeparators
import com.teamscale.report.util.ILogger
import java.io.File
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.PathWalkOption
import kotlin.io.path.walk

/** Helper class to support resolving file paths which may contain Ant patterns.  */
class FilePatternResolver(private val logger: ILogger) {
	/**
	 * Interprets the given pattern as an Ant pattern and resolves it to one or multiple existing [File]s. If the
	 * given path is relative, it is resolved relative to the current working directory.
	 * 
	 * 
	 * Visible for testing only.
	 */
	@Throws(IOException::class)
	fun resolveToMultipleFiles(
		optionName: String?, pattern: String,
		workingDirectory: File = File(".")
	): List<File> {
		if (isPathWithPattern(pattern)) {
			return parseFileFromPattern(optionName, pattern, workingDirectory).allMatchingPaths
				.map { it.toFile() }
		}
		try {
			return listOf<File>(workingDirectory.toPath().resolve(Paths.get(pattern)).toFile())
		} catch (e: InvalidPathException) {
			throw IOException(
				"Invalid path given for option '$optionName': '$pattern' (${e.message})." +
						" Use a normal file system path or an Ant-style pattern.", e
			)
		}
	}

	/**
	 * Interprets the given pattern as an Ant pattern and resolves it to one existing [Path]. If the given path is
	 * relative, it is resolved relative to the given working directory. If more than one file matches the pattern, one
	 * of the matching files is used without any guarantees as to which. The selection is, however, guaranteed to be
	 * deterministic, i.e. if you run the pattern twice and get the same set of files, the same file will be picked each
	 * time.
	 */
	@Throws(IOException::class)
	fun parsePath(
		optionName: String?,
		pattern: String,
		workingDirectory: File = File(".")
	): Path {
		if (isPathWithPattern(pattern)) {
			return parseFileFromPattern(optionName, pattern, workingDirectory).singlePath
		}
		try {
			return workingDirectory.toPath().resolve(Paths.get(pattern))
		} catch (e: InvalidPathException) {
			throw IOException(
				"Invalid path given for option '$optionName': '$pattern' (${e.message})." +
						" Use a normal file system path or an Ant-style pattern.", e
			)
		}
	}

	/** Parses the pattern as an Ant pattern to one or multiple files or directories.  */
	@Throws(IOException::class)
	private fun parseFileFromPattern(
		optionName: String?,
		pattern: String,
		workingDirectory: File
	): FilePatternResolverRun {
		return FilePatternResolverRun(logger, optionName, pattern, workingDirectory).resolve()
	}

	private class FilePatternResolverRun(
		private val logger: ILogger,
		private val optionName: String?,
		private val pattern: String,
		workingDirectory: File
	) {
		private val workingDirectory = workingDirectory.getAbsoluteFile()
		private lateinit var suffixPattern: String
		private lateinit var basePath: Path
		private var matchingPaths = listOf<Path>()

		init {
			splitIntoBasePathAndPattern(pattern)
		}

		/**
		 * Splits the path into a base dir, i.e. the directory-prefix of the path that does not contain any ? or *
		 * placeholders, and a pattern suffix. We need to replace the pattern characters with stand-ins, because ? and *
		 * are not allowed as path characters on windows.
		 */
		fun splitIntoBasePathAndPattern(value: String) {
			val pathWithArtificialPattern = value.replace("?", QUESTION_REPLACEMENT)
				.replace("*", ASTERISK_REPLACEMENT)
			val pathWithPattern = Paths.get(pathWithArtificialPattern)
			var baseDir = pathWithPattern
			while (isPathWithArtificialPattern(baseDir.toString())) {
				baseDir = baseDir.parent
				if (baseDir == null) {
					suffixPattern = value
					basePath = workingDirectory.toPath().resolve("").normalize().toAbsolutePath()
					return
				}
			}

			suffixPattern = baseDir.relativize(pathWithPattern).toString()
				.replace(QUESTION_REPLACEMENT, "?")
				.replace(ASTERISK_REPLACEMENT, "*")
			basePath = workingDirectory.toPath().resolve(baseDir).normalize().toAbsolutePath()
		}

		val singlePath: Path
			/** Returns the result of a resolution as a single Path and warns when multiple paths match.  */
			get() {
				if (matchingPaths.isEmpty()) {
					throw IOException(
						"Invalid path given for option $optionName: ${pattern}. The pattern $suffixPattern did not match any files in ${basePath.toAbsolutePath()}"
					)
				} else if (matchingPaths.size > 1) {
					logger.warn(
						"Multiple files match the pattern $suffixPattern in $basePath for option $optionName! The first one is used, but consider to adjust the pattern to match only one file. Candidates are: " + matchingPaths.joinToString {
							basePath.relativize(it).toString()
						}
					)
				}
				val path = matchingPaths.first().normalize()
				logger.info("Using file $path for option $optionName")
				return path
			}

		val allMatchingPaths: List<Path>
			/** Returns all matched paths after the resolution.  */
			get() {
				if (matchingPaths.isEmpty()) {
					logger.warn(
						"The pattern '$suffixPattern' under '$basePath' (option '$optionName') matched no files." +
							" Check the pattern and the working directory."
					)
				}
				logger.info("Resolved $pattern to ${matchingPaths.size} for option $optionName")
				return matchingPaths
			}

		/**
		 * Resolves the pattern. The results can be retrieved via [singlePath] or [allMatchingPaths].
		 */
		fun resolve(): FilePatternResolverRun {
			val pathRegex = AntPatternUtils.convertPattern(suffixPattern, false)

			try {
				matchingPaths = basePath.walk(PathWalkOption.INCLUDE_DIRECTORIES).filter {
					pathRegex.matcher(normalizeSeparators(basePath.relativize(it).toString())).matches()
				}.sorted().toList()
			} catch (e: IOException) {
				throw IOException(
					"Could not recursively list files in directory $basePath in order to resolve pattern $suffixPattern given for option $optionName",
					e
				)
			}
			return this
		}
	}

	companion object {
		/** Stand-in for the question mark operator.  */
		private const val QUESTION_REPLACEMENT = "!@"

		/** Stand-in for the asterisk operator.  */
		private const val ASTERISK_REPLACEMENT = "#@"

		/** Returns whether the given path contains Ant pattern characters (?,*).  */
		private fun isPathWithPattern(path: String) =
			path.contains("?") || path.contains("*")

		/**
		 * Returns whether the given path contains artificial pattern characters ([QUESTION_REPLACEMENT],
		 * [ASTERISK_REPLACEMENT]).
		 */
		private fun isPathWithArtificialPattern(path: String) =
			path.contains(QUESTION_REPLACEMENT) || path.contains(ASTERISK_REPLACEMENT)
	}
}
