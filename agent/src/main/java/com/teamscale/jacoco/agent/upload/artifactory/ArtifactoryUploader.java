package com.teamscale.jacoco.agent.upload.artifactory;

import com.teamscale.client.CommitDescriptor;
import com.teamscale.client.EReportFormat;
import com.teamscale.client.FileSystemUtils;
import com.teamscale.client.HttpUtils;
import com.teamscale.client.StringUtils;
import com.teamscale.jacoco.agent.commit_resolution.git_properties.CommitInfo;
import com.teamscale.jacoco.agent.upload.HttpZipUploaderBase;
import com.teamscale.jacoco.agent.upload.IUploadRetry;
import com.teamscale.report.jacoco.CoverageFile;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Response;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static com.teamscale.jacoco.agent.upload.teamscale.ETeamscaleServerProperties.COMMIT;
import static com.teamscale.jacoco.agent.upload.teamscale.ETeamscaleServerProperties.PARTITION;
import static com.teamscale.jacoco.agent.upload.teamscale.ETeamscaleServerProperties.REVISION;
import static com.teamscale.jacoco.agent.upload.teamscale.TeamscaleUploader.RETRY_UPLOAD_FILE_SUFFIX;

/**
 * Uploads XMLs to Artifactory.
 */
public class ArtifactoryUploader extends HttpZipUploaderBase<IArtifactoryUploadApi> implements IUploadRetry {

	/**
	 * Header that can be used as alternative to basic authentication to authenticate requests against artifactory. For
	 * details check https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API
	 */
	public static final String ARTIFACTORY_API_HEADER = "X-JFrog-Art-Api";
	private final ArtifactoryConfig artifactoryConfig;
	private final String coverageFormat;
	private String uploadPath;

	/** Constructor. */
	public ArtifactoryUploader(ArtifactoryConfig config, List<Path> additionalMetaDataFiles,
			EReportFormat reportFormat) {
		super(config.url, additionalMetaDataFiles, IArtifactoryUploadApi.class);
		this.artifactoryConfig = config;
		this.coverageFormat = reportFormat.name().toLowerCase();
	}

	@Override
	public void markFileForUploadRetry(CoverageFile coverageFile) {
		File uploadMetadataFile = new File(FileSystemUtils.replaceFilePathFilenameWith(
				FileSystemUtils.normalizeSeparators(coverageFile.toString()),
				coverageFile.getName() + RETRY_UPLOAD_FILE_SUFFIX));
		Properties properties = createArtifactoryProperties();
		try (FileWriter writer = new FileWriter(uploadMetadataFile)) {
			properties.store(writer, null);
		} catch (IOException e) {
			logger.warn(
					"Failed to create metadata file for automatic upload retry of {}. Please manually retry the coverage upload to Azure.",
					coverageFile);
			uploadMetadataFile.delete();
		}
	}

	@Override
	public void reupload(CoverageFile coverageFile, Properties reuploadProperties) {
		ArtifactoryConfig config = new ArtifactoryConfig();
		config.url = artifactoryConfig.url;
		config.user = artifactoryConfig.user;
		config.password = artifactoryConfig.password;
		config.legacyPath = artifactoryConfig.legacyPath;
		config.zipPath = artifactoryConfig.zipPath;
		config.pathSuffix = artifactoryConfig.pathSuffix;
		String revision = reuploadProperties.getProperty(REVISION.name());
		String commitString = reuploadProperties.getProperty(COMMIT.name());
		config.commitInfo = new CommitInfo(revision, CommitDescriptor.parse(commitString));
		config.apiKey = artifactoryConfig.apiKey;
		config.partition = StringUtils.emptyToNull(reuploadProperties.getProperty(PARTITION.name()));
		setUploadPath(coverageFile, config);
		super.upload(coverageFile);
	}

	/** Creates properties from the artifactory configs. */
	private Properties createArtifactoryProperties() {
		Properties properties = new Properties();
		properties.setProperty(REVISION.name(), artifactoryConfig.commitInfo.revision);
		properties.setProperty(COMMIT.name(), artifactoryConfig.commitInfo.commit.toString());
		properties.setProperty(PARTITION.name(), StringUtils.nullToEmpty(artifactoryConfig.partition));
		return properties;
	}

	@Override
	protected void configureOkHttp(OkHttpClient.Builder builder) {
		super.configureOkHttp(builder);
		if (artifactoryConfig.apiKey != null) {
			builder.addInterceptor(getArtifactoryApiHeaderInterceptor());
		} else {
			builder.addInterceptor(
					HttpUtils.getBasicAuthInterceptor(artifactoryConfig.user, artifactoryConfig.password));
		}
	}

	private void setUploadPath(CoverageFile coverageFile, ArtifactoryConfig artifactoryConfig) {
		if (artifactoryConfig.legacyPath) {
			this.uploadPath = String.join("/", artifactoryConfig.commitInfo.commit.branchName,
					artifactoryConfig.commitInfo.commit.timestamp + "-" + artifactoryConfig.commitInfo.revision,
					coverageFile.getNameWithoutExtension() + ".zip");
		} else if (artifactoryConfig.pathSuffix == null) {
			this.uploadPath = String.join("/", "uploads", artifactoryConfig.commitInfo.commit.branchName,
					artifactoryConfig.commitInfo.commit.timestamp + "-" + artifactoryConfig.commitInfo.revision,
					artifactoryConfig.partition, coverageFormat, coverageFile.getNameWithoutExtension() + ".zip");
		} else {
			this.uploadPath = String.join("/", "uploads", artifactoryConfig.commitInfo.commit.branchName,
					artifactoryConfig.commitInfo.commit.timestamp + "-" + artifactoryConfig.commitInfo.revision,
					artifactoryConfig.partition, coverageFormat, artifactoryConfig.pathSuffix,
					coverageFile.getNameWithoutExtension() + ".zip");
		}
	}

	@Override
	public void upload(CoverageFile coverageFile) {
		setUploadPath(coverageFile, this.artifactoryConfig);
		super.upload(coverageFile);
	}

	@Override
	protected Response<ResponseBody> uploadCoverageZip(File zipFile) throws IOException {
		return getApi().uploadCoverageZip(uploadPath, zipFile);
	}

	@Override
	protected String getZipEntryCoverageFileName(CoverageFile coverageFile) {
		String path = coverageFile.getName();
		if (!StringUtils.isEmpty(artifactoryConfig.zipPath)) {
			path = artifactoryConfig.zipPath + "/" + path;
		}

		return path;
	}

	/** {@inheritDoc} */
	@Override
	public String describe() {
		return "Uploading to " + uploadUrl;
	}

	private Interceptor getArtifactoryApiHeaderInterceptor() {
		return chain -> {
			Request newRequest = chain.request().newBuilder().header(ARTIFACTORY_API_HEADER, artifactoryConfig.apiKey)
					.build();
			return chain.proceed(newRequest);
		};
	}
}
