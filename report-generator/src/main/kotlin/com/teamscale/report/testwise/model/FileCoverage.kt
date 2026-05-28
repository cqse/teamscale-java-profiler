package com.teamscale.report.testwise.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/** Holds coverage of a single file.  */
data class FileCoverage @JsonCreator constructor(
	/** The name of the file.  */
	@JvmField @param:JsonProperty("fileName") val fileName: String,
	/** A list of line ranges that have been covered.  */
	@JvmField @param:JsonProperty("coveredLines") val coveredLines: String
)
