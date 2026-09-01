package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.lang.reflect.Modifier;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.junit.jupiter.api.Test;

final class SkyforgeStructureCandidateAdmissionTest {
    private static final int MINIMUM_Y = -64;
    private static final int HEIGHT = 384;

    @Test
    void accessTransformerExposesOnlyTheCandidateMethodForSubtypeOverride() throws Exception {
        var method = ChunkGenerator.class.getDeclaredMethod(
                "tryGenerateStructure",
                StructureSet.StructureSelectionEntry.class,
                StructureManager.class,
                RegistryAccess.class,
                RandomState.class,
                StructureTemplateManager.class,
                long.class,
                ChunkAccess.class,
                ChunkPos.class,
                SectionPos.class);

        assertTrue(Modifier.isProtected(method.getModifiers()));
    }

    @Test
    void accommodationDevelopmentFixtureBuildsKernelValidGraphs() {
        var catalog = SkyforgeNeoForge1211AccommodationDevRuntime.catalog();

        assertEquals(1, catalog.volumes().size());
        assertNotNull(catalog.volumes().getFirst().compiledVolume().upperSurfaceGraph());
        assertNotNull(catalog.volumes().getFirst().compiledVolume().densityGraph());
    }

    @Test
    void accommodationDevelopmentFixtureIsOneBoundedIslandRatherThanSquareSlab() throws Exception {
        SkyforgeNeoForge1211ChunkAdapter adapter = SkyforgeNeoForge1211AccommodationDevRuntime.adapter();
        SkyIslandWorldVolumeId volumeId = SkyforgeNeoForge1211AccommodationDevRuntime.catalog().volumes().getFirst().id();

        try (AutoCloseable activeBinding = SkyforgeNeoForge1211SurfaceStage.install(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(activeBinding);
            assertTrue(SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(volumeId, 0, 200, 0).orElseThrow());
            assertFalse(SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(volumeId, 110, 200, 110).orElseThrow());
        }
    }

    @Test
    void earlyHeightClaimCarriesIndependentWorldVolumeIdentity() throws Exception {
        SkyforgeNeoForge1211ChunkAdapter adapter = SkyforgeNeoForge1211DevRuntime.adapter();
        SkyIslandWorldVolumeId expectedId = SkyforgeNeoForge1211DevRuntime.catalog().volumes().getFirst().id();
        SkyIslandWorldVolumeId unrelatedId = new SkyIslandWorldVolumeId(1L, "unrelated", 0, 0, 2L);

        try (AutoCloseable activeBinding = SkyforgeNeoForge1211SurfaceStage.install(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(activeBinding);
            MinecraftSkyforgeHeightClaim claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                            0,
                            0,
                            Heightmap.Types.OCEAN_FLOOR_WG,
                            MINIMUM_Y,
                            HEIGHT)
                    .orElseThrow();

            assertTrue(claim.height() > 160);
            assertEquals(List.of(expectedId), claim.volumeIds());
            assertTrue(SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            expectedId,
                            0,
                            claim.height() - 1,
                            0)
                    .orElseThrow());
            assertFalse(SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            unrelatedId,
                            0,
                            claim.height() - 1,
                            0)
                    .orElseThrow());
        }
    }

    @Test
    void candidateTraceRetainsFullClaimsAndDistinctVolumes() {
        SkyIslandWorldVolumeId first = new SkyIslandWorldVolumeId(1L, "first", 0, 0, 11L);
        SkyIslandWorldVolumeId second = new SkyIslandWorldVolumeId(1L, "second", 0, 1, 12L);
        MinecraftSkyforgeHeightClaim firstClaim = new MinecraftSkyforgeHeightClaim(120, List.of(first));
        MinecraftSkyforgeHeightClaim secondClaim = new MinecraftSkyforgeHeightClaim(220, List.of(second));

        try (SkyforgeStructureCandidateStage.Scope scope = SkyforgeStructureCandidateStage.open()) {
            SkyforgeStructureCandidateStage.record(firstClaim);
            SkyforgeStructureCandidateStage.record(secondClaim);
            assertEquals(List.of(firstClaim, secondClaim), scope.claims());
            assertEquals(2, scope.claimedVolumeIds().size());
            assertTrue(scope.claimedVolumeIds().contains(first));
            assertTrue(scope.claimedVolumeIds().contains(second));
            assertThrows(IllegalStateException.class, SkyforgeStructureCandidateStage::open);
        }
    }

    @Test
    void onlyStartsResolvedAtClaimedSurfacePlaneEnterSkyforgeAdmission() {
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(1L, "surface", 0, 0, 2L);
        MinecraftSkyforgeHeightClaim claim = new MinecraftSkyforgeHeightClaim(224, List.of(volumeId));

        assertTrue(SkyforgeNoiseBasedChunkGenerator.claimResolvesSurfacePlane(
                new BoundingBox(0, 224, 0, 20, 240, 20), claim));
        assertTrue(SkyforgeNoiseBasedChunkGenerator.claimResolvesSurfacePlane(
                new BoundingBox(0, 223, 0, 20, 240, 20), claim));
        assertFalse(SkyforgeNoiseBasedChunkGenerator.claimResolvesSurfacePlane(
                new BoundingBox(0, 64, 0, 20, 78, 20), claim));
    }

    @Test
    void minecraftBoundingBoxTranslatesToNeutralSupportRequirements() {
        BoundingBox box = new BoundingBox(-10, 180, -6, 14, 205, 18);
        var requirements = MinecraftStructureSupportPolicy.requirements(box);

        assertEquals(-10.0, requirements.minimumX());
        assertEquals(14.0, requirements.maximumX());
        assertEquals(-6.0, requirements.minimumZ());
        assertEquals(18.0, requirements.maximumZ());
        assertEquals(4.0, requirements.sampleSpacing());
        assertEquals(2.0, requirements.clearance());
        assertEquals(0.90, requirements.minimumCoverageFraction());
        assertEquals(0.50, requirements.minimumClearanceCoverageFraction());
        assertEquals(4.0, requirements.maximumHeightSpan());
    }

    @Test
    void foundationPolicySeparatesFillPlaneFromResolvedSurfaceCeiling() {
        BoundingBox box = new BoundingBox(-10, 180, -6, 14, 205, 18);
        var requirements = MinecraftStructureSupportPolicy.foundationRequirements(box, 181);

        assertEquals(-10.0, requirements.supportRequirements().minimumX());
        assertEquals(14.0, requirements.supportRequirements().maximumX());
        assertEquals(-6.0, requirements.supportRequirements().minimumZ());
        assertEquals(18.0, requirements.supportRequirements().maximumZ());
        assertEquals(1.0, requirements.supportRequirements().sampleSpacing());
        assertEquals(1.0, requirements.supportRequirements().minimumCoverageFraction());
        assertEquals(0.50, requirements.supportRequirements().minimumClearanceCoverageFraction());
        assertEquals(12.0, requirements.supportRequirements().maximumHeightSpan());
        assertEquals(180.0, requirements.foundationTopY());
        assertEquals(181.0, requirements.maximumSurfaceY());
        assertEquals(8.0, requirements.maximumFillDepth());
    }

    @Test
    void foundationPolicyDoesNotLowerSurfaceCeilingBelowFillPlane() {
        BoundingBox box = new BoundingBox(-10, 180, -6, 14, 205, 18);
        var requirements = MinecraftStructureSupportPolicy.foundationRequirements(box, 179);

        assertEquals(180.0, requirements.foundationTopY());
        assertEquals(180.0, requirements.maximumSurfaceY());
    }

    @Test
    void foundationPieceStaysBelowStructureAndPersistsVolumeProvenance() {
        BoundingBox structureBounds = new BoundingBox(-10, 180, -6, 14, 205, 18);
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(91L, "accommodation", 2, 3, 92L);
        SkyforgeFoundationPiece piece = new SkyforgeFoundationPiece(structureBounds, volumeId, 8);

        assertEquals(volumeId, piece.supportingVolumeId());
        assertEquals(179, piece.foundationTopY());
        assertEquals(8, piece.maximumFillDepth());
        assertEquals(-10, piece.getBoundingBox().minX());
        assertEquals(14, piece.getBoundingBox().maxX());
        assertEquals(-6, piece.getBoundingBox().minZ());
        assertEquals(18, piece.getBoundingBox().maxZ());
        assertEquals(179, piece.getBoundingBox().maxY());
        assertEquals(172, piece.getBoundingBox().minY());

        CompoundTag tag = new CompoundTag();
        piece.addAdditionalSaveData(null, tag);
        assertEquals(91L, tag.getLong("SkyforgeRootSeed"));
        assertEquals("accommodation", tag.getString("SkyforgeGroup"));
        assertEquals(2, tag.getInt("SkyforgeGroupOrdinal"));
        assertEquals(3, tag.getInt("SkyforgeMemberOrdinal"));
        assertEquals(92L, tag.getLong("SkyforgeGeometrySeed"));
        assertEquals(179, tag.getInt("SkyforgeFoundationTopY"));
        assertEquals(8, tag.getInt("SkyforgeMaximumFillDepth"));
    }
}
