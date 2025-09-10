/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2018 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent;

import com.teamscale.client.FileSystemUtils;
import com.teamscale.jacoco.agent.options.AgentOptions;
import com.teamscale.jacoco.agent.upload.IUploadRetry;
import com.teamscale.jacoco.agent.upload.IUploader;
import com.teamscale.jacoco.agent.upload.UploaderException;
import com.teamscale.jacoco.agent.util.AgentUtils;
import com.teamscale.jacoco.agent.util.Benchmark;
import com.teamscale.jacoco.agent.util.Timer;
import com.teamscale.report.jacoco.CoverageFile;
import com.teamscale.report.jacoco.EmptyReportException;
import com.teamscale.report.jacoco.JaCoCoXmlReportGenerator;
import com.teamscale.report.jacoco.dump.Dump;
import org.conqat.lib.commons.string.StringUtils;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static com.teamscale.jacoco.agent.logging.LoggingUtils.wrap;
import static com.teamscale.jacoco.agent.upload.teamscale.TeamscaleUploader.RETRY_UPLOAD_FILE_SUFFIX;

/**
 * A wrapper around the JaCoCo Java agent that automatically triggers a dump and XML conversion based on a time
 * interval.
 */
public class Agent extends AgentBase {

	/** Converts binary data to XML. */
	private final JaCoCoXmlReportGenerator generator;

	/** Regular dump task. */
	private Timer timer;

	/** Stores the XML files. */
	protected final IUploader uploader;

	/** Constructor. */
	public Agent(AgentOptions options, Instrumentation instrumentation)
			throws IllegalStateException, UploaderException {
		super(options);

		uploader = options.createUploader(instrumentation);
		logger.info("Upload method: {}", uploader.describe());
		retryUnsuccessfulUploads(options, uploader);
		generator = new JaCoCoXmlReportGenerator(options.getClassDirectoriesOrZips(),
				options.getLocationIncludeFilter(), options.getDuplicateClassFileBehavior(),
				options.shouldIgnoreUncoveredClasses(), wrap(logger));

		if (options.shouldDumpInIntervals()) {
			timer = new Timer(this::dumpReport, Duration.ofMinutes(options.getDumpIntervalInMinutes()));
			timer.start();
			logger.info("Dumping every {} minutes.", options.getDumpIntervalInMinutes());
		}
		if (options.getTeamscaleServerOptions().partition != null) {
			controller.setSessionId(options.getTeamscaleServerOptions().partition);
		}
	}

	/**
	 * If we have coverage that was leftover because of previously unsuccessful coverage uploads, we retry to upload
	 * them again with the same configuration as in the previous try.
	 */
	private void retryUnsuccessfulUploads(AgentOptions options, IUploader uploader) {
		Path outputPath = options.getOutputDirectory();
		if (outputPath == null) {
			// Default fallback
			outputPath = AgentUtils.getAgentDirectory().resolve("coverage");
		}

		Path parentPath = outputPath.getParent();
		if (parentPath == null) {
			logger.error("The output path '{}' does not have a parent path. Canceling upload retry.",
					outputPath.toAbsolutePath());
			return;
		}

		List<File> reuploadCandidates = FileSystemUtils.listFilesRecursively(parentPath.toFile(),
				filepath -> filepath.getName().endsWith(RETRY_UPLOAD_FILE_SUFFIX));
		for (File file : reuploadCandidates) {
			reuploadCoverageFromPropertiesFile(file, uploader);
		}
	}

	private void reuploadCoverageFromPropertiesFile(File file, IUploader uploader) {
		logger.info("Retrying previously unsuccessful coverage upload for file {}.", file);
		try {
			Properties properties = FileSystemUtils.readProperties(file);
			CoverageFile coverageFile = new CoverageFile(
					new File(StringUtils.stripSuffix(file.getAbsolutePath(), RETRY_UPLOAD_FILE_SUFFIX)));

			if (uploader instanceof IUploadRetry) {
				((IUploadRetry) uploader).reupload(coverageFile, properties);
			} else {
				logger.info("Reupload not implemented for uploader {}", uploader.describe());
			}
			Files.deleteIfExists(file.toPath());
		} catch (IOException e) {
			logger.error("Reuploading coverage failed. " + e);
		}
	}

	@Override
	protected ResourceConfig initResourceConfig() {
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.property(ServerProperties.WADL_FEATURE_DISABLE, Boolean.TRUE.toString());
		AgentResource.setAgent(this);
		return resourceConfig.register(AgentResource.class).register(GenericExceptionMapper.class);
	}

	@Override
	protected void prepareShutdown() {
		if (timer != null) {
			timer.stop();
		}
		if (options.shouldDumpOnExit()) {
			dumpReport();
		}

		try {
			deleteDirectoryIfEmpty(options.getOutputDirectory());
		} catch (IOException e) {
			logger.info(
					"Could not delete empty output directory {}. "
							+ "This directory was created inside the configured output directory to be able to "
							+ "distinguish between different runs of the profiled JVM. You may delete it manually.",
					options.getOutputDirectory(), e);
		}
	}

	/**
	 * Delete a directory from disk if it is empty. This method does nothing if the path provided does not exist or
	 * point to a file.
	 *
	 * @throws IOException if the deletion of the directory fails
	 */
	private static void deleteDirectoryIfEmpty(Path directory) throws IOException {
		if (!Files.isDirectory(directory)) {
			return;
		}

		try (Stream<Path> stream = Files.list(directory)) {
			if (stream.findFirst().isPresent()) {
				return;
			}
		}

		Files.delete(directory);
	}

	/**
	 * Dumps the current execution data, converts it, writes it to the output directory defined in {@link #options} and
	 * uploads it if an uploader is configured. Logs any errors, never throws an exception.
	 */
	@Override
	public void dumpReport() {
		logger.debug("Starting dump");

		try {
			dumpReportUnsafe();
		} catch (Throwable t) {
			// we want to catch anything in order to avoid crashing the whole system under
			// test
			logger.error("Dump job failed with an exception", t);
		}
	}

	private void dumpReportUnsafe() {
		Dump dump;
		try {
			dump = controller.dumpAndReset();
		} catch (JacocoRuntimeController.DumpException e) {
			logger.error("Dumping failed, retrying later", e);
			return;
		}

		try (Benchmark ignored = new Benchmark("Generating the XML report")) {
			File outputFile = options.createNewFileInOutputDirectory("jacoco", "xml");
			CoverageFile coverageFile = generator.convertSingleDumpToReport(dump, outputFile);
			uploader.upload(coverageFile);
		} catch (IOException e) {
			logger.error("Converting binary dump to XML failed", e);
		} catch (EmptyReportException e) {
			logger.error("No coverage was collected. " + e.getMessage(), e);
		}
	}
}
