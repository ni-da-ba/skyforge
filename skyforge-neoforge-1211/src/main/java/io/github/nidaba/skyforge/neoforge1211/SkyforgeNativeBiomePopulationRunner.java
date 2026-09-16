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
    private static final long SLOW_FEATURE_LOG_THRESHOLD_NANOS = 50_000_000L;
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
                maximumAttachmentDepth,
                SkyforgeNativeVegetalFeatureRoute.ALL);
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
        return populateStep(
                level,
                generator,
                biomeResolver,
                volumeId,
                originChunk,
                biomeSample,
                generationStep,
                maximumAttachmentDepth,
                SkyforgeNativeVegetalFeatureRoute.ALL);
    }

    static Result populateStep(
            WorldGenLevel level,
            ChunkGenerator generator,
            SkyforgeExactVolumeBiomeResolver biomeResolver,
            SkyIslandWorldVolumeId volumeId,
            ChunkPos originChunk,
            BlockPos biomeSample,
            GenerationStep.Decoration generationStep,
            int maximumAttachmentDepth,
            SkyforgeNativeVegetalFeatureRoute route) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(biomeResolver, "biomeResolver");
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(originChunk, "originChunk");
        Objects.requireNonNull(biomeSample, "biomeSample");
        Objects.requireNonNull(generationStep, "generationStep");
        Objects.requireNonNull(route, "route");
        if (generationStep != GenerationStep.Decoration.VEGETAL_DECORATION
                && route != SkyforgeNativeVegetalFeatureRoute.ALL) {
            throw new IllegalArgumentException(
                    "non-vegetal native population requires the ALL feature route");
        }

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
        List<BlockPos> admittedLakeOrigins = new ArrayList<>();
        List<BlockPos> rejectedLakeOrigins = new ArrayList<>();
        boolean performanceMetricsEnabled = SkyforgeRuntimePerformanceMetrics.enabled();

        int featureOrdinal = 0;
        for (Holder<PlacedFeature> placedFeature : featureSteps.get(stepIndex)) {
            int occurrenceIndex = featureOrdinal++;
            if (!route.accepts(generationStep, placedFeature.value())) {
                continue;
            }

            ResourceLocation featureKey = placedFeature.unwrapKey()
                    .map(key -> key.location())
                    .orElseGet(() -> placedFeatureRegistry.getKey(placedFeature.value()));
            if (featureKey == null) {
                throw new IllegalStateException(
                        "biome generation settings contain a PlacedFeature absent from the final registry");
            }
            if (!SkyforgeCreateUndergroundResourceAuthority.admits(featureKey, generationStep)) {
                continue;
            }

            var operation = SkyforgePopulationOperation.create(
                    volumeId,
                    originChunk,
                    featureKey,
                    stepIndex,
                    occurrenceIndex);
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

            long featurePerformanceStart = performanceMetricsEnabled
                    ? SkyforgeRuntimePerformanceMetrics.start()
                    : 0L;
            var result = SkyforgeNativePlacedFeatureRunner.place(
                    level,
                    generator,
                    placedFeature,
                    biome,
                    operation,
                    nativeChunkOrigin,
                    maximumAttachmentDepth);
            if (performanceMetricsEnabled) {
                long elapsedNanos = SkyforgeRuntimePerformanceMetrics.elapsedSince(featurePerformanceStart);
                String performanceStage = featurePerformanceStage(
                        generationStep,
                        biomeKey.location(),
                        featureKey,
                        occurrenceIndex);
                SkyforgeRuntimePerformanceMetrics.recordElapsed(performanceStage, elapsedNanos);
                SkyforgeRuntimePerformanceMetrics.recordSample(
                        performanceStage + ".placed",
                        result.placed() ? 1L : 0L);
                SkyforgeRuntimePerformanceMetrics.recordSample(
                        performanceStage + ".attachmentWrites",
                        result.attachmentWrites());
                SkyforgeRuntimePerformanceMetrics.recordSample(
                        performanceStage + ".latencyBucket." + latencyBucket(elapsedNanos),
                        1L);
                if (elapsedNanos >= SLOW_FEATURE_LOG_THRESHOLD_NANOS) {
                    LOGGER.log(
                            System.Logger.Level.INFO,
                            "SKYFORGE PERF NATIVE FEATURE: elapsedNanos=" + elapsedNanos
                                    + ", volume=" + volumeId.path()
                                    + ", chunk=" + originChunk
                                    + ", biome=" + biomeKey.location()
                                    + ", phase=" + generationStep.name()
                                    + ", feature=" + featureKey
                                    + ", ordinal=" + occurrenceIndex
                                    + ", placed=" + result.placed()
                                    + ", attachmentWrites=" + result.attachmentWrites());
                }
            }

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
                    admittedLakeOrigins.addAll(lake.admittedOrigins());
                    rejectedLakeOrigins.addAll(lake.rejectedOrigins());
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
                                lakeDecisionDigest,
                                List.copyOf(admittedLakeOrigins),
                                List.copyOf(rejectedLakeOrigins))
                        : LakeEvidence.empty());
    }

    static String featurePerformanceStage(
            GenerationStep.Decoration generationStep,
            ResourceLocation biomeKey,
            ResourceLocation featureKey,
            int occurrenceIndex) {
        Objects.requireNonNull(generationStep, "generationStep");
        Objects.requireNonNull(biomeKey, "biomeKey");
        Objects.requireNonNull(featureKey, "featureKey");
        if (occurrenceIndex < 0) {
            throw new IllegalArgumentException("feature occurrence index must be non-negative");
        }
        return "nativeFeature."
                + generationStep.name()
                + ".biome."
                + biomeKey.getNamespace()
                + "."
                + encodeMetricPath(biomeKey.getPath())
                + ".feature."
                + featureKey.getNamespace()
                + "."
                + encodeMetricPath(featureKey.getPath())
                + ".ordinal."
                + occurrenceIndex;
    }

    static String latencyBucket(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            throw new IllegalArgumentException("elapsed time must be non-negative");
        }
        long elapsedMillis = elapsedNanos / 1_000_000L;
        if (elapsedMillis < 1L) {
            return "lt1ms";
        }
        if (elapsedMillis < 2L) {
            return "1to2ms";
        }
        if (elapsedMillis < 4L) {
            return "2to4ms";
        }
        if (elapsedMillis < 8L) {
            return "4to8ms";
        }
        if (elapsedMillis < 16L) {
            return "8to16ms";
        }
        if (elapsedMillis < 32L) {
            return "16to32ms";
        }
        if (elapsedMillis < 64L) {
            return "32to64ms";
        }
        if (elapsedMillis < 128L) {
            return "64to128ms";
        }
        if (elapsedMillis < 256L) {
            return "128to256ms";
        }
        if (elapsedMillis < 512L) {
            return "256to512ms";
        }
        return "ge512ms";
    }

    private static String encodeMetricPath(String path) {
        return path.replace('/', '~');
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
            long decisionDigest,
            List<BlockPos> admittedOrigins,
            List<BlockPos> rejectedOrigins) {
        private static LakeEvidence empty() {
            return new LakeEvidence(0, 0, 0, 0, 0, 0xcbf29ce484222325L, List.of(), List.of());
        }

        LakeEvidence {
            Objects.requireNonNull(admittedOrigins, "admittedOrigins");
            Objects.requireNonNull(rejectedOrigins, "rejectedOrigins");
            if (attemptedConfiguredLakes < 0
                    || admittedConfiguredLakes < 0
                    || rejectedConfiguredLakes < 0
                    || unsupportedPlacedFeatures < 0
                    || inspectedPositions < 0) {
                throw new IllegalArgumentException("lake evidence counts must be non-negative");
            }
            if (admittedConfiguredLakes + rejectedConfiguredLakes != attemptedConfiguredLakes
                    || admittedOrigins.size() != admittedConfiguredLakes
                    || rejectedOrigins.size() != rejectedConfiguredLakes) {
                throw new IllegalArgumentException("lake admission evidence counts are inconsistent");
            }
            admittedOrigins = List.copyOf(admittedOrigins);
            rejectedOrigins = List.copyOf(rejectedOrigins);
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
