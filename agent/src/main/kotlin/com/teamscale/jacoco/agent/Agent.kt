package com.teamscale.jacoco.agent

import com.teamscale.client.FileSystemUtils
import com.teamscale.client.StringUtils
import com.teamscale.jacoco.agent.logging.LoggingUtils
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.jacoco.agent.upload.IUploadRetry
import com.teamscale.jacoco.agent.upload.IUploader
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleUploader
import com.teamscale.jacoco.agent.util.AgentUtils
import com.teamscale.report.jacoco.CoverageFile
import com.teamscale.report.jacoco.EmptyReportException
import com.teamscale.report.jacoco.JaCoCoXmlReportGenerator
import com.teamscale.report.jacoco.dump.Dump
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import java.io.File
import java.io.IOException
import java.lang.instrument.Instrumentation
import java.nio.file.Files
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * A wrapper around the JaCoCo Java agent that automatically triggers a dump and XML conversion based on a time
 * interval.
 */
class Agent(options: AgentOptions, instrumentation: Instrumentation?) : AgentBase(options) {
	/** Converts binary data to XML.  */
	private val generator: JaCoCoXmlReportGenerator

	/** Regular dump task.  */
	private var timer: Timer? = null

	/** Stores the XML files.  */
	private val uploader = options.createUploader(instrumentation)

	/** Constructor.  */
	init {
		logger.info("Upload method: {}", uploader.describe())
		retryUnsuccessfulUploads(options, uploader)
		generator = JaCoCoXmlReportGenerator(
			options.getClassDirectoriesOrZips(),
			options.locationIncludeFilter,
			options.getDuplicateClassFileBehavior(),
			options.shouldIgnoreUncoveredClasses(),
			LoggingUtils.wrap(logger)
		)

		if (options.shouldDumpInIntervals()) {
			val period = options.dumpIntervalInMinutes.toDuration(DurationUnit.MINUTES).inWholeMilliseconds
			timer = fixedRateTimer("Teamscale-Java-Profiler", true, 0, period) {
				dumpReport()
			}
			logger.info("Dumping every ${options.dumpIntervalInMinutes} minutes.")
		}
		options.teamscaleServerOptions.partition?.let { partition ->
			controller.sessionId = partition
		}
	}

	/**
	 * If we have coverage that was leftover because of previously unsuccessful coverage uploads, we retry to upload
	 * them again with the same configuration as in the previous try.
	 */
	private fun retryUnsuccessfulUploads(options: AgentOptions, uploader: IUploader) {
		var outputPath = options.outputDirectory
		if (outputPath == null) {
			// Default fallback
			outputPath = AgentUtils.getAgentDirectory().resolve("coverage")
		}

		val parentPath = outputPath.parent
		if (parentPath == null) {
			logger.error("The output path '{}' does not have a parent path. Canceling upload retry.", outputPath.toAbsolutePath())
			return
		}

		parentPath.toFile().walk()
			.filter { it.name.endsWith(TeamscaleUploader.RETRY_UPLOAD_FILE_SUFFIX) }
			.forEach { file ->
				reuploadCoverageFromPropertiesFile(file, uploader)
			}
	}

	private fun reuploadCoverageFromPropertiesFile(file: File, uploader: IUploader) {
		logger.info("Retrying previously unsuccessful coverage upload for file {}.", file)
		try {
			val properties = FileSystemUtils.readProperties(file)
			val coverageFile = CoverageFile(
				File(StringUtils.stripSuffix(file.absolutePath, TeamscaleUploader.RETRY_UPLOAD_FILE_SUFFIX))
			)

			if (uploader is IUploadRetry) {
				uploader.reupload(coverageFile, properties)
			} else {
				logger.info("Reupload not implemented for uploader {}", uploader.describe())
			}
			Files.deleteIfExists(file.toPath())
		} catch (e: IOException) {
			logger.error("Reuploading coverage failed. $e")
		}
	}

	override fun initResourceConfig(): ResourceConfig? {
		val resourceConfig = ResourceConfig()
		resourceConfig.property(ServerProperties.WADL_FEATURE_DISABLE, true.toString())
		AgentResource.setAgent(this)
		return resourceConfig.register(AgentResource::class.java).register(GenericExceptionMapper::class.java)
	}

	override fun prepareShutdown() {
		timer?.cancel()
		if (options.shouldDumpOnExit()) dumpReport()

		val dir = options.outputDirectory
		try {
			if (dir.listDirectoryEntries().isEmpty()) dir.deleteIfExists()
		} catch (e: IOException) {
			logger.info(
				("Could not delete empty output directory {}. "
						+ "This directory was created inside the configured output directory to be able to "
						+ "distinguish between different runs of the profiled JVM. You may delete it manually."),
				dir, e
			)
		}
	}

	/**
	 * Dumps the current execution data, converts it, writes it to the output directory defined in [.options] and
	 * uploads it if an uploader is configured. Logs any errors, never throws an exception.
	 */
	override fun dumpReport() {
		logger.debug("Starting dump")

		try {
			dumpReportUnsafe()
		} catch (t: Throwable) {
			// we want to catch anything in order to avoid crashing the whole system under
			// test
			logger.error("Dump job failed with an exception", t)
		}
	}

	private fun dumpReportUnsafe() {
		val dump: Dump
		try {
			dump = controller.dumpAndReset()
		} catch (e: JacocoRuntimeController.DumpException) {
			logger.error("Dumping failed, retrying later", e)
			return
		}

		try {
			benchmark("Generating the XML report") {
				val outputFile = options.createNewFileInOutputDirectory("jacoco", "xml")
				val coverageFile = generator.convertSingleDumpToReport(dump, outputFile)
				uploader.upload(coverageFile)
			}
		} catch (e: IOException) {
			logger.error("Converting binary dump to XML failed", e)
		} catch (e: EmptyReportException) {
			logger.error("No coverage was collected. ${e.message}", e)
		}
	}
}