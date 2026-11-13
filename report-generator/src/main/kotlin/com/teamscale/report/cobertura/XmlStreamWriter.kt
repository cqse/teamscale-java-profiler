package com.teamscale.report.cobertura

import java.io.BufferedWriter
import java.io.Closeable
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Minimal streaming XML writer for the Cobertura report.
 *
 * JaCoCo's own XML infrastructure is not reused here because it can only emit `PUBLIC` doctype declarations, while
 * Cobertura reports carry a `SYSTEM` one, and because it lives in JaCoCo's `internal` packages, which are explicitly
 * not part of its public API. Writing the elements as a stream instead of building a DOM keeps the memory footprint
 * low, which matters because the agent runs inside the profiled application.
 *
 * @param output The stream the report is written to. Closing this writer closes the stream.
 * @param rootElement The name of the root element, used in the doctype declaration.
 * @param systemId The DTD URL to declare as the system identifier.
 */
internal class XmlStreamWriter(
	output: OutputStream,
	rootElement: String,
	systemId: String
) : Closeable {

	private val writer = BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))

	/** The current nesting depth, used to indent the output. */
	private var depth = 0

	init {
		writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
		writer.write("<!DOCTYPE $rootElement SYSTEM \"$systemId\">\n")
	}

	/**
	 * Writes an element with the given attributes. If [block] is given, it writes the element's children, otherwise
	 * the element is written in self-closing form.
	 */
	fun element(name: String, vararg attributes: Pair<String, Any>, block: (() -> Unit)? = null) {
		writer.write("\t".repeat(depth))
		writer.write("<$name")
		attributes.forEach { (attributeName, value) ->
			writer.write(" $attributeName=\"${value.toString().escapeXml()}\"")
		}
		if (block == null) {
			writer.write("/>\n")
			return
		}
		writer.write(">\n")
		depth++
		block()
		depth--
		writer.write("\t".repeat(depth))
		writer.write("</$name>\n")
	}

	override fun close() {
		writer.close()
	}

	/**
	 * Escapes the characters that must not appear literally in an attribute value. Single quotes need no escaping
	 * because attribute values are always delimited with double quotes here.
	 */
	private fun String.escapeXml() =
		replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
}
