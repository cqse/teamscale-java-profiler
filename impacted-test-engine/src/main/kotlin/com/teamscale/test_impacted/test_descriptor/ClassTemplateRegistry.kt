package com.teamscale.test_impacted.test_descriptor

import com.teamscale.test_impacted.test_descriptor.TestDescriptorUtils.isClassTemplate
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId

/**
 * Remembers the tests of the `@ParameterizedClass`es in the discovered test tree.
 *
 * The JUnit platform prunes the tests of a `@ParameterizedClass` out of the test tree right after discovery and only
 * re-registers them, once per parameter set, while the class is being executed. They are therefore no longer visible
 * when the engine collects the available tests, which is why we have to record them while they are still there.
 */
class ClassTemplateRegistry {
	private val testsByClassTemplate = mutableMapOf<UniqueId, List<TestDescriptor>>()

	/**
	 * Records the tests of all `@ParameterizedClass`es below the given descriptor. Must be called during discovery,
	 * i.e. before the JUnit platform prunes the test tree.
	 */
	fun record(testDescriptor: TestDescriptor) {
		if (testDescriptor.isClassTemplate() && testDescriptor.children.isNotEmpty()) {
			testsByClassTemplate[testDescriptor.uniqueId] = testDescriptor.children.toList()
		}
		testDescriptor.children.forEach { record(it) }
	}

	/**
	 * Returns the tests that were recorded for the given `@ParameterizedClass` during discovery, falling back to its
	 * current children if nothing was recorded for it.
	 */
	fun testsOf(classTemplate: TestDescriptor): Collection<TestDescriptor> =
		testsByClassTemplate[classTemplate.uniqueId] ?: classTemplate.children
}
