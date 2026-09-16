package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

final class SkyforgeDr50IntegratedRegionEvidenceTest {
    @Test
    void canonicalMaterialAndAuthoredHydrologyPlansAreConcreteAndNoncolliding() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                java.util.Map.of(fixture.volume().id(), fixture.descriptor()));
        var iron = SkyforgeIronDepositAdapter.plan(
                        new SkyIslandBaseMetalOpportunityProfiler().profile(fixture.descriptor()),
                        fixture.volume(),
                        terrain,
                        SkyforgeIronDepositAdapter.Specification.representative())
                .orElseThrow();
        var hydrology = SkyforgeAuthoredVisibleHydrologyAdapter.plan(
                fixture.descriptor(), fixture.volume(), terrain);
        var waterPositions = new HashSet<net.minecraft.core.BlockPos>();
        hydrology.forEach(deployment -> waterPositions.addAll(deployment.positions()));

        assertFalse(hydrology.isEmpty());
        assertFalse(waterPositions.isEmpty());
        assertFalse(waterPositions.contains(iron.position()));
        assertTrue(terrain.isSolidOwnedBy(
                iron.volumeId(), iron.position().getX(), iron.position().getY(), iron.position().getZ()));
    }

    @Test
    void candidatePacketPinsTheDr00SpecimenAndObjectiveBoundaries() throws Exception {
        Path project = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                .toAbsolutePath().normalize();
        String packet = Files.readString(project.resolve("../docs/agent-state/DR50_INTEGRATED_REGION_EVIDENCE.json"));
        assertTrue(packet.contains("\"milestone\": \"DR-50\""));
        assertTrue(packet.contains("\"specimen_id\": \"P2_DRESSED_REGION_A\""));
        assertTrue(packet.contains("\"state\": \"CANDIDATE\""));
        assertTrue(packet.contains("\"aesthetic_tuning_authorized\": false"));
        assertTrue(packet.contains("\"impossible_deposit_placement\""));
        assertTrue(packet.contains("\"overwritten_structure_support\""));
        assertTrue(packet.contains("\"escaped_generated_fluid\""));
    }
}
