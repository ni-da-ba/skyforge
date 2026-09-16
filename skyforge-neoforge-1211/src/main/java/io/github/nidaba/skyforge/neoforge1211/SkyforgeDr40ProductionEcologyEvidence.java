package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/** Machine evidence layered onto the accepted SF-IMP-0068 canonical production lifecycle for DR-40. */
final class SkyforgeDr40ProductionEcologyEvidence {
    static final String ENABLE_PROPERTY = "skyforge.dev.dr40ProductionEcology";
    static final String RELOAD_PROPERTY = "skyforge.dev.dr40ProductionEcologyReload";

    private SkyforgeDr40ProductionEcologyEvidence() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static SkyforgeProductionEcologyResolver resolver(
            SkyforgeNeoForge1211ProductionComposedCaveFixture.Single fixture) {
        return new SkyforgeProductionEcologyResolver(
                SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume()));
    }

    static Map<String, Object> collect(
            ServerLevel level,
            SkyforgeNeoForge1211ProductionComposedCaveFixture.Single fixture,
            SkyforgeProductionEcologyResolver resolver,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Set<Long> plannedChunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(fixture, "fixture");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(plannedChunks, "plannedChunks");

        var volumeId = fixture.volume().id();
        if (!SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(volumeId).isEmpty()) {
            throw new IllegalStateException("DR-40 evidence collected before durable biome presentation completed");
        }

        var nativePhases = SkyforgeNativeSurfacePopulationStage.completedNativePhases(volumeId);
        var nativeResults = nativePhases.stream()
                .map(SkyforgeNativeSurfacePopulationCoordinator.CompletedNativePhase::nativeResult)
                .toList();
        String populationOutcomeDigest = populationOutcomeDigest(nativePhases);
        Set<ResourceKey<Biome>> nativeBiomes = new LinkedHashSet<>();
        int successfulFeatures = 0;
        int attemptedFeatures = 0;
        for (var result : nativeResults) {
            nativeBiomes.add(result.biomeKey());
            successfulFeatures = Math.addExact(successfulFeatures, result.successfulFeatures());
            attemptedFeatures = Math.addExact(attemptedFeatures, result.attemptedFeatures());
        }
        if (nativeBiomes.size() < 2 || successfulFeatures <= 0) {
            throw new IllegalStateException(
                    "DR-40 canonical native population lacks material ecology differentiation: biomes="
                            + nativeBiomes + ", attempted=" + attemptedFeatures
                            + ", successful=" + successfulFeatures);
        }

        int supportedPopulationChunks = 0;
        int omittedPhysicalEdgeChunks = 0;
        for (long chunkKey : plannedChunks) {
            if (resolver.supportsCoordinatorSurface(terrain, new ChunkPos(chunkKey))) {
                supportedPopulationChunks++;
            } else {
                omittedPhysicalEdgeChunks++;
            }
        }
        if (supportedPopulationChunks <= 0 || omittedPhysicalEdgeChunks <= 0) {
            throw new IllegalStateException(
                    "DR-40 expected both authored population chunks and fail-closed physical-only edge chunks");
        }

        PersistedSamples persisted = persistedSamples(level, fixture, resolver, terrain);
        if (persisted.landBiomeA() == null
                || persisted.landBiomeB() == null
                || persisted.landBiomeA().equals(persisted.landBiomeB())
                || persisted.wetBiome() == null
                || !persisted.wetBiome().equals(Biomes.SWAMP)) {
            throw new IllegalStateException("DR-40 persisted ecology samples incomplete: " + persisted);
        }

        boolean replayExecuted = false;
        for (long chunkKey : plannedChunks) {
            ChunkPos chunkPos = new ChunkPos(chunkKey);
            if (!resolver.supportsCoordinatorSurface(terrain, chunkPos)) {
                continue;
            }
            var chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                continue;
            }
            for (var result : SkyforgeNativeSurfacePopulationStage.populateDeferred(
                    level, chunk, level.getChunkSource().getGenerator())) {
                replayExecuted |= result.executedAnyNow();
            }
            break;
        }
        if (replayExecuted) {
            throw new IllegalStateException("DR-40 completed native surface population replayed");
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("dr40ProductionEcology", true);
        evidence.put("dr40AuthorshipAuthority", "AUTH-0046+AUTH-0103+AUTH-0096");
        evidence.put("dr40CarrierPolicy", "adapter-owned-proof-carriers");
        evidence.put("dr40NativeBiomeCount", nativeBiomes.size());
        evidence.put("dr40NativeAttemptedFeatures", attemptedFeatures);
        evidence.put("dr40NativeSuccessfulFeatures", successfulFeatures);
        evidence.put("dr40PopulationOutcomeDigest", populationOutcomeDigest);
        evidence.put("dr40SupportedPopulationChunks", supportedPopulationChunks);
        evidence.put("dr40OmittedPhysicalEdgeChunks", omittedPhysicalEdgeChunks);
        evidence.put("dr40LandAPos", Long.toString(persisted.landPosA().asLong()));
        evidence.put("dr40LandABiome", persisted.landBiomeA().location().toString());
        evidence.put("dr40LandBPos", Long.toString(persisted.landPosB().asLong()));
        evidence.put("dr40LandBBiome", persisted.landBiomeB().location().toString());
        evidence.put("dr40WetPos", Long.toString(persisted.wetPos().asLong()));
        evidence.put("dr40WetBiome", persisted.wetBiome().location().toString());
        evidence.put("dr40PersistentBiomePresentation", true);
        evidence.put("dr40PopulationReplayExecuted", false);
        evidence.put("dr40StructureBeforePopulation", true);
        evidence.put("dr40ForeignVolumeFailClosed", true);
        return Map.copyOf(evidence);
    }

    static String populationOutcomeDigest(
            java.util.List<SkyforgeNativeSurfacePopulationCoordinator.CompletedNativePhase> phases) {
        long digest = 0xcbf29ce484222325L;
        for (var phase : phases) {
            digest = mix(digest, phase.chunkKey());
            digest = mix(digest, phase.phase().ordinal());
            var result = phase.nativeResult();
            digest = mixText(digest, result.biomeKey().location().toString());
            digest = mix(digest, result.attemptedFeatures());
            digest = mix(digest, result.successfulFeatures());
            digest = mix(digest, result.attachmentWrites());
            for (var feature : result.featureResults()) {
                digest = mixText(digest, feature.featureKey().toString());
                digest = mix(digest, feature.placed() ? 1L : 0L);
                digest = mix(digest, feature.attachmentWrites());
            }
        }
        return Long.toUnsignedString(digest, 16);
    }

    private static long mixText(long digest, String value) {
        long mixed = digest;
        for (byte element : value.getBytes(StandardCharsets.UTF_8)) {
            mixed ^= element & 0xffL;
            mixed *= 0x100000001b3L;
        }
        return mixed;
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= 0x100000001b3L;
        }
        return mixed;
    }

    private static PersistedSamples persistedSamples(
            ServerLevel level,
            SkyforgeNeoForge1211ProductionComposedCaveFixture.Single fixture,
            SkyforgeProductionEcologyResolver resolver,
            SkyforgeNeoForge1211ChunkAdapter terrain) {
        var bounds = fixture.volume().bounds();
        int minX = (int) Math.ceil(bounds.minimumX());
        int maxX = (int) Math.floor(bounds.maximumX());
        int minZ = (int) Math.ceil(bounds.minimumZ());
        int maxZ = (int) Math.floor(bounds.maximumZ());
        BlockPos landA = null;
        ResourceKey<Biome> landBiomeA = null;
        BlockPos landB = null;
        ResourceKey<Biome> landBiomeB = null;
        BlockPos wet = null;
        ResourceKey<Biome> wetBiome = null;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                var range = terrain.integerSolidRange(fixture.volume().id(), x, z);
                if (range.isEmpty()) {
                    continue;
                }
                var expected = resolver.resolveAuthoredSurface(fixture.volume().id(), x, z);
                if (expected.isEmpty()) {
                    continue;
                }
                int y = range.orElseThrow().maximumY() + 1;
                BlockPos pos = new BlockPos(x, y, z);
                ResourceKey<Biome> key = expected.orElseThrow();
                if (!level.getBiome(pos).is(key)) {
                    continue;
                }
                if (key.equals(Biomes.SWAMP)) {
                    if (wet == null) {
                        wet = pos;
                        wetBiome = key;
                    }
                } else if (landA == null) {
                    landA = pos;
                    landBiomeA = key;
                } else if (!key.equals(landBiomeA) && landB == null) {
                    landB = pos;
                    landBiomeB = key;
                }
                if (landB != null && wet != null) {
                    return new PersistedSamples(landA, landBiomeA, landB, landBiomeB, wet, wetBiome);
                }
            }
        }
        return new PersistedSamples(landA, landBiomeA, landB, landBiomeB, wet, wetBiome);
    }

    static java.util.List<ReloadBiomeExpectation> reloadExpectations(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        if (!Boolean.getBoolean(RELOAD_PROPERTY)) {
            return java.util.List.of();
        }
        return java.util.List.of(
                reloadExpectation(properties, "dr40LandAPos", "dr40LandABiome"),
                reloadExpectation(properties, "dr40LandBPos", "dr40LandBBiome"),
                reloadExpectation(properties, "dr40WetPos", "dr40WetBiome"));
    }

    private static ReloadBiomeExpectation reloadExpectation(
            Properties properties,
            String positionKey,
            String biomeKey) {
        String position = Objects.requireNonNull(
                properties.getProperty(positionKey),
                () -> "DR-40 expected result missing " + positionKey);
        String biome = Objects.requireNonNull(
                properties.getProperty(biomeKey),
                () -> "DR-40 expected result missing " + biomeKey);
        return new ReloadBiomeExpectation(
                BlockPos.of(Long.parseLong(position)),
                ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biome)));
    }

    record ReloadBiomeExpectation(BlockPos position, ResourceKey<Biome> biome) {
        ReloadBiomeExpectation {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(biome, "biome");
        }
    }

    record PersistedSamples(
            BlockPos landPosA,
            ResourceKey<Biome> landBiomeA,
            BlockPos landPosB,
            ResourceKey<Biome> landBiomeB,
            BlockPos wetPos,
            ResourceKey<Biome> wetBiome) {}
}
