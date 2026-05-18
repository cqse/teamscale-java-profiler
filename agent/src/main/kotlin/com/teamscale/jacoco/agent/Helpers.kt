package com.teamscale.jacoco.agent

import kotlin.time.measureTime

fun benchmark(name: String, action: () -> Unit) =
	measureTime { action() }.also { duration -> Main.logger.debug("$name took $duration") }