package com.teamscale.test_impacted.test_descriptor.samples

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource

/**
 * Sample tests that are only discovered explicitly by
 * [com.teamscale.test_impacted.test_descriptor.JupiterClassTemplateTest]. They are excluded from this project's own
 * test run, see `impacted-test-engine/build.gradle.kts`.
 */
class SampleTestClass {
	@Test
	fun testOuter() {
		// Nothing to do, we are only interested in the shape of the discovered test tree.
	}

	/** A plain `@Nested` test class, see [SampleTestClass]. */
	@Nested
	inner class PlainNested {
		@Test
		fun testPlain() {
			// See above.
		}
	}

	/** A `@Nested` `@ParameterizedClass`, see [SampleTestClass]. */
	@Nested
	@ParameterizedClass
	@ValueSource(strings = ["a", "b"])
	inner class NestedParameterized {
		/** The parameter the enclosing class is instantiated with. */
		@Parameter
		@JvmField
		var value: String = ""

		@Test
		fun testOne() {
			// See above.
		}

		@Test
		fun testTwo() {
			// See above.
		}
	}
}

/** A top-level `@ParameterizedClass`, see [SampleTestClass]. */
@ParameterizedClass
@ValueSource(ints = [1, 2, 3])
class SampleParameterizedTestClass {
	/** The parameter the enclosing class is instantiated with. */
	@Parameter
	@JvmField
	var value: Int = 0

	@Test
	fun testA() {
		// See above.
	}

	@Test
	fun testB() {
		// See above.
	}
}
