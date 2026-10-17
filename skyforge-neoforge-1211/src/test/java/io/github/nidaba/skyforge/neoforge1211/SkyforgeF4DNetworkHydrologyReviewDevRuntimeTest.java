package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyforgeF4DNetworkHydrologyReviewDevRuntimeTest {
    @Test
    void key632MultiReachNetworkRemainsAuthorizedThroughF4H() {
        SkyforgeF4DNetworkHydrologyReviewDevRuntime.Fixture fixture =
                SkyforgeF4DNetworkHydrologyReviewDevRuntime.fixture();

        assertTrue(
                fixture.realizedNetworkComponents() > 0,
                "F4D must start from a qualified multi-reach F4A network");
        assertTrue(
                fixture.refined().reaches().size() >= 2,
                "the network specimen must carry multiple reaches through F4G");
        assertFalse(
                fixture.authorization().quantization().authorizedColumns().isEmpty(),
                "the exact F4H authorization must retain its qualified network columns");
        assertFalse(
                SkyforgeF4DNetworkHydrologyReviewDevRuntime
                        .expectedWaterHeadPositions(fixture)
                        .isEmpty(),
                "the exact Minecraft adapter must receive persisted water-head positions");
        assertTrue(
                fixture.authorization().quantization().authorizedColumns().stream()
                        .anyMatch(column -> column.projection().semanticSample().wet()),
                "the authorized key-632 specimen must contain actual semantic water");
    }
}
