package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Diagnostic characterization only; a passing run does not certify join continuity. */
class SkyIslandHydrologyJoinDiagnosticsTest {
    @Test
    void characterizesAnalyticAndNaturalSegmentSwitches() throws Exception {
        var points = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(10.0, 0.0),
                new SkyIslandLocalPosition(10.0, 10.0));
        var route = new SkyIslandGeomorphicCandidateRoute(
                points, 0.0, 20.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        var analytic = SkyIslandHydraulicCandidateBuilder.build(
                route,
                p -> 0.8 - 0.001 * (p.x() + p.z()),
                new SkyIslandHydraulicCandidateBuilder.Configuration(
                        1.0, 100.0, 0.0, 0.1, 5.0, 4.0, 2.0, 4.0, 20.0, 30.0));
        double coarse = analyticJump(analytic, 1.0e-3);
        double fine = analyticJump(analytic, 1.0e-6);
        // Characterize the existing bug against its exact, independently derived 0.4 m limit.
        // Replace this characterization with a zero-jump acceptance regression when repaired.
        assertEquals(0.4, fine, 1.0e-6);
        assertTrue(fine / coarse > 0.99);

        var descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 8L, 81L, 77L));
        var plan = SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        assertEquals(2, plan.terrainField().acceptedReaches().size());
        StringBuilder rows = new StringBuilder(
                "reach,vertex,offsetFraction,epsilon,x,z,terrainJumpMetres,stageJumpMetres,bothWet,status\n");
        double maximumTerrainJump = 0.0;
        double maximumStageJump = 0.0;
        String worstTerrain = "none";
        String worstStage = "none";
        int measured = 0;
        int failClosed = 0;
        for (var reach : plan.terrainField().acceptedReaches()) {
            var line = reach.centerline().points();
            var semantic = reach.geomorphicRoute().semanticReach();
            String identity = semantic.startCellIndex() + "->" + semantic.endCellIndex();
            for (int i = 1; i + 1 < line.size(); i++) {
                var before = line.get(i - 1);
                var vertex = line.get(i);
                var after = line.get(i + 1);
                double inLength = Math.hypot(vertex.x() - before.x(), vertex.z() - before.z());
                double outLength = Math.hypot(after.x() - vertex.x(), after.z() - vertex.z());
                double ux = (vertex.x() - before.x()) / inLength;
                double uz = (vertex.z() - before.z()) / inLength;
                double vx = (after.x() - vertex.x()) / outLength;
                double vz = (after.z() - vertex.z()) / outLength;
                double inwardLength = Math.hypot(vx - ux, vz - uz);
                double tangentLength = Math.hypot(vx + ux, vz + uz);
                if (inwardLength < 1.0e-8 || tangentLength < 1.0e-8) {
                    continue;
                }
                for (double offsetFraction : new double[] {0.25, 0.5, 1.0}) {
                    double offset = reach.samples().get(i).bankfullHalfWidth() * offsetFraction;
                    double x = vertex.x() + (vx - ux) / inwardLength * offset;
                    double z = vertex.z() + (vz - uz) / inwardLength * offset;
                    for (double epsilon : new double[] {1.0e-3, 1.0e-6}) {
                        try {
                            var a = plan.terrainField().sampleDetailed(new SkyIslandLocalPosition(
                                    x - epsilon * (vx + ux) / tangentLength,
                                    z - epsilon * (vz + uz) / tangentLength));
                            var b = plan.terrainField().sampleDetailed(new SkyIslandLocalPosition(
                                    x + epsilon * (vx + ux) / tangentLength,
                                    z + epsilon * (vz + uz) / tangentLength));
                            if (a.provenance().isEmpty() || b.provenance().isEmpty()) {
                                continue;
                            }
                            double terrainJump = Math.abs(a.targetTerrainPotential()
                                    - b.targetTerrainPotential()) * descriptor.reliefBudget();
                            double stageJump = Math.abs(a.waterSurfacePotential()
                                    - b.waterSurfacePotential()) * descriptor.reliefBudget();
                            assertTrue(Double.isFinite(terrainJump) && Double.isFinite(stageJump));
                            rows.append(String.format(Locale.ROOT,
                                    "%s,%d,%.2f,%.9f,%.9f,%.9f,%.9f,%.9f,%s,MEASURED%n",
                                    identity, i, offsetFraction, epsilon, x, z,
                                    terrainJump, stageJump, a.wet() && b.wet()));
                            if (epsilon == 1.0e-6) {
                                measured++;
                                String location = identity + " vertex=" + i
                                        + " x=" + x + " z=" + z;
                                if (terrainJump > maximumTerrainJump) {
                                    maximumTerrainJump = terrainJump;
                                    worstTerrain = location;
                                }
                                if (stageJump > maximumStageJump) {
                                    maximumStageJump = stageJump;
                                    worstStage = location;
                                }
                            }
                        } catch (IllegalStateException exception) {
                            failClosed++;
                            rows.append(identity).append(',').append(i).append(',')
                                    .append(offsetFraction).append(',').append(epsilon).append(',')
                                    .append(x).append(',').append(z)
                                    .append(",0,0,false,FAIL_CLOSED\n");
                        }
                    }
                }
            }
        }
        // Zero eligible bend probes is evidence, not a continuity pass.
        StringBuilder geometry = new StringBuilder(
                "reach,index,x,z,stationFraction,bedPotential,stagePotential,bankfullHalfWidth\n");
        for (var reach : plan.terrainField().acceptedReaches()) {
            var semantic = reach.geomorphicRoute().semanticReach();
            String identity = semantic.startCellIndex() + "->" + semantic.endCellIndex();
            for (int i = 0; i < reach.centerline().points().size(); i++) {
                var point = reach.centerline().points().get(i);
                var sample = reach.samples().get(i);
                geometry.append(String.format(Locale.ROOT,
                        "%s,%d,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f%n",
                        identity, i, point.x(), point.z(), sample.stationFraction(),
                        sample.bedElevationPotential(), sample.waterSurfacePotential(),
                        sample.bankfullHalfWidth()));
            }
        }
        Path directory = Path.of("build", "evidence", "hydrology-join-diagnostics");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("samples.csv"), rows);
        Files.writeString(directory.resolve("geometry.csv"), geometry);
        System.out.println(geometry);
        Files.writeString(directory.resolve("summary.txt"), String.format(Locale.ROOT,
                "DIAGNOSTIC ONLY: does not certify continuity or Minecraft appearance.%n"
                        + "analyticRightAngleJumpCoarseMetres=%.9f%n"
                        + "analyticRightAngleJumpFineMetres=%.9f%n"
                        + "key77FineProbeCount=%d%n"
                        + "key77MaximumTerrainJumpMetres=%.9f%n"
                        + "key77WorstTerrain=%s%n"
                        + "key77MaximumStageJumpMetres=%.9f%n"
                        + "key77WorstStage=%s%n"
                        + "failClosedProbeCount=%d%n",
                coarse, fine, measured, maximumTerrainJump, worstTerrain,
                maximumStageJump, worstStage, failClosed));
    }

    private static double analyticJump(
            SkyIslandHydraulicCandidateBuilder.Candidate candidate, double epsilon) {
        return Math.abs(candidate.sampleTerrainMetres(new SkyIslandLocalPosition(8.0 - epsilon, 2.0))
                - candidate.sampleTerrainMetres(new SkyIslandLocalPosition(8.0 + epsilon, 2.0)));
    }
}
