package com.teamscale.test_impacted.test_descriptor

import com.teamscale.test_impacted.test_descriptor.TestDescriptorUtils.getAvailableTests
import com.teamscale.test_impacted.test_descriptor.samples.SampleParameterizedTestClass
import com.teamscale.test_impacted.test_descriptor.samples.SampleTestClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.engine.JupiterTestEngine
import org.junit.jupiter.engine.descriptor.ClassTemplateInvocationTestDescriptor
import org.junit.jupiter.engine.descriptor.ClassTemplateTestDescriptor
import org.junit.jupiter.engine.descriptor.ClassTestDescriptor
import org.junit.jupiter.engine.descriptor.NestedClassTestDescriptor
import org.junit.jupiter.engine.descriptor.TestFactoryTestDescriptor
import org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor
import org.junit.jupiter.engine.descriptor.TestTemplateTestDescriptor
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder

/**
 * Tests the handling of `@ParameterizedClass` against the real JUnit Jupiter engine. The unit tests in
 * [TestDescriptorUtilsTest] and [JUnitJupiterTestDescriptorResolverTest] construct their unique IDs by hand, so only
 * these tests notice when the jupiter engine changes the shape of the test tree it reports.
 */
internal class JupiterClassTemplateTest {

	/**
	 * A `@ParameterizedClass` registers its invocations and their test methods only while it is being executed, which
	 * is why the JUnit platform prunes them from the discovered test tree. The class template must therefore be
	 * reported as an available test itself, otherwise its tests neither show up in the test list uploaded to Teamscale
	 * nor get any coverage recorded for them.
	 */
	@Test
	fun testParameterizedClassesAreDiscoveredAsAvailableTests() {
		val discoveryRequest = LauncherDiscoveryRequestBuilder.request()
			.selectors(selectClass(SampleTestClass::class.java), selectClass(SampleParameterizedTestClass::class.java))
			.build()
		val jupiterEngine = JupiterTestEngine()

		val rootDescriptor = jupiterEngine.discover(discoveryRequest, UniqueId.forEngine(jupiterEngine.id))
		// The engine records the tests of the @ParameterizedClasses while they are still in the tree, ...
		val classTemplateRegistry = ClassTemplateRegistry().apply { record(rootDescriptor) }
		// ... because the JUnit platform launcher prunes the tree before handing it to the engines for execution.
		rootDescriptor.accept(TestDescriptor::prune)

		val samples = "com/teamscale/test_impacted/test_descriptor/samples"
		assertThat(getAvailableTests(rootDescriptor, classTemplateRegistry).testList)
			.extracting<String> { it.uniformPath }
			.containsExactlyInAnyOrder(
				"$samples/SampleTestClass/testOuter()",
				"$samples/SampleTestClass\$PlainNested/testPlain()",
				"$samples/SampleTestClass\$NestedParameterized/testOne()",
				"$samples/SampleTestClass\$NestedParameterized/testTwo()",
				"$samples/SampleParameterizedTestClass/testA()",
				"$samples/SampleParameterizedTestClass/testB()"
			)
	}

	/**
	 * The segment types we match on are not part of the public JUnit API, so make sure they stay in sync with the ones
	 * the jupiter engine actually uses.
	 */
	@Test
	fun testSegmentTypesMatchTheOnesUsedByTheJupiterEngine() {
		assertThat(JUnitJupiterTestDescriptorResolver.CLASS_SEGMENT_TYPE)
			.isEqualTo(ClassTestDescriptor.SEGMENT_TYPE)
		assertThat(JUnitJupiterTestDescriptorResolver.NESTED_CLASS_SEGMENT_TYPE)
			.isEqualTo(NestedClassTestDescriptor.SEGMENT_TYPE)
		assertThat(JUnitJupiterTestDescriptorResolver.METHOD_SEGMENT_TYPE)
			.isEqualTo(TestMethodTestDescriptor.SEGMENT_TYPE)
		assertThat(JUnitJupiterTestDescriptorResolver.TEST_FACTORY_SEGMENT_TYPE)
			.isEqualTo(TestFactoryTestDescriptor.SEGMENT_TYPE)
		assertThat(JUnitJupiterTestDescriptorResolver.DYNAMIC_TEST_SEGMENT_TYPE)
			.isEqualTo(TestFactoryTestDescriptor.DYNAMIC_TEST_SEGMENT_TYPE)
		assertThat(JUnitJupiterTestDescriptorResolver.TEST_TEMPLATE_SEGMENT_TYPE)
			.isEqualTo(TestTemplateTestDescriptor.SEGMENT_TYPE)
		assertThat(JUnitJupiterTestDescriptorResolver.CLASS_TEMPLATE_SEGMENT_TYPE)
			.isEqualTo(ClassTemplateTestDescriptor.STANDALONE_CLASS_SEGMENT_TYPE)
		assertThat(JUnitJupiterTestDescriptorResolver.NESTED_CLASS_TEMPLATE_SEGMENT_TYPE)
			.isEqualTo(ClassTemplateTestDescriptor.NESTED_CLASS_SEGMENT_TYPE)
		assertThat(JUnitJupiterTestDescriptorResolver.CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE)
			.isEqualTo(ClassTemplateInvocationTestDescriptor.SEGMENT_TYPE)
	}
}
