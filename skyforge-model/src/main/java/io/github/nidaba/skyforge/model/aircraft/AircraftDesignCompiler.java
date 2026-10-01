package io.github.nidaba.skyforge.model.aircraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic finite-domain compiler for backend-neutral aircraft analytical design.
 *
 * <p>The search and objective ordering preserve the accepted AIRCRAFT-001 v0.1 analytical semantics.
 * Runtime target behavior remains a separate downstream evidence domain.
 */
public final class AircraftDesignCompiler {
    private static final List<String> OBJECTIVE_ORDER = List.of(
            "cruise_lift_residual",
            "tail_volume_reference_error_sum",
            "analytical_induced_drag_coefficient",
            "geometry_complexity_proxy",
            "lexical_geometry_tiebreak");

    /** Compiles one finite-domain analytical design spec. */
    public AircraftDesignIR compile(AircraftDesignSpec spec) {
        Objects.requireNonNull(spec, "spec");

        AircraftDesignSpec.Mission mission = spec.mission();
        AircraftDesignSpec.DesignDomains domains = spec.designDomains();
        AircraftDesignSpec.Constraints constraints = spec.constraints();
        AircraftDesignSpec.ReferenceTargets references = spec.referenceTargets();
        AircraftDesignSpec.AnalyticalAssumptions assumptions = spec.declaredAssumptions();

        double dynamicPressure = AircraftAnalyticalMath.dynamicPressure(
                mission.airDensityKgM3(), mission.cruiseSpeedMS());
        double weight = AircraftAnalyticalMath.weightForce(
                mission.grossMassKg(), mission.gravityMS2());
        double requiredWingArea = weight
                / (dynamicPressure * mission.designLiftCoefficient());

        List<Candidate> feasible = new ArrayList<>();
        Map<String, Integer> rejectionCounts = new HashMap<>();

        for (double fuselageLength : domains.fuselageLengthM()) {
            for (double span : domains.wingSpanM()) {
                for (double rootChord : domains.wingRootChordM()) {
                    for (double tipChord : domains.wingTipChordM()) {
                        for (double wingLeadingEdge : domains.wingLeadingEdgeXM()) {
                            if (rootChord < tipChord || tipChord <= 0.0) {
                                reject(rejectionCounts, "invalid_taper");
                                continue;
                            }
                            if (wingLeadingEdge <= 0.0
                                    || wingLeadingEdge + rootChord >= fuselageLength) {
                                reject(rejectionCounts, "wing_not_contained_by_fuselage_station");
                                continue;
                            }

                            WingPlanform wing = new WingPlanform(
                                    span, rootChord, tipChord, wingLeadingEdge);
                            double wingArea = AircraftAnalyticalMath.trapezoidArea(
                                    span, rootChord, tipChord);
                            double wingMac = AircraftAnalyticalMath.meanAerodynamicChord(
                                    rootChord, tipChord);
                            double aspectRatio = AircraftAnalyticalMath.aspectRatio(span, wingArea);
                            if (!constraints.aspectRatioRange().contains(aspectRatio)) {
                                reject(rejectionCounts, "aspect_ratio");
                                continue;
                            }

                            double lift = AircraftAnalyticalMath.liftForce(
                                    mission.airDensityKgM3(),
                                    mission.cruiseSpeedMS(),
                                    wingArea,
                                    mission.designLiftCoefficient());
                            double liftResidual = Math.abs(lift - weight) / weight;
                            if (liftResidual > constraints.maxCruiseLiftResidualFraction()) {
                                reject(rejectionCounts, "cruise_lift_residual");
                                continue;
                            }

                            List<AircraftDesignIR.MassItem> massItems = resolveMassItems(
                                    spec.massLedger(), fuselageLength);
                            double massSum = massItems.stream()
                                    .mapToDouble(AircraftDesignIR.MassItem::massKg)
                                    .sum();
                            if (Math.abs(massSum - mission.grossMassKg()) > 1.0e-9) {
                                throw new IllegalArgumentException(
                                        "mass ledger " + massSum
                                                + " kg does not equal gross mass "
                                                + mission.grossMassKg() + " kg");
                            }
                            double cgX = AircraftAnalyticalMath.massCenter(massItems.stream()
                                    .map(item -> new AircraftAnalyticalMath.MassPoint(
                                            item.massKg(), item.stationXM()))
                                    .toList());
                            double cgMacFraction = (cgX - wingLeadingEdge) / wingMac;
                            if (!constraints.specimenCgMacFractionRange().contains(cgMacFraction)) {
                                reject(rejectionCounts, "specimen_cg_mac_fraction");
                                continue;
                            }

                            TailChoice tail = bestTailPair(
                                    wing,
                                    wingArea,
                                    wingMac,
                                    fuselageLength,
                                    domains,
                                    references);
                            if (tail == null) {
                                reject(rejectionCounts, "tail_geometry");
                                continue;
                            }
                            if (tail.horizontalReferenceErrorFraction()
                                    > constraints.maxHorizontalTailVolumeReferenceErrorFraction()) {
                                reject(rejectionCounts, "horizontal_tail_volume_reference");
                                continue;
                            }
                            if (tail.verticalReferenceErrorFraction()
                                    > constraints.maxVerticalTailVolumeReferenceErrorFraction()) {
                                reject(rejectionCounts, "vertical_tail_volume_reference");
                                continue;
                            }

                            double inducedDrag = AircraftAnalyticalMath.inducedDragCoefficient(
                                    mission.designLiftCoefficient(),
                                    aspectRatio,
                                    assumptions.spanEfficiency());
                            double complexity = fuselageLength
                                    + span
                                    + 0.5 * (wingArea
                                            + tail.horizontalAreaM2()
                                            + tail.verticalAreaM2());

                            AircraftDesignIR.Metrics metrics = new AircraftDesignIR.Metrics(
                                    dynamicPressure,
                                    weight,
                                    requiredWingArea,
                                    wingArea,
                                    AircraftAnalyticalMath.taperRatio(rootChord, tipChord),
                                    aspectRatio,
                                    wingMac,
                                    lift,
                                    liftResidual,
                                    inducedDrag,
                                    cgX,
                                    cgMacFraction,
                                    tail.horizontalAreaM2(),
                                    tail.horizontalMacM(),
                                    tail.horizontalArmM(),
                                    tail.horizontalVolume(),
                                    tail.horizontalReferenceErrorFraction(),
                                    tail.verticalAreaM2(),
                                    tail.verticalMacM(),
                                    tail.verticalArmM(),
                                    tail.verticalVolume(),
                                    tail.verticalReferenceErrorFraction());

                            List<Double> objective = List.of(
                                    round12(liftResidual),
                                    round12(tail.horizontalReferenceErrorFraction()
                                            + tail.verticalReferenceErrorFraction()),
                                    round12(inducedDrag),
                                    round12(complexity),
                                    fuselageLength,
                                    span,
                                    rootChord,
                                    tipChord,
                                    wingLeadingEdge,
                                    tail.horizontal().spanOrHeightM(),
                                    tail.horizontal().rootChordM(),
                                    tail.horizontal().tipChordM(),
                                    tail.vertical().spanOrHeightM(),
                                    tail.vertical().rootChordM(),
                                    tail.vertical().tipChordM());

                            feasible.add(new Candidate(
                                    fuselageLength,
                                    wing,
                                    tail,
                                    massItems,
                                    metrics,
                                    objective));
                        }
                    }
                }
            }
        }

        if (feasible.isEmpty()) {
            throw new IllegalArgumentException(
                    "no feasible aircraft candidate; rejection counts="
                            + new java.util.TreeMap<>(rejectionCounts));
        }

        Candidate chosen = feasible.stream()
                .min(Comparator.comparing(Candidate::objective, AircraftDesignCompiler::compareObjective))
                .orElseThrow();

        AircraftDesignSpec.GeometryEnvelope envelope = spec.geometryEnvelope();
        AircraftDesignIR.Geometry geometry = new AircraftDesignIR.Geometry(
                new AircraftDesignIR.Fuselage(
                        chosen.fuselageLengthM(),
                        envelope.fuselageMaxWidthM(),
                        envelope.fuselageMaxHeightM()),
                new AircraftDesignIR.Wing(
                        chosen.wing().spanM(),
                        chosen.wing().rootChordM(),
                        chosen.wing().tipChordM(),
                        chosen.wing().leadingEdgeXM(),
                        spec.configuration().wingPosition(),
                        "zero"),
                new AircraftDesignIR.HorizontalTail(
                        chosen.tail().horizontal().spanOrHeightM(),
                        chosen.tail().horizontal().rootChordM(),
                        chosen.tail().horizontal().tipChordM(),
                        chosen.tail().horizontal().leadingEdgeXM()),
                new AircraftDesignIR.VerticalTail(
                        chosen.tail().vertical().spanOrHeightM(),
                        chosen.tail().vertical().rootChordM(),
                        chosen.tail().vertical().tipChordM(),
                        chosen.tail().vertical().leadingEdgeXM()),
                envelope.propellerEnvelope());

        return new AircraftDesignIR(
                AircraftDesignIR.SCHEMA_VERSION,
                spec.assetId(),
                AircraftDesignIR.COMPILER_VERSION,
                spec.configuration(),
                mission,
                assumptions,
                references,
                geometry,
                chosen.massItems(),
                chosen.metrics(),
                new AircraftDesignIR.SolverEvidence(
                        "deterministic_exhaustive_lexicographic_search",
                        feasible.size(),
                        rejectionCounts,
                        OBJECTIVE_ORDER,
                        chosen.objective()),
                new AircraftDesignIR.Validation(
                        true,
                        "analytical_geometry_and_balance_only",
                        List.of(
                                "target-runtime lift or stability",
                                "structural strength",
                                "stall behavior",
                                "dynamic stability",
                                "control authority")),
                new AircraftDesignIR.TargetBoundary(false, false));
    }

