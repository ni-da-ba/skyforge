package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Screens explicit game-scale hydraulic parameter hypotheses against fixed ordinary-span controls.
 *
 * <p>Key 700 is reported only as a held-out audit case; it is never used to choose parameters.
 * Transition boundary heads still come from the current transition planners, so this is calibration
 * evidence for ordinary spans, not yet a replacement F3E component solution.
 */
public final class HydrologyGameScaleCalibrationSweepCli {
    public static final String EVIDENCE_ID = "hydrology-game-scale-calibration-sweep-v1";
    private static final long SEED = 0x534B59464F524745L;
    private static final double[] DISCHARGE_SCALES = {0.1, 0.5, 2.0};
    private static final double[] MANNING_ROUGHNESSES = {0.025, 0.035, 0.05};
    private static final double[] SIDE_SLOPES = {0.25, 0.5, 1.0};

    private HydrologyGameScaleCalibrationSweepCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);
        StringBuilder rows = new StringBuilder(
                "control,heldOut,province,cluster,key,parameterSet,metersPerWorldUnit,dischargeScale,"
                        + "manningRoughness,sideSlope,spanCount,qualified,rejected,hydraulicFailure,"
                        + "upstreamStageMismatch,deferred,invalidGeometry,maxEnergyResidualMeters,"
                        + "maxUpstreamStageResidualMeters\n");

        for (Control control : List.of(
                new Control("accepted-77", 6L, 61L, 77L, false),
                new Control("rejected-287", 8L, 81L, 287L, false),
                new Control("heldout-700", 8L, 81L, 700L, true))) {
            SkyIslandDescriptor descriptor = descriptor(control.province(), control.cluster(), control.key());
            SkyIslandOrdinarySpanPlan plan = SkyIslandOrdinarySpanPlanner.plan(descriptor);
            SkyIslandGeomorphicQualificationPolicy policy =
                    SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
            SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);
            Map<Integer, SkyIslandChannelTerminalFateKind> terminalFates = terminalFates(plan);
            double planningSpacing = plan.cascadePlan().transitionGeometry().topology()
                    .skeletonPlan().geomorphicNetwork().planningSpacing();

            for (double dischargeScale : DISCHARGE_SCALES) {
                for (double roughness : MANNING_ROUGHNESSES) {
                    for (double sideSlope : SIDE_SLOPES) {
                        SkyIslandGameScaleHydraulicCalibration calibration =
                                new SkyIslandGameScaleHydraulicCalibration(
                                        1.0, dischargeScale, roughness, sideSlope,
                                        1.0, 9.81, 1.0e-8, 160);
                        Assessment assessment = assess(
                                descriptor, plan, policy, terrain, terminalFates,
                                planningSpacing, calibration);
                        rows.append(control.name()).append(',')
                                .append(control.heldOut()).append(',')
                                .append(control.province()).append(',')
                                .append(control.cluster()).append(',')
                                .append(control.key()).append(',')
                                .append(parameterSet(dischargeScale, roughness, sideSlope)).append(',')
                                .append(format(calibration.metersPerWorldUnit())).append(',')
                                .append(format(dischargeScale)).append(',')
                                .append(format(roughness)).append(',')
                                .append(format(sideSlope)).append(',')
                                .append(assessment.spanCount()).append(',')
                                .append(assessment.qualified()).append(',')
                                .append(assessment.rejected()).append(',')
                                .append(assessment.hydraulicFailure()).append(',')
                                .append(assessment.upstreamStageMismatch()).append(',')
                                .append(assessment.deferred()).append(',')
                                .append(assessment.invalidGeometry()).append(',')
                                .append(format(assessment.maxEnergyResidualMeters())).append(',')
                                .append(format(assessment.maxUpstreamStageResidualMeters())).append('\n');
                    }
                }
            }
        }

        Files.writeString(out.resolve("ordinary-span-sweep.csv"), rows, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("README.txt"), """
                Game-scale open-channel calibration sweep v1

                Fixed controls: accepted ordinary outlet 6/61/77; known rejected 8/81/287;
                held-out audit identity 8/81/700. Key 700 is not used to choose parameters.
                The sweep is a deterministic 3x3x3 grid over explicit discharge scale,
                Manning roughness, and trapezoid side slope. One world unit is provisionally
                mapped to one metre; gravity and energy coefficient are explicit SI values.
                No candidate is silently selected by this program.

                Each row screens ordinary spans with the standard-step gradually-varied-flow solver,
                then independently applies the existing D2 geomorphic evaluator. Fixed transition
                stages still come from the current transition planners; deferred boundaries remain
                deferred. This is calibration evidence, not production F3E/F4 admission, and grants
                no terrain mutation, water placement, Minecraft, or human-review authority.
                """, StandardCharsets.UTF_8);
        System.out.println(out.resolve("ordinary-span-sweep.csv").toAbsolutePath());
    }

    private static Assessment assess(
            SkyIslandDescriptor descriptor,
            SkyIslandOrdinarySpanPlan plan,
            SkyIslandGeomorphicQualificationPolicy policy,
            SkyIslandSemanticField terrain,
            Map<Integer, SkyIslandChannelTerminalFateKind> terminalFates,
            double planningSpacing,
            SkyIslandGameScaleHydraulicCalibration calibration) {
        int qualified = 0;
        int rejected = 0;
        int hydraulicFailure = 0;
        int upstreamStageMismatch = 0;
        int deferred = 0;
        int invalidGeometry = 0;
        double maxEnergyResidual = 0.0;
        double maxUpstreamResidual = 0.0;

        for (SkyIslandOrdinarySpanOutcome outcome : plan.outcomes()) {
            SkyIslandOrdinaryHydraulicSpan span = outcome.span();
            if (span.boundaryDeferred()) {
                deferred++;
                continue;
            }
            try {
                SkyIslandOpenChannelOrdinarySpanSolver.Outcome solved =
                        SkyIslandOpenChannelOrdinarySpanSolver.solve(
                                descriptor,
                                span,
                                terrain,
                                policy,
                                planningSpacing,
                                calibration,
                                terminalFates);
                maxEnergyResidual = Math.max(
                        maxEnergyResidual,
                        solved.hydraulicProfile().maximumEnergyResidualMeters());
                if (solved.upstreamStageResidualMeters().isPresent()) {
                    maxUpstreamResidual = Math.max(
                            maxUpstreamResidual,
                            solved.upstreamStageResidualMeters().orElseThrow());
                }
                if (!solved.geomorphicallyQualified()) {
                    rejected++;
                } else if (!solved.upstreamStageCompatible()) {
                    upstreamStageMismatch++;
                } else {
                    qualified++;
                }
            } catch (IllegalArgumentException invalidSectionOrControl) {
                invalidGeometry++;
            } catch (IllegalStateException hydraulicNoSolution) {
                hydraulicFailure++;
            }
        }
        return new Assessment(
                plan.outcomes().size(),
                qualified,
                rejected,
                hydraulicFailure,
                upstreamStageMismatch,
                deferred,
                invalidGeometry,
                maxEnergyResidual,
                maxUpstreamResidual);
    }

    private static Map<Integer, SkyIslandChannelTerminalFateKind> terminalFates(
            SkyIslandOrdinarySpanPlan plan) {
        var network = plan.cascadePlan().transitionGeometry().topology().skeletonPlan()
                .geomorphicNetwork();
        Map<Integer, SkyIslandChannelTerminalFateKind> result = new HashMap<>();
        for (SkyIslandChannelTerminalFate fate :
                SkyIslandChannelTerminalFatePlanner.plan(plan.descriptor(), network)) {
            result.put(fate.channelTerminalCellIndex(), fate.kind());
        }
        return Map.copyOf(result);
    }

    private static String parameterSet(double discharge, double roughness, double sideSlope) {
        return String.format(Locale.ROOT, "q%.1f-n%.3f-m%.2f", discharge, roughness, sideSlope);
    }

    private static String format(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%.9f", value)
                : "";
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }

    private record Control(String name, long province, long cluster, long key, boolean heldOut) {}
    private record Assessment(
            int spanCount,
            int qualified,
            int rejected,
            int hydraulicFailure,
            int upstreamStageMismatch,
            int deferred,
            int invalidGeometry,
            double maxEnergyResidualMeters,
            double maxUpstreamStageResidualMeters) {}
}
