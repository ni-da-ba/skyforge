package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfiler;
import io.github.nidaba.skyforge.world.content.FreightTransferEdgeSitePolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class Dr30CanonicalFreightRouteBindingTest {
    @Test
    void canonicalSpecimenBindsFirstEligibleFreightSiteAndFirstObservedOpenRay() throws IOException {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var association = SkyIslandAuthoredRealizationAssociation.of(
                fixture.descriptor(), fixture.volume());
        var access = new SkyIslandSurfaceAccessCapabilityProfiler()
                .profile(new SkyIslandSurfaceSiteCapabilityProfiler().profile(association));

        var policy = new FreightTransferEdgeSitePolicy();
        var plan = policy.plan(access);

        assertEquals(FreightTransferEdgeSitePolicy.Outcome.SITE_AND_ROUTE_BOUND, plan.outcome());
        assertFalse(plan.requiresReplan());
        var selected = plan.selection().orElseThrow();
        assertEquals(association, selected.association());

        var expectedCell = access.cells().stream()
                .filter(cell -> cell.sourceCell().physicalSurfacePresent())
                .filter(cell -> cell.rays().stream().anyMatch(ray -> ray.observedOpenSample()))
                .findFirst()
                .orElseThrow();
        var expectedRay = expectedCell.rays().stream()
                .filter(ray -> ray.observedOpenSample())
                .findFirst()
                .orElseThrow();

        assertEquals(expectedCell, selected.siteEvidence());
        assertEquals(expectedCell.watershedCellIndex(), selected.watershedCellIndex());
        assertEquals(expectedRay, selected.routeRay());
        assertTrue(selected.routeRay().observedOpenSample());

        assertEquals(0x534B59464F524745L, selected.association().authoredIdentity().worldSeed());
        assertEquals(8L, selected.association().authoredIdentity().provinceKey());
        assertEquals(81L, selected.association().authoredIdentity().clusterKey());
        assertEquals(1471L, selected.association().authoredIdentity().islandKey());
        assertEquals(fixture.volume().id(), selected.association().realizedVolumeId());

        Path project = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                .toAbsolutePath().normalize();
        String contract = Files.readString(project.resolve(
                "../docs/agent-state/DR30_CONTENT_CANDIDATE_ROUTE_BINDING.json"));
        assertTrue(contract.contains("\"contract_id\": \"CONTENT-DR30-CANONICAL-FREIGHT-002\""));
        assertTrue(contract.contains("\"route_plan\": {"));
        assertTrue(contract.contains("\"authority\": \"CONTENT_BOUND\""));
        assertTrue(contract.contains("FreightTransferEdgeSitePolicy"));
        assertTrue(contract.contains("\"no_candidate\": \"REPLAN_REQUIRED\""));
        assertTrue(contract.contains("\"numeric_thresholds\": []"));
    }
}
