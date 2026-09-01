package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.lang.reflect.Modifier;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
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
    void earlyHeightClaimCarriesIndependentWorldVolumeIdentity() throws Exception {
        SkyforgeNeoForge1211ChunkAdapter adapter = SkyforgeNeoForge1211DevRuntime.adapter();
        SkyIslandWorldVolumeId expectedId = SkyforgeNeoForge1211DevRuntime.catalog().volumes().getFirst().id();

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
        }
    }

    @Test
    void candidateTracePreservesDistinctClaimedVolumes() {
        SkyIslandWorldVolumeId first = new SkyIslandWorldVolumeId(1L, "first", 0, 0, 11L);
        SkyIslandWorldVolumeId second = new SkyIslandWorldVolumeId(1L, "second", 0, 1, 12L);

        try (SkyforgeStructureCandidateStage.Scope scope = SkyforgeStructureCandidateStage.open()) {
            SkyforgeStructureCandidateStage.record(new MinecraftSkyforgeHeightClaim(120, List.of(first)));
            SkyforgeStructureCandidateStage.record(new MinecraftSkyforgeHeightClaim(220, List.of(second)));
            assertEquals(2, scope.claimedVolumeIds().size());
            assertTrue(scope.claimedVolumeIds().contains(first));
            assertTrue(scope.claimedVolumeIds().contains(second));
            assertThrows(IllegalStateException.class, SkyforgeStructureCandidateStage::open);
        }
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
}
