package com.teamscale.test_impacted.engine.executor

import com.teamscale.test_impacted.test_descriptor.ClassTemplateRegistry
import org.junit.platform.engine.TestDescriptor

/**
 * NOP sorter that does nothing for when the engine is configured to only collect testwise coverage, but not using
 * Teamscale to select or prioritize tests.
 */
class NOPTestSorter : ITestSorter {
	override fun selectAndSort(testDescriptor: TestDescriptor, classTemplateRegistry: ClassTemplateRegistry) {
		// Nothing to do
	}
}
