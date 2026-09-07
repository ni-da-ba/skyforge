package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompiler;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;

/**
 * Exact Minecraft carrier fixtures for the remaining AUTH-0083 SMALL / seed-skyforge built-ins.
 *
 * <p>AUTH-0083's canonical SMALL descriptor is reconstructed exactly in the backend adapter rather
 * than introducing a dependency on the reference-evidence module. The only Minecraft adjustment is
 * an integer suspension-Y translation into the build interval. Full detail and full provider
 * secondary morphology remain enabled through the provider-neutral production compiler.
 */
final class SkyforgeProductionMorphologyAtlasFixture {
    static final long GEOMETRY_SEED = 0x534b59464f524745L;

    private static final long ROOT_SEED = 0x5346494d50303082L;
    private static final double SOURCE_SUSPENSION = 512.0;
    private static final int TARGET_MINIMUM_SOLID_Y = 96;
    private static final Map<Member, Fixture> FIXTURES = buildFixtures();

    private SkyforgeProductionMorphologyAtlasFixture() {}

    static Fixture fixture(Member member) {
        return Objects.requireNonNull(FIXTURES.get(Objects.requireNonNull(member, "member")));
    }

    static Member member(String id) {
        for (Member member : Member.values()) {
            if (member.id().equals(id)) {
                return member;
            }
        }
        throw new IllegalArgumentException("unknown SF-IMP-0082 atlas member: " + id);
    }

