package io.github.nidaba.skyforge.reference.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.nidaba.skyforge.world.SkyIslandTerrainSampleContext;
import io.github.nidaba.skyforge.world.SkyIslandTerrainSemantic;
import org.junit.jupiter.api.Test;

final class ReferenceTerrainMaterialAdapterTest {
    private final ReferenceTerrainMaterialAdapter adapter = new ReferenceTerrainMaterialAdapter();

    @Test
    void backendOwnedEnvironmentCanChangeRepresentationWithoutChangingSkyforgeMeaning() {
        var sample = new SkyIslandTerrainSampleContext(
                12.0, 320.0, -4.0, SkyIslandTerrainSemantic.SURFACE_MANTLE);

        ReferenceBackendMaterial temperate =
                adapter.materialFor(sample, ReferenceBackendEnvironment.TEMPERATE);
        ReferenceBackendMaterial frozen =
                adapter.materialFor(sample, ReferenceBackendEnvironment.FROZEN);

        assertEquals(ReferenceBackendMaterial.GREEN_SURFACE, temperate);
        assertEquals(ReferenceBackendMaterial.FROZEN_SURFACE, frozen);
        assertNotEquals(temperate, frozen);

        assertEquals(SkyIslandTerrainSemantic.SURFACE_MANTLE, sample.semantic());
        assertEquals(12.0, sample.x());
        assertEquals(320.0, sample.y());
        assertEquals(-4.0, sample.z());
    }

    @Test
    void referencePolicyPreservesSkyforgeOccupancyForEverySemanticAndEnvironment() {
        for (SkyIslandTerrainSemantic semantic : SkyIslandTerrainSemantic.values()) {
            var sample = new SkyIslandTerrainSampleContext(0.0, 0.0, 0.0, semantic);
            for (ReferenceBackendEnvironment environment : ReferenceBackendEnvironment.values()) {
                ReferenceBackendMaterial material = adapter.materialFor(sample, environment);
                assertEquals(
                        semantic.isSolid(),
                        material.isSolid(),
                        () -> "occupancy changed for " + semantic + " under " + environment);
            }
        }
    }

    @Test
    void sameSkyforgeAndBackendInputsAreDeterministic() {
        var sample = new SkyIslandTerrainSampleContext(
                -32.0, 208.0, 48.0, SkyIslandTerrainSemantic.UNDERSIDE_SHELL);

        assertEquals(
                adapter.materialFor(sample, ReferenceBackendEnvironment.TEMPERATE),
                adapter.materialFor(sample, ReferenceBackendEnvironment.TEMPERATE));
    }
}
