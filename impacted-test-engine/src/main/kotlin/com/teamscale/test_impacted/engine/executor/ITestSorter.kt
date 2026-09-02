package com.teamscale.test_impacted.engine.executor

import org.junit.platform.engine.TestDescriptor

/** Interface for implementing different ways of ordering tests.  */
interface ITestSorter {
	/**
	 * Removes any tests from the test descriptor that should not be executed and changes the execution order of the
	 * remaining tests.
	 *
	 * @param availableTests The tests contained in the given [testDescriptor], as determined during discovery.
	 */
	fun selectAndSort(testDescriptor: TestDescriptor, availableTests: AvailableTests)
}
