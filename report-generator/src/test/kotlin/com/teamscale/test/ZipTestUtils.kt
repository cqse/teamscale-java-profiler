package com.teamscale.test

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Creates a ZIP archive (e.g. a JAR or WAR) with the given entries, each given by its name and content. */
fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
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
