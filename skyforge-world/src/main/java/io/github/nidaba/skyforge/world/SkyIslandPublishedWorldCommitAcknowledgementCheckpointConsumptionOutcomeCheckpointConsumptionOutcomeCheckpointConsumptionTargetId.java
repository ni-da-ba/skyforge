package io.github.nidaba.skyforge.world;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * AUTH-0080 backend-neutral identity for one downstream audit/storage target that consumes an exact
 * AUTH-0078 outcome-checkpoint-consumption outcome checkpoint.
 *
 * <p>The namespace/key pair names a destination conceptually. This type performs no connection,
 * storage, replication, filesystem, or network behavior.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId(
        int schemaVersion,
        String namespace,
        String key) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported outcome-checkpoint-consumption outcome-checkpoint consumption target schema: " + schemaVersion);
        }
        namespace = canonicalComponent(namespace, "namespace");
        key = canonicalComponent(key, "key");
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                    of(String namespace, String key) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId(
                SCHEMA_VERSION,
                namespace,
                key);
    }

    public String canonicalToken() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return "sfackcpoutcpouttarget:v"
                + schemaVersion
                + ":"
                + encoder.encodeToString(namespace.getBytes(StandardCharsets.UTF_8))
                + ":"
                + encoder.encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalComponent(String value, String name) {
        value = Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException(name + " must not contain surrounding whitespace");
        }
        return value;
    }
}
