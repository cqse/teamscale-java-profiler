package com.teamscale.test_impacted.engine.executor

import com.teamscale.client.TestWithClusterId.Companion.fromClusteredTestDetails
import com.teamscale.test_impacted.engine.ImpactedTestEngine
import com.teamscale.test_impacted.test_descriptor.TestDescriptorUtils.getAvailableTests
import org.junit.platform.engine.TestDescriptor
import java.util.*

/**
 * Test sorter that requests impacted tests from Teamscale and rewrites the [TestDescriptor] to take the returned
 * order into account when executing the tests.
 */
class ImpactedTestsSorter(private val impactedTestsProvider: ImpactedTestsProvider) : ITestSorter {

	override fun selectAndSort(testDescriptor: TestDescriptor) {
		val availableTests = getAvailableTests(testDescriptor)

		val testClusters = impactedTestsProvider.getImpactedTestsFromTeamscale(
			availableTests.testList
				.map { fromClusteredTestDetails(it, impactedTestsProvider.partition) })

		if (testClusters == null) {
			ImpactedTestEngine.LOG.fine { "Falling back to execute all!" }
			return
		}

		val testRepresentatives = Collections.newSetFromMap<TestDescriptor>(IdentityHashMap())
		val seenDescriptors = Collections.newSetFromMap<TestDescriptor>(IdentityHashMap())

		val allTests = testClusters.asSequence()
			.flatMap { it.tests?.asSequence() ?: emptySequence() }

		allTests.forEach { test ->
			val uniqueId = availableTests.convertToUniqueId(test)
			if (!uniqueId.isPresent) {
				ImpactedTestEngine.LOG.warning {
					"Could not map impacted test '${test.testName}' (from Teamscale) to a local unique ID." +
							" Falling back to executing all tests." +
							" Check that the local test set matches the one indexed in Teamscale for this commit."
				}
				return
			}
			val availableTest = testDescriptor.findByUniqueId(uniqueId.get())
			if (!availableTest.isPresent) {
				ImpactedTestEngine.LOG.warning {
					"Impacted test '${test.testName}' was mapped to a unique ID" +
							" but no matching test descriptor exists in the current test hierarchy." +
							" Falling back to executing all tests."
				}
				return
			}
			val descriptor = availableTest.get()
			testRepresentatives.add(descriptor)
			reinsertIntoHierarchy(descriptor, seenDescriptors)
		}

		removeNonImpactedTests(testDescriptor, testRepresentatives)
	}

	private fun removeNonImpactedTests(
		testDescriptor: TestDescriptor,
		impactedTestDescriptors: Set<TestDescriptor>
	) {
		if (testDescriptor in impactedTestDescriptors) return

		testDescriptor.children.toList().forEach { child ->
			removeNonImpactedTests(child, impactedTestDescriptors)
		}

		if (testDescriptor.children.isEmpty() && !testDescriptor.isRoot) {
			testDescriptor.removeFromHierarchy()
		}
	}

	/**
	 * Reinserts the given [testDescriptor] into the hierarchy by walking up the parent chain recursively.
	 * This ensures that parent descriptors are sorted according to the order of their most important child descriptors.
	 */
	private tailrec fun reinsertIntoHierarchy(
		testDescriptor: TestDescriptor,
		seenDescriptors: MutableSet<TestDescriptor>
	) {
		if (!seenDescriptors.add(testDescriptor)) return
		testDescriptor.reinsertIntoParent()
		val parentDescriptor = testDescriptor.parent.orElse(null)
		if (parentDescriptor != null) {
			reinsertIntoHierarchy(parentDescriptor, seenDescriptors)
		}
	}

	/**
	 * Removes the [this@reinsertIntoParent] from its parent and re-inserts it.
	 * This moves the descriptor to the end of the iteration order in the parent's children,
	 * effectively reordering it.
	 */
	private fun TestDescriptor.reinsertIntoParent() {
		parent.ifPresent { parent ->
			parent.removeChild(this)
			parent.addChild(this)
		}
	}
}
