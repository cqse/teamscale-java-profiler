package com.teamscale.jacoco.agent.upload.azure;

import com.teamscale.client.EReportFormat;
import com.teamscale.client.FileSystemUtils;
import com.teamscale.jacoco.agent.upload.HttpZipUploaderBase;
import com.teamscale.jacoco.agent.upload.IUploadRetry;
import com.teamscale.jacoco.agent.upload.UploaderException;
import com.teamscale.report.jacoco.CoverageFile;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.teamscale.jacoco.agent.upload.azure.AzureFileStorageHttpUtils.EHttpMethod.HEAD;
import static com.teamscale.jacoco.agent.upload.azure.AzureFileStorageHttpUtils.EHttpMethod.PUT;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.AUTHORIZATION;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_LENGTH;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_TYPE;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_CONTENT_LENGTH;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_RANGE;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_TYPE;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_WRITE;
import static com.teamscale.jacoco.agent.upload.teamscale.TeamscaleUploader.RETRY_UPLOAD_FILE_SUFFIX;

/** Uploads the coverage archive to a provided azure file storage. */
public class AzureFileStorageUploader extends HttpZipUploaderBase<IAzureUploadApi> implements IUploadRetry {

	/** Pattern matches the host of a azure file storage */
	private static final Pattern AZURE_FILE_STORAGE_HOST_PATTERN = Pattern
			.compile("^(\\w*)\\.file\\.core\\.windows\\.net$");

	/** The access key for the azure file storage */
	private final String accessKey;

	/** The account for the azure file storage */
	private final String account;

	/** Constructor. */
	public AzureFileStorageUploader(AzureFileStorageConfig config, List<Path> additionalMetaDataFiles)
			throws UploaderException {
		super(config.url, additionalMetaDataFiles, IAzureUploadApi.class);
		this.accessKey = config.accessKey;
		this.account = getAccount();

		validateUploadUrl();
	}

	@Override
	public void markFileForUploadRetry(CoverageFile coverageFile) {
		File uploadMetadataFile = new File(FileSystemUtils.replaceFilePathFilenameWith(
				FileSystemUtils.normalizeSeparators(coverageFile.toString()),
				coverageFile.getName() + RETRY_UPLOAD_FILE_SUFFIX));
		try {
			uploadMetadataFile.createNewFile();
		} catch (IOException e) {
			logger.warn(
					"Failed to create metadata file for automatic upload retry of {}. Please manually retry the coverage upload to Azure.",
					coverageFile);
			uploadMetadataFile.delete();
		}
	}

	@Override
	public void reupload(CoverageFile coverageFile, Properties properties) {
		// The azure uploader does not have any special reupload properties, so it will
		// just use the normal upload instead.
		this.upload(coverageFile);
	}

	/**
	 * Extracts and returns the account of the provided azure file storage from the URL.
	 */
	private String getAccount() throws UploaderException {
		Matcher matcher = AZURE_FILE_STORAGE_HOST_PATTERN.matcher(this.uploadUrl.host());
		if (matcher.matches()) {
			return matcher.group(1);
		} else {
			throw new UploaderException(String.format("URL is malformed. Must be in the format "
					+ "\"https://<account>.file.core.windows.net/<share>/\", but was instead: %s", uploadUrl));
		}
	}

	@Override
	public String describe() {
		return String.format("Uploading coverage to the Azure File Storage at %s", this.uploadUrl);
	}

	@Override
	protected Response<ResponseBody> uploadCoverageZip(File zipFile) throws IOException, UploaderException {
		String fileName = createFileName();
		if (checkFile(fileName).isSuccessful()) {
			logger.warn(String.format("The file %s does already exists at %s", fileName, uploadUrl));
		}

		return createAndFillFile(zipFile, fileName);
	}

	/**
	 * Makes sure that the upload url is valid and that it exists on the file storage. If some directories do not
	 * exists, they will be created.
	 */
	private void validateUploadUrl() throws UploaderException {
		List<String> pathParts = this.uploadUrl.pathSegments();

		if (pathParts.size() < 2) {
			throw new UploaderException(String.format(
					"%s is too short for a file path on the storage. "
							+ "At least the share must be provided: https://<account>.file.core.windows.net/<share>/",
					uploadUrl.url().getPath()));
		}

		try {
			checkAndCreatePath(pathParts);
		} catch (IOException e) {
			throw new UploaderException(String.format(
					"Checking the validity of %s failed. "
							+ "There is probably something wrong with the URL or a problem with the account/key: ",
					this.uploadUrl.url().getPath()), e);
		}
	}

	/**
	 * Checks the directory path in the azure url. Creates any missing directories.
	 */
	private void checkAndCreatePath(List<String> pathParts) throws IOException, UploaderException {
		for (int i = 2; i <= pathParts.size() - 1; i++) {
			String directoryPath = String.format("/%s/", String.join("/", pathParts.subList(0, i)));
			if (!checkDirectory(directoryPath).isSuccessful()) {
				Response<ResponseBody> mkdirResponse = createDirectory(directoryPath);
				if (!mkdirResponse.isSuccessful()) {
					throw new UploaderException(String.format("Creation of path '/%s' was unsuccessful", directoryPath),
							mkdirResponse);
				}
			}
		}
	}

