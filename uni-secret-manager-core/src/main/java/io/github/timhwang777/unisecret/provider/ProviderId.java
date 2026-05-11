package io.github.timhwang777.unisecret.provider;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Open provider identity used by the core resolver and provider SPI.
 *
 * @param value normalized provider id
 */
public record ProviderId(String value) {

    private static final int MAX_LENGTH = 64;
    private static final Pattern VALID_PATTERN =
            Pattern.compile("[a-z0-9][a-z0-9-_.]*[a-z0-9]");

    public static final ProviderId AWS = new ProviderId("aws");
    public static final ProviderId GCP = new ProviderId("gcp");
    public static final ProviderId VAULT = new ProviderId("vault");
    public static final ProviderId LOCAL = new ProviderId("local");

    /**
     * Creates a normalized provider id.
     *
     * @param value raw provider id
     */
    public ProviderId {
        value = normalize(value);
        validate(value);
    }

    /**
     * Creates a normalized provider id from external input.
     *
     * @param value raw provider id
     * @return normalized provider id
     */
    public static ProviderId of(String value) {
        return new ProviderId(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static void validate(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Provider id must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Provider id must be no longer than " + MAX_LENGTH + " characters");
        }
        if (isPathLike(value)) {
            throw new IllegalArgumentException("Provider id must not be path-like: " + value);
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Provider id must match [a-z0-9][a-z0-9-_.]*[a-z0-9]: " + value);
        }
    }

    private static boolean isPathLike(String value) {
        return value.contains("/") || value.contains("\\") || value.contains("..");
    }

    @Override
    public String toString() {
        return value;
    }
}
