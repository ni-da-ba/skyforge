package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionStructure;

/** DR-50 machine evidence that composes the accepted dressed-region systems on the DR-00 specimen. */
final class SkyforgeDr50IntegratedRegionEvidence {
    static final String ENABLE_PROPERTY = "skyforge.dev.dr50IntegratedRegion";
    static final String RELOAD_PROPERTY = "skyforge.dev.dr50IntegratedRegionReload";
    private static final int PROBE_CHUNK_X = 0;
    private static final int PROBE_CHUNK_Z = 0;
    private static final ResourceLocation MANSION = ResourceLocation.withDefaultNamespace("mansion");
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private SkyforgeDr50IntegratedRegionEvidence() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static boolean suppressBaseWorldProbe(Structure structure, ChunkPos chunkPos) {
        return enabled() && structure instanceof WoodlandMansionStructure && probeChunk(chunkPos);
    }

    static boolean isProbeCandidate(
            Structure structure,
            ChunkPos chunkPos,
            SkyIslandWorldVolumeId volumeId) {
        return enabled()
                && structure instanceof WoodlandMansionStructure
                && probeChunk(chunkPos)
                && canonicalVolumeId().equals(volumeId);
    }

    static boolean allowsExactProbe(
            Structure structure,
            ChunkPos chunkPos,
            SkyIslandWorldVolumeId volumeId) {
        if (!enabled() || !(structure instanceof WoodlandMansionStructure) || !probeChunk(chunkPos)) {
            return true;
        }
        return canonicalVolumeId().equals(volumeId);
    }

    static IllegalStateException exactProbeFailure(
            Structure structure,
            ChunkPos chunkPos,
            SkyIslandWorldVolumeId volumeId,
            String reason) {
        return new IllegalStateException(
                "DR-50 canonical native structure probe failed: structure="
                        + structure.getClass().getSimpleName()
                        + ", chunk=" + chunkPos
                        + ", volume=" + volumeId.path()
                        + ", reason=" + reason);
    }

    private static boolean probeChunk(ChunkPos chunkPos) {
        return chunkPos.x == PROBE_CHUNK_X && chunkPos.z == PROBE_CHUNK_Z;
    }

    private static SkyIslandWorldVolumeId canonicalVolumeId() {
        return SkyforgeNeoForge1211ProductionComposedCaveFixture.single().volume().id();
    }

