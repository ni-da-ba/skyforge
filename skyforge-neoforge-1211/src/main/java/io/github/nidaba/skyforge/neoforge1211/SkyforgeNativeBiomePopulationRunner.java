package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Executes one native biome generation step inside one exact Skyforge world volume. */
final class SkyforgeNativeBiomePopulationRunner {
    private static final String BIOME_PROOF_PROPERTY = "skyforge.dev.biomePopulation";
    private static final System.Logger LOGGER = System.getLogger(SkyforgeNativeBiomePopulationRunner.class.getName());

    private SkyforgeNativeBiomePopulationRunner() {}

    /**
     * Compatibility overload retained for the accepted SF-IMP-0054 fixture. New coordinators should
     * pass an actual exact-volume surface sample so later within-island biome fields are evaluated at
     * owned terrain rather than an arbitrary chunk center.
     */
    static Result populateStep(
            WorldGenLevel level,
            ChunkGenerator generator,
            SkyforgeExactVolumeBiomeResolver biomeResolver,
            SkyIslandWorldVolumeId volumeId,
            ChunkPos originChunk,
            int sampleY,
            GenerationStep.Decoration generationStep,
            int maximumAttachmentDepth) {
        return populateStep(
                level,
                generator,
                biomeResolver,
                volumeId,
                originChunk,
                new BlockPos(originChunk.getMiddleBlockX(), sampleY, originChunk.getMiddleBlockZ()),
                generationStep,
                maximumAttachmentDepth);
    }

    static Result populateStep(
            WorldGenLevel level,
            ChunkGenerator generator,
            SkyforgeExactVolumeBiomeResolver biomeResolver,
            SkyIslandWorldVolumeId volumeId,
            ChunkPos originChunk,
            BlockPos biomeSample,
            GenerationStep.Decoration generationStep,
            int maximumAttachmentDepth) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(biomeResolver, "biomeResolver");
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(originChunk, "originChunk");
        Objects.requireNonNull(biomeSample, "biomeSample");
        Objects.requireNonNull(generationStep, "generationStep");

        ResourceKey<Biome> biomeKey = Objects.requireNonNull(
                biomeResolver.resolve(
                        volumeId,
                        biomeSample.getX(),
                        biomeSample.getY(),
                        biomeSample.getZ()),
                "biome resolver returned null");
        Holder<Biome> biome = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(biomeKey)
                .orElseThrow(() -> new IllegalStateException(
                        "exact-volume biome is absent from final registry: " + biomeKey.location()));

        var featureSteps = biome.value().getGenerationSettings().features();
        int stepIndex = generationStep.ordinal();
        if (stepIndex >= featureSteps.size()) {
            return new Result(
                    biomeKey,
                    generationStep,
                    0,
                    0,
                    0,
                    List.of(),
                    LakeEvidence.empty());
        }

        var placedFeatureRegistry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        BlockPos nativeChunkOrigin = new BlockPos(
                originChunk.getMinBlockX(),
                level.getMinBuildHeight(),
                originChunk.getMinBlockZ());
        List<FeatureResult> featureResults = new ArrayList<>();
        int attempted = 0;
        int successful = 0;
        int attachmentWrites = 0;
        int lakeAdmissionAttempts = 0;
        int lakeAdmissions = 0;
        int lakeRejections = 0;
        int unsupportedLakeFeatures = 0;
        int lakeInspectedPositions = 0;
        long lakeDecisionDigest = 0xcbf29ce484222325L;

        for (Holder<PlacedFeature> placedFeature : featureSteps.get(stepIndex)) {
            ResourceLocation featureKey = placedFeature.unwrapKey()
                    .map(key -> key.location())
                    .orElseGet(() -> placedFeatureRegistry.getKey(placedFeature.value()));
            if (featureKey == null) {
                throw new IllegalStateException(
                        "biome generation settings contain a PlacedFeature absent from the final registry");
            }

            var operation = SkyforgePopulationOperation.create(
                    volumeId,
                    originChunk,
                    featureKey,
                    stepIndex,
                    attempted);
            boolean treeFeature = featureKey.getPath().toLowerCase(Locale.ROOT).contains("tree");
            if (Boolean.getBoolean(BIOME_PROOF_PROPERTY) && treeFeature) {
                var probe = SkyforgeNativePlacedFeatureRunner.probeTreePrerequisites(
                        level,
                        biome,
                        operation,
                        maximumAttachmentDepth);
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "SF-IMP-0054 TREE PREREQUISITES: volume=" + volumeId.path()
                                + ", chunk=" + originChunk
                                + ", biome=" + biomeKey.location()
                                + ", feature=" + featureKey
                                + ", sample=(" + probe.x() + "," + probe.firstFreeY() + "," + probe.z() + ")"
                                + ", oceanFloorY=" + probe.oceanFloorY()
                                + ", blockBelow=" + probe.blockBelow()
                                + ", blockAt=" + probe.blockAt()
                                + ", expectedBiome=" + probe.observedExpectedBiome()
                                + ", oakSurvives=" + probe.oakSurvives()
                                + ", birchSurvives=" + probe.birchSurvives()
                                + ", spruceSurvives=" + probe.spruceSurvives());
            }

