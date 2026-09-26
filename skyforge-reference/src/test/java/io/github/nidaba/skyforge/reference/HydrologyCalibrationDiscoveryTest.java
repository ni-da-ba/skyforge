package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfileKind;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandSemanticChannelReach;
import io.github.nidaba.skyforge.world.SkyIslandSemanticChannelReachPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyCandidate;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyKind;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyPlanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Temporary deterministic discovery sweep for missing calibration classes.
 *
 * <p>This scanner is intentionally not production evidence. Once fixed specimens are discovered,
 * replace it with named corpus entries and remove the sweep.
 */
@EnabledIfEnvironmentVariable(named = "SKYFORGE_HYDROLOGY_DISCOVERY", matches = "1")
class HydrologyCalibrationDiscoveryTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAX_HITS_PER_CLASS = 12;

    @Test
    void discoverPondAndPureIncisedSpecimensBeyondPriorSweep() throws Exception {
        Path out = Path.of("build", "evidence", "hydrology-calibration-discovery-v2");
        Files.createDirectories(out);

        List<String> hits = new ArrayList<>();
        List<NearPond> nearPonds = new ArrayList<>();
        int pondHits = 0;
        int lakeHits = 0;
        int incisedHits = 0;
        int scanned = 0;

        // E1/E2 already scanned 1..2048 in the 8/81 namespace without finding POND.
        int[][] namespaces = {{8, 81, 2049, 8192}, {6, 61, 1, 4096}};
        for (int[] namespace : namespaces) {
            for (int key = namespace[2]; key <= namespace[3]; key++) {
                SkyIslandDescriptor descriptor =
                        descriptor(namespace[0], namespace[1], key);
                scanned++;

                for (SkyIslandWaterbodyCandidate candidate :
                        SkyIslandWaterbodyPlanner.plan(descriptor).candidates()) {
                    double wetlandScore =
                            0.45 * candidate.saturationPotential()
                                    + 0.35 * candidate.retentionPotential()
                                    + 0.20 * (1.0 - candidate.relativeInflow());
                    double openWaterScore =
                            0.45 * candidate.relativeInflow()
                                    + 0.30 * candidate.basinScale()
                                    + 0.25 * candidate.persistence();

                    if (candidate.kind() == SkyIslandWaterbodyKind.POND
                            && pondHits < MAX_HITS_PER_CLASS) {
                        hits.add(String.format(
                                Locale.ROOT,
                                "POND,%d,%d,%d,%d,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f",
                                namespace[0],
                                namespace[1],
                                key,
                                candidate.sinkCellIndex(),
                                candidate.relativeInflow(),
                                candidate.retentionPotential(),
                                candidate.saturationPotential(),
                                candidate.persistence(),
                                wetlandScore,
                                openWaterScore));
                        pondHits++;
                    }

                    if (candidate.kind() == SkyIslandWaterbodyKind.LAKE
                            && lakeHits < MAX_HITS_PER_CLASS) {
                        hits.add(String.format(
                                Locale.ROOT,
                                "LAKE,%d,%d,%d,%d,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f",
                                namespace[0],
                                namespace[1],
                                key,
                                candidate.sinkCellIndex(),
                                candidate.relativeInflow(),
                                candidate.retentionPotential(),
                                candidate.saturationPotential(),
                                candidate.persistence(),
                                wetlandScore,
                                openWaterScore));
                        lakeHits++;
                    }

                    double pondDistance =
                            Math.max(0.0, openWaterScore - 0.55)
                                    + Math.max(0.0, wetlandScore - 0.63);
                    nearPonds.add(new NearPond(
                            pondDistance,
                            namespace[0],
                            namespace[1],
                            key,
                            candidate.sinkCellIndex(),
                            candidate.kind(),
                            wetlandScore,
                            openWaterScore));
                }

                if (incisedHits < MAX_HITS_PER_CLASS) {
                    for (SkyIslandSemanticChannelReach reach :
                            SkyIslandSemanticChannelReachPlanner.plan(descriptor).reaches()) {
                        boolean pureIncised = reach.profiles().stream()
                                .allMatch(profile ->
                                        profile.kind() == SkyIslandChannelProfileKind.INCISED);
                        if (pureIncised) {
                            hits.add(String.format(
                                    Locale.ROOT,
                                    "PURE_INCISED,%d,%d,%d,%d,%d,%d,%.9f",
                                    namespace[0],
                                    namespace[1],
                                    key,
                                    reach.startCellIndex(),
                                    reach.endCellIndex(),
                                    reach.profiles().size(),
                                    reach.downstreamRelativeDischarge()));
                            incisedHits++;
                            if (incisedHits >= MAX_HITS_PER_CLASS) {
                                break;
                            }
                        }
                    }
                }

                if (pondHits >= MAX_HITS_PER_CLASS
                        && incisedHits >= MAX_HITS_PER_CLASS) {
                    break;
                }
            }
            if (pondHits >= MAX_HITS_PER_CLASS
                    && incisedHits >= MAX_HITS_PER_CLASS) {
                break;
            }
        }

        nearPonds.sort(Comparator
                .comparingDouble(NearPond::distance)
                .thenComparingInt(NearPond::province)
                .thenComparingInt(NearPond::cluster)
                .thenComparingInt(NearPond::key)
                .thenComparingInt(NearPond::sink));

        StringBuilder csv = new StringBuilder(
                "kind,province,cluster,key,startOrSink,endOrBlank,countOrBlank,valueOrWetlandScore,"
                        + "openWaterScoreOrBlank,extra\n");
        for (String hit : hits) {
            csv.append(hit).append('\n');
        }
        csv.append("SUMMARY,scanned,").append(scanned)
                .append(",pondHits,").append(pondHits)
                .append(",lakeHits,").append(lakeHits)
                .append(",pureIncisedHits,").append(incisedHits)
                .append("\n");
        csv.append("NEAR_POND_HEADER,province,cluster,key,sink,kind,distance,wetlandScore,openWaterScore\n");
        for (NearPond near : nearPonds.stream().limit(20).toList()) {
            csv.append(String.format(
                    Locale.ROOT,
                    "NEAR_POND,%d,%d,%d,%d,%s,%.9f,%.9f,%.9f%n",
                    near.province(),
                    near.cluster(),
                    near.key(),
                    near.sink(),
                    near.kind().name(),
                    near.distance(),
                    near.wetlandScore(),
                    near.openWaterScore()));
        }

        Files.writeString(out.resolve("discovery.csv"), csv, StandardCharsets.UTF_8);
        assertTrue(scanned > 0);
    }

    private static SkyIslandDescriptor descriptor(int province, int cluster, int key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }

    private record NearPond(
            double distance,
            int province,
            int cluster,
            int key,
            int sink,
            SkyIslandWaterbodyKind kind,
            double wetlandScore,
            double openWaterScore) {}
}
