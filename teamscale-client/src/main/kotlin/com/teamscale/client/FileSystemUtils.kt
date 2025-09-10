package com.teamscale.client

import java.io.*
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.regex.Pattern

/**
 * File system utilities.
 */
object FileSystemUtils {
	/** Unix file path separator  */
	private const val UNIX_SEPARATOR = '/'

	/** Windows file path separator  */
	private const val WINDOWS_SEPARATOR = '\\'

	/**
	 * Returns a list of all files and directories contained in the given directory and all subdirectories matching the
	 * filter provided. The given directory itself is not included in the result.
	 *
	 *
	 * The file filter may or may not exclude directories.
	 *
	 *
	 * This method knows nothing about (symbolic and hard) links, so care should be taken when traversing directories
	 * containing recursive links.
	 *
	 * @param directory the directory to start the search from. If this is null or the directory does not exist, an
	 * empty list is returned.
	 * @param filter    the filter used to determine whether the result should be included. If the filter is null, all
	 * files and directories are included.
	 * @return the list of files found (the order is determined by the file system).
	 */
	@JvmStatic
	fun listFilesRecursively(directory: File?, filter: FileFilter?): List<File> {
		if (directory == null || !directory.isDirectory) {
			return emptyList()
		}
		val result = arrayListOf<File>()
		listFilesRecursively(directory, result, filter)
		return result
	}

	/**
	 * Returns the extension of the file.
	 *
	 * @return File extension, i.e. "java" for "FileSystemUtils.java", or
	 * `null`, if the file has no extension (i.e. if a filename
	 * contains no '.'), returns the empty string if the '.' is the filename's last character.
	 */
	@JvmStatic
	fun getFileExtension(file: File): String? {
		val name = file.name
		val posLastDot = name.lastIndexOf('.')
		if (posLastDot < 0) {
			return null
		}
		return name.substring(posLastDot + 1)
	}

	/**
	 * Finds all files and directories contained in the given directory and all subdirectories matching the filter
	 * provided and put them into the result collection. The given directory itself is not included in the result.
	 *
	 *
	 * This method knows nothing about (symbolic and hard) links, so care should be taken when traversing directories
	 * containing recursive links.
	 *
	 * @param directory the directory to start the search from.
	 * @param result    the collection to add to all files found.
	 * @param filter    the filter used to determine whether the result should be included. If the filter is null, all
	 * files and directories are included.
	 */
	private fun listFilesRecursively(directory: File, result: MutableCollection<File>, filter: FileFilter?) {
		val files = directory.listFiles()
			?: // From the docs of `listFiles`:
			// 		"If this abstract pathname does not denote a directory, then this method returns null."
			// Based on this, it seems to be ok to just return here without throwing an exception.
			return

		for (file in files) {
			if (file.isDirectory) {
				listFilesRecursively(file, result, filter)
			}
			if (filter == null || filter.accept(file)) {
				result.add(file)
			}
		}
	}

	/**
	 * Replace platform dependent separator char with forward slashes to create system-independent paths.
	 */
	@JvmStatic
	fun normalizeSeparators(path: String) =
		path.replace(File.separatorChar, UNIX_SEPARATOR)

	/**
	 * Copy an input stream to an output stream. This does *not* close the
	 * streams.
	 *
	 * @param input
	 * input stream
	 * @param output
	 * output stream
	 * @return number of bytes copied
	 * @throws IOException
	 * if an IO exception occurs.
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun copy(input: InputStream, output: OutputStream): Int {
		val buffer = ByteArray(1024)
		var size = 0
		var len: Int
		while ((input.read(buffer).also { len = it }) > 0) {
			output.write(buffer, 0, len)
			size += len
		}
		return size
	}

	/**
	 * Checks if a directory exists and is writable. If not it creates the directory
	 * and all necessary parent directories.
	 *
	 * @throws IOException
	 * if directories couldn't be created.
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun ensureDirectoryExists(directory: File) {
		if (!directory.exists() && !directory.mkdirs()) {
			throw IOException("Couldn't create directory: " + directory)
		}
		if (directory.exists() && directory.canWrite()) {
			return
		}
		// Something is wrong. Either the directory does not exist yet, or it is not
		// writable (yet?). We had a case on a Windows OS where the directory was not
		// writable in a very small fraction of the calls. We assume this was because
		// the directory was not "ready" yet although mkdirs returned.
		val start = Instant.now()
		while ((!directory.exists() || !directory.canWrite()) && start.until(Instant.now(), ChronoUnit.MILLIS) < 100) {
			try {
				Thread.sleep(10)
			} catch (e: InterruptedException) {
				// just continue
			}
		}
		if (!directory.exists()) {
			throw IOException("Temp directory " + directory + " could not be created.")
		}
		if (!directory.canWrite()) {
			throw IOException("Temp directory " + directory + " exists, but is not writable.")
		}
	}

	/** Read file content into a string using UTF-8 encoding. */
	@JvmStatic
	@Throws(IOException::class)
	fun readFileUTF8(file: File): String {
		return file.readText()
	}