    private static TailChoice bestTailPair(
            WingPlanform wing,
            double wingArea,
            double wingMac,
            double fuselageLength,
            AircraftDesignSpec.DesignDomains domains,
            AircraftDesignSpec.ReferenceTargets references) {
        double horizontalLeadingEdge = fuselageLength
                - domains.horizontalTailLeadingEdgeInsetM();
        double verticalLeadingEdge = fuselageLength
                - domains.verticalTailLeadingEdgeInsetM();
        double wingAcX = wing.leadingEdgeXM() + 0.25 * wingMac;

        TailChoice best = null;
        List<Double> bestScore = null;

        for (double horizontalSpan : domains.horizontalTailSpanM()) {
            for (double horizontalRoot : domains.horizontalTailRootChordM()) {
                for (double horizontalTip : domains.horizontalTailTipChordM()) {
                    if (horizontalTip <= 0.0 || horizontalRoot < horizontalTip) {
                        continue;
                    }
                    TailPlanform horizontal = new TailPlanform(
                            horizontalSpan,
                            horizontalRoot,
                            horizontalTip,
                            horizontalLeadingEdge);
                    double horizontalMac = AircraftAnalyticalMath.meanAerodynamicChord(
                            horizontalRoot, horizontalTip);
                    double horizontalArea = AircraftAnalyticalMath.trapezoidArea(
                            horizontalSpan, horizontalRoot, horizontalTip);
                    double horizontalArm = horizontal.leadingEdgeXM()
                            + 0.25 * horizontalMac - wingAcX;
                    if (horizontalArm <= 0.0) {
                        continue;
                    }
                    double horizontalVolume = AircraftAnalyticalMath.horizontalTailVolume(
                            horizontalArea, horizontalArm, wingArea, wingMac);
                    double horizontalError = Math.abs(
                                    horizontalVolume - references.horizontalTailVolume())
                            / references.horizontalTailVolume();

                    for (double verticalHeight : domains.verticalTailHeightM()) {
                        for (double verticalRoot : domains.verticalTailRootChordM()) {
                            for (double verticalTip : domains.verticalTailTipChordM()) {
                                if (verticalTip <= 0.0 || verticalRoot < verticalTip) {
                                    continue;
                                }
                                TailPlanform vertical = new TailPlanform(
                                        verticalHeight,
                                        verticalRoot,
                                        verticalTip,
                                        verticalLeadingEdge);
                                double verticalMac = AircraftAnalyticalMath.meanAerodynamicChord(
                                        verticalRoot, verticalTip);
                                double verticalArea = AircraftAnalyticalMath.singleTrapezoidArea(
                                        verticalHeight, verticalRoot, verticalTip);
                                double verticalArm = vertical.leadingEdgeXM()
                                        + 0.25 * verticalMac - wingAcX;
                                if (verticalArm <= 0.0) {
                                    continue;
                                }
                                double verticalVolume = AircraftAnalyticalMath.verticalTailVolume(
                                        verticalArea, verticalArm, wingArea, wing.spanM());
                                double verticalError = Math.abs(
                                                verticalVolume - references.verticalTailVolume())
                                        / references.verticalTailVolume();

                                List<Double> score = List.of(
                                        round12(horizontalError + verticalError),
                                        round12(horizontalArea + verticalArea),
                                        horizontalSpan,
                                        horizontalRoot,
                                        horizontalTip,
                                        verticalHeight,
                                        verticalRoot,
                                        verticalTip);
                                if (bestScore == null || compareObjective(score, bestScore) < 0) {
                                    bestScore = score;
                                    best = new TailChoice(
                                            horizontal,
                                            vertical,
                                            horizontalArea,
                                            horizontalMac,
                                            horizontalArm,
                                            horizontalVolume,
                                            horizontalError,
                                            verticalArea,
                                            verticalMac,
                                            verticalArm,
                                            verticalVolume,
                                            verticalError);
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private static List<AircraftDesignIR.MassItem> resolveMassItems(
            List<AircraftDesignSpec.MassItemSpec> specs, double fuselageLength) {
        List<AircraftDesignIR.MassItem> result = new ArrayList<>();
        for (AircraftDesignSpec.MassItemSpec item : specs) {
            if (item.stationXM() != null) {
                result.add(new AircraftDesignIR.MassItem(
                        item.name(), item.massKg(), item.stationXM(), "absolute_specimen_station"));
            } else {
                result.add(new AircraftDesignIR.MassItem(
                        item.name(),
                        item.massKg(),
                        item.stationFractionFuselage() * fuselageLength,
                        "fraction_of_fuselage_length"));
            }
        }
        return List.copyOf(result);
    }

    private static int compareObjective(List<Double> left, List<Double> right) {
        int length = Math.min(left.size(), right.size());
        for (int index = 0; index < length; index++) {
            int comparison = Double.compare(left.get(index), right.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static void reject(Map<String, Integer> counts, String reason) {
        counts.merge(reason, 1, Integer::sum);
    }

    private static double round12(double value) {
        return Math.round(value * 1.0e12) / 1.0e12;
    }

    private record WingPlanform(
            double spanM,
            double rootChordM,
            double tipChordM,
            double leadingEdgeXM) {}

    private record TailPlanform(
            double spanOrHeightM,
            double rootChordM,
            double tipChordM,
            double leadingEdgeXM) {}

    private record TailChoice(
            TailPlanform horizontal,
            TailPlanform vertical,
            double horizontalAreaM2,
            double horizontalMacM,
            double horizontalArmM,
            double horizontalVolume,
            double horizontalReferenceErrorFraction,
            double verticalAreaM2,
            double verticalMacM,
            double verticalArmM,
            double verticalVolume,
            double verticalReferenceErrorFraction) {}

    private record Candidate(
            double fuselageLengthM,
            WingPlanform wing,
            TailChoice tail,
            List<AircraftDesignIR.MassItem> massItems,
            AircraftDesignIR.Metrics metrics,
            List<Double> objective) {}
}
