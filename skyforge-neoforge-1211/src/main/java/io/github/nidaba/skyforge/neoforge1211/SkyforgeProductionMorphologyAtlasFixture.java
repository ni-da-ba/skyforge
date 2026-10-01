package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
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
 * Exact Minecraft carrier fixtures for the accepted AUTH-0083 morphology review corpus.
 *
 * <p>AUTH-0083's canonical SMALL descriptor is reconstructed exactly in the backend adapter rather
 * than introducing a dependency on the reference-evidence module. The only Minecraft adjustment is
 * an integer suspension-Y translation into the build interval. Full detail and full provider
 * secondary morphology remain enabled through the provider-neutral production compiler.
 *
 * <p>AUTH-0052 intentionally does not certify true non-endpoint provider blends. HS-03 therefore
 * derives their finite horizontal scan domain from the constituent positive-inside support proofs.
 * Outside both constituent domains both footprint residuals are non-positive, so their convex blend
 * is also non-positive. The crescent fixture's radius is proved directly from its normalized
 * along/across equations. Exact voxel scanning then rejects any result that touches the proof bound.
 */
final class SkyforgeProductionMorphologyAtlasFixture {
    static final long GEOMETRY_SEED = 0x534b59464f524745L;

    private static final long ROOT_SEED = 0x5346494d50303082L;
    private static final double SOURCE_SUSPENSION = 512.0;
    private static final int TARGET_MINIMUM_SOLID_Y = 96;
    private static final MorphologyProviderId REFERENCE_CRESCENT =
            new MorphologyProviderId("reference", "crescent");
    private static final Map<Member, Fixture> FIXTURES = new EnumMap<>(Member.class);

    private SkyforgeProductionMorphologyAtlasFixture() {}

    static Fixture fixture(Member member) {
        synchronized (FIXTURES) {
            return FIXTURES.computeIfAbsent(Objects.requireNonNull(member, "member"),
                    SkyforgeProductionMorphologyAtlasFixture::buildFixture);
        }
    }