	/** Read file content into a byte array.  */
	@JvmStatic
	@Throws(IOException::class)
	fun readFileBinary(file: File): ByteArray {
		return file.readBytes()
	}

	/**
	 * Returns a safe filename that can be used for downloads. Replaces everything
	 * that is not a letter or number with "-".
	 *
	 *
	 * Attention: This replaces dots, including the file-end-separator.
	 * `toSafeFilename("a.c")=="a-c"`
	 */
	@JvmStatic
	fun toSafeFilename(name: String): String {
		var name = name
		name = name.replace("\\W+".toRegex(), "-")
		name = name.replace("[-_]+".toRegex(), "-")
		return name
	}

	/**
	 * Replaces the file name of the given path with the given new extension.
	 * Returns the newFileName if the file denoted by the uniform path does not
	 * contain a '/'. This method assumes that folders are separated by '/' (uniform
	 * paths).
	 *
	 *
	 * Examples:
	 *
	 *  * `replaceFilePathFilenameWith("xx", "yy")` returns
	 * `"yy"`
	 *  * `replaceFilePathFilenameWith("xx/zz", "yy")` returns *
	 * `"xx/yy"`
	 *  * `replaceFilePathFilenameWith("xx/zz/", "yy")` returns *
	 * `"xx/zz/yy"`
	 *  * `replaceFilePathFilenameWith("", "yy")` returns *
	 * `"yy"`
	 *
	 */
	@JvmStatic
	fun replaceFilePathFilenameWith(uniformPath: String, newFileName: String): String {
		val folderSepIndex = uniformPath.lastIndexOf('/')

		if (uniformPath.endsWith("/")) {
			return uniformPath + newFileName
		} else if (folderSepIndex == -1) {
			return newFileName
		}
		return uniformPath.take(folderSepIndex) + "/" + newFileName
	}

	/**
	 * Write string to a file with UTF8 encoding. This ensures all directories
	 * exist.
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun writeFileUTF8(file: File, content: String) {
		file.writeText(content)
	}

	/**
	 * Checks if a string is a valid path. It will return false when the path is
	 * invalid on the current platform e.g. because of any non-allowed characters or
	 * because the path schema is for Windows (D:\test) but runs under Linux.
	 */
	@JvmStatic
	fun isValidPath(path: String): Boolean {
		try {
			Paths.get(path)
		} catch (ex: InvalidPathException) {
			return false
		}

		// Split at the default platform separators and check whether there remain any
		// separator characters in the path segments
		return Arrays.stream(path.split(Pattern.quote(File.separator).toRegex()).dropLastWhile { it.isEmpty() }
			.toTypedArray())
			.noneMatch { pathSegment: String? ->
				pathSegment!!.contains(WINDOWS_SEPARATOR.toString())
						|| pathSegment.contains(UNIX_SEPARATOR.toString())
			}
	}

	/** Reads properties from a properties file.  */
	@JvmStatic
	@Throws(IOException::class)
	fun readProperties(propertiesFile: File): Properties {
		propertiesFile.inputStream().use { stream ->
			val props = Properties()
			props.load(stream)
			return props
		}
	}

	/**
	 * Read file content into a list of lines (strings) using UTF-8 encoding.
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun readLinesUTF8(file: File): List<String> {
		return file.readLines()
	}

	/**
	 * Copy all files specified by a file filter from one directory to another. This
	 * automatically creates all necessary directories.
	 *
	 * @param fileFilter
	 * filter to specify file types. If all files should be copied, use
	 * [FileOnlyFilter].
	 * @return number of files copied
	 */
	@JvmStatic
	@Throws(IOException::class)
	fun copyFiles(sourceDirectory: File, targetDirectory: File?, fileFilter: FileFilter?): Int {
		val files: List<File> = listFilesRecursively(sourceDirectory, fileFilter)

		var fileCount = 0
		for (sourceFile in files) {
			if (sourceFile.isFile()) {
				val path = sourceFile.absolutePath
				val index = sourceDirectory.absolutePath.length
				val newPath = path.substring(index)
				val targetFile = File(targetDirectory, newPath)
				sourceFile.copyTo(targetFile, overwrite = true)
				fileCount++
			}
		}
		return fileCount
	}

	/**
	 * Recursively delete directories and files. This method ignores the return
	 * value of delete(), i.e. if anything fails, some files might still exist.
	 */
	@JvmStatic
	fun deleteRecursively(directory: File) {
		requireNotNull(directory) { "Directory may not be null." }

		val filesInDirectory = directory.listFiles()
		if (filesInDirectory == null) {
			if (!directory.exists()) {
				// If filesInDirectory is null, that could have two reasons: Either
				// directory.isInvalid() is true, or there is a low-level IO error that is not
				// wrapped in an exception. We can't precisely distinguish the cases. But
				// directory.exists() checks directory.isInvalid(), and if the directory does
				// not exist, our job is actually done.
				return
			}
			throw IllegalArgumentException(directory.getAbsolutePath() + " is not a valid directory.")
		}

		for (entry in filesInDirectory) {
			if (entry.isDirectory()) {
				deleteRecursively(entry)
			}
			entry.delete()
		}
		directory.delete()
	}
}
