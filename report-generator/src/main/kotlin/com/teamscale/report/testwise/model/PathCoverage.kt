package com.teamscale.report.testwise.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/** Container for [FileCoverage]s of the same path.  */
data class PathCoverage @JsonCreator constructor(
	/** File system path.  */
	@param:JsonProperty("path") val path: String?,
	/** Files with coverage.  */
	@JvmField @param:JsonProperty("files") val files: List<FileCoverage>
)
