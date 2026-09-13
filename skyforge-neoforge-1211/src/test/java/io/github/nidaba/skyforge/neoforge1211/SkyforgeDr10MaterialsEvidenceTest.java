package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandBaseMetalKind;
import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfiler;
import io.github.nidaba.skyforge.world.content.SkyIslandBaseMetalContentPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Machine guard for DR-10's C20 starting-cluster scope selection on the locked DR-00 specimen. */
final class SkyforgeDr10MaterialsEvidenceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."));
    private static final Path EVIDENCE = PROJECT_DIRECTORY
            .getParent()
            .resolve("docs/agent-state/DR10_MATERIALS_EVIDENCE.json");

    @Test
    void canonicalDr00SpecimenSatisfiesTheC20IronStartingClusterCondition() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var profile = new SkyIslandBaseMetalOpportunityProfiler().profile(fixture.descriptor());
        var iron = SkyIslandBaseMetalContentPolicy.policyFor(SkyIslandBaseMetalKind.IRON);
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
        var deployment = SkyforgeIronDepositAdapter.plan(
                        profile,
                        fixture.volume(),
                        terrain,
                        SkyforgeIronDepositAdapter.Specification.representative())
                .orElseThrow();

        assertAll(
                () -> assertEquals(1471L, fixture.islandKey()),
                () -> assertEquals(
                        "6001989086914692933/sf-imp-0068-production-composed-cave/0/0/680068",
                        fixture.volume().id().path()),
                () -> assertEquals(
                        SkyIslandBaseMetalContentPolicy.AvailabilityClass.COMMON_REGIONAL,
                        iron.availabilityClass()),
                () -> assertTrue(iron.firstFlightCritical()),
                () -> assertEquals(
                        SkyIslandBaseMetalContentPolicy.GuaranteeScope.STARTING_CLUSTER,
                        iron.guaranteeScope()),
                () -> assertTrue(
                        SkyIslandBaseMetalContentPolicy.geologicallyEligible(profile, SkyIslandBaseMetalKind.IRON)),
                () -> assertTrue(profile.peakOpportunity(SkyIslandBaseMetalKind.IRON) > 0.0),
                () -> assertEquals(
                        deployment,
                        SkyforgeIronDepositAdapter.plan(
                                        profile,
                                        fixture.volume(),
                                        terrain,
                                        SkyforgeIronDepositAdapter.Specification.representative())
                                .orElseThrow()),
                () -> assertTrue(terrain.isSolidOwnedBy(
                        deployment.volumeId(),
                        deployment.position().getX(),
                        deployment.position().getY(),
                        deployment.position().getZ())));
    }

    @Test
    void machineReadableEvidenceRetainsTheCanonicalScopeAndPolicyBoundary() throws Exception {
        String evidence = Files.readString(EVIDENCE);

        assertAll(
                () -> assertTrue(evidence.contains("\"milestone\": \"DR-10\"")),
                () -> assertTrue(evidence.contains("\"specimen_id\": \"P2_DRESSED_REGION_A\"")),
                () -> assertTrue(evidence.contains(
                        "\"canonical_volume_id\": \"6001989086914692933/sf-imp-0068-production-composed-cave/0/0/680068\"")),
                () -> assertTrue(evidence.contains("\"required_metal\": \"IRON\"")),
                () -> assertTrue(evidence.contains("\"guarantee_scope\": \"STARTING_CLUSTER\"")),
                () -> assertTrue(evidence.contains("\"geological_eligibility\": \"AUTH-0093 peakOpportunity(IRON) > 0\"")),
                () -> assertTrue(evidence.contains("\"copper\": \"accepted vanilla Copper exact-volume deployment; POST_FLIGHT_PROVINCE only\"")),
                () -> assertTrue(evidence.contains("\"zinc\": \"accepted Create Zinc exact-volume deployment with C21 noncompetition; POST_FLIGHT_PROVINCE only\"")));
