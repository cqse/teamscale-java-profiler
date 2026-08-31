package com.teamscale.test_impacted.engine.executor

import com.teamscale.test_impacted.test_descriptor.ClassTemplateRegistry
import org.junit.platform.engine.TestDescriptor

/** Interface for implementing different ways of ordering tests.  */
interface ITestSorter {
	/**
	 * Removes any tests from the test descriptor that should not be executed and changes the execution order of the
	 * remaining tests.
	 */
	fun selectAndSort(testDescriptor: TestDescriptor, classTemplateRegistry: ClassTemplateRegistry)
}
