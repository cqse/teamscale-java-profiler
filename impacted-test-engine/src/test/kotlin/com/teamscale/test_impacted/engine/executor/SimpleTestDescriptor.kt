package com.teamscale.test_impacted.engine.executor

import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor

/**
 * Represents a simple implementation of a test descriptor for managing test cases and test containers within a
 * test engine framework. This class is designed to provide functionality for defining the structure and behavior
 * of test descriptors.
 *
 * @constructor Private constructor for creating an instance of this class. Instances can only be created through
 * companion object functions.
 *
 * @param uniqueId The unique identifier for the test descriptor.
 * @param type The type of the test descriptor, represented by [TestDescriptor.Type].
 * @param displayName The display name for the test descriptor.
 */
class SimpleTestDescriptor private constructor(
	uniqueId: UniqueId,
	private val type: TestDescriptor.Type,
	displayName: String
) : AbstractTestDescriptor(uniqueId, displayName) {
	private var shouldSkip = false

	/**
	 * A mutable list that holds dynamic test descriptors for a test case. These tests are registered dynamically
	 * during test execution and can include child tests or nested dynamic executions.
	 */
	val dynamicTests = mutableListOf<TestDescriptor>()

	/**
	 * Represents the execution result of a test, indicating whether the test was successful, failed, or aborted.
	 * This property is updated during the test execution process when the result of the test is determined.
	 */
	var executionResult: TestExecutionResult = TestExecutionResult.successful()
		private set

	override fun getType() = type

	/** Marks the test as being skipped.  */
	fun skip(): SimpleTestDescriptor {
		this.shouldSkip = true
		return this
	}

	/** Whether the test should be skipped.  */
	fun shouldBeSkipped() = shouldSkip

	/** Sets the execution result that the engine should report when simulating the test's execution.  */
	fun result(executionResult: TestExecutionResult): SimpleTestDescriptor {
		this.executionResult = executionResult
		return this
	}

	companion object {
		/**
		 * Creates a [TestDescriptor] for a concrete test case without children. The display name doubles as the
		 * descriptor's legacy reporting name, which the jupiter engine e.g. suffixes with the index of the enclosing
		 * `@ParameterizedClass` invocation.
		 */
		@JvmOverloads
		fun testCase(uniqueId: UniqueId, displayName: String = getSimpleDisplayName(uniqueId)) =
			SimpleTestDescriptor(uniqueId, TestDescriptor.Type.TEST, displayName)

		private fun getSimpleDisplayName(uniqueId: UniqueId) =
			uniqueId.segments[uniqueId.segments.size - 1].value

		/** Creates a [TestDescriptor] for a dynamic test case which registers children during test execution.  */
		fun dynamicTestCase(uniqueId: UniqueId, vararg dynamicTestCases: TestDescriptor): TestDescriptor {
			val simpleTestDescriptor = SimpleTestDescriptor(
				uniqueId, TestDescriptor.Type.CONTAINER_AND_TEST,
				getSimpleDisplayName(uniqueId)
			)
			simpleTestDescriptor.dynamicTests.addAll(listOf(*dynamicTestCases))
			return simpleTestDescriptor
		}

		/**
		 * Creates a [TestDescriptor] for a test container (e.g. a `@ParameterizedClass`) which registers all of its
		 * children dynamically during test execution.
		 */
		fun dynamicTestContainer(uniqueId: UniqueId, vararg dynamicChildren: TestDescriptor) =
			SimpleTestDescriptor(uniqueId, TestDescriptor.Type.CONTAINER, getSimpleDisplayName(uniqueId)).apply {
				dynamicTests.addAll(dynamicChildren)
			}

		/**
		 * Creates a [TestDescriptor] for a test container (e.g., a test class or test engine) containing other
		 * [TestDescriptor] children.
		 */
		fun testContainer(uniqueId: UniqueId, vararg children: TestDescriptor): SimpleTestDescriptor {
			val result = SimpleTestDescriptor(
				uniqueId, TestDescriptor.Type.CONTAINER,
				getSimpleDisplayName(uniqueId)
			)
			children.forEach { child ->
				result.addChild(child)
			}
			return result
		}
	}
}
