package com.teamscale.jacoco.agent

import com.teamscale.client.FileSystemUtils
import com.teamscale.client.StringUtils
import com.teamscale.jacoco.agent.logging.LoggingUtils
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.jacoco.agent.upload.IUploadRetry
import com.teamscale.jacoco.agent.upload.IUploader
import com.teamscale.jacoco.agent.upload.LocalDiskUploader
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleUploader
import com.teamscale.jacoco.agent.util.AgentUtils
import com.teamscale.report.jacoco.CoverageFile
import com.teamscale.report.jacoco.EmptyReportException
import com.teamscale.report.jacoco.dump.Dump
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import java.io.File
import java.io.IOException
import java.lang.instrument.Instrumentation
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries

/**
 * A wrapper around the JaCoCo Java agent that automatically triggers a dump and XML conversion based on a time
 * interval.
 */
class Agent(options: AgentOptions, instrumentation: Instrumentation?) : AgentBase(options) {

	/** Stores the XML files.  */
	private val uploader = options.createUploader(instrumentation)

	/** Runs the regular dumps. Null when the agent dumps in no interval.  */
	private val intervalDumper = if (options.shouldDumpInIntervals()) {
		IntervalDumper(
			options.dumpIntervalInMinutes,
			uploader !is LocalDiskUploader,
			logger,
			TimerScheduler("Teamscale-Java-Profiler")
		) { dumpReport() }
	} else {
		null
	}

	init {
		logger.info("Upload method: {}", uploader.describe())
		retryUnsuccessfulUploads(options, uploader)
		intervalDumper?.start()
		options.teamscaleServer.partition?.let { partition ->
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
			outputPath = AgentUtils.agentDirectory.resolve("coverage")
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
				File(file.absolutePath.removeSuffix(TeamscaleUploader.RETRY_UPLOAD_FILE_SUFFIX))
			)

			if (uploader is IUploadRetry) {
				uploader.reupload(coverageFile, properties)
			} else {
				logger.info("Reupload not implemented for uploader {}", uploader.describe())
			}
			Files.deleteIfExists(file.toPath())
		} catch (e: IOException) {
			logger.error(
				"Reuploading cached coverage from {} failed. The file is kept and the upload will be retried on" +
						" the next agent restart.", file, e
			)
		}
	}

	override fun initResourceConfig(): ResourceConfig {
		val resourceConfig = ResourceConfig()
		resourceConfig.property(ServerProperties.WADL_FEATURE_DISABLE, true.toString())
		return resourceConfig
			.register(AgentResource(this))
			.register(GenericExceptionMapper::class.java)
	}

	override fun prepareShutdown() {
		intervalDumper?.stop()
		if (options.shouldDumpOnExit) dumpReport()

		val dir = options.outputDirectory ?: return
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
	 * Dumps the current execution data, converts it, writes it to the output directory defined in [options] and
	 * uploads it if an uploader is configured. Logs any errors, never throws an exception.
	 *
	 * Synchronized because this can be triggered concurrently by the timer and by the HTTP /dump endpoint.
	 */
	@Synchronized
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
			logger.error(
				"Dumping coverage data failed. The agent will retry on the next dump interval." +
						" If this persists, report a bug.", e
			)
			return
		}

		try {
			if (uploadReport(dump)) {
				intervalDumper?.recordSuccessfulUpload()
			}
		} catch (e: IOException) {
			logger.error(
				"Converting the binary JaCoCo dump to a coverage report failed. This dump's coverage will be skipped." +
						" If you set 'class-dir', ensure it points to your compiled classes and they are readable.", e
			)
		} catch (e: EmptyReportException) {
			logger.warn(
				"No coverage was collected in this dump interval: {}." +
						" This is normal if no profiled code ran." +
						" Check the 'includes'/'excludes' patterns if you expected coverage.",
				e.message
			)
		}
	}

	/** Converts [dump] to a coverage report and returns whether the uploader uploaded that report. */
	private fun uploadReport(dump: Dump): Boolean {
		val format = options.normalModeReportFormat
		val generator = options.createReportGenerator(LoggingUtils.wrap(logger))
		var hasUploaded = false
		benchmark("Generating the coverage report") {
			val outputFile = options.createNewFileInOutputDirectory(format.fileNamePrefix, format.fileExtension)
			val coverageFile = generator.convertSingleDumpToReport(dump, outputFile)
			hasUploaded = uploader.upload(coverageFile)
		}
		return hasUploaded
	}
}