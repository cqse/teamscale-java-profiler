package com.teamscale.report.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.io.IOException

/**
 * Custom Serializer to serialize [CompactLines] to string separated line ranges. See
 * [LineRangeStringParser] for more details.
 */
class LineRangeSerializer private constructor(t: Class<CompactLines>?) : StdSerializer<CompactLines>(t) {

	constructor() : this(null)

	@Throws(IOException::class)
	override fun serialize(value: CompactLines, jsonGen: JsonGenerator, provider: SerializerProvider) {
		val compactLineRanges = LineRangeStringParser.compactifyToRanges(value)
		jsonGen.writeString(compactLineRanges.joinToString(","))
	}

	companion object {
		private const val serialVersionUID = -9191867553019031697L
	}
}
