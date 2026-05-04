package com.teamscale.report.jacoco.dump

import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfo

/** All data received in one dump.  */
data class Dump(
	val info: SessionInfo,
	val store: ExecutionDataStore
)
