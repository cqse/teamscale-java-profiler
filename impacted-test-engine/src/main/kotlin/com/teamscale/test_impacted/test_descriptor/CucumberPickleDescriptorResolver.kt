package com.teamscale.test_impacted.test_descriptor

import com.teamscale.test_impacted.commons.LoggerUtils.createLogger
import com.teamscale.test_impacted.test_descriptor.TestDescriptorUtils.getUniqueIdSegment
import org.junit.platform.engine.TestDescriptor
import java.lang.reflect.Field

/**
 * Test descriptor resolver for Cucumber. For details how we extract the uniform path, see comment in
 * [getPickleName]. The cluster id is the .feature file in which the tests are defined.
 */
class CucumberPickleDescriptorResolver : ITestDescriptorResolver {
	override fun getUniformPath(descriptor: TestDescriptor): String? {
		val featurePath = descriptor.featurePath() ?: run {
			LOG.severe {
				"Cannot resolve the feature classpath for Cucumber test descriptor '${descriptor.displayName}'." +
						" Please report a bug to CQSE" +
						" including the full descriptor: $descriptor"
			}
			return null
		}
		val pickleName = descriptor.getPickleName() ?: run {
			LOG.severe {
				"Cannot resolve the pickle name for Cucumber test descriptor '${descriptor.displayName}'." +
						" Please report a bug to CQSE" +
						" including the full descriptor: $descriptor"
			}
			return null
		}

		val featureDescriptor = descriptor.getFeatureFileTestDescriptor()
		val indexSuffix = if (featureDescriptor == null) {
			""
		} else {
			val testsWithTheSameName = featureDescriptor.childrenWithPickleName(pickleName)
			" #${testsWithTheSameName.indexOf(descriptor) + 1}"
		}

		val picklePath = "$featurePath/$pickleName"
		val uniformPath = (picklePath + indexSuffix).removeDuplicatedSlashes()
		LOG.fine { "Resolved uniform path: $uniformPath" }
		return uniformPath
	}

	override fun getClusterId(descriptor: TestDescriptor): String? =
		descriptor.featurePath()?.removeDuplicatedSlashes()

	override val engineId = CUCUMBER_ENGINE_ID

	/**
	 * Transform unique id segments from something like
	 * [feature:classpath%3Ahellocucumber%2Fcalculator.feature]/[scenario:11]/[examples:16]/[example:21] to
	 * hellocucumber/calculator.feature/11/16/21
	 */
	private fun TestDescriptor.featurePath(): String? {
		LOG.fine { "Unique ID of cucumber test descriptor: $uniqueId" }
		val featureSegment = getUniqueIdSegment(FEATURE_SEGMENT_TYPE).orElse(null)
		LOG.fine { "Resolved feature segment: $featureSegment" }
		return featureSegment?.replace("classpath:".toRegex(), "")
	}

	private fun TestDescriptor.getPickleName(): String? {
		// The PickleDescriptor test descriptor class is not public, so we can't import and use it to get access to the pickle attribute containing the name => reflection
		// https://github.com/cucumber/cucumber-jvm/blob/main/cucumber-junit-platform-engine/src/main/java/io/cucumber/junit/platform/engine/NodeDescriptor.java#L90
		// We want to use the name, though, because the unique id of the test descriptor can easily result in inconsistencies,
		// e.g. for
		//
		// Scenario Outline: Add two numbers
		//    Given I have a calculator
		//    When I add <num1> and <num2>
		//    Then the result should be <total>
		//
		//    Examples:
		//      | num1 | num2 | total |
		//      | -2   | 3    | 1     |
		//      | 10   | 15   | 25    |
		//      | 12   | 13   | 25    |
		//
		// tests will be executed for every line of the examples table. The unique id refers to the line number (!) of the example in the .feature file.
		// unique id: [...][feature:classpath%3Ahellocucumber%2Fcalculator.feature]/[scenario:11]/[examples:16]/[example:18] <- the latter numbers are line numbers in the file!!
		// This means, everytime the line numbers change the test would not be recognised as the same in Teamscale anymore.
		// So we use the pickle name (testDescriptor.pickle.getName()) to get the descriptive name "Add two numbers".
		// This is not unique yet, as all the executions of the test (all examples) will have the same name then => may not be the case in Teamscale.
		// To resolve this, we add an index afterwards in getUniformPath()

		var pickleField: Field? = null
		try {
			pickleField = javaClass.getDeclaredField("pickle")
		} catch (e: NoSuchFieldException) {
			// Pre cucumber 7.11.2, the field was called pickleEvent
			// (see NodeDescriptor in this merge request: https://github.com/cucumber/cucumber-jvm/pull/2711/files)
			// ...
		}
		return runCatching {
			if (pickleField == null) {
				// ... so try again with "pickleEvent"
				pickleField = javaClass.getDeclaredField("pickleEvent")
			}
			pickleField?.let { field ->
				field.isAccessible = true
				val pickle = field.get(this)
				// getName() is required by the pickle interface
				val getNameMethod = pickle.javaClass.getDeclaredMethod("getName")
				getNameMethod.isAccessible = true
				getNameMethod.invoke(pickle).toString()
					.escapeSlashes()
			}
		}.getOrNull()
	}

	private fun TestDescriptor.getFeatureFileTestDescriptor(): TestDescriptor? {
		if (!isFeatureFileTestDescriptor()) {
			if (!parent.isPresent) {
				return null
			}
			return parent.get().getFeatureFileTestDescriptor()
		}
		return this
	}

	private fun TestDescriptor.isFeatureFileTestDescriptor() =
		uniqueId.lastSegment.type == FEATURE_SEGMENT_TYPE

	private fun TestDescriptor.childrenWithPickleName(pickleName: String): List<TestDescriptor> {
		if (children.isEmpty()) {
			val pickleId = getPickleName()
			if (pickleId != null && pickleName == pickleId) {
				return listOf(this)
			}
			return emptyList()
		}
		return children.flatMap { childDescriptor ->
			childDescriptor.childrenWithPickleName(pickleName)
		}
	}

	companion object {
		private val LOG = createLogger()

		/** Name of the cucumber test engine as used in the unique id of the test descriptor  */
		const val CUCUMBER_ENGINE_ID = "cucumber"

		/** Type of the unique id segment of a test descriptor representing a cucumber feature file  */
		const val FEATURE_SEGMENT_TYPE = "feature"

		private val ESCAPE_SLASHES_REGEX = """(?<!\\)/""".toRegex()
		private val DUPLICATED_SLASHES_REGEX = """(?<!\\)/+""".toRegex()

		/**
		 * Escapes slashes (/) in a given input (usually a scenario name) with a backslash (\).
		 *  * `/ -> \/`
		 *  * `\/ -> \/`
		 */
		fun String.escapeSlashes() = replace(ESCAPE_SLASHES_REGEX, "\\\\/")

		/**
		 * Remove duplicated "/" with one (due to [TS-39915](https://cqse.atlassian.net/browse/TS-39915))
		 */
		fun String.removeDuplicatedSlashes() = replace(DUPLICATED_SLASHES_REGEX, "/")
	}
}
