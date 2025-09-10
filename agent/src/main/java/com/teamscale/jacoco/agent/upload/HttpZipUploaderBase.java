package com.teamscale.jacoco.agent.upload;

import com.teamscale.client.FileSystemUtils;
import com.teamscale.client.HttpUtils;
import com.teamscale.jacoco.agent.logging.LoggingUtils;
import com.teamscale.jacoco.agent.util.Benchmark;
import com.teamscale.report.jacoco.CoverageFile;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Base class for uploading the coverage zip to a provided url */
public abstract class HttpZipUploaderBase<T> implements IUploader {

	/** The logger. */
	protected final Logger logger = LoggingUtils.getLogger(this);

	/** The URL to upload to. */
	protected HttpUrl uploadUrl;

	/** Additional files to include in the uploaded zip. */
	protected final List<Path> additionalMetaDataFiles;

	/** The API class. */
	private final Class<T> apiClass;

	/** The API which performs the upload */
	private T api;

	/** Constructor. */
	public HttpZipUploaderBase(HttpUrl uploadUrl, List<Path> additionalMetaDataFiles, Class<T> apiClass) {
		this.uploadUrl = uploadUrl;
		this.additionalMetaDataFiles = additionalMetaDataFiles;
		this.apiClass = apiClass;
	}

	/** Template method to configure the OkHttp Client. */
	protected void configureOkHttp(OkHttpClient.Builder builder) {
	}

	/** Returns the API for creating request to the http uploader */
	protected T getApi() {
		if (api == null) {
			Retrofit retrofit = HttpUtils.createRetrofit(retrofitBuilder -> retrofitBuilder.baseUrl(uploadUrl),
					this::configureOkHttp);
			api = retrofit.create(apiClass);
		}

		return api;
	}

	/** Uploads the coverage zip to the server */
	protected abstract Response<ResponseBody> uploadCoverageZip(File coverageFile)
			throws IOException, UploaderException;

	@Override
	public void upload(CoverageFile coverageFile) {
		try (Benchmark ignored = new Benchmark("Uploading report via HTTP")) {
			if (tryUpload(coverageFile)) {
				coverageFile.delete();
			} else {
				logger.warn("Failed to upload coverage to Teamscale. "
						+ "Won't delete local file {} so that the upload can automatically be retried upon profiler restart. "
						+ "Upload can also be retried manually.", coverageFile);
				if (this instanceof IUploadRetry) {
					((IUploadRetry) this).markFileForUploadRetry(coverageFile);
				}
			}
		} catch (IOException e) {
			logger.warn("Could not delete file {} after upload", coverageFile);
		}
	}

	/** Performs the upload and returns <code>true</code> if successful. */
	protected boolean tryUpload(CoverageFile coverageFile) {
		logger.debug("Uploading coverage to {}", uploadUrl);

		File zipFile;
		try {
			zipFile = createZipFile(coverageFile);
		} catch (IOException e) {
			logger.error("Failed to compile coverage zip file for upload to {}", uploadUrl, e);
			return false;
		}

		try {
			Response<ResponseBody> response = uploadCoverageZip(zipFile);
			if (response.isSuccessful()) {
				return true;
			}

			String errorBody = "<no server response>";
			if (response.errorBody() != null) {
				errorBody = response.errorBody().string();
			}

			logger.error("Failed to upload coverage to {}. Request failed with error code {}. Error:\n{}", uploadUrl,
					response.code(), errorBody);
			return false;
		} catch (IOException e) {
			logger.error("Failed to upload coverage to {}. Probably a network problem", uploadUrl, e);
			return false;
		} catch (UploaderException e) {
			logger.error("Failed to upload coverage to {}. The configuration is probably incorrect", uploadUrl, e);
			return false;
		} finally {
			zipFile.delete();
		}
	}

	/**
	 * Creates the zip file in the system temp directory to upload which includes the given coverage XML and all
	 * {@link #additionalMetaDataFiles}. The file is marked to be deleted on exit.
	 */
	private File createZipFile(CoverageFile coverageFile) throws IOException {
		File zipFile = Files.createTempFile(coverageFile.getNameWithoutExtension(), ".zip").toFile();
		zipFile.deleteOnExit();
		try (FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
			 ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
			fillZipFile(zipOutputStream, coverageFile);
			return zipFile;
		}
	}

	/**
	 * Fills the upload zip file with the given coverage XML and all {@link #additionalMetaDataFiles}.
	 */
	private void fillZipFile(ZipOutputStream zipOutputStream, CoverageFile coverageFile) throws IOException {
		zipOutputStream.putNextEntry(new ZipEntry(getZipEntryCoverageFileName(coverageFile)));
		coverageFile.copyStream(zipOutputStream);

		for (Path additionalFile : additionalMetaDataFiles) {
			zipOutputStream.putNextEntry(new ZipEntry(additionalFile.getFileName().toString()));
			zipOutputStream.write(FileSystemUtils.readFileBinary(additionalFile.toFile()));
		}
	}

	protected String getZipEntryCoverageFileName(CoverageFile coverageFile) {
		return "coverage.xml";
	}
}
