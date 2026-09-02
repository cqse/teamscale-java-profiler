package com.teamscale.test_impacted.test_descriptor

import com.teamscale.test_impacted.test_descriptor.TestDescriptorUtils.isInsideClassTemplate
import org.junit.platform.engine.TestDescriptor

/** Test default test descriptor resolver for the JUnit jupiter [TestEngine].  */
class JUnitJupiterTestDescriptorResolver : JUnitClassBasedTestDescriptorResolverBase() {
	override fun getTestName(descriptor: TestDescriptor): String {
		val reportingName = super.getTestName(descriptor)
		if (!descriptor.isInsideClassTemplate()) {
			return reportingName
		}
		// Within a @ParameterizedClass the jupiter engine appends the index of the enclosing invocation(s) to the
		// reporting name, e.g. "testOne()[1]". All invocations run the same test, so the index must not become part
		// of the uniform path: whatever the test covered in any of them belongs to that one test.
		return reportingName.replace(INVOCATION_INDEXES, "")
	}

	override fun TestDescriptor.getClassName(): String? {
		val classSegment = uniqueId.segments
			.firstOrNull { it.type == CLASS_SEGMENT_TYPE || it.type == CLASS_TEMPLATE_SEGMENT_TYPE }
			?.value ?: return null

		val nestedClassNames = uniqueId.segments
			.filter { it.type == NESTED_CLASS_SEGMENT_TYPE || it.type == NESTED_CLASS_TEMPLATE_SEGMENT_TYPE }
			.joinToString("") { "\$${it.value}" }

		return classSegment + nestedClassNames
	}

	override val engineId: String
		get() = "junit-jupiter"

	companion object {
		/** The invocation indexes that the jupiter engine appends to reporting names within a @ParameterizedClass.  */
		private val INVOCATION_INDEXES = Regex("(\\[\\d+])+$")

		/** The segment type name that the jupiter engine uses for the class descriptor nodes.  */
		const val CLASS_SEGMENT_TYPE = "class"

		/** The segment type name that the jupiter engine uses for @Nested inner class descriptor nodes.  */
		const val NESTED_CLASS_SEGMENT_TYPE = "nested-class"

		/** The segment type name that the jupiter engine uses for top-level @ParameterizedClass descriptor nodes.  */
		const val CLASS_TEMPLATE_SEGMENT_TYPE = "class-template"

		/** The segment type name that the jupiter engine uses for @Nested @ParameterizedClass descriptor nodes.  */
		const val NESTED_CLASS_TEMPLATE_SEGMENT_TYPE = "nested-class-template"

		/**
		 * The segment type name that the jupiter engine uses for the individual invocations of a @ParameterizedClass.
		 * These are only registered dynamically during test execution.
		 */
		const val CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE = "class-template-invocation"

		/** The segment type name that the jupiter engine uses for the method descriptor nodes.  */
		const val METHOD_SEGMENT_TYPE = "method"

		/** The segment type name that the jupiter engine uses for the test factory method descriptor nodes.  */
		const val TEST_FACTORY_SEGMENT_TYPE = "test-factory"

		/** The segment type name that the jupiter engine uses for the test template descriptor nodes.  */
		const val TEST_TEMPLATE_SEGMENT_TYPE = "test-template"

		/** The segment type name that the jupiter engine uses for dynamic test descriptor nodes.  */
		const val DYNAMIC_TEST_SEGMENT_TYPE = "dynamic-test"
	}
}
