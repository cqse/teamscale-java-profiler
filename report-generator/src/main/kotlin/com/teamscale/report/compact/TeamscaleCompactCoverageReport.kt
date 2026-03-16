package com.teamscale.report.compact

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.json.JsonMapper
import com.teamscale.report.util.CompactLines
import com.teamscale.report.util.LineRangeDeserializer
import com.teamscale.report.util.LineRangeSerializer
import java.io.OutputStream

/**
 * Teamscale Compact Coverage report on a per-file-based granularity to reduce the amount of data
 * sent to and processed by Teamscale.
 *
 * See [Teamscale Compact Coverage Documentation](https://docs.teamscale.com/reference/upload-formats-and-samples/teamscale-compact-coverage/).
 *
 * @property version Version number for the Teamscale Compact Coverage report. Default is version 1.
 * @property coverage Coverage information on an aggregated per-file granularity.
 */
data class TeamscaleCompactCoverageReport @JsonCreator constructor(
	@JsonProperty("version") val version: Int,
	@JsonProperty("coverage") val coverage: List<CompactCoverageFileInfo>
) {
	/** Serializes the compact coverage into a JSON report and writes it into the given output stream. */
	fun writeTo(output: OutputStream) {
		return JsonMapper.builder()
			.defaultPropertyInclusion(JsonInclude.Value.ALL_NON_NULL).build()
			.writeValue(output, this)
	}

	/**
	 * Describes the coverage data for a single file.
	 * All lists of lines are represented by strings, possibly including ranges (denoted by '-').
	 */
	data class CompactCoverageFileInfo @JsonCreator constructor(
		/** The file path of the covered file. */
		@JsonProperty("filePath")
		val filePath: String,

		/** All fully covered lines */
		@JsonProperty("fullyCoveredLines")
		@JsonSerialize(using = LineRangeSerializer::class)
		@JsonDeserialize(using = LineRangeDeserializer::class)
		val fullyCoveredLines: CompactLines = CompactLines(),

		/** All partially covered lines */
		@JsonProperty("partiallyCoveredLines")
		@JsonSerialize(using = LineRangeSerializer::class)
		@JsonDeserialize(using = LineRangeDeserializer::class)
		val partiallyCoveredLines: CompactLines? = CompactLines(),

		/**
		 * All uncovered lines covered lines. This is not used anywhere in here,
		 * but was added for completeness with the spec.
		 */
		@JsonProperty("uncoveredLines")
		@JsonSerialize(using = LineRangeSerializer::class)
		@JsonDeserialize(using = LineRangeDeserializer::class)
		val uncoveredLines: CompactLines? = null
	)
}