            var result = SkyforgeNativePlacedFeatureRunner.place(
                    level,
                    generator,
                    placedFeature,
                    biome,
                    operation,
                    nativeChunkOrigin,
                    maximumAttachmentDepth);
            attempted++;
            if (result.placed()) {
                successful++;
            }
            attachmentWrites = Math.addExact(attachmentWrites, result.attachmentWrites());
            if (generationStep == GenerationStep.Decoration.LAKES) {
                var lake = result.lakeAdmission();
                if (lake == null) {
                    unsupportedLakeFeatures++;
                    lakeDecisionDigest = mix(lakeDecisionDigest, featureKey.toString().hashCode());
                    lakeDecisionDigest = mix(lakeDecisionDigest, -1L);
                } else {
                    lakeAdmissionAttempts = Math.addExact(lakeAdmissionAttempts, lake.attempted());
                    lakeAdmissions = Math.addExact(lakeAdmissions, lake.admitted());
                    lakeRejections = Math.addExact(lakeRejections, lake.rejected());
                    lakeInspectedPositions = Math.addExact(lakeInspectedPositions, lake.inspectedPositions());
                    lakeDecisionDigest = mix(lakeDecisionDigest, featureKey.toString().hashCode());
                    lakeDecisionDigest = mix(lakeDecisionDigest, lake.decisionDigest());
                }
            }
            featureResults.add(new FeatureResult(featureKey, result.placed(), result.attachmentWrites()));

            if (Boolean.getBoolean(BIOME_PROOF_PROPERTY) && treeFeature) {
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "SF-IMP-0054 TREE FEATURE: volume=" + volumeId.path()
                                + ", chunk=" + originChunk
                                + ", biome=" + biomeKey.location()
                                + ", feature=" + featureKey
                                + ", placed=" + result.placed()
                                + ", attachments=" + result.attachmentWrites());
            }
        }

        return new Result(
                biomeKey,
                generationStep,
                attempted,
                successful,
                attachmentWrites,
                List.copyOf(featureResults),
                generationStep == GenerationStep.Decoration.LAKES
                        ? new LakeEvidence(
                                lakeAdmissionAttempts,
                                lakeAdmissions,
                                lakeRejections,
                                unsupportedLakeFeatures,
                                lakeInspectedPositions,
                                lakeDecisionDigest)
                        : LakeEvidence.empty());
    }

    record FeatureResult(
            ResourceLocation featureKey,
            boolean placed,
            int attachmentWrites) {
        FeatureResult {
            Objects.requireNonNull(featureKey, "featureKey");
            if (attachmentWrites < 0) {
                throw new IllegalArgumentException("feature attachmentWrites must be non-negative");
            }
        }
    }

    record LakeEvidence(
            int attemptedConfiguredLakes,
            int admittedConfiguredLakes,
            int rejectedConfiguredLakes,
            int unsupportedPlacedFeatures,
            int inspectedPositions,
            long decisionDigest) {
        private static LakeEvidence empty() {
            return new LakeEvidence(0, 0, 0, 0, 0, 0xcbf29ce484222325L);
        }

        LakeEvidence {
            if (attemptedConfiguredLakes < 0
                    || admittedConfiguredLakes < 0
                    || rejectedConfiguredLakes < 0
                    || unsupportedPlacedFeatures < 0
                    || inspectedPositions < 0) {
                throw new IllegalArgumentException("lake evidence counts must be non-negative");
            }
            if (admittedConfiguredLakes + rejectedConfiguredLakes != attemptedConfiguredLakes) {
                throw new IllegalArgumentException("lake admission evidence counts are inconsistent");
            }
        }
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= 0x100000001b3L;
        }
        return mixed;
    }

    record Result(
            ResourceKey<Biome> biomeKey,
            GenerationStep.Decoration generationStep,
            int attemptedFeatures,
            int successfulFeatures,
            int attachmentWrites,
            List<FeatureResult> featureResults,
            LakeEvidence lakeEvidence) {
        Result {
            Objects.requireNonNull(biomeKey, "biomeKey");
            Objects.requireNonNull(generationStep, "generationStep");
            Objects.requireNonNull(featureResults, "featureResults");
            Objects.requireNonNull(lakeEvidence, "lakeEvidence");
            if (attemptedFeatures < 0 || successfulFeatures < 0 || attachmentWrites < 0) {
                throw new IllegalArgumentException("population result counts must be non-negative");
            }
            if (successfulFeatures > attemptedFeatures || featureResults.size() != attemptedFeatures) {
                throw new IllegalArgumentException("population result counts are inconsistent");
            }
            int derivedSuccessful = 0;
            int derivedAttachments = 0;
            for (FeatureResult featureResult : featureResults) {
                if (featureResult.placed()) {
                    derivedSuccessful++;
                }
                derivedAttachments = Math.addExact(derivedAttachments, featureResult.attachmentWrites());
            }
            if (derivedSuccessful != successfulFeatures || derivedAttachments != attachmentWrites) {
                throw new IllegalArgumentException("per-feature outcomes do not match aggregate population counts");
            }
            featureResults = List.copyOf(featureResults);
        }

        List<ResourceLocation> featureKeys() {
            return featureResults.stream().map(FeatureResult::featureKey).toList();
        }
    }
}
