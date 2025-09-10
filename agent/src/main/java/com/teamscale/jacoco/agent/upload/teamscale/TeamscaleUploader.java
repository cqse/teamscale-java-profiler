package com.teamscale.jacoco.agent.upload.teamscale;

import com.google.common.base.Strings;
import com.teamscale.client.CommitDescriptor;
import com.teamscale.client.EReportFormat;
import com.teamscale.client.FileSystemUtils;
import com.teamscale.client.ITeamscaleService;
import com.teamscale.client.ITeamscaleServiceKt;
import com.teamscale.client.TeamscaleServer;
import com.teamscale.client.TeamscaleServiceGenerator;
import com.teamscale.jacoco.agent.logging.LoggingUtils;
import com.teamscale.jacoco.agent.upload.IUploadRetry;
import com.teamscale.jacoco.agent.upload.IUploader;
import com.teamscale.jacoco.agent.util.Benchmark;
import com.teamscale.report.jacoco.CoverageFile;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static com.teamscale.jacoco.agent.upload.teamscale.ETeamscaleServerProperties.COMMIT;
import static com.teamscale.jacoco.agent.upload.teamscale.ETeamscaleServerProperties.MESSAGE;
import static com.teamscale.jacoco.agent.upload.teamscale.ETeamscaleServerProperties.PARTITION;
import static com.teamscale.jacoco.agent.upload.teamscale.ETeamscaleServerProperties.PROJECT;
import static com.teamscale.jacoco.agent.upload.teamscale.ETeamscaleServerProperties.REPOSITORY;
import static com.teamscale.jacoco.agent.upload.teamscale.ETeamscaleServerProperties.REVISION;

/** Uploads XML Coverage to a Teamscale instance. */
public class TeamscaleUploader implements IUploader, IUploadRetry {

	/**
	 * The properties file suffix for unsuccessful coverage uploads.
	 */
	public static final String RETRY_UPLOAD_FILE_SUFFIX = "_upload-retry.properties";

	/** The logger. */
	private final Logger logger = LoggingUtils.getLogger(this);

	public TeamscaleServer getTeamscaleServer() {
		return teamscaleServer;
	}

	/** Teamscale server details. */
	private final TeamscaleServer teamscaleServer;

	/** Constructor. */
	public TeamscaleUploader(TeamscaleServer teamscaleServer) {
		this.teamscaleServer = teamscaleServer;
	}

	@Override
	public void upload(CoverageFile coverageFile) {
		doUpload(coverageFile, this.teamscaleServer);
	}

	@Override
	public void reupload(CoverageFile coverageFile, Properties reuploadProperties) {
		TeamscaleServer server = new TeamscaleServer();
		server.project = reuploadProperties.getProperty(PROJECT.name());
		server.commit = CommitDescriptor.parse(reuploadProperties.getProperty(COMMIT.name()));
		server.partition = reuploadProperties.getProperty(PARTITION.name());
		server.revision = Strings.emptyToNull(reuploadProperties.getProperty(REVISION.name()));
		server.repository = Strings.emptyToNull(reuploadProperties.getProperty(REPOSITORY.name()));
		server.userAccessToken = teamscaleServer.userAccessToken;
		server.userName = teamscaleServer.userName;
		server.url = teamscaleServer.url;
		server.setMessage(reuploadProperties.getProperty(MESSAGE.name()));
		doUpload(coverageFile, server);
	}

	private void doUpload(CoverageFile coverageFile, TeamscaleServer teamscaleServer) {
		try (Benchmark benchmark = new Benchmark("Uploading report to Teamscale")) {
			if (tryUploading(coverageFile, teamscaleServer)) {
				deleteCoverageFile(coverageFile);
			} else {
				logger.warn("Failed to upload coverage to Teamscale. "
						+ "Won't delete local file {} so that the upload can automatically be retried upon profiler restart. "
						+ "Upload can also be retried manually.", coverageFile);
				markFileForUploadRetry(coverageFile);
			}
		}
	}

	@Override
	public void markFileForUploadRetry(CoverageFile coverageFile) {
		File uploadMetadataFile = new File(FileSystemUtils.replaceFilePathFilenameWith(
				FileSystemUtils.normalizeSeparators(coverageFile.toString()),
				coverageFile.getName() + RETRY_UPLOAD_FILE_SUFFIX));
		Properties serverProperties = this.createServerProperties();
		try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(uploadMetadataFile.toPath()),
				StandardCharsets.UTF_8)) {
			serverProperties.store(writer, null);
		} catch (IOException e) {
			logger.warn(
					"Failed to create metadata file for automatic upload retry of {}. Please manually retry the coverage upload to Teamscale.",
					coverageFile);
			uploadMetadataFile.delete();
		}
	}

	/**
	 * Creates server properties to be written in a properties file.
	 */
	private Properties createServerProperties() {
		Properties serverProperties = new Properties();
		serverProperties.setProperty(PROJECT.name(), teamscaleServer.project);
		serverProperties.setProperty(PARTITION.name(), teamscaleServer.partition);
		if (teamscaleServer.commit != null) {
			serverProperties.setProperty(COMMIT.name(), teamscaleServer.commit.toString());
		}
		serverProperties.setProperty(REVISION.name(), Strings.nullToEmpty(teamscaleServer.revision));
		serverProperties.setProperty(REPOSITORY.name(), Strings.nullToEmpty(teamscaleServer.repository));
		serverProperties.setProperty(MESSAGE.name(), teamscaleServer.getMessage());
		return serverProperties;
	}

	private void deleteCoverageFile(CoverageFile coverageFile) {
		try {
			coverageFile.delete();
		} catch (IOException e) {
			logger.warn("The upload to Teamscale was successful, but the deletion of the coverage file {} failed. "
					+ "You can delete it yourself anytime - it is no longer needed.", coverageFile, e);
		}
	}

	/** Performs the upload and returns <code>true</code> if successful. */
	private boolean tryUploading(CoverageFile coverageFile, TeamscaleServer teamscaleServer) {
		logger.debug("Uploading JaCoCo artifact to {}", teamscaleServer);

		try {
			// Cannot be executed in the constructor as this causes issues in WildFly server
			// (See #100)
			ITeamscaleService api = TeamscaleServiceGenerator.createService(ITeamscaleService.class,
					teamscaleServer.url, teamscaleServer.userName, teamscaleServer.userAccessToken);
			ITeamscaleServiceKt.uploadReport(api, teamscaleServer.project, teamscaleServer.commit,
					teamscaleServer.revision,
					teamscaleServer.repository, teamscaleServer.partition, EReportFormat.JACOCO,
					teamscaleServer.getMessage(), coverageFile.createFormRequestBody());
			return true;
		} catch (IOException e) {
			logger.error("Failed to upload coverage to {}", teamscaleServer, e);
			return false;
		}
	}

	@Override
	public String describe() {
		return "Uploading to " + teamscaleServer;
	}
}
