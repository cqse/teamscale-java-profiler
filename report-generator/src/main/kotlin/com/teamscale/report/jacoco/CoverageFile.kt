package com.teamscale.report.jacoco

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Represents a coverage file on disk. The main purpose is to avoid reading the
 * entire file into memory as this dramatically increases the memory footprint
 * of the JVM which might run out of memory because of this.
 *
 * The object internally holds a counter of how many references to the file are
 * currently held. This allows to share the same file for multiple uploads and
 * deleting it once all uploads have succeeded. Use [.acquireReference]
 * to make the object aware that it was passed to another uploader and
 * [.delete] to signal that you no longer intend to access the file.
 */
data class CoverageFile(private val coverageFile: File) {
	private var referenceCounter = 0

	/**
	 * Marks the file as being used by an additional uploader. This ensures that the
	 * file is not deleted until all users have signed via [.delete] that
	 * they no longer intend to access the file.
	 */
	fun acquireReference(): CoverageFile {
		referenceCounter++
		return this
	}

	/**
	 * Copies the coverage File in blocks from the disk to the output stream to
	 * avoid having to read the entire file into memory.
	 */
	@Throws(IOException::class)
	fun copyStream(outputStream: OutputStream) {
		coverageFile.inputStream().use { input ->
			input.copyTo(outputStream)
		}
	}

	/**
	 * Get the filename of the coverage file on disk without its extension
	 */
	val nameWithoutExtension: String
		get() = coverageFile.nameWithoutExtension

	/** Get the filename of the coverage file.  */
	val name: String
		get() = coverageFile.name

	/** Get the filename extension of the coverage file (without leading dot, lowercase).  */
	val extension: String
		get() = coverageFile.extension.lowercase()

	/**
	 * Delete the coverage file from disk
	 */
	@Throws(IOException::class)
	fun delete() {
		referenceCounter--
		if (referenceCounter <= 0) {
			coverageFile.delete()
		}
	}

	/**
	 * Create a [okhttp3.MultipartBody] form body with the contents of the
	 * coverage file.
	 */
	fun createFormRequestBody(): RequestBody =
		coverageFile.asRequestBody(MultipartBody.FORM)

	/**
	 * Get the [java.io.OutputStream] in order to write to the coverage file.
	 *
	 * @throws IOException
	 * If the file did not exist yet and could not be created
	 */
	@get:Throws(IOException::class)
	val outputStream: OutputStream
		get() {
			return runCatching {
				coverageFile.outputStream()
			}.getOrElse {
				throw IOException(
					("Could not create temporary coverage file" + this + ". "
							+ "This is used to cache the coverage file on disk before uploading it to its final destination. "
							+ "This coverage is lost. Please fix the underlying issue to avoid losing coverage."), it
				)
			}
		}

	/**
	 * {@inheritDoc}
	 */
	override fun toString(): String = coverageFile.absolutePath
}