    static Set<Long> footprintChunkKeys(WorldBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        int minimumChunkX = Math.floorDiv(toExactInt(bounds.minimumX()), 16);
        int maximumChunkX = Math.floorDiv(toExactInt(bounds.maximumX()), 16);
        int minimumChunkZ = Math.floorDiv(toExactInt(bounds.minimumZ()), 16);
        int maximumChunkZ = Math.floorDiv(toExactInt(bounds.maximumZ()), 16);
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                result.add(new ChunkPos(chunkX, chunkZ).toLong());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    static int toExactInt(double value) {
        int integer = (int) value;
        if (integer != value) {
            throw new IllegalArgumentException("expected exact integer coordinate, found " + value);
        }
        return integer;
    }

    private static Map<Member, Fixture> buildFixtures() {
        EnumMap<Member, Fixture> fixtures = new EnumMap<>(Member.class);
        for (Member member : Member.values()) {
            fixtures.put(member, buildFixture(member));
        }
        return Map.copyOf(fixtures);
    }

    private static Fixture buildFixture(Member member) {
        ProviderMorphologySpec morphology = ProviderMorphologySpec.full(
                SkyIslandMorphologyProviders.builtInId(member.family()));
        var compiler = new SkyIslandMorphologySpecCompiler();
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        SkyIslandTerrainProfile profile = SkyIslandTerrainProfile.reference();

        SkyIslandVolumeDescriptor sourceDescriptor = descriptor(SOURCE_SUSPENSION);
        var sourceCompilation = compiler.compileWithSupport(sourceDescriptor, morphology, registry);
        var sourceCertificate = sourceCompilation.supportEnvelope()
                .orElseThrow(() -> new IllegalStateException(
                        member.id() + " unexpectedly lost its certified support envelope"));
        var sourceSupport = SkyforgeExactVoxelSupportBounds.derive(
                sourceCompilation.volume(), sourceCertificate, profile);

        int verticalTranslation = Math.subtractExact(
                TARGET_MINIMUM_SOLID_Y,
                toExactInt(sourceSupport.bounds().minimumY()));
        SkyIslandVolumeDescriptor translatedDescriptor =
                descriptor(SOURCE_SUSPENSION + verticalTranslation);
        var translatedCompilation =
                compiler.compileWithSupport(translatedDescriptor, morphology, registry);
        var translatedCertificate = translatedCompilation.supportEnvelope().orElseThrow();
        var translatedSupport = SkyforgeExactVoxelSupportBounds.derive(
                translatedCompilation.volume(), translatedCertificate, profile);

        requirePureIntegerTranslation(
                member, sourceSupport.bounds(), translatedSupport.bounds(), verticalTranslation);
        if (toExactInt(translatedSupport.bounds().minimumY()) != TARGET_MINIMUM_SOLID_Y
                || translatedSupport.bounds().maximumY() >= 320.0) {
            throw new IllegalStateException(
                    member.id() + " does not fit the Minecraft build interval after pure translation: "
                            + translatedSupport.bounds());
        }

        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(
                ROOT_SEED,
                "sf-imp-0082-morphology-atlas",
                member.ordinal(),
                0,
                GEOMETRY_SEED);
        SkyIslandWorldVolume worldVolume = new SkyIslandWorldVolume(
                volumeId,
                translatedSupport.bounds(),
                translatedCompilation.volume());
        SkyIslandWorldCatalog catalog =
                new SkyIslandWorldCatalog(ROOT_SEED, java.util.List.of(worldVolume));
        Set<Long> footprint = footprintChunkKeys(translatedSupport.bounds());

        return new Fixture(
                member,
                morphology.stableIdentifier(),
                sourceDescriptor,
                translatedDescriptor,
                verticalTranslation,
                sourceSupport,
                translatedSupport,
                catalog,
                volumeId,
                footprint);
    }

    private static SkyIslandVolumeDescriptor descriptor(double suspensionElevation) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                GEOMETRY_SEED,
                0.0,
                0.0,
                suspensionElevation,
                160.0,
                60.0,
                80.0,
                40.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                20.0);
    }

    private static void requirePureIntegerTranslation(
            Member member,
            WorldBounds source,
            WorldBounds translated,
            int deltaY) {
        if (source.minimumX() != translated.minimumX()
                || source.maximumX() != translated.maximumX()
                || source.minimumZ() != translated.minimumZ()
                || source.maximumZ() != translated.maximumZ()
                || source.minimumY() + deltaY != translated.minimumY()
                || source.maximumY() + deltaY != translated.maximumY()) {
            throw new IllegalStateException(
                    member.id() + " Minecraft translation changed AUTH-0083 discrete morphology support");
        }
    }

    enum Member {
        TABLELAND("builtin-tableland-small-seed-skyforge", MorphologyFamily.TABLELAND),
        SPINE("builtin-spine-small-seed-skyforge", MorphologyFamily.SPINE),
        BASIN("builtin-basin-small-seed-skyforge", MorphologyFamily.BASIN),
        LOBED("builtin-lobed-small-seed-skyforge", MorphologyFamily.LOBED);

        private final String id;
        private final MorphologyFamily family;

        Member(String id, MorphologyFamily family) {
            this.id = id;
            this.family = family;
        }

        String id() {
            return id;
        }

        MorphologyFamily family() {
            return family;
        }

        String commandId() {
            return family.identifier();
        }
    }

    record Fixture(
            Member member,
            String morphologyIdentifier,
            SkyIslandVolumeDescriptor sourceDescriptor,
            SkyIslandVolumeDescriptor translatedDescriptor,
            int verticalTranslation,
            SkyforgeExactVoxelSupportBounds.Result sourceSupport,
            SkyforgeExactVoxelSupportBounds.Result exactSupport,
            SkyIslandWorldCatalog catalog,
            SkyIslandWorldVolumeId volumeId,
            Set<Long> footprintChunkKeys) {
        Fixture {
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(morphologyIdentifier, "morphologyIdentifier");
            Objects.requireNonNull(sourceDescriptor, "sourceDescriptor");
            Objects.requireNonNull(translatedDescriptor, "translatedDescriptor");
            Objects.requireNonNull(sourceSupport, "sourceSupport");
            Objects.requireNonNull(exactSupport, "exactSupport");
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(volumeId, "volumeId");
            footprintChunkKeys = Set.copyOf(Objects.requireNonNull(footprintChunkKeys, "footprintChunkKeys"));
            if (footprintChunkKeys.isEmpty()) {
                throw new IllegalArgumentException("atlas fixture footprint must not be empty");
            }
        }
    }
}
