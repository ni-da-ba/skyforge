package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandChannelDropKind;
import io.github.nidaba.skyforge.world.SkyIslandChannelProfile;
import io.github.nidaba.skyforge.world.SkyIslandCoherentHydrologicRealizationPlan;
import io.github.nidaba.skyforge.world.SkyIslandCoherentHydrologicRealizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPath;
import io.github.nidaba.skyforge.world.SkyIslandSemanticField;
import io.github.nidaba.skyforge.world.SkyIslandSemanticFieldSet;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cheap fixed-specimen diagnostic loop for the hydrology overhaul.
 *
 * <p>The primary island is intentionally feature-rich rather than aesthetically selected: it has a
 * retained basin, interior drops, an authored edge fall and a long routed channel component. The
 * secondary specimen is retained only until the primary scenario gains a real confluence; it keeps
 * hierarchy/confluence regressions visible without re-running a 4096-island search.
 */
public final class HydrologyReferenceIslandCli {
    public static final String EVIDENCE_ID = "hydrology-reference-island-v1";
    public static final long PRIMARY_ISLAND_KEY = 287L;
    public static final long CONFLUENCE_CONTROL_ISLAND_KEY = 649L;

    private static final long WORLD_SEED = 0x534B59464F524745L;
    private static final long PROVINCE_KEY = 8L;
    private static final long CLUSTER_KEY = 81L;

    private HydrologyReferenceIslandCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Result result = generate(out);
        System.out.print(result.summary());
    }

    static Result generate(Path out) throws IOException {
        Files.createDirectories(out);
        Diagnostic primary = diagnose("primary", descriptor(PRIMARY_ISLAND_KEY));
        Diagnostic confluence =
                diagnose("confluence-control", descriptor(CONFLUENCE_CONTROL_ISLAND_KEY));
        String csv = csv(List.of(primary, confluence));
        String summary = summary(primary, confluence);
        Files.writeString(out.resolve("diagnostics.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("summary.txt"), summary, StandardCharsets.UTF_8);
        return new Result(primary, confluence, summary);
    }

    private static Diagnostic diagnose(String specimen, SkyIslandDescriptor descriptor) {
        SkyIslandCoherentHydrologicRealizationPlan coherent =
                SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor);
        var naturalized = coherent.naturalizedChannels();
        SkyIslandSemanticField terrain = SkyIslandSemanticFieldSet.create(descriptor).elevationTendency();

        int retainedWater =
                SkyIslandWaterbodyFootprintPlanner.plan(descriptor).footprints().size();
        int interiorDrops = (int) (coherent.drops().count(SkyIslandChannelDropKind.CASCADE_STEP)
                + coherent.drops().count(SkyIslandChannelDropKind.WATERFALL));
        int edgeFalls = (int) coherent.drops().count(SkyIslandChannelDropKind.EDGE_FALL);

        Map<Integer, Integer> inbound = new HashMap<>();
        int maxStreamOrder = 0;
        for (SkyIslandChannelProfile profile : coherent.channels().profiles()) {
            inbound.merge(profile.segment().downstreamCellIndex(), 1, Integer::sum);
            maxStreamOrder = Math.max(maxStreamOrder, profile.segment().streamOrder());
        }
        long confluences = inbound.values().stream().filter(count -> count >= 2).count();

        long routeSteps = 0;
        long uphillSteps = 0;
        long endpointUphillReaches = 0;
        long lateralSamples = 0;
        long ridgeCrossings = 0;
        double valleyAdvantage = 0.0;
        double spacing = naturalized.planningSpacing();

        for (SkyIslandNaturalizedChannelPath path : naturalized.paths()) {
            List<SkyIslandLocalPosition> points = path.points();
            if (terrain.sample(path.profile().segment().end())
                    > terrain.sample(path.profile().segment().start()) + 1.0e-4) {
                endpointUphillReaches++;
            }
            for (int i = 1; i < points.size(); i++) {
                routeSteps++;
                double previous = terrain.sample(points.get(i - 1));
                double current = terrain.sample(points.get(i));
                if (current > previous + 1.0e-4) {
                    uphillSteps++;
                }
            }
            for (int i = 1; i + 1 < points.size(); i++) {
                SkyIslandLocalPosition previous = points.get(i - 1);
                SkyIslandLocalPosition current = points.get(i);
                SkyIslandLocalPosition next = points.get(i + 1);
                double tx = next.x() - previous.x();
                double tz = next.z() - previous.z();
                double length = Math.hypot(tx, tz);
                if (length <= 1.0e-12) {
                    continue;
                }
                double nx = -tz / length;
                double nz = tx / length;
                double probe = spacing * 0.22;
                SkyIslandLocalPosition left =
                        new SkyIslandLocalPosition(current.x() + nx * probe, current.z() + nz * probe);
                SkyIslandLocalPosition right =
                        new SkyIslandLocalPosition(current.x() - nx * probe, current.z() - nz * probe);
                double center = terrain.sample(current);
                double sideMean = 0.5 * (terrain.sample(left) + terrain.sample(right));
                valleyAdvantage += sideMean - center;
                lateralSamples++;
                if (center > sideMean + 1.0e-4) {
                    ridgeCrossings++;
                }
            }
        }

        return new Diagnostic(
                specimen,
                descriptor.identity().islandKey(),
                descriptor.morphologyFamily().identifier(),
                coherent.channels().retainedComponentCount(),
                coherent.channels().retainedReachCount(),
                maxStreamOrder,
                confluences,
                retainedWater,
                interiorDrops,
                edgeFalls,
                naturalized.paths().isEmpty()
                        ? 0.0
                        : (double) endpointUphillReaches / naturalized.paths().size(),
                routeSteps == 0 ? 0.0 : (double) uphillSteps / routeSteps,
                lateralSamples == 0 ? 0.0 : (double) ridgeCrossings / lateralSamples,
                lateralSamples == 0 ? 0.0 : valleyAdvantage / lateralSamples,
                naturalized.meanLengthRatio(),
                naturalized.maxLengthRatio());
    }

    private static String csv(List<Diagnostic> diagnostics) {
        StringBuilder out = new StringBuilder(
                "specimen,islandKey,morphology,components,reaches,maxStreamOrder,confluences,"
                        + "retainedWater,interiorDrops,edgeFalls,endpointUphillReachFraction,uphillStepFraction,"
                        + "ridgeCrossingFraction,meanValleyAdvantage,meanLengthRatio,maxLengthRatio\n");
        for (Diagnostic d : diagnostics) {
            out.append(d.specimen()).append(',')
                    .append(d.islandKey()).append(',')
                    .append(d.morphology()).append(',')
                    .append(d.components()).append(',')
                    .append(d.reaches()).append(',')
                    .append(d.maxStreamOrder()).append(',')
                    .append(d.confluences()).append(',')
                    .append(d.retainedWater()).append(',')
                    .append(d.interiorDrops()).append(',')
                    .append(d.edgeFalls()).append(',')
                    .append(format(d.endpointUphillReachFraction())).append(',')
                    .append(format(d.uphillStepFraction())).append(',')
                    .append(format(d.ridgeCrossingFraction())).append(',')
                    .append(format(d.meanValleyAdvantage())).append(',')
                    .append(format(d.meanLengthRatio())).append(',')
                    .append(format(d.maxLengthRatio())).append('\n');
        }
        return out.toString();
    }

    private static String summary(Diagnostic primary, Diagnostic confluence) {
        return "HYDROLOGY REFERENCE ISLAND\n"
                + "primary=" + primary.islandKey()
                + " reaches=" + primary.reaches()
                + " retainedWater=" + primary.retainedWater()
                + " interiorDrops=" + primary.interiorDrops()
                + " edgeFalls=" + primary.edgeFalls()
                + " endpointUphill=" + format(primary.endpointUphillReachFraction())
                + " uphill=" + format(primary.uphillStepFraction())
                + " ridge=" + format(primary.ridgeCrossingFraction()) + "\n"
                + "confluenceControl=" + confluence.islandKey()
                + " maxStreamOrder=" + confluence.maxStreamOrder()
                + " confluences=" + confluence.confluences()
                + " endpointUphill=" + format(confluence.endpointUphillReachFraction())
                + " uphill=" + format(confluence.uphillStepFraction())
                + " ridge=" + format(confluence.ridgeCrossingFraction()) + "\n";
    }

    private static SkyIslandDescriptor descriptor(long islandKey) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD_SEED, PROVINCE_KEY, CLUSTER_KEY, islandKey));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    record Result(Diagnostic primary, Diagnostic confluenceControl, String summary) {}

    record Diagnostic(
            String specimen,
            long islandKey,
            String morphology,
            int components,
            int reaches,
            int maxStreamOrder,
            long confluences,
            int retainedWater,
            int interiorDrops,
            int edgeFalls,
            double endpointUphillReachFraction,
            double uphillStepFraction,
            double ridgeCrossingFraction,
            double meanValleyAdvantage,
            double meanLengthRatio,
            double maxLengthRatio) {}
}
