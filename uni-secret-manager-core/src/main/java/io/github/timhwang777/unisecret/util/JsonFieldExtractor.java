package io.github.timhwang777.unisecret.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.timhwang777.unisecret.exception.SecretParsingException;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility for extracting specific fields from JSON-formatted secrets.
 *
 * <h2>Purpose</h2>
 * Many secrets are stored as JSON objects containing multiple values:
 * <pre>{@code
 * {
 *   "username": "admin",
 *   "password": "secret123",
 *   "host": "db.example.com",
 *   "port": 5432
 * }
 * }</pre>
 * This utility extracts individual fields from such secrets, so you can inject
 * just the password field into your application.
 *
 * <h2>Usage with @SecretValue</h2>
 * <pre>{@code
 * // Whole secret (returns entire JSON string)
 * @SecretValue("database-config")
 * private String fullConfig;
 *
 * // Single field extraction
 * @SecretValue(value = "database-config", field = "password")
 * private String password;  // Gets "secret123"
 *
 * // Nested field extraction (dot notation)
 * @SecretValue(value = "config", field = "database.connection.password")
 * private String nestedPassword;
 * }</pre>
 *
 * <h2>Supported Path Syntax</h2>
 * <ul>
 *   <li>{@code "fieldName"} - Top-level field</li>
 *   <li>{@code "parent.child"} - Nested field</li>
 *   <li>{@code "a.b.c.d"} - Deeply nested field</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * Throws {@link SecretParsingException} when:
 * <ul>
 *   <li>JSON is malformed</li>
 *   <li>Field path doesn't exist</li>
 *   <li>Field value is null</li>
 * </ul>
 *
 * @see io.github.timhwang777.unisecret.exception.SecretParsingException
 */
@Slf4j
public class JsonFieldExtractor {

    /**
     * Shared Jackson ObjectMapper for JSON parsing.
     * ObjectMapper is thread-safe and expensive to create, so we reuse one instance.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Extracts a field value from a JSON string using dot-notation path.
     *
     * <h3>Examples</h3>
     * <pre>{@code
     * // JSON: {"password": "secret"}
     * extractField(json, "password") → "secret"
     *
     * // JSON: {"db": {"password": "secret"}}
     * extractField(json, "db.password") → "secret"
     *
     * // If fieldPath is null/blank, returns the original JSON
     * extractField(json, null) → json
     * }</pre>
     *
     * @param jsonString the JSON string to parse
     * @param fieldPath  the dot-notation path to the field (e.g., "database.password"),
     *                   or null/blank to return the entire JSON string
     * @return the extracted field value as a string
     * @throws SecretParsingException if JSON is invalid, field not found, or field is null
     */
    public static String extractField(String jsonString, String fieldPath) {
        // If no field path specified, return the raw JSON
        if (fieldPath == null || fieldPath.isBlank()) {
            return jsonString;
        }

        try {
            // Parse the JSON string into a tree structure
            JsonNode root = OBJECT_MAPPER.readTree(jsonString);

            // Split the path into parts (e.g., "database.password" → ["database", "password"])
            String[] pathParts = fieldPath.split("\\.");

            // Navigate through the JSON tree following the path
            JsonNode current = root;
            for (String part : pathParts) {
                // Check if current node has the expected child
                if (current == null || !current.has(part)) {
                    throw new SecretParsingException(
                            String.format("Field '%s' not found in JSON at path '%s'", part, fieldPath)
                    );
                }
                // Move to the child node
                current = current.get(part);
            }

            // Ensure we didn't end up at a null value
            if (current == null || current.isNull()) {
                throw new SecretParsingException(
                        String.format("Field at path '%s' is null", fieldPath)
                );
            }

            // Convert the final node to a string value
            // asText() handles primitives (string, number, boolean) appropriately
            return current.asText();

        } catch (SecretParsingException e) {
            // Re-throw our own exceptions directly
            throw e;
        } catch (Exception e) {
            // Wrap any other exceptions (JSON parsing errors, etc.)
            log.error("Failed to parse JSON and extract field '{}': {}", fieldPath, e.getMessage());
            throw new SecretParsingException(
                    String.format("Failed to parse JSON and extract field '%s'", fieldPath),
                    e
            );
        }
    }
}