	/** Creates a file name for the zip-archive containing the coverage. */
	private String createFileName() {
		return String.format("%s-%s.zip", EReportFormat.JACOCO.name().toLowerCase(), System.currentTimeMillis());
	}

	/** Checks if the file with the given name exists */
	private Response<Void> checkFile(String fileName) throws IOException, UploaderException {
		String filePath = uploadUrl.url().getPath() + fileName;

		Map<String, String> headers = AzureFileStorageHttpUtils.getBaseHeaders();
		Map<String, String> queryParameters = new HashMap<>();

		String auth = AzureFileStorageHttpUtils.getAuthorizationString(HEAD, account, accessKey, filePath, headers,
				queryParameters);

		headers.put(AUTHORIZATION, auth);
		return getApi().head(filePath, headers, queryParameters).execute();
	}

	/** Checks if the directory given by the specified path does exist. */
	private Response<Void> checkDirectory(String directoryPath) throws IOException, UploaderException {
		Map<String, String> headers = AzureFileStorageHttpUtils.getBaseHeaders();

		Map<String, String> queryParameters = new HashMap<>();
		queryParameters.put("restype", "directory");

		String auth = AzureFileStorageHttpUtils.getAuthorizationString(HEAD, account, accessKey, directoryPath, headers,
				queryParameters);

		headers.put(AUTHORIZATION, auth);
		return getApi().head(directoryPath, headers, queryParameters).execute();
	}

	/**
	 * Creates the directory specified by the given path. The path must contain the share where it should be created
	 * on.
	 */
	private Response<ResponseBody> createDirectory(String directoryPath) throws IOException, UploaderException {
		Map<String, String> headers = AzureFileStorageHttpUtils.getBaseHeaders();

		Map<String, String> queryParameters = new HashMap<>();
		queryParameters.put("restype", "directory");

		String auth = AzureFileStorageHttpUtils.getAuthorizationString(PUT, account, accessKey, directoryPath, headers,
				queryParameters);

		headers.put(AUTHORIZATION, auth);
		return getApi().put(directoryPath, headers, queryParameters).execute();
	}

	/** Creates and fills a file with the given data and name. */
	private Response<ResponseBody> createAndFillFile(File zipFile, String fileName)
			throws UploaderException, IOException {
		Response<ResponseBody> response = createFile(zipFile, fileName);
		if (response.isSuccessful()) {
			return fillFile(zipFile, fileName);
		}
		logger.error(String.format("Creation of file '%s' was unsuccessful.", fileName));
		return response;
	}

	/**
	 * Creates an empty file with the given name. The size is defined by the length of the given byte array.
	 */
	private Response<ResponseBody> createFile(File zipFile, String fileName) throws IOException, UploaderException {
		String filePath = uploadUrl.url().getPath() + fileName;

		Map<String, String> headers = AzureFileStorageHttpUtils.getBaseHeaders();
		headers.put(X_MS_CONTENT_LENGTH, String.valueOf(zipFile.length()));
		headers.put(X_MS_TYPE, "file");

		Map<String, String> queryParameters = new HashMap<>();

		String auth = AzureFileStorageHttpUtils.getAuthorizationString(PUT, account, accessKey, filePath, headers,
				queryParameters);

		headers.put(AUTHORIZATION, auth);
		return getApi().put(filePath, headers, queryParameters).execute();
	}

	/**
	 * Fills the file defined by the name with the given data. Should be used with {@link #createFile(File, String)},
	 * because the request only writes exactly the length of the given data, so the file should be exactly as big as the
	 * data, otherwise it will be partially filled or is not big enough.
	 */
	private Response<ResponseBody> fillFile(File zipFile, String fileName) throws IOException, UploaderException {
		String filePath = uploadUrl.url().getPath() + fileName;

		String range = "bytes=0-" + (zipFile.length() - 1);
		String contentType = "application/octet-stream";

		Map<String, String> headers = AzureFileStorageHttpUtils.getBaseHeaders();
		headers.put(X_MS_WRITE, "update");
		headers.put(X_MS_RANGE, range);
		headers.put(CONTENT_LENGTH, String.valueOf(zipFile.length()));
		headers.put(CONTENT_TYPE, contentType);

		Map<String, String> queryParameters = new HashMap<>();
		queryParameters.put("comp", "range");

		String auth = AzureFileStorageHttpUtils.getAuthorizationString(PUT, account, accessKey, filePath, headers,
				queryParameters);
		headers.put(AUTHORIZATION, auth);
		RequestBody content = RequestBody.create(MediaType.parse(contentType), zipFile);
		return getApi().putData(filePath, headers, queryParameters, content).execute();
	}
}
