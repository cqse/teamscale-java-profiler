package com.teamscale.profiler.installer.utils

import com.teamscale.profiler.installer.FatalInstallerError
import com.teamscale.profiler.installer.PermissionError
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Utilities for manipulating files during the installation process.  */
object InstallFileUtils {
	/** Makes the given path world-readable.  */
	@Throws(FatalInstallerError::class)
	fun makeReadable(path: Path) {
		if (path.toFile().setReadable(true, false)) return
		throw PermissionError(
			"Failed to make $path readable. Please check file permissions."
		)
	}

	/** Creates the given directory, handling errors.  */
	@Throws(FatalInstallerError::class)
	fun createDirectory(directory: Path) {
		try {
			Files.createDirectories(directory)
		} catch (e: IOException) {
			throw PermissionError(
				"Cannot create directory '$directory': ${e.message}." +
						" Check that the parent directory exists and that you have write permissions" +
						" (try running the installer as root/Administrator).",
				e
			)
		}
	}
}
