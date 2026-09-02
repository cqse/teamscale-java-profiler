package com.teamscale.test_impacted.test_descriptor

import com.teamscale.test_impacted.engine.executor.SimpleTestDescriptor
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_TEMPLATE_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.METHOD_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.NESTED_CLASS_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.NESTED_CLASS_TEMPLATE_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.TEST_TEMPLATE_SEGMENT_TYPE
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
		assertThat(resolver.getUniformPath(descriptor)).isEqualTo("com/example/MyTest/myMethod()")
	}

	@Test
	fun testNestedClassUniformPath() {
		val methodId = UniqueId.forEngine("junit-jupiter")
			.append(CLASS_SEGMENT_TYPE, "com.example.OuterTest")
			.append(NESTED_CLASS_SEGMENT_TYPE, "Inner")
			.append(METHOD_SEGMENT_TYPE, "testMethod()")

		val descriptor = SimpleTestDescriptor.testCase(methodId)
		assertThat(resolver.getUniformPath(descriptor)).isEqualTo("com/example/OuterTest\$Inner/testMethod()")
	}

	@Test
	fun testDeeplyNestedClassUniformPath() {
		val methodId = UniqueId.forEngine("junit-jupiter")
			.append(CLASS_SEGMENT_TYPE, "com.example.A")
			.append(NESTED_CLASS_SEGMENT_TYPE, "B")
			.append(NESTED_CLASS_SEGMENT_TYPE, "C")
			.append(METHOD_SEGMENT_TYPE, "test()")

		val descriptor = SimpleTestDescriptor.testCase(methodId)
		assertThat(resolver.getUniformPath(descriptor)).isEqualTo("com/example/A\$B\$C/test()")
	}

	/**
	 * The tests of a top-level `@ParameterizedClass` are registered below one invocation per parameter set. They all
	 * belong to the same test, so the invocation must not appear in the uniform path.
	 */
	@Test
	fun testMethodInsideTopLevelParameterizedClassUniformPath() {
		val methodId = UniqueId.forEngine("junit-jupiter")
			.append(CLASS_TEMPLATE_SEGMENT_TYPE, "com.example.MyTest")
			.append(CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE, "#2")
			.append(METHOD_SEGMENT_TYPE, "testMethod()")

		val descriptor = SimpleTestDescriptor.testCase(methodId, "testMethod()[2]")
		assertThat(resolver.getUniformPath(descriptor)).isEqualTo("com/example/MyTest/testMethod()")
		assertThat(resolver.getClusterId(descriptor)).isEqualTo("com.example.MyTest")
	}

	/** The same for a `@Nested @ParameterizedClass`, whose nesting must be kept in the class name. */
	@Test
	fun testMethodInsideNestedParameterizedClassUniformPath() {
		val methodId = UniqueId.forEngine("junit-jupiter")
			.append(CLASS_SEGMENT_TYPE, "com.example.OuterTest")
			.append(NESTED_CLASS_TEMPLATE_SEGMENT_TYPE, "Inner")
			.append(CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE, "#1")
			.append(METHOD_SEGMENT_TYPE, "testMethod()")

		val descriptor = SimpleTestDescriptor.testCase(methodId, "testMethod()[1]")
		assertThat(resolver.getUniformPath(descriptor)).isEqualTo("com/example/OuterTest\$Inner/testMethod()")
		assertThat(resolver.getClusterId(descriptor)).isEqualTo("com.example.OuterTest\$Inner")
	}

	/** A `@Nested` class inside a `@ParameterizedClass` must keep both class names. */
	@Test
	fun testNestedClassInsideParameterizedClassUniformPath() {
		val methodId = UniqueId.forEngine("junit-jupiter")
			.append(CLASS_TEMPLATE_SEGMENT_TYPE, "com.example.MyTest")
			.append(CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE, "#1")
			.append(NESTED_CLASS_SEGMENT_TYPE, "Inner")
			.append(METHOD_SEGMENT_TYPE, "testMethod()")

		val descriptor = SimpleTestDescriptor.testCase(methodId, "testMethod()[1]")
		assertThat(resolver.getUniformPath(descriptor)).isEqualTo("com/example/MyTest\$Inner/testMethod()")
	}

	/** A `@ParameterizedTest` inside a `@ParameterizedClass` keeps its arguments but loses the invocation index. */
	@Test
	fun testParameterizedTestInsideParameterizedClassUniformPath() {
		val testTemplateId = UniqueId.forEngine("junit-jupiter")
			.append(CLASS_TEMPLATE_SEGMENT_TYPE, "com.example.MyTest")
			.append(CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE, "#3")
			.append(TEST_TEMPLATE_SEGMENT_TYPE, "testMethod(java.lang.String)")

		val descriptor = SimpleTestDescriptor.testCase(testTemplateId, "testMethod(String)[3]")
		assertThat(resolver.getUniformPath(descriptor)).isEqualTo("com/example/MyTest/testMethod(String)")
	}

	/** Reporting names outside of a `@ParameterizedClass` must be kept as they are. */
	@Test
	fun testBracketsOutsideOfParameterizedClassesAreKept() {
		val methodId = UniqueId.forEngine("junit-jupiter")
			.append(CLASS_SEGMENT_TYPE, "com.example.MyTest")
			.append(METHOD_SEGMENT_TYPE, "testMethod()")

		val descriptor = SimpleTestDescriptor.testCase(methodId, "testMethod()[1]")
		assertThat(resolver.getUniformPath(descriptor)).isEqualTo("com/example/MyTest/testMethod()[1]")
	}
}
