package com.teamscale.jacoco.agent.upload.azure;

import com.teamscale.jacoco.agent.upload.UploaderException;
import com.teamscale.jacoco.agent.util.Assertions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_ENCODING;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_LANGUAGE;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_LENGTH;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_MD_5;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_TYPE;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.DATE;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.IF_MATCH;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.IF_MODIFIED_SINCE;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.IF_NONE_MATCH;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.IF_UNMODIFIED_SINCE;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.RANGE;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_DATE;
import static com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_VERSION;

/** Utils class for communicating with an azure file storage. */
/* package */ class AzureFileStorageHttpUtils {

	/** Version of the azure file storage. Must be in every request */
	private static final String VERSION = "2018-03-28";

	/** Formatting pattern for every date in a request */
	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("E, dd MMM y HH:mm:ss z").withZone(
			ZoneId.of("GMT"));


	/** Creates the string that must be signed as the authorization for the request. */
	private static String createSignString(EHttpMethod httpMethod, Map<String, String> headers, String account,
			String path, Map<String, String> queryParameters) {
		Assertions.isTrue(headers.keySet().containsAll(Arrays.asList(X_MS_DATE, X_MS_VERSION)),
				"Headers for the azure request cannot be empty! At least 'x-ms-version' and 'x-ms-date' must be set");

		Map<String, String> xmsHeader = headers.entrySet().stream().filter(x -> x.getKey().startsWith("x-ms"))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		return String.join("\n", httpMethod.toString(),
				getStringOrEmpty(headers, CONTENT_ENCODING),
				getStringOrEmpty(headers, CONTENT_LANGUAGE),
				getStringOrEmpty(headers, CONTENT_LENGTH),
				getStringOrEmpty(headers, CONTENT_MD_5),
				getStringOrEmpty(headers, CONTENT_TYPE),
				getStringOrEmpty(headers, DATE),
				getStringOrEmpty(headers, IF_MODIFIED_SINCE),
				getStringOrEmpty(headers, IF_MATCH),
				getStringOrEmpty(headers, IF_NONE_MATCH),
				getStringOrEmpty(headers, IF_UNMODIFIED_SINCE),
				getStringOrEmpty(headers, RANGE),
				createCanonicalizedString(xmsHeader),
				createCanonicalizedResources(account, path, queryParameters));
	}

	/** Returns the value from the map for the given key or an empty string if the key does not exist. */
	private static String getStringOrEmpty(Map<String, String> map, String key) {
		return Objects.toString(map.get(key), "");
	}

	/** Creates the string for the canonicalized resources. */
	private static String createCanonicalizedResources(String account, String path, Map<String, String> options) {
		String canonicalizedResources = String.format("/%s%s", account, path);

		if (options.size() > 0) {
			canonicalizedResources += "\n" + createCanonicalizedString(options);
		}

		return canonicalizedResources;
	}

	/** Creates a string with a map where each key-value pair is in a newline separated by a colon. */
	private static String createCanonicalizedString(Map<String, String> options) {
		List<String> sortedKeys = new ArrayList<>(options.keySet());
		sortedKeys.sort(String::compareTo);

		List<String> values = sortedKeys.stream()
				.map(key -> String.format("%s:%s", key, options.get(key))).collect(Collectors.toList());
		return String.join("\n", values);
	}

	/** Creates the string which is needed for the authorization of an azure file storage request. */
	/* package */
	static String getAuthorizationString(EHttpMethod method, String account, String key, String path,
			Map<String, String> headers, Map<String, String> queryParameters)
			throws UploaderException {
		String stringToSign = createSignString(method, headers, account, path, queryParameters);

		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"));
			String authKey = new String(Base64.getEncoder().encode(mac.doFinal(stringToSign.getBytes("UTF-8"))));
			return "SharedKey " + account + ":" + authKey;
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			throw new UploaderException("Something is really wrong...", e);
		} catch (InvalidKeyException | IllegalArgumentException e) {
			throw new UploaderException(String.format("The given access key is malformed: %s", key), e);
		}
	}

	/** Returns the list of headers which must be present at every request */
	/* package */
	static Map<String, String> getBaseHeaders() {
		Map<String, String> headers = new HashMap<>();
		headers.put(X_MS_VERSION, AzureFileStorageHttpUtils.VERSION);
		headers.put(X_MS_DATE, FORMAT.format(LocalDateTime.now()));
		return headers;
	}

	/** Simple enum for all available HTTP methods. */
	public enum EHttpMethod {
		PUT,
		HEAD
	}
}

