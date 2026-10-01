package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.Locale;
import java.util.Objects;

/**
 * Stable backend-neutral AUTH-0038 key for one semantic palette binding.
 *
 * <p>The key identifies where a downstream backend should reuse one concrete binding decision. It
 * contains no backend registry or material identifier.
 */
public record SkyIslandSemanticPaletteBindingKey(
        int schemaVersion,
        SkyIslandIdentity islandIdentity,
        SkyIslandSemanticMaterialPaletteRole role,
        SkyIslandLithologicRealizationChannel sourceChannel,
        SkyIslandSemanticPaletteBindingDomainKind domainKind,
        int anchorId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandSemanticPaletteBindingKey {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported semantic palette binding-key schema: " + schemaVersion);
        }
        islandIdentity = Objects.requireNonNull(islandIdentity, "islandIdentity");
        role = Objects.requireNonNull(role, "role");
        sourceChannel = Objects.requireNonNull(sourceChannel, "sourceChannel");
        domainKind = Objects.requireNonNull(domainKind, "domainKind");
        if (anchorId < 0) {
            throw new IllegalArgumentException("binding-key anchorId must be non-negative");
        }
    }

    public static SkyIslandSemanticPaletteBindingKey of(
            SkyIslandIdentity identity,
            SkyIslandSemanticMaterialPaletteRole role,
            SkyIslandLithologicRealizationChannel sourceChannel,
            SkyIslandSemanticPaletteBindingDomainKind domainKind,
            int anchorId) {
        return new SkyIslandSemanticPaletteBindingKey(
                SCHEMA_VERSION,
                identity,
                role,
                sourceChannel,
                domainKind,
                anchorId);
    }

    /** Portable deterministic token suitable for backend cache/persistence keys. */
    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfbind:v%d:%016x:%016x:%016x:%016x:%s:%s:%s:%08x",
                schemaVersion,
                islandIdentity.worldSeed(),
                islandIdentity.provinceKey(),
                islandIdentity.clusterKey(),
                islandIdentity.islandKey(),
                domainKind.name().toLowerCase(Locale.ROOT),
                role.name().toLowerCase(Locale.ROOT),
                sourceChannel.name().toLowerCase(Locale.ROOT),
                anchorId);
    }
}
