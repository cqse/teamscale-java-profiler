package com.teamscale.test_impacted.engine

import com.teamscale.test_impacted.commons.LoggerUtils.createLogger
import com.teamscale.test_impacted.engine.ImpactedTestEngine.Companion.ENGINE_NAME
import com.teamscale.test_impacted.engine.executor.TestwiseCoverageCollectingExecutionListener
import com.teamscale.test_impacted.test_descriptor.TestDescriptorResolverRegistry.getTestDescriptorResolver
import com.teamscale.test_impacted.test_descriptor.TestDescriptorUtils.getAvailableTests
import com.teamscale.test_impacted.test_descriptor.TestDescriptorUtils.getTestDescriptorAsString
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.EngineDescriptor


/**
 * Internal test engine implementation for executing impacted tests. This class provides functionality
 * for discovering and executing tests based on their impacted status, as determined by the provided
 * configuration and partitioning logic.
 *
 * @constructor Initializes the test engine with the given configuration and partition.
 * @param configuration The configuration object that provides dependencies such as test engine registry,
 * test sorter, and test data writer.
 * @param partition The partition identifier used to divide tests and manage their execution.
 */
internal class InternalImpactedTestEngine(
	configuration: ImpactedTestEngineConfiguration,
	private val partition: String?
) {
	private val testEngineRegistry = configuration.testEngineRegistry
	private val testSorter = configuration.testSorter
	private val teamscaleAgentNotifier = configuration.teamscaleAgentNotifier
	private val testDataWriter = configuration.testDataWriter

	/**
	 * Performs test discovery by aggregating the result of all [TestEngine]s from the [TestEngineRegistry]
	 * in a single engine [TestDescriptor].
	 */
	fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
		val engineDescriptor = EngineDescriptor(uniqueId, ENGINE_NAME)

		LOG.fine { "Starting test discovery for engine " + ImpactedTestEngine.ENGINE_ID }

		testEngineRegistry.forEach { delegateTestEngine ->
			LOG.fine { "Starting test discovery for delegate engine: " + delegateTestEngine.id }
			val delegateEngineDescriptor = delegateTestEngine.discover(
				discoveryRequest,
				UniqueId.forEngine(delegateTestEngine.id)
			)

			engineDescriptor.addChild(delegateEngineDescriptor)
		}

		LOG.fine {
			"Discovered test descriptor for engine ${ImpactedTestEngine.ENGINE_ID}:\n${
				getTestDescriptorAsString(engineDescriptor)
			}"
		}

		return engineDescriptor
	}

	/**
	 * Executes the request by requesting execution of the [TestDescriptor] children aggregated in
	 * [.discover] with the corresponding [org.junit.platform.engine.TestEngine].
	 */
	fun execute(request: ExecutionRequest) {
		val rootTestDescriptor = request.rootTestDescriptor
		val availableTests = getAvailableTests(rootTestDescriptor)

		LOG.fine {
			"Starting selection and sorting ${ImpactedTestEngine.ENGINE_ID}:\n${
				getTestDescriptorAsString(rootTestDescriptor)
			}"
		}

		testSorter.selectAndSort(rootTestDescriptor)

		LOG.fine {
			"Starting execution of request for engine ${ImpactedTestEngine.ENGINE_ID}:\n${
				getTestDescriptorAsString(rootTestDescriptor)
			}"
		}

		val testExecutions = executeTests(request, rootTestDescriptor)

		testDataWriter.dumpTestExecutions(testExecutions)
		testDataWriter.dumpTestDetails(availableTests.testList)
		teamscaleAgentNotifier.testRunEnded()
	}

	private fun executeTests(request: ExecutionRequest, rootTestDescriptor: TestDescriptor) =
		rootTestDescriptor.children.flatMap { engineTestDescriptor ->
			val engineId = engineTestDescriptor.uniqueId.engineId
			if (!engineId.isPresent) {
				LOG.severe { "Engine ID for test descriptor $engineTestDescriptor not present. Skipping execution of the engine." }
				return@flatMap emptyList()
			}

			val testEngine = testEngineRegistry.getTestEngine(engineId.get()) ?: return@flatMap emptyList()
			val testDescriptorResolver = getTestDescriptorResolver(testEngine.id) ?: return@flatMap emptyList()
			val executionListener =
				TestwiseCoverageCollectingExecutionListener(
					teamscaleAgentNotifier,
					testDescriptorResolver,
					request.engineExecutionListener
				)

			testEngine.execute(
				createExecutionRequest(
					request,
					engineTestDescriptor,
					executionListener
				)
			)

			executionListener.testExecutions
		}

	/**
	 * Creates an ExecutionRequest, attempting to use the newer overloads via reflection
	 * if available (JUnit 5.13+), falling back to the 3-parameter constructor version for older versions.
	 */
	private fun createExecutionRequest(
		request: ExecutionRequest,
		engineTestDescriptor: TestDescriptor,
		executionListener: TestwiseCoverageCollectingExecutionListener
	): ExecutionRequest {
		// Try JUnit 6.0+ with 6 parameters (OutputDirectoryCreator + CancellationToken)
		tryCreateExecutionRequestJUnit60Plus(request, engineTestDescriptor, executionListener)?.let { return it }

		// Try JUnit 5.13-6.0 with 5 parameters (OutputDirectoryProvider)
		tryCreateExecutionRequestJUnit513To60(request, engineTestDescriptor, executionListener)?.let { return it }

		// Fall back to the 3-parameter constructor version for older JUnit versions
		return createLegacyExecutionRequest(engineTestDescriptor, executionListener, request)
	}

	/**
	 * Attempts to create an ExecutionRequest using the JUnit 6.0+ API with 6 parameters.
	 */
	private fun tryCreateExecutionRequestJUnit60Plus(
		request: ExecutionRequest,
		engineTestDescriptor: TestDescriptor,
		executionListener: TestwiseCoverageCollectingExecutionListener
	): ExecutionRequest? {
		return try {
			val outputDirectoryCreatorClass = Class.forName("org.junit.platform.engine.OutputDirectoryCreator")
			val namespacedStoreClass =
				Class.forName("org.junit.platform.engine.support.store.NamespacedHierarchicalStore")
			val cancellationTokenClass = Class.forName("org.junit.platform.engine.CancellationToken")

			val createMethod = ExecutionRequest::class.java.getMethod(
				"create",
				TestDescriptor::class.java,
				org.junit.platform.engine.EngineExecutionListener::class.java,
				org.junit.platform.engine.ConfigurationParameters::class.java,
				outputDirectoryCreatorClass,
				namespacedStoreClass,
				cancellationTokenClass
			)

			val outputDirectoryCreator = request.javaClass.getMethod("getOutputDirectoryCreator").invoke(request)
			val requestLevelStore = request.javaClass.getMethod("getStore").invoke(request)
			val cancellationToken = request.javaClass.getMethod("getCancellationToken").invoke(request)

			createMethod.invoke(
				null,
				engineTestDescriptor,
				executionListener,
				request.configurationParameters,
				outputDirectoryCreator,
				requestLevelStore,
				cancellationToken
			) as ExecutionRequest
		} catch (e: Exception) {
			LOG.fine { "6-parameter create method not available: ${e.javaClass.simpleName}" }
			null
		}
	}

	/**
	 * Attempts to create an ExecutionRequest using the JUnit 5.13-6.0 API with 5 parameters.
	 */
	private fun tryCreateExecutionRequestJUnit513To60(
		request: ExecutionRequest,
		engineTestDescriptor: TestDescriptor,
		executionListener: TestwiseCoverageCollectingExecutionListener
	): ExecutionRequest? {
		return try {
			val outputDirectoryProviderClass = Class.forName("org.junit.platform.engine.reporting.OutputDirectoryProvider")
			val namespacedStoreClass =
				Class.forName("org.junit.platform.engine.support.store.NamespacedHierarchicalStore")

			val createMethod = ExecutionRequest::class.java.getMethod(
				"create",
				TestDescriptor::class.java,
				org.junit.platform.engine.EngineExecutionListener::class.java,
				org.junit.platform.engine.ConfigurationParameters::class.java,
				outputDirectoryProviderClass,
				namespacedStoreClass
			)

			val outputDirectoryProvider = request.javaClass.getMethod("getOutputDirectoryProvider").invoke(request)
			val requestLevelStore = request.javaClass.getMethod("getStore").invoke(request)

			createMethod.invoke(
				null,
				engineTestDescriptor,
				executionListener,
				request.configurationParameters,
				outputDirectoryProvider,
				requestLevelStore
			) as ExecutionRequest
		} catch (e: Exception) {
			LOG.fine { "5-parameter create method not available: ${e.javaClass.simpleName}" }
			null
		}
	}

	/**
	 * Creates an ExecutionRequest using the legacy 3-parameter constructor for older JUnit versions.
	 */
	private fun createLegacyExecutionRequest(
		engineTestDescriptor: TestDescriptor,
		executionListener: TestwiseCoverageCollectingExecutionListener,
		request: ExecutionRequest
	): ExecutionRequest {
		LOG.fine { "Using legacy 3-parameter ExecutionRequest()" }
		@Suppress("DEPRECATION")
		return ExecutionRequest(
			engineTestDescriptor,
			executionListener,
			request.configurationParameters
		)
	}

	companion object {
		private val LOG = createLogger()
	}
}