    static Member member(String id) {
        for (Member member : Member.values()) {
            if (member.id().equals(id)) {
                return member;
            }
        }
        throw new IllegalArgumentException("unknown production morphology atlas member: " + id);
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

    private static Fixture buildFixture(Member member) {
        SkyIslandMorphologySpec morphology = member.morphology();
        var compiler = new SkyIslandMorphologySpecCompiler();
        var registry = registry(member);
        SkyIslandTerrainProfile profile = SkyIslandTerrainProfile.reference();

        SkyIslandVolumeDescriptor sourceDescriptor = descriptor(SOURCE_SUSPENSION);
        SupportCompilation sourceCompilation = compileExactSupport(
                member,
                sourceDescriptor,
                morphology,
                registry,
                compiler,
                profile);
        var sourceSupport = sourceCompilation.support();

        int verticalTranslation = Math.subtractExact(
                TARGET_MINIMUM_SOLID_Y,
                toExactInt(sourceSupport.bounds().minimumY()));
        SkyIslandVolumeDescriptor translatedDescriptor =
                descriptor(SOURCE_SUSPENSION + verticalTranslation);
        SupportCompilation translatedCompilation = compileExactSupport(
                member,
                translatedDescriptor,
                morphology,
                registry,
                compiler,
                profile);
        var translatedSupport = translatedCompilation.support();

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

    private static SupportCompilation compileExactSupport(
            Member member,
            SkyIslandVolumeDescriptor descriptor,
            SkyIslandMorphologySpec morphology,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandMorphologySpecCompiler compiler,
            SkyIslandTerrainProfile profile) {
        var compilation = compiler.compileWithSupport(descriptor, morphology, registry);
        var certificate = compilation.supportEnvelope();
        if (certificate.isPresent()) {
            return new SupportCompilation(
                    compilation.volume(),
                    SkyforgeExactVoxelSupportBounds.derive(
                            compilation.volume(), certificate.orElseThrow(), profile));
        }
        if (!(morphology instanceof ProviderBlendMorphologySpec blend)) {
            throw new IllegalStateException(
                    member.id() + " lacks both AUTH-0052 support and an HS-03 blend-domain proof");
        }
        double horizontalRadius = provenBlendHorizontalRadius(descriptor, blend.blend(), registry);
        return new SupportCompilation(
                compilation.volume(),
                SkyforgeExactVoxelSupportBounds.deriveWithinHorizontalRadius(
                        compilation.volume(),
                        horizontalRadius,
                        profile,
                        "hs03-provider-blend-horizontal-proof-v1:" + blend.blend().pairIdentifier()));
    }

    private static double provenBlendHorizontalRadius(
            SkyIslandVolumeDescriptor descriptor,
            MorphologyProviderBlend blend,
            SkyIslandMorphologyProviderRegistry registry) {
        double first = provenProviderHorizontalRadius(descriptor, blend.first(), registry);
        double second = provenProviderHorizontalRadius(descriptor, blend.second(), registry);
        return Math.nextUp(Math.max(first, second));
    }

    private static double provenProviderHorizontalRadius(
            SkyIslandVolumeDescriptor descriptor,
            MorphologyProviderId providerId,
            SkyIslandMorphologyProviderRegistry registry) {
        SkyIslandMorphologyProvider provider = registry.require(providerId);
        var certificate = provider.certifiedPrimarySupportEnvelope(descriptor);
        if (certificate.isPresent()) {
            return certificate.orElseThrow().maximumHorizontalRadius();
        }
        if (REFERENCE_CRESCENT.equals(providerId)) {
            return referenceCrescentHorizontalRadius(descriptor);
        }
        throw new IllegalStateException(
                "HS-03 true blend constituent has no analytical horizontal support proof: " + providerId);
    }

    /**
     * Conservative radial support proof for the fixture-only reference crescent.
     *
     * <p>Positive footprint requires |along| &lt; 1 and |across| &lt; 1. The normalized bend is
     * 0.62 * (along^2 - 0.22), whose maximum absolute value over |along| &lt;= 1 is 0.62 * 0.78.
     * Rotation preserves Euclidean radius, so the hypotenuse of the resulting longitudinal and
     * transverse bounds contains every positive-inside point.
     */
    private static double referenceCrescentHorizontalRadius(SkyIslandVolumeDescriptor descriptor) {
        double radius = descriptor.nominalRadius();
        double maximumAlong = 1.05 * radius;
        double maximumAcross = 0.58 * radius * (1.0 + 0.62 * 0.78);
        return Math.nextUp(Math.hypot(maximumAlong, maximumAcross));
    }

    private static SkyIslandMorphologyProviderRegistry registry(Member member) {
        if (!member.requiresReferenceCrescent()) {
            return SkyIslandMorphologyProviders.builtInRegistry();
        }
        SkyIslandMorphologyProviderRegistry.Builder builder = SkyIslandMorphologyProviderRegistry.builder();
        for (SkyIslandMorphologyProvider provider : SkyIslandMorphologyProviders.builtInRegistry().providers()) {
            builder.register(provider);
        }
        builder.register(new SkyforgeHs03ReferenceCrescentFixtureProvider());
        return builder.build();
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
        LOBED("builtin-lobed-small-seed-skyforge", MorphologyFamily.LOBED),
        HYBRID_MASSIF_SPINE("hybrid-massif-spine-midpoint", MorphologyFamily.MASSIF),
        PROVIDER_CRESCENT_LOBED("provider-crescent-to-lobed-midpoint", MorphologyFamily.LOBED);

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

        static java.util.List<Member> builtInMembers() {
            return java.util.List.of(TABLELAND, SPINE, BASIN, LOBED);
        }

        boolean requiresReferenceCrescent() {
            return this == PROVIDER_CRESCENT_LOBED;
        }

        SkyIslandMorphologySpec morphology() {
            return switch (this) {
                case HYBRID_MASSIF_SPINE -> ProviderBlendMorphologySpec.full(new MorphologyProviderBlend(
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.SPINE), 0.5));
                case PROVIDER_CRESCENT_LOBED -> ProviderBlendMorphologySpec.full(new MorphologyProviderBlend(
                        REFERENCE_CRESCENT,
                        SkyIslandMorphologyProviders.builtInId(MorphologyFamily.LOBED), 0.5));
                default -> ProviderMorphologySpec.full(SkyIslandMorphologyProviders.builtInId(family));
            };
        }
    }

    private record SupportCompilation(
            CompiledSkyIslandVolume volume,
            SkyforgeExactVoxelSupportBounds.Result support) {
        SupportCompilation {
            Objects.requireNonNull(volume, "volume");
            Objects.requireNonNull(support, "support");
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
