package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelDropKind;
import io.github.nidaba.skyforge.world.SkyIslandCoherentChannelComponent;
import io.github.nidaba.skyforge.world.SkyIslandCoherentChannelPlan;
import io.github.nidaba.skyforge.world.SkyIslandCoherentChannelPlanner;
import io.github.nidaba.skyforge.world.SkyIslandCoherentHydrologicRealizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPath;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPlan;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPlanner;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * CI-executed physical admissibility gate for the accepted AUTH-0105 specimen corpus.
 *
 * <p>This reproduces AUTH-0105's exact deterministic 4096-key cheap pass, top-48 full-evaluation
 * boundary, score, and tie ordering. Implementation adds only Minecraft-facing review constraints:
 * bounded physical size, a substantial visible drainage workload, at least one interior drop, and
 * at least one dry physical exterior cave-mouth witness. It does not rerank authored hydrology.
 */
final class SkyforgeDr70SpecimenSearchTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final long GROUP = 8L;
    private static final long REGION = 81L;
    private static final int SEARCH_COUNT = 4096;
    private static final int FULL_EVALUATION_COUNT = 48;
    private static final long HUMAN_REJECTED_REVIEW_KEY = 2885L;

    @Test
    void selectedReviewSpecimenIsFirstAuth0105RankedPhysicallyAdmissibleCandidate() {
        Assumptions.assumeTrue(
                Boolean.getBoolean("skyforge.test.dr70SpecimenSearch"),
                "DR-70 physical specimen search runs only in an explicit qualification task");
        double rejectedRadius = SkyIslandDescriptorGenerator.derive(
                        SkyIslandIdentity.of(SEED, GROUP, REGION, HUMAN_REJECTED_REVIEW_KEY))
                .nominalRadius();
        var qualified = rankedAuth0105Candidates().stream()
                .filter(candidate -> candidate.descriptor().nominalRadius() > rejectedRadius)
                .filter(candidate -> candidate.reachCount() >= 20)
                .filter(candidate -> candidate.interiorDrops() >= 1)
                .filter(SkyforgeDr70SpecimenSearchTest::physicallyAdmissible)
                .limit(8)
                .toList();

        org.junit.jupiter.api.Assertions.assertFalse(
                qualified.isEmpty(),
                "none of AUTH-0105's fully evaluated top-48 candidates is both larger than "
                        + "the human-rejected key-2885 specimen and compatible with the retained "
                        + "hydrology + dry physical cave-mouth review constraints");
        Candidate selected = qualified.getFirst();
        assertEquals(
                selected.descriptor().identity().islandKey(),
                SkyforgeNeoForge1211ProductionComposedCaveFixture.dr70Review().islandKey(),
                "DR-70 review fixture must use first physically admissible larger AUTH-0105-ranked "
                        + "candidate; qualified=" + qualified);
    }

    private static java.util.List<Candidate> rankedAuth0105Candidates() {
        var preliminary = new ArrayList<PreCandidate>(SEARCH_COUNT);
        for (long key = 0; key < SEARCH_COUNT; key++) {
            var descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, GROUP, REGION, key));
            SkyIslandCoherentChannelPlan channels = SkyIslandCoherentChannelPlanner.plan(descriptor);
            if (channels.retainedReachCount() == 0) {
                continue;
            }
            SkyIslandNaturalizedChannelPlan naturalized =
                    SkyIslandNaturalizedChannelPlanner.plan(descriptor, channels.profiles());
            preliminary.add(preCandidate(descriptor, channels, naturalized));
        }
        preliminary.sort(Comparator.comparingDouble(PreCandidate::score)
                .reversed()
                .thenComparingLong(candidate -> candidate.descriptor().identity().islandKey()));

        return preliminary.stream()
                .limit(FULL_EVALUATION_COUNT)
                .map(SkyforgeDr70SpecimenSearchTest::fullyEvaluate)
                .sorted(Comparator.comparingDouble(Candidate::score)
                        .reversed()
                        .thenComparingLong(candidate -> candidate.descriptor().identity().islandKey()))
                .toList();
    }

    private static PreCandidate preCandidate(
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor,
            SkyIslandCoherentChannelPlan channels,
            SkyIslandNaturalizedChannelPlan naturalized) {
        Map<Integer, SkyIslandNaturalizedChannelPath> bySource = new HashMap<>();
        for (SkyIslandNaturalizedChannelPath path : naturalized.paths()) {
            bySource.put(path.profile().segment().sourceCellIndex(), path);
        }
        Map<Integer, Double> memo = new HashMap<>();
        double longestChain = bySource.values().stream()
                .mapToDouble(path -> chainLength(path, bySource, memo))
                .max()
                .orElse(0.0);
        double chainRatio = longestChain / descriptor.nominalRadius();
        double terminalDischarge = channels.retainedComponents().stream()
                .mapToDouble(SkyIslandCoherentChannelComponent::terminalRelativeDischarge)
                .max()
                .orElse(0.0);
        int maxOrder = channels.retainedComponents().stream()
                .mapToInt(SkyIslandCoherentChannelComponent::maxStreamOrder)
                .max()
                .orElse(1);
        int components = channels.retainedComponentCount();
        int reaches = channels.retainedReachCount();

        double score = 3.2 * Math.min(2.0, chainRatio)
                + 1.4 * terminalDischarge
                + 0.55 * Math.min(1.0, (maxOrder - 1) / 3.0)
                + 0.25 * Math.min(1.0, reaches / 20.0)
                - 0.18 * Math.max(0, components - 2);
        return new PreCandidate(
                descriptor, score, components, reaches, chainRatio, terminalDischarge, maxOrder);
    }

    private static Candidate fullyEvaluate(PreCandidate preliminary) {
        var descriptor = preliminary.descriptor();
        var coherent = SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor);
        int retainedWater = SkyIslandWaterbodyFootprintPlanner.plan(descriptor).footprints().size();
        int interiorDrops = (int) (coherent.drops().count(SkyIslandChannelDropKind.CASCADE_STEP)
                + coherent.drops().count(SkyIslandChannelDropKind.WATERFALL));
        int edgeFalls = (int) coherent.drops().count(SkyIslandChannelDropKind.EDGE_FALL);

        double score = preliminary.score()
                + 0.65 * Math.min(1, retainedWater)
                + 0.45 * Math.min(1.0, interiorDrops / 3.0)
                - 0.04 * Math.max(0, edgeFalls - 2);
        return new Candidate(
                descriptor,
                score,
                preliminary.componentCount(),
                preliminary.reachCount(),
                preliminary.longestChainRatio(),
                preliminary.terminalDischarge(),
                preliminary.maxStreamOrder(),
                retainedWater,
                interiorDrops,
                edgeFalls);
    }

    private static double chainLength(
            SkyIslandNaturalizedChannelPath path,
            Map<Integer, SkyIslandNaturalizedChannelPath> bySource,
            Map<Integer, Double> memo) {
        int source = path.profile().segment().sourceCellIndex();
        Double cached = memo.get(source);
        if (cached != null) {
            return cached;
        }
        SkyIslandNaturalizedChannelPath next =
                bySource.get(path.profile().segment().downstreamCellIndex());
        double value = path.pathLength()
                + (next == null ? 0.0 : chainLength(next, bySource, memo));
        memo.put(source, value);
        return value;
    }

    private static boolean physicallyAdmissible(Candidate candidate) {
        long key = candidate.descriptor().identity().islandKey();
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.dr70ReviewCandidate(key);
        if (fixture.field().exposureGeometry().connectionCount() <= 0) {
            return false;
        }
        return physicalDryMouth(fixture) != null;
    }

    private static BlockPos physicalDryMouth(
            SkyforgeNeoForge1211ProductionComposedCaveFixture.Single fixture) {
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                Map.of(fixture.volume().id(), fixture.descriptor()));
        Set<Long> wet = terrain.authoredHydrologyDeployments(fixture.volume().id()).stream()
                .flatMap(deployment -> deployment.positions().stream())
                .map(BlockPos::asLong)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                fixture.field(),
                new SkyIslandCompiledVolumeColumnField(fixture.volume().compiledVolume()));
        var spatial = SkyforgeExteriorConnectedCaveSpatialIndex.create(fixture.field());

        LinkedHashSet<Long> candidateChunks = new LinkedHashSet<>();
        var physical = fixture.volume().compiledVolume().descriptor();
        for (var connection : fixture.field().exposureGeometry().connections()) {
            var mouth = realized.transform()
                    .toPhysical(connection.mouthPoint().position())
                    .orElseThrow(() -> new IllegalStateException(
                            "accepted exposure mouth has no physical column for candidate " + fixture.islandKey()));
            int worldX = (int) Math.floor(physical.centerX() + mouth.localX());
            int worldZ = (int) Math.floor(physical.centerZ() + mouth.localZ());
            int centerChunkX = Math.floorDiv(worldX, 16);
            int centerChunkZ = Math.floorDiv(worldZ, 16);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    candidateChunks.add(ChunkPos.asLong(centerChunkX + dx, centerChunkZ + dz));
                }
            }
        }

        int minBuildY = (int) Math.floor(fixture.volume().bounds().minimumY());
        int maxBuildYExclusive = (int) Math.ceil(fixture.volume().bounds().maximumY()) + 1;
        for (long chunkKey : candidateChunks) {
            var cursor = new SkyforgeExteriorConnectedCavePreparationCursor(
                    fixture.volume(),
                    realized,
                    spatial,
                    new ChunkPos(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)),
                    minBuildY,
                    maxBuildYExclusive,
                    position -> terrain.isSolidOwnedBy(
                            fixture.volume().id(),
                            position.getX(),
                            position.getY(),
                            position.getZ()),
                    position -> terrain.isSolidOwnedByOtherVolume(
                            fixture.volume().id(),
                            position.getX(),
                            position.getY(),
                            position.getZ()));
            while (!cursor.complete()) {
                cursor.advance(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
            }
            var prepared = cursor.prepared();
            if (prepared.firstMouthPosition() != null
                    && !wet.contains(prepared.firstMouthPosition().asLong())) {
                return prepared.firstMouthPosition();
            }
        }
        return null;
    }

    private record PreCandidate(
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor,
            double score,
            int componentCount,
            int reachCount,
            double longestChainRatio,
            double terminalDischarge,
            int maxStreamOrder) {}

    private record Candidate(
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor,
            double score,
            int componentCount,
            int reachCount,
            double longestChainRatio,
            double terminalDischarge,
            int maxStreamOrder,
            int retainedWater,
            int interiorDrops,
            int edgeFalls) {}
}
