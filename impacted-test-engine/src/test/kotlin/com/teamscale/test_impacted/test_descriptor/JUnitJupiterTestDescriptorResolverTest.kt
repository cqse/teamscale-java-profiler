package com.teamscale.test_impacted.test_descriptor

import com.teamscale.test_impacted.engine.executor.SimpleTestDescriptor
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.METHOD_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.NESTED_CLASS_SEGMENT_TYPE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.platform.engine.UniqueId

internal class JUnitJupiterTestDescriptorResolverTest {

	private val resolver = JUnitJupiterTestDescriptorResolver()

	@Test
	fun testRegularClassUniformPath() {
		val methodId = UniqueId.forEngine("junit-jupiter")
			.append(CLASS_SEGMENT_TYPE, "com.example.MyTest")
			.append(METHOD_SEGMENT_TYPE, "myMethod()")

		val descriptor = SimpleTestDescriptor.testCase(methodId)
		assertThat(resolver.getUniformPath(descriptor)).hasValue("com/example/MyTest/myMethod()")
	}

	@Test
	fun testNestedClassUniformPath() {
		val methodId = UniqueId.forEngine("junit-jupiter")
			.append(CLASS_SEGMENT_TYPE, "com.example.OuterTest")
			.append(NESTED_CLASS_SEGMENT_TYPE, "Inner")
			.append(METHOD_SEGMENT_TYPE, "testMethod()")

		val descriptor = SimpleTestDescriptor.testCase(methodId)
		assertThat(resolver.getUniformPath(descriptor)).hasValue("com/example/OuterTest\$Inner/testMethod()")
	}

	@Test
	fun testDeeplyNestedClassUniformPath() {
		val methodId = UniqueId.forEngine("junit-jupiter")
			.append(CLASS_SEGMENT_TYPE, "com.example.A")
			.append(NESTED_CLASS_SEGMENT_TYPE, "B")
			.append(NESTED_CLASS_SEGMENT_TYPE, "C")
			.append(METHOD_SEGMENT_TYPE, "test()")

		val descriptor = SimpleTestDescriptor.testCase(methodId)
		assertThat(resolver.getUniformPath(descriptor)).hasValue("com/example/A\$B\$C/test()")
	}
}
