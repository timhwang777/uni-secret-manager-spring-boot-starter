package io.github.timhwang777.unisecret.provider;

import java.util.Map;
import java.util.Optional;

interface VaultSecretOperations {

    Optional<Map<String, Object>> read(String key, String version);
}
