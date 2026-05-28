package com.teamscale.test_impacted.test_descriptor

import org.junit.platform.engine.TestDescriptor

/** Interface for implementation of mappings from [TestDescriptor]s to uniform paths.  */
interface ITestDescriptorResolver {
	/** Returns the uniform path or `null` if no uniform path could be determined.  */
	fun getUniformPath(descriptor: TestDescriptor): String?

	/** Returns the cluster id or `null` if no cluster id could be determined.  */
	fun getClusterId(descriptor: TestDescriptor): String?

	/**
	 * Returns the [org.junit.platform.engine.TestEngine.getId] of the [org.junit.platform.engine.TestEngine]
	 * to use this [ITestDescriptorResolver] for.
	 */
	val engineId: String

	companion object {
		/** Type of the unique id segment of a test descriptor representing a test engine  */
		const val ENGINE_SEGMENT_TYPE = "engine"
	}
}
