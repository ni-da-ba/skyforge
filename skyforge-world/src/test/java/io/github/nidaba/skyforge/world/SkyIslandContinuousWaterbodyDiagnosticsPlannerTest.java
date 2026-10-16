package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandContinuousWaterbodyDiagnosticsPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void basinDiagnosticsAreDeterministicAndFiniteAcrossControls() {
        for (long key : new long[] {83L, 287L, 512L, 632L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            List<SkyIslandContinuousWaterbodyDiagnostics> first =
                    SkyIslandContinuousWaterbodyDiagnosticsPlanner.measure(descriptor);
            List<SkyIslandContinuousWaterbodyDiagnostics> second =
                    SkyIslandContinuousWaterbodyDiagnosticsPlanner.measure(descriptor);
            assertEquals(first, second);
            for (SkyIslandContinuousWaterbodyDiagnostics d : first) {
                assertTrue(d.equivalentDiameter() > 0.0);
                assertTrue(Double.isFinite(d.maximumDepthWorldUnits()));
                assertTrue(Double.isFinite(d.depthToEquivalentDiameterRatio()));
                assertTrue(Double.isFinite(d.maximumShorelineGrade()));
                assertTrue(Double.isFinite(d.basin().shorelinePerimeterWorldUnits()));
                assertTrue(Double.isFinite(d.basin().shorelineIsoperimetricRatio()));
                assertTrue(d.basin().closedShorelineLoopCount() >= 0);
                assertTrue(d.basin().nonDegreeTwoShorelineVertexCount() >= 0);
                assertTrue(Double.isFinite(d.spillHeadroomWorldUnits()));
                assertTrue(d.matchedTerminalReachCount() >= 0);
                assertTrue(Double.isFinite(d.minimumTerminalChannelDatumOffsetWorldUnits()));
                assertTrue(Double.isFinite(d.maximumTerminalChannelDatumOffsetWorldUnits()));
                assertTrue(d.minimumTerminalChannelDatumOffsetWorldUnits()
                        <= d.maximumTerminalChannelDatumOffsetWorldUnits());
                assertEquals(
                        d.maximumChannelDatumMismatchWorldUnits(),
                        Math.max(
                                Math.abs(d.minimumTerminalChannelDatumOffsetWorldUnits()),
                                Math.abs(d.maximumTerminalChannelDatumOffsetWorldUnits())),
                        1.0e-9);
                assertTrue(Double.isFinite(d.maximumChannelDatumMismatchWorldUnits()));
                assertTrue(d.maximumChannelDatumMismatchWorldUnits() >= 0.0);
            }
        }
    }


    /**
     * Reports the explicitly authored channel-profile sequence at each fixed v5 LAKE sink.
     * This distinguishes an ordinary level junction from a transition that already carries
     * CASCADE semantics; it is diagnostic evidence only and never grants basin authority.
     */
    @Test
    void fixedV5LakeCorpusReportsTerminalProfileClassification() throws java.io.IOException {
        long[][] identities = {
                {0x534B59464F524745L, 8L, 81L, 609L},
                {0x3000000000000000L, 6L, 61L, 35L},
                {0x3000000000000000L, 6L, 61L, 200L},
                {0x9000000000000000L, 6L, 61L, 624L},
                {0xB000000000000000L, 8L, 81L, 500L},
                {0xF000000000000000L, 6L, 61L, 772L}
        };
        StringBuilder evidence = new StringBuilder(
                "seed_hex,province,cluster,key,sink_cell,reach_start,reach_end,"
                        + "terminal_profile,profile_sequence,signed_datum_offset_world_units,"
                        + "spill_headroom_world_units\n");
        int lakeCount = 0;
        for (long[] identity : identities) {
            SkyIslandDescriptor descriptor = descriptor(
                    identity[0], identity[1], identity[2], identity[3]);
            List<SkyIslandContinuousWaterbodyDiagnostics> lakes =
                    SkyIslandContinuousWaterbodyDiagnosticsPlanner.measure(descriptor).stream()
                            .filter(d -> d.basin().sourceCandidate().kind()
                                    == SkyIslandWaterbodyKind.LAKE)
                            .toList();
            assertEquals(1, lakes.size(),
                    "fixed v5 LAKE identity must remain explicit: " + java.util.Arrays.toString(identity));
            SkyIslandContinuousWaterbodyDiagnostics diagnostics = lakes.getFirst();
            int sink = diagnostics.basin().sourceCandidate().sinkCellIndex();
            List<SkyIslandHydraulicReachGeometry> matched =
                    SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor).reaches().stream()
                            .filter(reach -> reach.geomorphicRoute().semanticReach().endCellIndex() == sink)
                            .toList();
            assertEquals(diagnostics.matchedTerminalReachCount(), matched.size());
            assertTrue(!matched.isEmpty(),
                    "fixed v5 lake must retain its exact terminal-reach match");

            for (SkyIslandHydraulicReachGeometry reach : matched) {
                SkyIslandSemanticChannelReach semantic = reach.geomorphicRoute().semanticReach();
                String profileSequence = String.join("|", semantic.profiles().stream()
                        .map(profile -> profile.kind().name())
                        .toList());
                double offset = (reach.endWaterSurfacePotential()
                        - diagnostics.basin().waterSurfacePotential())
                        * descriptor.reliefBudget();
                evidence.append(String.format(
                        java.util.Locale.ROOT,
                        "0x%016X,%d,%d,%d,%d,%d,%d,%s,%s,%.9f,%.9f%n",
                        identity[0], identity[1], identity[2], identity[3], sink,
                        semantic.startCellIndex(), semantic.endCellIndex(),
                        semantic.profiles().getLast().kind().name(),
                        profileSequence, offset, diagnostics.spillHeadroomWorldUnits()));
                lakeCount++;
            }
        }
        assertEquals(6, lakeCount, "all six exact v5 lakes must have one matched terminal reach");
        java.nio.file.Path report = java.nio.file.Path.of(
                "build", "evidence", "hydrology-retained-basin-transition-profiles",
                "terminal-transition-profiles.csv");
        java.nio.file.Files.createDirectories(report.getParent());
        java.nio.file.Files.writeString(report, evidence.toString());
        System.out.println("RETAINED LAKE TERMINAL TRANSITION PROFILE EVIDENCE (diagnostic only)\n"
                + evidence);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return descriptor(SEED, 6L, 61L, key);
    }

    private static SkyIslandDescriptor descriptor(
            long seed, long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(seed, province, cluster, key));
    }
}
