package com.teamscale.jacoco.agent.util

import java.io.IOException
import java.io.OutputStream

/** NOP output stream implementation.  */
class NullOutputStream : OutputStream() {
	override fun write(b: ByteArray, off: Int, len: Int) {
		// to /dev/null
	}

	override fun write(b: Int) {
		// to /dev/null
	}

	@Throws(IOException::class)
	override fun write(b: ByteArray) {
		// to /dev/null
	}
}