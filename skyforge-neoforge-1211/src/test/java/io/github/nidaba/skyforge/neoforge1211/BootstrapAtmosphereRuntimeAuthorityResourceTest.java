package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class BootstrapAtmosphereRuntimeAuthorityResourceTest {
    @Test
    void exposesPinnedSingleAuthorityContractForImplementation() throws IOException {
        Path project = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                .toAbsolutePath().normalize();
        String contract = Files.readString(project.resolve(
                "../docs/agent-state/BOOTSTRAP_ATMOSPHERE_RUNTIME_AUTHORITY.json"));

        assertTrue(contract.contains("\"contract_id\": \"CONTENT-BOOTSTRAP-ATMOSPHERE-001\""));
        assertTrue(contract.contains(
                "\"source_commit\": \"62a52a584e9c65246e50226b29a1f0449e43995e\""));

        assertTrue(contract.contains(
                "com.aerodynamics4mc.api.minecraft.AeroMinecraftWindApi#sampleGameplay(ServerLevel, Vec3)"));
        assertTrue(contract.contains("com.aerodynamics4mc.api.AeroWindApi#sampleGameplay"));
        assertTrue(contract.contains("com.aerodynamics4mc.api.SamplePolicy.GAMEPLAY_SERVER_ONLY"));

        assertTrue(contract.contains("\"meanX\""));
        assertTrue(contract.contains("\"meanY\""));
        assertTrue(contract.contains("\"meanZ\""));
        assertTrue(contract.contains("\"gustX\""));
        assertTrue(contract.contains("\"field\": \"updraftMetersPerSecond\""));
        assertTrue(contract.contains("\"source\": \"updraftMetersPerSecond < 0\""));
        assertTrue(contract.contains("\"field\": \"turbulenceIntensity\""));
        assertTrue(contract.contains("\"normalized_zero_to_one\": false"));
        assertTrue(contract.contains("\"field\": \"windShearMagnitudePerBlock\""));
        assertTrue(contract.contains("\"field\": \"pressure\""));
        assertTrue(contract.contains("\"isTrustedForGameplay()\""));
        assertTrue(contract.contains("\"sourceLevel\""));
        assertTrue(contract.contains("\"authority\""));
        assertTrue(contract.contains("\"l1Epoch\""));
        assertTrue(contract.contains("\"worldDeltaEpoch\""));
        assertTrue(contract.contains("\"l2Epoch\""));

        assertTrue(contract.contains("\"source\": \"sample query position Y\""));
        assertTrue(contract.contains("\"skyforge_duplicate_atmosphere_state\": false"));
        assertTrue(contract.contains("\"skyforge_persistence\": \"NONE_FOR_ATMOSPHERIC_TRUTH\""));
        assertTrue(contract.contains("\"persistent_skyforge_thermal_object_required\": false"));
        assertTrue(contract.contains("\"human_product_decision_required\": false"));
        assertTrue(contract.contains("\"player_glider\""));
        assertTrue(contract.contains("\"aircraft\""));
        assertTrue(contract.contains("\"soaring_fauna\""));

        assertFalse(contract.contains("AERONAUTICS_DEFAULT"));
        assertFalse(contract.contains("\"physicalAggregate\""));
        assertFalse(contract.contains("\"field\": \"downdraft\""));
        assertFalse(contract.contains("\"shearMagnitude\""));
        assertFalse(contract.contains("\"pressureProxy\""));
        assertFalse(contract.contains("\"sourceAgeTicks\""));
        assertFalse(contract.contains("\"sourceTrust\""));
        assertFalse(contract.contains("\"primarySource\""));
        assertFalse(contract.contains("\"contributingSourceCount\""));
        assertFalse(contract.contains("altitude_pressure_curve"));
        assertFalse(contract.contains("SECOND_SKYFORGE_WEATHER_AUTHORITY"));
    }
}
