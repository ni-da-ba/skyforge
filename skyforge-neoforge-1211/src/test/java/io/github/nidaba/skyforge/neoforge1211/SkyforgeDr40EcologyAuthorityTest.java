package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationEcologicalOpportunityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationIsolationProfiler;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationSurfaceEcologyResolver;
import io.github.nidaba.skyforge.world.SkyIslandEcologicalOpportunityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandFreshwaterHabitatOpportunityProfiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyforgeDr40EcologyAuthorityTest {
    @Test
    void canonicalRuntimeVolumeHasExactNonPublishedEcologyAuthority() throws IOException {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        SkyIslandAuthoredRealizationAssociation association =
                SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume());
        SkyIslandAuthoredRealizationCatalog catalog = new SkyIslandAuthoredRealizationCatalog(
                fixture.descriptor().identity().worldSeed(),
                fixture.volume().id().archipelagoRootSeed(),
                List.of(association));

        assertEquals(1, catalog.size());
        assertEquals(association, catalog.associationFor(fixture.volume().id()).orElseThrow());
        assertEquals(1471L, association.authoredIdentity().islandKey());
        assertEquals(
                "6001989086914692933/sf-imp-0068-production-composed-cave/0/0/680068",
                association.realizedVolumeId().path());

        var realized = fixture.volume().compiledVolume().descriptor();
        var surface = new SkyIslandAuthoredRealizationSurfaceEcologyResolver(catalog)
                .sample(
                        fixture.volume().id(),
                        new Coordinate2(realized.centerX(), realized.centerZ()));
        assertTrue(surface.physicalColumnPresent());
        assertTrue(surface.authoredInteriority() > 0.0);
        assertTrue(surface.authoredSurfacePresent());

        var islandOpportunity =
                new SkyIslandEcologicalOpportunityProfiler().profile(fixture.descriptor());
        var catalogOpportunity =
                new SkyIslandAuthoredRealizationEcologicalOpportunityProfiler().profile(catalog);
        assertEquals(1, catalogOpportunity.islandCount());
        assertEquals(
                islandOpportunity,
                catalogOpportunity.islands().getFirst().islandProfile());
        assertEquals(
                islandOpportunity.horizontalOwnedAreaEstimate(),
                catalogOpportunity.totalHorizontalOwnedAreaEstimate(),
                0.0);

        var freshwater =
                new SkyIslandFreshwaterHabitatOpportunityProfiler().profile(fixture.descriptor());
        assertEquals(fixture.descriptor(), freshwater.descriptor());

        var isolation = new SkyIslandAuthoredRealizationIsolationProfiler().profile(catalog);
        assertEquals(1, isolation.islandCount());
        assertFalse(isolation.islands().getFirst().hasNeighbor());
        assertTrue(isolation.minimumNearestCenterDistance().isEmpty());
        assertTrue(isolation.minimumNearestNominalRadialGap().isEmpty());

        Path project = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                .toAbsolutePath().normalize();
        String authority = Files.readString(
                project.resolve("../docs/agent-state/DR40_ECOLOGY_AUTHORITY.json"));
        assertTrue(authority.contains("\"specimen_id\": \"P2_DRESSED_REGION_A\""));
        assertTrue(authority.contains("\"authority\": \"AUTH-0046_EXPLICIT_ASSOCIATION\""));
        assertTrue(authority.contains("\"publication_claim\": \"NONE\""));
        assertTrue(authority.contains("SkyIslandAuthoredRealizationSurfaceEcologyResolver"));
        assertTrue(authority.contains("SkyIslandAuthoredRealizationEcologicalOpportunityProfiler"));
        assertTrue(authority.contains("SkyIslandAuthoredRealizationIsolationProfiler"));
        assertTrue(authority.contains("\"nearest_neighbor\": \"ABSENT\""));
    }
}
