package com.teamscale.client

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import java.io.File
import java.io.IOException

/**
 * Utility class for serializing and deserializing JSON using Jackson.
 */
object JsonUtils {
	/**
	 * Jackson ObjectMapper that is used for serializing and deserializing JSON objects. The visibility settings of the
	 * OBJECT_MAPPER are configured to include all fields when serializing or deserializing objects, regardless of their
	 * visibility modifiers (public, private, etc.).
	 */
	val OBJECT_MAPPER: ObjectMapper = JsonMapper.builder()
		.visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
		.visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
		.defaultPropertyInclusion(JsonInclude.Value.ALL_NON_NULL)
		.build()

	/**
	 * Creates a new instance of [JsonFactory] using the default [ObjectMapper].
	 */
	fun createFactory() = JsonFactory(OBJECT_MAPPER)

	/**
	 * Deserializes a JSON string into an object of the given class.
	 */
	@Throws(JsonProcessingException::class)
	@JvmStatic
	fun <T> deserialize(json: String, clazz: Class<T>): T =
		OBJECT_MAPPER.readValue(json, clazz)

	/**
	 * Deserializes a JSON string into an object of the given class.
	 */
	@Throws(JsonProcessingException::class)
	@JvmStatic
	inline fun <reified T> deserialize(json: String): T =
		OBJECT_MAPPER.readValue(json, T::class.java)

	/**
	 * Deserializes the contents of the given file into an object of the given class.
	 */
	@Throws(IOException::class)
	fun <T> deserializeFile(file: File, clazz: Class<T>): T =
		OBJECT_MAPPER.readValue(file, clazz)

	/**
	 * Deserializes a JSON string into a list of objects of the given class.
	 */
	@Throws(JsonProcessingException::class)
	@JvmStatic
	inline fun <reified T> deserializeList(json: String): List<T> =
		OBJECT_MAPPER.readValue(
			json, OBJECT_MAPPER.typeFactory.constructCollectionLikeType(MutableList::class.java, T::class.java)
		)

	/**
	 * Serializes an object into its JSON representation.
	 */
	@JvmStatic
	@Throws(JsonProcessingException::class)
	fun Any.serializeToJson(): String =
		OBJECT_MAPPER.writeValueAsString(this)

	/**
	 * Serializes an object to a file with pretty printing enabled.
	 */
	@Throws(IOException::class)
	fun <T> serializeToFile(file: File, value: T) {
		OBJECT_MAPPER.writer().withDefaultPrettyPrinter().writeValue(file, value)
	}
}
