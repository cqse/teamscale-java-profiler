package com.teamscale.test_impacted.test_descriptor

import com.teamscale.test_impacted.commons.LoggerUtils.createLogger
import org.junit.platform.commons.util.ClassLoaderUtils
import java.util.*

/**
 * Registry containing the default and custom [ITestDescriptorResolver]s discovered by the java
 * [ServiceLoader].
 */
object TestDescriptorResolverRegistry {
	private val LOG = createLogger()

	private val TEST_DESCRIPTOR_RESOLVER_BY_ENGINE_ID = mutableMapOf<String, ITestDescriptorResolver>()

	init {
		// Register default test descriptor resolvers
		JUnitJupiterTestDescriptorResolver().register()
		JUnitVintageTestDescriptorResolver().register()
		JUnitPlatformSuiteDescriptorResolver().register()
		CucumberPickleDescriptorResolver().register()

		// Override existing or register new test descriptor resolvers
		ServiceLoader
			.load(ITestDescriptorResolver::class.java, ClassLoaderUtils.getDefaultClassLoader())
			.forEach { testDescriptorResolver ->
				testDescriptorResolver.register()
			}
	}

	private fun ITestDescriptorResolver.register() {
		TEST_DESCRIPTOR_RESOLVER_BY_ENGINE_ID[engineId] = this
	}

	/** Returns the test descriptor resolver or null if none exists for the test engine.  */
	fun getTestDescriptorResolver(testEngineId: String): ITestDescriptorResolver? {
		if (!TEST_DESCRIPTOR_RESOLVER_BY_ENGINE_ID.containsKey(testEngineId)) {
			LOG.warning {
				testEngineId + " is not officially supported! You can add support by " +
						"implementing the ITestDescriptorResolver interface and making the implementation " +
						"discoverable via the Java Service Loader mechanism!"
			}
			return TEST_DESCRIPTOR_RESOLVER_BY_ENGINE_ID["junit-jupiter"]
		}
		return TEST_DESCRIPTOR_RESOLVER_BY_ENGINE_ID[testEngineId]
	}
}