    static Map<String, Object> collect(
            ServerLevel level,
            SkyforgeNeoForge1211ProductionComposedCaveFixture.Single fixture,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Set<Long> plannedChunks,
            String nativeTransformDigest,
            String nativeCarveDigest,
            String authoredChangedDigest,
            String authoredProvenanceDigest) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(fixture, "fixture");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(plannedChunks, "plannedChunks");
        var volume = fixture.volume();
        var volumeId = volume.id();
        if (!canonicalVolumeId().equals(volumeId)) {
            throw new IllegalArgumentException("DR-50 integration requires the exact DR-00 canonical volume");
        }
        if (SkyforgeComposedCaveStage.snapshot(volumeId).pendingObligations() != 0
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId).isEmpty()
                || !SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(volumeId).isEmpty()) {
            throw new IllegalStateException("DR-50 evidence collected before the canonical lifecycle settled");
        }

        HydrologyEvidence hydrology = verifyHydrology(level, fixture, terrain);
        StructureEvidence structure = verifyStructure(level, volumeId, plannedChunks);
        InteriorEvidence interior = verifyInteriorPopulation(level, volumeId, plannedChunks.size());
        MaterialEvidence material = placeAndVerifyIron(level, fixture, terrain, structure, hydrology.positions());
        String populationDigest = SkyforgeDr40ProductionEcologyEvidence.populationOutcomeDigest(
                SkyforgeNativeSurfacePopulationStage.completedNativePhases(volumeId));

        long regionDigest = FNV_OFFSET_BASIS;
        regionDigest = mixText(regionDigest, volumeId.path());
        regionDigest = mixText(regionDigest, nativeTransformDigest);
        regionDigest = mixText(regionDigest, nativeCarveDigest);
        regionDigest = mixText(regionDigest, authoredChangedDigest);
        regionDigest = mixText(regionDigest, authoredProvenanceDigest);
        regionDigest = mixText(regionDigest, populationDigest);
        regionDigest = mixText(regionDigest, hydrology.digest());
        regionDigest = mixText(regionDigest, interior.digest());
        regionDigest = mix(regionDigest, material.position().asLong());
        regionDigest = mixText(regionDigest, material.blockId().toString());
        regionDigest = mixText(regionDigest, structure.identity().structureId().toString());
        regionDigest = mix(regionDigest, structure.identity().targetChunkKey());
        regionDigest = mix(regionDigest, structure.representativePosition().asLong());
        regionDigest = mixText(regionDigest, structure.representativeBlockId().toString());

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("dr50IntegratedRegion", true);
        evidence.put("dr50SpecimenId", "P2_DRESSED_REGION_A");
        evidence.put("dr50Volume", volumeId.path());
        evidence.put("dr50WorldSeedUnsigned", Long.toUnsignedString(fixture.volume().id().archipelagoRootSeed()));
        evidence.put("dr50HydrologyPositions", hydrology.positions().size());
        evidence.put("dr50HydrologyDigest", hydrology.digest());
        evidence.put("dr50HydrologyRepresentativePos", Long.toString(hydrology.representativePosition().asLong()));
        evidence.put("dr50InteriorCompleted", interior.completedObligations());
        evidence.put("dr50InteriorNonEmpty", interior.nonEmptyObligations());
        evidence.put("dr50InteriorSuccessfulFeatures", interior.successfulFeatures());
        evidence.put("dr50InteriorUnsupportedLakeFeatures", interior.unsupportedLakeFeatures());
        evidence.put("dr50InteriorTrackedFluids", interior.trackedFluids());
        evidence.put("dr50InteriorFluidSchedulesOutsideOwner", interior.fluidSchedulesOutsideOwner());
        evidence.put("dr50InteriorRejectedBoundaryWrites", interior.rejectedBoundaryWrites());
        evidence.put("dr50InteriorReplayWorked", false);
        evidence.put("dr50InteriorDigest", interior.digest());
        evidence.put("dr50Material", material.blockId().toString());
        evidence.put("dr50MaterialPos", Long.toString(material.position().asLong()));
        evidence.put("dr50MaterialReplayWritten", false);
        evidence.put("dr50Structure", structure.identity().structureId().toString());
        evidence.put("dr50StructureTargetChunk", Long.toString(structure.identity().targetChunkKey()));
        evidence.put("dr50StructureMinX", structure.identity().minX());
        evidence.put("dr50StructureMinY", structure.identity().minY());
        evidence.put("dr50StructureMinZ", structure.identity().minZ());
        evidence.put("dr50StructureMaxX", structure.identity().maxX());
        evidence.put("dr50StructureMaxY", structure.identity().maxY());
        evidence.put("dr50StructureMaxZ", structure.identity().maxZ());
        evidence.put("dr50StructureRepresentativePos", Long.toString(structure.representativePosition().asLong()));
        evidence.put("dr50StructureRepresentativeBlock", structure.representativeBlockId().toString());
        evidence.put("dr50StructureCompletionPersistable", true);
        evidence.put("dr50PopulationOutcomeDigest", populationDigest);
        evidence.put("dr50ExactVolumeIsolation", true);
        evidence.put("dr50NoHydrologyMaterialCollision", true);
        evidence.put("dr50NoStructureMaterialCollision", true);
        evidence.put("dr50RegionDigest", Long.toUnsignedString(regionDigest, 16));
        return Map.copyOf(evidence);
    }

    private static HydrologyEvidence verifyHydrology(
            ServerLevel level,
            SkyforgeNeoForge1211ProductionComposedCaveFixture.Single fixture,
            SkyforgeNeoForge1211ChunkAdapter terrain) {
        List<SkyforgeAuthoredVisibleHydrologyAdapter.Deployment> deployments =
                SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.volume(), terrain);
        if (deployments.isEmpty()) {
            throw new IllegalStateException("DR-50 canonical specimen has no authored visible hydrology to compose");
        }
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        long digest = FNV_OFFSET_BASIS;
        for (var deployment : deployments) {
            digest = mix(digest, deployment.feature().ordinal());
            for (BlockPos position : deployment.positions()) {
                if (!terrain.isSolidOwnedBy(
                        fixture.volume().id(), position.getX(), position.getY(), position.getZ())) {
                    throw new IllegalStateException("DR-50 authored hydrology escaped exact volume ownership");
                }
                BlockState state = level.getBlockState(position);
                if (!state.is(Blocks.WATER)) {
                    throw new IllegalStateException(
                            "DR-50 later lifecycle overwrote authored hydrology at " + position + ": " + state);
                }
                positions.add(position.immutable());
                digest = mix(digest, position.asLong());
            }
        }
        if (positions.isEmpty()) {
            throw new IllegalStateException("DR-50 authored hydrology produced no physical water cells");
        }
        return new HydrologyEvidence(List.copyOf(positions), Long.toUnsignedString(digest, 16), positions.getFirst());
    }

    private static StructureEvidence verifyStructure(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            Set<Long> plannedChunks) {
        var saved = SkyforgeNativeStructurePlacementSavedData.forLevel(level);
        List<SkyforgeNativeStructurePlacementSavedData.PlacementIdentity> candidates = new ArrayList<>();
        for (long chunkKey : plannedChunks) {
            for (var identity : saved.ownedFor(volumeId, chunkKey)) {
                if (identity.structureId().equals(MANSION) && saved.completed(identity)) {
                    candidates.add(identity);
                }
            }
        }
        candidates.sort(Comparator.naturalOrder());
        if (candidates.size() != 1) {
            throw new IllegalStateException(
                    "DR-50 requires exactly one completed canonical generic structure probe; found " + candidates.size());
        }
        var identity = candidates.getFirst();
        if (identity.targetChunkKey() != ChunkPos.asLong(PROBE_CHUNK_X, PROBE_CHUNK_Z)) {
            throw new IllegalStateException("DR-50 generic structure probe resolved outside the canonical target chunk");
        }
        BlockPos representative = findRepresentativeStructureBlock(level, identity);
        if (representative == null) {
            throw new IllegalStateException("DR-50 structure completion has no surviving representative physical block");
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(representative).getBlock());
        return new StructureEvidence(identity, representative, blockId);
    }

    private static InteriorEvidence verifyInteriorPopulation(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            int requiredChunks) {
        var snapshot = SkyforgeNativeInteriorPopulationStage.snapshot(volumeId);
        if (snapshot.totalObligations() != requiredChunks
                || snapshot.pendingObligations() != 0
                || snapshot.completedObligations() != requiredChunks) {
            throw new IllegalStateException(
                    "DR-50 native interior lifecycle did not complete the canonical footprint: " + snapshot);
        }
        List<SkyforgeNativeInteriorPopulationStage.Completion> completions =
                SkyforgeNativeInteriorPopulationStage.completed().stream()
                        .filter(completion -> completion.volumeId().equals(volumeId))
                        .sorted(Comparator.comparingLong(completion -> completion.chunkPos().toLong()))
                        .toList();
        if (completions.size() != requiredChunks) {
            throw new IllegalStateException("DR-50 native interior completion evidence count drifted");
        }
        int nonEmpty = 0;
        int successful = 0;
        int unsupportedLakes = 0;
        long digest = FNV_OFFSET_BASIS;
        for (var completion : completions) {
            digest = mix(digest, completion.chunkPos().toLong());
            if (!completion.phaseResults().isEmpty()) {
                nonEmpty++;
            }
            for (var result : completion.phaseResults()) {
                digest = mix(digest, result.generationStep().ordinal());
                digest = mixText(digest, result.biomeKey().location().toString());
                digest = mix(digest, result.attemptedFeatures());
                digest = mix(digest, result.successfulFeatures());
                successful = Math.addExact(successful, result.successfulFeatures());
                unsupportedLakes = Math.addExact(
                        unsupportedLakes, result.lakeEvidence().unsupportedPlacedFeatures());
                for (var feature : result.featureResults()) {
                    digest = mixText(digest, feature.featureKey().toString());
                    digest = mix(digest, feature.placed() ? 1L : 0L);
                }
            }
        }
        var fluids = SkyforgeGeneratedFluidPropagationStage.snapshot(level, volumeId);
        if (fluids.scheduledOutsideOwner() != 0 || fluids.rejectedBoundaryWrites() != 0) {
            throw new IllegalStateException("DR-50 generated-fluid containment failed: " + fluids);
        }
        var replayCompletion = completions.stream()
                .filter(completion -> !completion.phaseResults().isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("DR-50 has no completed interior chunk to replay-check"));
        var replayChunk = level.getChunkSource().getChunkNow(
                replayCompletion.chunkPos().x, replayCompletion.chunkPos().z);
        if (replayChunk == null) {
            throw new IllegalStateException("DR-50 interior replay-check chunk is not stably loaded");
        }
        if (SkyforgeNativeInteriorPopulationStage.service(
                level, replayChunk, level.getChunkSource().getGenerator()).worked()) {
            throw new IllegalStateException("DR-50 completed native interior obligation replayed");
        }
        if (nonEmpty <= 0 || successful <= 0 || unsupportedLakes != 0) {
            throw new IllegalStateException(
                    "DR-50 native interior lifecycle lacks bounded accepted output: nonEmpty="
                            + nonEmpty + ", successful=" + successful + ", unsupportedLakes=" + unsupportedLakes);
        }
        digest = mix(digest, fluids.trackedPositions());
        digest = mix(digest, fluids.digest());
        return new InteriorEvidence(
                snapshot.completedObligations(),
                nonEmpty,
                successful,
                unsupportedLakes,
                fluids.trackedPositions(),
                fluids.scheduledOutsideOwner(),
                fluids.rejectedBoundaryWrites(),
                Long.toUnsignedString(digest, 16));
    }

    private static MaterialEvidence placeAndVerifyIron(
            ServerLevel level,
            SkyforgeNeoForge1211ProductionComposedCaveFixture.Single fixture,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            StructureEvidence structure,
            List<BlockPos> hydrologyPositions) {
        var profile = new SkyIslandBaseMetalOpportunityProfiler().profile(fixture.descriptor());
        var deployment = SkyforgeIronDepositAdapter.plan(
                        profile,
                        fixture.volume(),
                        terrain,
                        SkyforgeIronDepositAdapter.Specification.representative())
                .orElseThrow(() -> new IllegalStateException("DR-50 canonical specimen lost required starting-cluster Iron"));
        BlockPos position = deployment.position();
        if (hydrologyPositions.contains(position)) {
            throw new IllegalStateException("DR-50 Iron deployment collides with authored hydrology at " + position);
        }
        if (inside(structure.identity(), position)) {
            throw new IllegalStateException("DR-50 Iron deployment collides with native structure bounds at " + position);
        }
        if (!terrain.isSolidOwnedBy(deployment.volumeId(), position.getX(), position.getY(), position.getZ())
                || terrain.isSolidOwnedByOtherVolume(deployment.volumeId(), position.getX(), position.getY(), position.getZ())) {
            throw new IllegalStateException("DR-50 Iron deployment lost exact-volume ownership");
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(deployment.chunkPos().x, deployment.chunkPos().z);
        if (chunk == null) {
            throw new IllegalStateException("DR-50 Iron deployment chunk is not stably loaded");
        }
        BlockState before = chunk.getBlockState(position);
        if (before.isAir() || before.is(Blocks.WATER)) {
            throw new IllegalStateException(
                    "DR-50 required Iron support was overwritten before material realization at " + position + ": " + before);
        }
        var first = SkyforgeIronDepositAdapter.apply(chunk, deployment);
        var replay = SkyforgeIronDepositAdapter.apply(chunk, deployment);
        if (!first.writtenNow() || replay.writtenNow() || !chunk.getBlockState(position).is(Blocks.IRON_ORE)) {
            throw new IllegalStateException("DR-50 Iron realization is not deterministic/idempotent at " + position);
        }
        return new MaterialEvidence(position, SkyforgeIronDepositAdapter.IRON_ORE);
    }

    private static boolean inside(
            SkyforgeNativeStructurePlacementSavedData.PlacementIdentity identity,
            BlockPos position) {
        return position.getX() >= identity.minX() && position.getX() <= identity.maxX()
                && position.getY() >= identity.minY() && position.getY() <= identity.maxY()
                && position.getZ() >= identity.minZ() && position.getZ() <= identity.maxZ();
    }

    private static BlockPos findRepresentativeStructureBlock(
            ServerLevel level,
            SkyforgeNativeStructurePlacementSavedData.PlacementIdentity identity) {
        int chunkMinX = ChunkPos.getX(identity.targetChunkKey()) * 16;
        int chunkMinZ = ChunkPos.getZ(identity.targetChunkKey()) * 16;
        int minX = Math.max(identity.minX(), chunkMinX);
        int maxX = Math.min(identity.maxX(), chunkMinX + 15);
        int minZ = Math.max(identity.minZ(), chunkMinZ);
        int maxZ = Math.min(identity.maxZ(), chunkMinZ + 15);
        for (int y = Math.max(identity.minY(), level.getMinBuildHeight());
                y <= Math.min(identity.maxY(), level.getMaxBuildHeight() - 1); y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.DARK_OAK_PLANKS)
                            || state.is(Blocks.DARK_OAK_LOG)
                            || state.is(Blocks.COBBLESTONE)
                            || state.is(Blocks.GLASS_PANE)) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    static ReloadExpectation reloadExpectation(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        if (!Boolean.getBoolean(RELOAD_PROPERTY)) {
            return null;
        }
        return new ReloadExpectation(
                BlockPos.of(Long.parseLong(required(properties, "dr50MaterialPos"))),
                BlockPos.of(Long.parseLong(required(properties, "dr50HydrologyRepresentativePos"))),
                BlockPos.of(Long.parseLong(required(properties, "dr50StructureRepresentativePos"))),
                ResourceLocation.parse(required(properties, "dr50StructureRepresentativeBlock")),
                ResourceLocation.parse(required(properties, "dr50Structure")),
                Long.parseLong(required(properties, "dr50StructureTargetChunk")));
    }

    private static String required(Properties properties, String key) {
        return Objects.requireNonNull(properties.getProperty(key), () -> "DR-50 expected result missing " + key);
    }

    private static long mixText(long digest, String value) {
        long mixed = digest;
        for (byte element : value.getBytes(StandardCharsets.UTF_8)) {
            mixed ^= element & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    record ReloadExpectation(
            BlockPos materialPosition,
            BlockPos hydrologyPosition,
            BlockPos structurePosition,
            ResourceLocation structureBlockId,
            ResourceLocation structureId,
            long structureTargetChunk) {}

    private record HydrologyEvidence(List<BlockPos> positions, String digest, BlockPos representativePosition) {}
    private record StructureEvidence(
            SkyforgeNativeStructurePlacementSavedData.PlacementIdentity identity,
            BlockPos representativePosition,
            ResourceLocation representativeBlockId) {}
    private record MaterialEvidence(BlockPos position, ResourceLocation blockId) {}
    private record InteriorEvidence(
            int completedObligations,
            int nonEmptyObligations,
            int successfulFeatures,
            int unsupportedLakeFeatures,
            int trackedFluids,
            long fluidSchedulesOutsideOwner,
            long rejectedBoundaryWrites,
            String digest) {}
}
