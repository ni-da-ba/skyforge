package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

/**
 * Relaxes a C1 centerline inside the broader semantic reach corridor.
 *
 * <p>The fine-lattice route is a deterministic terrain-aware seed, not final geometric authority.
 * Shared endpoints remain exact. Interior points may move away from the lattice path when they remain
 * inside the semantic guidance corridor, do not climb materially above the seeded terrain route, and
 * stay inside the authored island domain.
 */
public final class SkyIslandSemanticCorridorCenterlinePlanner {
    public static final int MAXIMUM_RELAXATION_SWEEPS = 48;
    private static final int MAXIMUM_D2_RELAXATION_SWEEPS = 96;
    public static final double RELAXATION_FRACTION = 0.40;
    public static final double MAXIMUM_TERRAIN_RISE_FROM_SEED = 0.015;
    public static final double MINIMUM_INTERIORITY = 0.025;

    private static final double EPSILON = 1.0e-12;

    private SkyIslandSemanticCorridorCenterlinePlanner() {}

    public static SkyIslandContinuousChannelCenterline refine(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double planningSpacing,
            double semanticCorridorHalfWidth,
            double minimumBendRadius) {
        return refine(
                searchRoute,
                semanticGuidance,
                terrain,
                interiority,
                planningSpacing,
                semanticCorridorHalfWidth,
                minimumBendRadius,
                ignored -> 0.0,
                null);
    }

    static SkyIslandContinuousChannelCenterline refine(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double planningSpacing,
            double semanticCorridorHalfWidth,
            double minimumBendRadius,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap) {
        return refineWithDiagnostics(
                searchRoute, semanticGuidance, terrain, interiority, planningSpacing,
                semanticCorridorHalfWidth, minimumBendRadius, bankfullHalfWidthAtStation,
                headEnvelopeGap).centerline();
    }

    static RefinementOutcome refineWithDiagnostics(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double planningSpacing,
            double semanticCorridorHalfWidth,
            double minimumBendRadius,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap) {
        return refineWithDiagnostics(
                searchRoute, semanticGuidance, terrain, interiority, planningSpacing,
                semanticCorridorHalfWidth, minimumBendRadius, bankfullHalfWidthAtStation,
                headEnvelopeGap, null);
    }

    static RefinementOutcome refineWithDiagnostics(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double planningSpacing,
            double semanticCorridorHalfWidth,
            double minimumBendRadius,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap,
            SkyIslandCenterlineLongitudinalHeadFeasibility longitudinalHeadFeasibility) {
        Objects.requireNonNull(searchRoute, "searchRoute");
        Objects.requireNonNull(bankfullHalfWidthAtStation, "bankfullHalfWidthAtStation");
        semanticGuidance = List.copyOf(semanticGuidance);
        semanticGuidance.forEach(p -> Objects.requireNonNull(p, "guidance point"));
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        requirePositive(planningSpacing, "planningSpacing");
        requirePositive(semanticCorridorHalfWidth, "semanticCorridorHalfWidth");
        requireNonNegative(minimumBendRadius, "minimumBendRadius");
        if (semanticGuidance.size() < 2) {
            throw new IllegalArgumentException("semantic guidance requires at least two points");
        }

        SkyIslandContinuousChannelCenterline seed =
                SkyIslandContinuousChannelCenterlinePlanner.refine(
                        searchRoute, terrain, interiority, planningSpacing);
        if (headEnvelopeGap != null) {
            // Begin D2-directed refinement from the already-qualified geometry-only C2 result.
            // A lower local head gap must not trade away C2's hard curvature-width boundary.
            seed = refine(
                    searchRoute,
                    semanticGuidance,
                    terrain,
                    interiority,
                    planningSpacing,
                    semanticCorridorHalfWidth,
                    minimumBendRadius,
                    bankfullHalfWidthAtStation,
                    null);
        }

        Candidate initial = evaluate(
                searchRoute, seed.points(), headEnvelopeGap, bankfullHalfWidthAtStation,
                longitudinalHeadFeasibility);
        Candidate best = initial;
        long lateralCandidateProposals = 0;
        long lateralCandidateAdmissible = 0;
        long lateralCandidateCorridorRejected = 0;
        long lateralCandidateTerrainRejected = 0;
        long lateralCandidateInteriorityRejected = 0;
        long lateralCandidateCurvatureRejected = 0;
        long lateralCandidateGapImproving = 0;
        long selectedLateralMoves = 0;
        long globalGapImprovementsBlockedByCurvature = 0;
        long globalModeSearchProposals = 0;
        long globalModeSearchAdmissible = 0;
        long globalModeSearchAcceptedMoves = 0;
        int globalModeSearchStages = 0;
        double globalModeSearchMaximumBudget = 0.0;
        int relaxationSweeps = 0;
        if ((headEnvelopeGap != null || longitudinalHeadFeasibility != null)
                && (best.maximumHeadEnvelopeGap() > EPSILON
                        || best.maximumLocalEnvelopeConflict() > EPSILON
                        || best.maximumLongitudinalGradeConflict() > EPSILON
                        || best.longitudinalHeadFeasibilityGap() > EPSILON)) {
            GlobalModeSearchOutcome globalModes = refineGlobalModes(
                    searchRoute, semanticGuidance, terrain, interiority,
                    semanticCorridorHalfWidth, minimumBendRadius,
                    bankfullHalfWidthAtStation, headEnvelopeGap,
                    longitudinalHeadFeasibility, best);
            best = globalModes.candidate();
            globalModeSearchProposals = globalModes.proposals();
            globalModeSearchAdmissible = globalModes.admissible();
            globalModeSearchAcceptedMoves = globalModes.acceptedMoves();
            globalModeSearchStages = globalModes.stages();
            globalModeSearchMaximumBudget = globalModes.maximumBudget();
        }
        List<SkyIslandLocalPosition> current = new ArrayList<>(best.points());
        int maximumSweeps = headEnvelopeGap == null
                ? MAXIMUM_RELAXATION_SWEEPS : MAXIMUM_D2_RELAXATION_SWEEPS;
        for (int sweep = 0; sweep < maximumSweeps; sweep++) {
            relaxationSweeps++;
            RelaxationStep step = relaxOnce(
                    searchRoute, semanticGuidance, current, terrain, interiority,
                    semanticCorridorHalfWidth, minimumBendRadius,
                    bankfullHalfWidthAtStation, headEnvelopeGap,
                    longitudinalHeadFeasibility);
            List<SkyIslandLocalPosition> next = new ArrayList<>(step.points());
            lateralCandidateProposals += step.lateralCandidateProposals();
            lateralCandidateAdmissible += step.lateralCandidateAdmissible();
            lateralCandidateCorridorRejected += step.lateralCandidateCorridorRejected();
            lateralCandidateTerrainRejected += step.lateralCandidateTerrainRejected();
            lateralCandidateInteriorityRejected += step.lateralCandidateInteriorityRejected();
            lateralCandidateCurvatureRejected += step.lateralCandidateCurvatureRejected();
            lateralCandidateGapImproving += step.lateralCandidateGapImproving();
            selectedLateralMoves += step.selectedLateralMoves();
            next.set(0, searchRoute.points().getFirst());
            next.set(next.size() - 1, searchRoute.points().getLast());

            Candidate candidate = evaluate(
                    searchRoute, next, headEnvelopeGap, bankfullHalfWidthAtStation,
                    longitudinalHeadFeasibility);
            int comparison = candidate.compareTo(best, minimumBendRadius);
            if (comparison < 0) {
                best = candidate;
            } else if (headEnvelopeGap != null
                    && (candidate.maximumHeadEnvelopeGap()
                                    < best.maximumHeadEnvelopeGap() - EPSILON
                            || candidate.integratedSquaredHeadEnvelopeGap()
                                    < best.integratedSquaredHeadEnvelopeGap() - EPSILON)
                    && candidate.curvatureExcess(minimumBendRadius)
                            > best.curvatureExcess(minimumBendRadius) + EPSILON) {
                globalGapImprovementsBlockedByCurvature++;
            }
            boolean unchanged = next.equals(current);
            current = next;

            if (headEnvelopeGap == null && longitudinalHeadFeasibility == null
                    && (minimumBendRadius <= EPSILON
                            || candidate.maximumCurvature() * minimumBendRadius
                                    <= 1.0 + EPSILON)) {
                best = candidate;
                break;
            }
            if (headEnvelopeGap != null
                    && (unchanged
                            || (candidate.maximumHeadEnvelopeGap() <= EPSILON
                                    && candidate.integratedSquaredHeadEnvelopeGap() <= EPSILON
                                    && candidate.maximumLocalEnvelopeConflict() <= EPSILON
                                    && candidate.maximumLongitudinalGradeConflict() <= EPSILON
                                    && candidate.longitudinalHeadFeasibilityGap() <= EPSILON
                                    && (minimumBendRadius <= EPSILON
                                            || candidate.maximumCurvature() * minimumBendRadius
                                                    <= 1.0 + EPSILON)))) {
                break;
            }
        }

        SourceEndpointSearchSummary sourceEndpointSearch =
                SourceEndpointSearchSummary.none();
        if (headEnvelopeGap != null
                && (best.maximumHeadEnvelopeGap() > EPSILON
                        || best.maximumLocalEnvelopeConflict() > EPSILON
                        || best.maximumLongitudinalGradeConflict() > EPSILON
                        || best.maximumConfluenceCascadeGradeConflict() > EPSILON)) {
            CoupledBlockOutcome coupled = refineCoupledBlocks(
                    searchRoute, semanticGuidance, terrain, interiority,
                    semanticCorridorHalfWidth, minimumBendRadius,
                    bankfullHalfWidthAtStation, headEnvelopeGap,
                    longitudinalHeadFeasibility, best);
            best = coupled.candidate();
            sourceEndpointSearch = coupled.sourceEndpointSearch();
        }

        for (SkyIslandLocalPosition point : best.points()) {
            if (distanceToPolyline(point, semanticGuidance) > semanticCorridorHalfWidth + EPSILON) {
                throw new IllegalStateException("relaxed centerline escaped semantic corridor");
            }
        }

        SkyIslandContinuousChannelCenterline centerline =
                new SkyIslandContinuousChannelCenterline(
                        searchRoute, best.points(), best.pathLength(),
                        best.maximumSearchDeviation(), best.maximumTurnAngle());
        SearchDiagnostics diagnostics = new SearchDiagnostics(
                initial.maximumHeadEnvelopeGap(),
                initial.maximumHeadEnvelopeGapIndex(),
                initial.maximumHeadEnvelopeGapStation(),
                initial.integratedSquaredHeadEnvelopeGap(),
                initial.longitudinalHeadFeasibilityGap(),
                initial.maximumLocalEnvelopeConflict(),
                initial.maximumSourceEndpointEnvelopeConflict(),
                initial.maximumLongitudinalGradeConflict(),
                initial.maximumConfluenceCascadeGradeConflict(),
                best.maximumHeadEnvelopeGap(),
                best.maximumHeadEnvelopeGapIndex(),
                best.maximumHeadEnvelopeGapStation(),
                best.integratedSquaredHeadEnvelopeGap(),
                best.longitudinalHeadFeasibilityGap(),
                best.maximumLocalEnvelopeConflict(),
                best.maximumSourceEndpointEnvelopeConflict(),
                best.maximumLongitudinalGradeConflict(),
                best.maximumConfluenceCascadeGradeConflict(),
                sourceEndpointSearch,
                lateralCandidateProposals, lateralCandidateAdmissible,
                lateralCandidateCorridorRejected, lateralCandidateTerrainRejected,
                lateralCandidateInteriorityRejected, lateralCandidateCurvatureRejected,
                lateralCandidateGapImproving,
                selectedLateralMoves, globalGapImprovementsBlockedByCurvature,
                relaxationSweeps,
                globalModeSearchProposals,
                globalModeSearchAdmissible,
                globalModeSearchAcceptedMoves,
                globalModeSearchStages,
                globalModeSearchMaximumBudget);
        return new RefinementOutcome(centerline, diagnostics);
    }

    /**
     * Searches smooth multi-sample lateral moves after pointwise D2 refinement reaches a plateau.
     *
     * <p>Pointwise moves can all violate the width-scaled curvature bound even when a coordinated
     * centerline displacement is admissible. This bounded direct search changes only continuous
     * centerline samples; semantic-corridor, terrain-rise, interiority, endpoint, and curvature
     * constraints remain hard. The head-envelope score ranks candidates but never authorizes
     * terrain mutation or bypasses the downstream D2 qualification gate.
     */
    private static CoupledBlockOutcome refineCoupledBlocks(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double semanticCorridorHalfWidth,
            double minimumBendRadius,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap,
            SkyIslandCenterlineLongitudinalHeadFeasibility longitudinalHeadFeasibility,
            Candidate initial) {
        Candidate best = initial;
        long sourceEndpointProposals = 0;
        long sourceEndpointGeometryAdmissible = 0;
        long sourceEndpointScoreImproving = 0;
        long sourceEndpointNoWorse = 0;
        long sourceEndpointHeadGapRegressions = 0;
        long sourceEndpointIntegratedGapRegressions = 0;
        long sourceEndpointLocalEnvelopeRegressions = 0;
        long sourceEndpointLongitudinalGradeRegressions = 0;
        long sourceEndpointConfluenceGradeRegressions = 0;
        long sourceEndpointAccepted = 0;
        List<Candidate> sourceEndpointCandidates = new ArrayList<>();
        double[] supportFractions = {0.25, 0.5, 0.75, 1.0};
        double[] sourceEndpointSupportScales = {1.0, 2.0, 4.0, 8.0};
        double[] amplitudeFractions = {0.03125, 0.0625, 0.125, 0.25, 0.5, 1.0};
        for (int round = 0; round < 4; round++) {
            List<SkyIslandLocalPosition> points = best.points();
            double[] arc = cumulativeArc(points);
            double totalLength = arc[arc.length - 1];
            if (totalLength <= EPSILON) {
                break;
            }
            double nominalStep = totalLength / (points.size() - 1.0);
            double[] pointGaps = headEnvelopeGaps(
                    points, bankfullHalfWidthAtStation, headEnvelopeGap);
            List<Integer> centers = new ArrayList<>(gapPeakCenters(pointGaps));
            double criticalGradeStation =
                    best.maximumConfluenceCascadeGradeConflictStation();
            if (Double.isFinite(criticalGradeStation) && points.size() > 2) {
                double[] pointStations = stations(points);
                int gradeCenter = 1;
                double nearestDistance = Math.abs(pointStations[gradeCenter] - criticalGradeStation);
                for (int index = 2; index < pointStations.length - 1; index++) {
                    double distance = Math.abs(pointStations[index] - criticalGradeStation);
                    if (distance < nearestDistance) {
                        gradeCenter = index;
                        nearestDistance = distance;
                    }
                }
                if (!centers.contains(gradeCenter)) centers.add(0, gradeCenter);
            }
            boolean sourceEndpointNeedsRefinement =
                    best.maximumSourceEndpointEnvelopeConflict() > EPSILON;
            if (sourceEndpointNeedsRefinement && points.size() > 2 && !centers.contains(1)) {
                centers.add(0, 1);
            }
            Candidate roundBest = best;
            for (int center : centers) {
                boolean sourceEndpointMode = sourceEndpointNeedsRefinement && center == 1;
                double availableSupport = sourceEndpointMode
                        ? totalLength - arc[center]
                        : Math.min(arc[center], totalLength - arc[center]);
                if (availableSupport <= EPSILON) {
                    continue;
                }
                double localHalfWidth = bankfullHalfWidthAtStation.applyAsDouble(
                        stations(points)[center]);
                double maximumAmplitude = Math.min(
                        semanticCorridorHalfWidth, Math.max(nominalStep, localHalfWidth));
                double[] supportLengths;
                if (sourceEndpointMode) {
                    supportLengths = new double[sourceEndpointSupportScales.length];
                    for (int i = 0; i < sourceEndpointSupportScales.length; i++) {
                        supportLengths[i] = nominalStep * sourceEndpointSupportScales[i];
                    }
                } else {
                    supportLengths = new double[supportFractions.length];
                    for (int i = 0; i < supportFractions.length; i++) {
                        supportLengths[i] = availableSupport * supportFractions[i];
                    }
                }
                for (double supportLength : supportLengths) {
                    if (supportLength > availableSupport + EPSILON
                            || supportLength + EPSILON < nominalStep) {
                        continue;
                    }
                    for (double amplitudeFraction : amplitudeFractions) {
                        double amplitude = maximumAmplitude * amplitudeFraction;
                        for (int direction : new int[] {-1, 1}) {
                            if (sourceEndpointMode) sourceEndpointProposals++;
                            List<SkyIslandLocalPosition> candidatePoints =
                                    coupledDisplacement(
                                            points, arc, center, supportLength,
                                            direction * amplitude);
                            if (!geometryAdmissible(
                                    candidatePoints, searchRoute, semanticGuidance,
                                    terrain, interiority, semanticCorridorHalfWidth,
                                    minimumBendRadius)) {
                                continue;
                            }
                            if (sourceEndpointMode) sourceEndpointGeometryAdmissible++;
                            Candidate candidate = evaluate(
                                    searchRoute, candidatePoints, headEnvelopeGap,
                                    bankfullHalfWidthAtStation, longitudinalHeadFeasibility);
                            boolean sourceEndpointImproved =
                                    candidate.maximumSourceEndpointEnvelopeConflict()
                                            < roundBest.maximumSourceEndpointEnvelopeConflict()
                                                    - EPSILON;
                            if (sourceEndpointMode && sourceEndpointImproved) {
                                sourceEndpointScoreImproving++;
                                if (candidate.refinementResidualsNoWorseThan(roundBest)) {
                                    sourceEndpointNoWorse++;
                                }
                                if (candidate.maximumHeadEnvelopeGap()
                                        > roundBest.maximumHeadEnvelopeGap() + EPSILON) {
                                    sourceEndpointHeadGapRegressions++;
                                }
                                if (candidate.integratedSquaredHeadEnvelopeGap()
                                        > roundBest.integratedSquaredHeadEnvelopeGap() + EPSILON) {
                                    sourceEndpointIntegratedGapRegressions++;
                                }
                                if (candidate.maximumLocalEnvelopeConflict()
                                        > roundBest.maximumLocalEnvelopeConflict() + EPSILON) {
                                    sourceEndpointLocalEnvelopeRegressions++;
                                }
                                if (candidate.maximumLongitudinalGradeConflict()
                                        > roundBest.maximumLongitudinalGradeConflict() + EPSILON) {
                                    sourceEndpointLongitudinalGradeRegressions++;
                                }
                                if (candidate.maximumConfluenceCascadeGradeConflict()
                                        > roundBest.maximumConfluenceCascadeGradeConflict() + EPSILON) {
                                    sourceEndpointConfluenceGradeRegressions++;
                                }
                            }
                            if (sourceEndpointMode && sourceEndpointImproved) {
                                sourceEndpointCandidates.add(candidate);
                            }
                            if (candidate.compareTo(roundBest, minimumBendRadius) < 0) {
                                if (sourceEndpointMode && sourceEndpointImproved) {
                                    sourceEndpointAccepted++;
                                }
                                roundBest = candidate;
                            }
                        }
                    }
                }
            }
            sourceEndpointCandidates.sort(Comparator
                    .comparingDouble(Candidate::maximumSourceEndpointEnvelopeConflict)
                    .thenComparingDouble(Candidate::maximumLocalEnvelopeConflict)
                    .thenComparingDouble(Candidate::integratedSquaredHeadEnvelopeGap));
            for (int seedIndex = 0;
                    seedIndex < Math.min(4, sourceEndpointCandidates.size());
                    seedIndex++) {
                Candidate endpointSeed = sourceEndpointCandidates.get(seedIndex);
                List<SkyIslandLocalPosition> seedPoints = endpointSeed.points();
                double[] seedArc = cumulativeArc(seedPoints);
                double seedLength = seedArc[seedArc.length - 1];
                double seedStep = seedLength / (seedPoints.size() - 1.0);
                double[] seedGaps = headEnvelopeGaps(
                        seedPoints, bankfullHalfWidthAtStation, headEnvelopeGap);
                double[] baselineGaps = headEnvelopeGaps(
                        best.points(), bankfullHalfWidthAtStation, headEnvelopeGap);
                double[] introducedGapPeaks = new double[seedGaps.length];
                for (int index = 0; index < seedGaps.length; index++) {
                    introducedGapPeaks[index] =
                            Math.max(0.0, seedGaps[index] - baselineGaps[index]);
                }
                List<Integer> correctionCenters =
                        new ArrayList<>(gapPeakCenters(introducedGapPeaks));
                for (int absolutePeak : gapPeakCenters(seedGaps)) {
                    if (correctionCenters.size() >= 3) break;
                    if (!correctionCenters.contains(absolutePeak)) {
                        correctionCenters.add(absolutePeak);
                    }
                }
                for (int correctionCenter : correctionCenters) {
                    if (correctionCenter == 1) continue;
                    double available = Math.min(
                            seedArc[correctionCenter],
                            seedLength - seedArc[correctionCenter]);
                    if (available + EPSILON < seedStep) continue;
                    double localHalfWidth = bankfullHalfWidthAtStation.applyAsDouble(
                            stations(seedPoints)[correctionCenter]);
                    double maximumAmplitude = Math.min(
                            semanticCorridorHalfWidth,
                            Math.max(seedStep, localHalfWidth));
                    double[] correctionSupports = {
                        available * 0.5, available
                    };
                    for (double supportLength : correctionSupports) {
                        if (supportLength + EPSILON < seedStep) continue;
                        for (double amplitudeFraction : new double[] {0.125, 0.25, 0.5, 1.0}) {
                            double amplitude = maximumAmplitude * amplitudeFraction;
                            for (int direction : new int[] {-1, 1}) {
                                sourceEndpointProposals++;
                                List<SkyIslandLocalPosition> candidatePoints =
                                        coupledDisplacement(
                                                seedPoints,
                                                seedArc,
                                                correctionCenter,
                                                supportLength,
                                                direction * amplitude);
                                if (!geometryAdmissible(
                                        candidatePoints,
                                        searchRoute,
                                        semanticGuidance,
                                        terrain,
                                        interiority,
                                        semanticCorridorHalfWidth,
                                        minimumBendRadius)) {
                                    continue;
                                }
                                sourceEndpointGeometryAdmissible++;
                                Candidate candidate = evaluate(
                                        searchRoute,
                                        candidatePoints,
                                        headEnvelopeGap,
                                        bankfullHalfWidthAtStation,
                                        longitudinalHeadFeasibility);
                                boolean sourceEndpointImproved =
                                        candidate.maximumSourceEndpointEnvelopeConflict()
                                                < roundBest.maximumSourceEndpointEnvelopeConflict()
                                                        - EPSILON;
                                if (sourceEndpointImproved) {
                                    sourceEndpointScoreImproving++;
                                    if (candidate.refinementResidualsNoWorseThan(roundBest)) {
                                        sourceEndpointNoWorse++;
                                    }
                                    if (candidate.maximumHeadEnvelopeGap()
                                            > roundBest.maximumHeadEnvelopeGap() + EPSILON) {
                                        sourceEndpointHeadGapRegressions++;
                                    }
                                    if (candidate.integratedSquaredHeadEnvelopeGap()
                                            > roundBest.integratedSquaredHeadEnvelopeGap() + EPSILON) {
                                        sourceEndpointIntegratedGapRegressions++;
                                    }
                                    if (candidate.maximumLocalEnvelopeConflict()
                                            > roundBest.maximumLocalEnvelopeConflict() + EPSILON) {
                                        sourceEndpointLocalEnvelopeRegressions++;
                                    }
                                    if (candidate.maximumLongitudinalGradeConflict()
                                            > roundBest.maximumLongitudinalGradeConflict() + EPSILON) {
                                        sourceEndpointLongitudinalGradeRegressions++;
                                    }
                                    if (candidate.maximumConfluenceCascadeGradeConflict()
                                            > roundBest.maximumConfluenceCascadeGradeConflict() + EPSILON) {
                                        sourceEndpointConfluenceGradeRegressions++;
                                    }
                                }
                                if (candidate.compareTo(roundBest, minimumBendRadius) < 0) {
                                    if (sourceEndpointImproved) sourceEndpointAccepted++;
                                    roundBest = candidate;
                                }
                            }
                        }
                    }
                }
            }
            if (roundBest.compareTo(best, minimumBendRadius) >= 0) {
                break;
            }
            best = roundBest;
        }
        return new CoupledBlockOutcome(
                best,
                new SourceEndpointSearchSummary(
                        sourceEndpointProposals,
                        sourceEndpointGeometryAdmissible,
                        sourceEndpointScoreImproving,
                        sourceEndpointNoWorse,
                        sourceEndpointHeadGapRegressions,
                        sourceEndpointIntegratedGapRegressions,
                        sourceEndpointLocalEnvelopeRegressions,
                        sourceEndpointLongitudinalGradeRegressions,
                        sourceEndpointConfluenceGradeRegressions,
                        sourceEndpointAccepted));
    }


    /**
     * Searches bounded low-frequency whole-route lateral deformations after local and coupled-window
     * refinement plateau. Sine modes vanish at both shared endpoints; their combined L1 amplitude
     * is bounded by the full authored semantic corridor. Every candidate
     * is rechecked against the unchanged corridor, terrain-rise, interiority, endpoint and curvature
     * constraints before the exact head-envelope objective can rank it.
     */
    private static GlobalModeSearchOutcome refineGlobalModes(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double semanticCorridorHalfWidth,
            double minimumBendRadius,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap,
            SkyIslandCenterlineLongitudinalHeadFeasibility longitudinalHeadFeasibility,
            Candidate initial) {
        List<SkyIslandLocalPosition> initialPoints = initial.points();
        if (initialPoints.size() < 4) {
            return new GlobalModeSearchOutcome(initial, 0, 0, 0, 0, 0.0);
        }

        double[] initialStations = stations(initialPoints);
        double maximumBankfullHalfWidth = 0.0;
        for (double station : initialStations) {
            double halfWidth = bankfullHalfWidthAtStation.applyAsDouble(station);
            if (!Double.isFinite(halfWidth) || halfWidth < 0.0) {
                throw new IllegalArgumentException(
                        "bankfull half-width must be finite and non-negative");
            }
            maximumBankfullHalfWidth = Math.max(maximumBankfullHalfWidth, halfWidth);
        }
        double nominalSpacing = length(initialPoints) / (initialPoints.size() - 1.0);
        double initialBudget = Math.min(
                semanticCorridorHalfWidth,
                Math.max(nominalSpacing, maximumBankfullHalfWidth));
        int modeCount = Math.min(8, initialPoints.size() - 2);
        if (!(initialBudget > EPSILON) || modeCount == 0) {
            return new GlobalModeSearchOutcome(initial, 0, 0, 0, 0, 0.0);
        }

        Candidate best = initial;
        long proposals = 0;
        long admissible = 0;
        long acceptedMoves = 0;
        int stages = 0;
        double maximumBudget = 0.0;
        double budget = initialBudget;
        while (true) {
            stages++;
            maximumBudget = budget;
            List<SkyIslandLocalPosition> reference = best.points();
            double[] referenceStations = stations(reference);
            double[] coefficients = new double[modeCount];
            for (int round = 0; round < 8; round++) {
                double step = budget * Math.scalb(1.0, -round);
                for (int mode = 0; mode < modeCount; mode++) {
                    for (int direction : new int[] {-1, 1}) {
                        double[] trialCoefficients = coefficients.clone();
                        trialCoefficients[mode] += direction * step;
                        double totalAmplitude = 0.0;
                        for (double coefficient : trialCoefficients) {
                            totalAmplitude += Math.abs(coefficient);
                        }
                        if (totalAmplitude > budget + EPSILON) {
                            continue;
                        }
                        proposals++;
                        List<SkyIslandLocalPosition> candidatePoints =
                                globalModeDisplacement(
                                        reference, referenceStations, trialCoefficients);
                        if (!geometryAdmissible(
                                candidatePoints, searchRoute, semanticGuidance,
                                terrain, interiority, semanticCorridorHalfWidth,
                                minimumBendRadius)) {
                            continue;
                        }
                        admissible++;
                        Candidate candidate = evaluate(
                                searchRoute, candidatePoints, headEnvelopeGap,
                                bankfullHalfWidthAtStation, longitudinalHeadFeasibility);
                        if (candidate.compareTo(best, minimumBendRadius) < 0) {
                            best = candidate;
                            coefficients = trialCoefficients;
                            acceptedMoves++;
                        }
                    }
                }
            }
            if (budget >= semanticCorridorHalfWidth - EPSILON) {
                break;
            }
            double nextBudget = Math.min(semanticCorridorHalfWidth, budget * 2.0);
            if (!(nextBudget > budget + EPSILON)) {
                break;
            }
            budget = nextBudget;
        }
        return new GlobalModeSearchOutcome(
                best, proposals, admissible, acceptedMoves, stages, maximumBudget);
    }

    private static List<SkyIslandLocalPosition> globalModeDisplacement(
            List<SkyIslandLocalPosition> reference,
            double[] stations,
            double[] coefficients) {
        List<SkyIslandLocalPosition> result = new ArrayList<>(reference.size());
        for (int i = 0; i < reference.size(); i++) {
            double displacement = 0.0;
            for (int mode = 0; mode < coefficients.length; mode++) {
                displacement += coefficients[mode]
                        * Math.sin((mode + 1.0) * Math.PI * stations[i]);
            }
            Vector tangent = tangentAt(reference, i);
            Vector normal = new Vector(-tangent.z(), tangent.x());
            SkyIslandLocalPosition point = reference.get(i);
            result.add(new SkyIslandLocalPosition(
                    point.x() + displacement * normal.x(),
                    point.z() + displacement * normal.z()));
        }
        result.set(0, reference.getFirst());
        result.set(result.size() - 1, reference.getLast());
        return List.copyOf(result);
    }

    private static double[] cumulativeArc(List<SkyIslandLocalPosition> points) {
        double[] arc = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            SkyIslandLocalPosition previous = points.get(i - 1);
            SkyIslandLocalPosition point = points.get(i);
            arc[i] = arc[i - 1]
                    + Math.hypot(point.x() - previous.x(), point.z() - previous.z());
        }
        return arc;
    }

    private static double[] headEnvelopeGaps(
            List<SkyIslandLocalPosition> points,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap) {
        double[] station = stations(points);
        double[] gaps = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Vector tangent = tangentAt(points, i);
            double halfWidth = bankfullHalfWidthAtStation.applyAsDouble(station[i]);
            if (!Double.isFinite(halfWidth) || halfWidth < 0.0) {
                throw new IllegalArgumentException(
                        "bankfull half-width must be finite and non-negative");
            }
            gaps[i] = checkedGap(headEnvelopeGap, points.get(i), station[i], tangent, halfWidth);
        }
        return gaps;
    }

    private static List<Integer> gapPeakCenters(double[] gaps) {
        List<Integer> centers = new ArrayList<>();
        int lastInterior = gaps.length - 2;
        int index = 1;
        while (index <= lastInterior) {
            if (gaps[index] <= EPSILON) {
                index++;
                continue;
            }
            int start = index;
            int end = index;
            double level = gaps[index];
            while (end < lastInterior
                    && Math.abs(gaps[end + 1] - level) <= EPSILON) {
                end++;
            }
            double left = start == 1 ? Double.NEGATIVE_INFINITY : gaps[start - 1];
            double right = end == lastInterior ? Double.NEGATIVE_INFINITY : gaps[end + 1];
            if (level + EPSILON >= left && level + EPSILON >= right) {
                centers.add((start + end) / 2);
            }
            index = end + 1;
        }
        centers.sort(Comparator
                .comparingDouble((Integer center) -> gaps[center])
                .reversed()
                .thenComparingInt(Integer::intValue));
        if (centers.size() > 3) {
            return List.copyOf(centers.subList(0, 3));
        }
        if (centers.isEmpty() && gaps.length > 2) {
            int maximum = maximumIndex(gaps);
            if (maximum > 0 && maximum < gaps.length - 1 && gaps[maximum] > EPSILON) {
                centers.add(maximum);
            }
        }
        return List.copyOf(centers);
    }

    private static List<SkyIslandLocalPosition> coupledDisplacement(
            List<SkyIslandLocalPosition> points,
            double[] arc,
            int center,
            double supportLength,
            double amplitude) {
        List<SkyIslandLocalPosition> result = new ArrayList<>(points);
        for (int i = 1; i < points.size() - 1; i++) {
            double distance = Math.abs(arc[i] - arc[center]);
            if (distance >= supportLength) {
                continue;
            }
            double weight = 0.5 * (1.0 + Math.cos(Math.PI * distance / supportLength));
            Vector tangent = tangentAt(points, i);
            Vector normal = new Vector(-tangent.z(), tangent.x());
            SkyIslandLocalPosition point = points.get(i);
            result.set(
                    i,
                    new SkyIslandLocalPosition(
                            point.x() + normal.x() * amplitude * weight,
                            point.z() + normal.z() * amplitude * weight));
        }
        return result;
    }

    private static boolean geometryAdmissible(
            List<SkyIslandLocalPosition> points,
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double semanticCorridorHalfWidth,
            double minimumBendRadius) {
        if (!points.getFirst().equals(searchRoute.points().getFirst())
                || !points.getLast().equals(searchRoute.points().getLast())) {
            return false;
        }
        for (int i = 0; i < points.size(); i++) {
            if (!admissibilityCheck(
                            points.get(i), searchRoute, semanticGuidance,
                            terrain, interiority, semanticCorridorHalfWidth)
                    .allowed()) {
                return false;
            }
            if (i > 0 && i < points.size() - 1
                    && localCurvature(points.get(i - 1), points.get(i), points.get(i + 1))
                                    * minimumBendRadius
                            > 1.0 + EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static RelaxationStep relaxOnce(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            List<SkyIslandLocalPosition> current,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double semanticCorridorHalfWidth,
            double minimumBendRadius,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap,
            SkyIslandCenterlineLongitudinalHeadFeasibility longitudinalHeadFeasibility) {
        List<SkyIslandLocalPosition> result = new ArrayList<>(current);
        long lateralCandidateProposals = 0;
        long lateralCandidateAdmissible = 0;
        long lateralCandidateCorridorRejected = 0;
        long lateralCandidateTerrainRejected = 0;
        long lateralCandidateInteriorityRejected = 0;
        long lateralCandidateCurvatureRejected = 0;
        long lateralCandidateGapImproving = 0;
        long selectedLateralMoves = 0;
        boolean objectiveActive =
                headEnvelopeGap != null || longitudinalHeadFeasibility != null;
        double[] stationSnapshot = objectiveActive ? null : stations(current);
        for (int i = 1; i < current.size() - 1; i++) {
            // D2 geometry depends on physical station through discharge-scaled width and depth.
            // Re-score each lateral move against the path already updated in this sweep.
            List<SkyIslandLocalPosition> working = objectiveActive ? result : current;
            double station = objectiveActive
                    ? stations(working)[i]
                    : stationSnapshot[i];
            SkyIslandLocalPosition previous = working.get(i - 1);
            SkyIslandLocalPosition point = working.get(i);
            SkyIslandLocalPosition next = working.get(i + 1);
            SkyIslandLocalPosition midpoint =
                    new SkyIslandLocalPosition(
                            0.5 * (previous.x() + next.x()),
                            0.5 * (previous.z() + next.z()));
            SkyIslandLocalPosition smoothed = lerp(point, midpoint, RELAXATION_FRACTION);
            List<SkyIslandLocalPosition> options = new ArrayList<>(13);
            options.add(smoothed);
            Vector tangent = tangent(previous, next);
            Vector normal = new Vector(-tangent.z(), tangent.x());
            double halfWidth = bankfullHalfWidthAtStation.applyAsDouble(station);
            if (!Double.isFinite(halfWidth) || halfWidth < 0.0) {
                throw new IllegalArgumentException(
                        "bankfull half-width must be finite and non-negative");
            }
            double currentGap = 0.0;
            if (objectiveActive && halfWidth > EPSILON) {
                if (headEnvelopeGap != null) {
                    currentGap = checkedGap(headEnvelopeGap, point, station, tangent, halfWidth);
                }
                SkyIslandCenterlineLongitudinalHeadFeasibility.Score longitudinalScore =
                        checkedLongitudinalScore(longitudinalHeadFeasibility, working);
                if (currentGap > EPSILON
                        || longitudinalScore.maximumLocalEnvelopeConflictWorldUnits() > EPSILON
                        || longitudinalScore.maximumGradePropagationConflictWorldUnits() > EPSILON
                        || longitudinalScore.integratedSquaredConflictWorldUnits() > EPSILON) {
                    for (int direction = -1; direction <= 1; direction += 2) {
                        double offset = 0.5 * halfWidth;
                        int backtracks = 0;
                        SkyIslandLocalPosition option =
                                lateralOption(point, normal, direction, offset);
                        while (!curvatureAdmissible(result, i, option, minimumBendRadius)
                                && backtracks < 12) {
                            lateralCandidateProposals++;
                            lateralCandidateCurvatureRejected++;
                            offset *= 0.5;
                            backtracks++;
                            option = lateralOption(point, normal, direction, offset);
                        }
                        for (int refinement = 0;
                                refinement < 3
                                        && curvatureAdmissible(
                                                result, i, option, minimumBendRadius);
                                refinement++) {
                            options.add(option);
                            offset *= 0.5;
                            option = lateralOption(point, normal, direction, offset);
                        }
                        if (backtracks == 12
                                && !curvatureAdmissible(
                                        result, i, option, minimumBendRadius)) {
                            lateralCandidateProposals++;
                            lateralCandidateCurvatureRejected++;
                        }
                    }
                }
            }

            SkyIslandLocalPosition selected = point;
            int selectedOption = -1;
            for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                SkyIslandLocalPosition option = options.get(optionIndex);
                AdmissionCheck admission = admissibilityCheck(
                        option, searchRoute, semanticGuidance, terrain, interiority,
                        semanticCorridorHalfWidth);
                boolean curvatureAllowed =
                        !objectiveActive
                                || curvatureAdmissible(
                                        result, i, option, minimumBendRadius);
                if (objectiveActive && optionIndex > 0) {
                    lateralCandidateProposals++;
                    if (!admission.insideSemanticCorridor()) lateralCandidateCorridorRejected++;
                    if (!admission.withinTerrainRise()) lateralCandidateTerrainRejected++;
                    if (!admission.insideIslandInteriority()) lateralCandidateInteriorityRejected++;
                    if (!curvatureAllowed) lateralCandidateCurvatureRejected++;
                    if (admission.allowed() && curvatureAllowed) {
                        lateralCandidateAdmissible++;
                        if (headEnvelopeGap != null
                                && checkedGap(headEnvelopeGap, option, station, tangent, halfWidth)
                                        < currentGap - EPSILON) {
                            lateralCandidateGapImproving++;
                        }
                    }
                }
                if (!admission.allowed() || !curvatureAllowed) continue;
                if ((headEnvelopeGap == null && longitudinalHeadFeasibility == null)
                        || compareLocalCandidates(
                                        result, i, option, selected,
                                        bankfullHalfWidthAtStation, headEnvelopeGap,
                                        searchRoute, longitudinalHeadFeasibility) < 0) {
                    selected = option;
                    selectedOption = optionIndex;
                }
            }
            if (selectedOption > 0) selectedLateralMoves++;
            result.set(i, selected);
        }
        return new RelaxationStep(
                List.copyOf(result), lateralCandidateProposals, lateralCandidateAdmissible,
                lateralCandidateCorridorRejected, lateralCandidateTerrainRejected,
                lateralCandidateInteriorityRejected, lateralCandidateCurvatureRejected,
                lateralCandidateGapImproving, selectedLateralMoves);
    }

    private static Candidate evaluate(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> points,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineLongitudinalHeadFeasibility longitudinalHeadFeasibility) {
        double maximumSearchDeviation = 0.0;
        double[] gaps = new double[points.size()];
        double[] station = stations(points);
        for (int i = 0; i < points.size(); i++) {
            SkyIslandLocalPosition point = points.get(i);
            maximumSearchDeviation =
                    Math.max(
                            maximumSearchDeviation,
                            project(point, searchRoute.points()).distance());
            if (headEnvelopeGap != null) {
                Vector tangent = tangentAt(points, i);
                double halfWidth = bankfullHalfWidthAtStation.applyAsDouble(station[i]);
                if (!Double.isFinite(halfWidth) || halfWidth < 0.0) {
                    throw new IllegalArgumentException(
                            "bankfull half-width must be finite and non-negative");
                }
                gaps[i] = checkedGap(
                        headEnvelopeGap, point, station[i], tangent, halfWidth);
            }
        }
        double pathLength = length(points);
        double integratedSquaredGap = 0.0;
        if (headEnvelopeGap != null && pathLength > EPSILON) {
            for (int i = 0; i + 1 < points.size(); i++) {
                double ds = pathLength * (station[i + 1] - station[i]);
                integratedSquaredGap +=
                        0.5 * (gaps[i] * gaps[i] + gaps[i + 1] * gaps[i + 1]) * ds;
            }
            integratedSquaredGap /= pathLength;
        }
        int maximumGapIndex = maximumIndex(gaps);
        SkyIslandCenterlineLongitudinalHeadFeasibility.Score longitudinalScore =
                checkedLongitudinalScore(longitudinalHeadFeasibility, points);
        return new Candidate(
                List.copyOf(points),
                pathLength,
                maximumSearchDeviation,
                maximumTurnAngle(points),
                maximumCurvature(points),
                gaps[maximumGapIndex],
                maximumGapIndex,
                station[maximumGapIndex],
                integratedSquaredGap,
                longitudinalScore.maximumLocalEnvelopeConflictWorldUnits(),
                longitudinalScore.maximumSourceEndpointEnvelopeConflictWorldUnits(),
                longitudinalScore.maximumGradePropagationConflictWorldUnits(),
                longitudinalScore.maximumConfluenceCascadeGradeConflictWorldUnits(),
                longitudinalScore.integratedSquaredConflictWorldUnits(),
                longitudinalScore.maximumConfluenceCascadeGradeConflictStation());
    }

    private static AdmissionCheck admissibilityCheck(
            SkyIslandLocalPosition candidate,
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double semanticCorridorHalfWidth) {
        Projection seedProjection = project(candidate, searchRoute.points());
        return new AdmissionCheck(
                distanceToPolyline(candidate, semanticGuidance)
                        <= semanticCorridorHalfWidth + EPSILON,
                terrain.sample(candidate) - terrain.sample(seedProjection.position())
                        <= MAXIMUM_TERRAIN_RISE_FROM_SEED + EPSILON,
                interiority.sample(candidate) >= MINIMUM_INTERIORITY);
    }

    private static int compareLocalCandidates(
            List<SkyIslandLocalPosition> points,
            int changedIndex,
            SkyIslandLocalPosition first,
            SkyIslandLocalPosition second,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap,
            SkyIslandGeomorphicCandidateRoute searchRoute,
            SkyIslandCenterlineLongitudinalHeadFeasibility longitudinalHeadFeasibility) {
        LocalHeadGapScore firstGap = localHeadGapScore(
                points, changedIndex, first, bankfullHalfWidthAtStation, headEnvelopeGap);
        LocalHeadGapScore secondGap = localHeadGapScore(
                points, changedIndex, second, bankfullHalfWidthAtStation, headEnvelopeGap);
        boolean firstGeometryValid = Double.isFinite(firstGap.maximumGap());
        boolean secondGeometryValid = Double.isFinite(secondGap.maximumGap());
        if (firstGeometryValid != secondGeometryValid) {
            return firstGeometryValid ? -1 : 1;
        }
        if (!firstGeometryValid) {
            return 0;
        }
        if (longitudinalHeadFeasibility != null) {
            List<SkyIslandLocalPosition> firstPoints = new ArrayList<>(points);
            firstPoints.set(changedIndex, first);
            List<SkyIslandLocalPosition> secondPoints = new ArrayList<>(points);
            secondPoints.set(changedIndex, second);
            SkyIslandCenterlineLongitudinalHeadFeasibility.Score firstScore =
                    checkedLongitudinalScore(longitudinalHeadFeasibility, firstPoints);
            SkyIslandCenterlineLongitudinalHeadFeasibility.Score secondScore =
                    checkedLongitudinalScore(longitudinalHeadFeasibility, secondPoints);
            boolean firstD2NoWorse =
                    firstGap.maximumGap() <= secondGap.maximumGap() + EPSILON
                            && firstGap.integratedSquaredGap()
                                    <= secondGap.integratedSquaredGap() + EPSILON;
            boolean secondD2NoWorse =
                    secondGap.maximumGap() <= firstGap.maximumGap() + EPSILON
                            && secondGap.integratedSquaredGap()
                                    <= firstGap.integratedSquaredGap() + EPSILON;
            if (firstD2NoWorse != secondD2NoWorse) return firstD2NoWorse ? -1 : 1;
            if (!firstD2NoWorse) return 0;
            int sourceEndpoint = Double.compare(
                    firstScore.maximumSourceEndpointEnvelopeConflictWorldUnits(),
                    secondScore.maximumSourceEndpointEnvelopeConflictWorldUnits());
            if (sourceEndpoint != 0) return sourceEndpoint;
            int confluenceCascadeGrade = Double.compare(
                    firstScore.maximumConfluenceCascadeGradeConflictWorldUnits(),
                    secondScore.maximumConfluenceCascadeGradeConflictWorldUnits());
            if (confluenceCascadeGrade != 0) return confluenceCascadeGrade;
            int maximumGrade = Double.compare(
                    firstScore.maximumGradePropagationConflictWorldUnits(),
                    secondScore.maximumGradePropagationConflictWorldUnits());
            if (maximumGrade != 0) return maximumGrade;
            int maximumLocal = Double.compare(
                    firstScore.maximumLocalEnvelopeConflictWorldUnits(),
                    secondScore.maximumLocalEnvelopeConflictWorldUnits());
            if (maximumLocal != 0) return maximumLocal;
            int integratedGrade = Double.compare(
                    firstScore.integratedSquaredConflictWorldUnits(),
                    secondScore.integratedSquaredConflictWorldUnits());
            if (integratedGrade != 0) return integratedGrade;
        }
        int maximumGap = Double.compare(firstGap.maximumGap(), secondGap.maximumGap());
        if (maximumGap != 0) return maximumGap;
        int integratedGap = Double.compare(
                firstGap.integratedSquaredGap(), secondGap.integratedSquaredGap());
        if (integratedGap != 0) return integratedGap;
        SkyIslandLocalPosition previous = points.get(changedIndex - 1);
        SkyIslandLocalPosition next = points.get(changedIndex + 1);
        int curvature = Double.compare(
                localCurvature(previous, first, next),
                localCurvature(previous, second, next));
        if (curvature != 0) return curvature;
        return Double.compare(
                project(first, searchRoute.points()).distance(),
                project(second, searchRoute.points()).distance());
    }

    private static LocalHeadGapScore localHeadGapScore(
            List<SkyIslandLocalPosition> points,
            int changedIndex,
            SkyIslandLocalPosition candidate,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap) {
        if (headEnvelopeGap == null) {
            return new LocalHeadGapScore(0.0, 0.0);
        }
        List<SkyIslandLocalPosition> candidatePoints = new ArrayList<>(points);
        candidatePoints.set(changedIndex, candidate);
        double[] candidateStations = stations(candidatePoints);
        double maximumGap = 0.0;
        double integratedSquaredGap = 0.0;
        for (int index = Math.max(0, changedIndex - 1);
                index <= Math.min(candidatePoints.size() - 1, changedIndex + 1);
                index++) {
            double station = candidateStations[index];
            double halfWidth = bankfullHalfWidthAtStation.applyAsDouble(station);
            if (!Double.isFinite(halfWidth) || halfWidth < 0.0) {
                throw new IllegalArgumentException(
                        "bankfull half-width must be finite and non-negative");
            }
            Vector tangent;
            try {
                tangent = tangentAt(candidatePoints, index);
            } catch (IllegalStateException degenerateTangent) {
                return new LocalHeadGapScore(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            double gap = checkedGap(
                    headEnvelopeGap, candidatePoints.get(index), station, tangent, halfWidth);
            maximumGap = Math.max(maximumGap, gap);
            integratedSquaredGap += gap * gap;
        }
        return new LocalHeadGapScore(maximumGap, integratedSquaredGap);
    }

    private record LocalHeadGapScore(double maximumGap, double integratedSquaredGap) {}

    private record GlobalModeSearchOutcome(
            Candidate candidate,
            long proposals,
            long admissible,
            long acceptedMoves,
            int stages,
            double maximumBudget) {}

    private static SkyIslandLocalPosition lateralOption(
            SkyIslandLocalPosition point,
            Vector normal,
            int direction,
            double offset) {
        return new SkyIslandLocalPosition(
                point.x() + direction * normal.x() * offset,
                point.z() + direction * normal.z() * offset);
    }

    private static boolean curvatureAdmissible(
            List<SkyIslandLocalPosition> points,
            int changedIndex,
            SkyIslandLocalPosition candidate,
            double minimumBendRadius) {
        if (minimumBendRadius <= EPSILON) {
            return true;
        }
        double maximumAllowedCurvature = 1.0 / minimumBendRadius + EPSILON;
        for (int index = Math.max(1, changedIndex - 1);
                index <= Math.min(points.size() - 2, changedIndex + 1);
                index++) {
            SkyIslandLocalPosition previous =
                    index - 1 == changedIndex ? candidate : points.get(index - 1);
            SkyIslandLocalPosition point =
                    index == changedIndex ? candidate : points.get(index);
            SkyIslandLocalPosition next =
                    index + 1 == changedIndex ? candidate : points.get(index + 1);
            if (localCurvature(previous, point, next) > maximumAllowedCurvature) {
                return false;
            }
        }
        return true;
    }

    private static double localCurvature(
            SkyIslandLocalPosition previous,
            SkyIslandLocalPosition point,
            SkyIslandLocalPosition next) {
        double ax = point.x() - previous.x();
        double az = point.z() - previous.z();
        double bx = next.x() - point.x();
        double bz = next.z() - point.z();
        double al = Math.hypot(ax, az);
        double bl = Math.hypot(bx, bz);
        if (al <= EPSILON || bl <= EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        double cosine = Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (al * bl)));
        return Math.acos(cosine) / (0.5 * (al + bl));
    }

    private static SkyIslandCenterlineLongitudinalHeadFeasibility.Score checkedLongitudinalScore(
            SkyIslandCenterlineLongitudinalHeadFeasibility feasibility,
            List<SkyIslandLocalPosition> points) {
        if (feasibility == null) {
            return SkyIslandCenterlineLongitudinalHeadFeasibility.Score.zero();
        }
        return Objects.requireNonNull(
                feasibility.evaluate(List.copyOf(points)),
                "longitudinal feasibility score");
    }

    private static double checkedGap(
            SkyIslandCenterlineHeadEnvelopeGap evaluator,
            SkyIslandLocalPosition position,
            double station,
            Vector tangent,
            double halfWidth) {
        double gap =
                evaluator.evaluate(
                        position, station, tangent.x(), tangent.z(), halfWidth);
        if (!Double.isFinite(gap) || gap < 0.0) {
            throw new IllegalArgumentException(
                    "head-envelope gap evaluator must return a finite non-negative value");
        }
        return gap;
    }

    private static double[] stations(List<SkyIslandLocalPosition> points) {
        double[] result = new double[points.size()];
        double total = length(points);
        if (total <= EPSILON) {
            return result;
        }
        for (int i = 1; i < points.size(); i++) {
            result[i] = result[i - 1]
                    + Math.hypot(
                            points.get(i).x() - points.get(i - 1).x(),
                            points.get(i).z() - points.get(i - 1).z());
        }
        for (int i = 0; i < result.length; i++) {
            result[i] /= total;
        }
        return result;
    }

    private static Vector tangent(SkyIslandLocalPosition previous, SkyIslandLocalPosition next) {
        double dx = next.x() - previous.x();
        double dz = next.z() - previous.z();
        double length = Math.hypot(dx, dz);
        if (length <= EPSILON) {
            throw new IllegalStateException("centerline tangent must have positive length");
        }
        return new Vector(dx / length, dz / length);
    }

    private static Vector tangentAt(List<SkyIslandLocalPosition> points, int index) {
        return index == 0
                ? tangent(points.get(0), points.get(1))
                : index == points.size() - 1
                        ? tangent(points.get(index - 1), points.get(index))
                        : tangent(points.get(index - 1), points.get(index + 1));
    }

    private static int maximumIndex(double[] values) {
        int result = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[result]) {
                result = i;
            }
        }
        return result;
    }

    private static double maximumCurvature(List<SkyIslandLocalPosition> points) {
        double maximum = 0.0;
        for (int i = 1; i < points.size() - 1; i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            SkyIslandLocalPosition c = points.get(i + 1);
            double ax = b.x() - a.x();
            double az = b.z() - a.z();
            double bx = c.x() - b.x();
            double bz = c.z() - b.z();
            double al = Math.hypot(ax, az);
            double bl = Math.hypot(bx, bz);
            if (al <= EPSILON || bl <= EPSILON) {
                continue;
            }
            double cosine =
                    Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (al * bl)));
            maximum = Math.max(maximum, Math.acos(cosine) / (0.5 * (al + bl)));
        }
        return maximum;
    }

    private static double maximumTurnAngle(List<SkyIslandLocalPosition> points) {
        double maximum = 0.0;
        for (int i = 1; i < points.size() - 1; i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            SkyIslandLocalPosition c = points.get(i + 1);
            double ax = b.x() - a.x();
            double az = b.z() - a.z();
            double bx = c.x() - b.x();
            double bz = c.z() - b.z();
            double al = Math.hypot(ax, az);
            double bl = Math.hypot(bx, bz);
            if (al <= EPSILON || bl <= EPSILON) {
                continue;
            }
            double cosine =
                    Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (al * bl)));
            maximum = Math.max(maximum, Math.acos(cosine));
        }
        return maximum;
    }

    private static double distanceToPolyline(
            SkyIslandLocalPosition point,
            List<SkyIslandLocalPosition> polyline) {
        return project(point, polyline).distance();
    }

    private static Projection project(
            SkyIslandLocalPosition point,
            List<SkyIslandLocalPosition> polyline) {
        Projection best = null;
        for (int i = 1; i < polyline.size(); i++) {
            SkyIslandLocalPosition a = polyline.get(i - 1);
            SkyIslandLocalPosition b = polyline.get(i);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            double t =
                    lengthSquared <= EPSILON
                            ? 0.0
                            : Math.max(
                                    0.0,
                                    Math.min(
                                            1.0,
                                            ((point.x() - a.x()) * dx
                                                            + (point.z() - a.z()) * dz)
                                                    / lengthSquared));
            SkyIslandLocalPosition projected =
                    new SkyIslandLocalPosition(a.x() + t * dx, a.z() + t * dz);
            Projection candidate =
                    new Projection(
                            projected,
                            Math.hypot(point.x() - projected.x(), point.z() - projected.z()));
            if (best == null || candidate.distance() < best.distance() - EPSILON) {
                best = candidate;
            }
        }
        return Objects.requireNonNull(best, "polyline projection");
    }

    private static double length(List<SkyIslandLocalPosition> points) {
        double result = 0.0;
        for (int i = 1; i < points.size(); i++) {
            result += Math.hypot(
                    points.get(i).x() - points.get(i - 1).x(),
                    points.get(i).z() - points.get(i - 1).z());
        }
        return result;
    }

    private static SkyIslandLocalPosition lerp(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b,
            double t) {
        return new SkyIslandLocalPosition(
                a.x() + (b.x() - a.x()) * t,
                a.z() + (b.z() - a.z()) * t);
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    record RefinementOutcome(
            SkyIslandContinuousChannelCenterline centerline,
            SearchDiagnostics diagnostics) {}

    record SearchDiagnostics(
            double initialMaximumHeadEnvelopeGap,
            int initialMaximumHeadEnvelopeGapIndex,
            double initialMaximumHeadEnvelopeGapStation,
            double initialIntegratedSquaredHeadEnvelopeGap,
            double initialLongitudinalHeadFeasibilityGap,
            double initialMaximumLocalEnvelopeConflict,
            double initialMaximumSourceEndpointEnvelopeConflict,
            double initialMaximumLongitudinalGradeConflict,
            double initialMaximumConfluenceCascadeGradeConflict,
            double finalMaximumHeadEnvelopeGap,
            int finalMaximumHeadEnvelopeGapIndex,
            double finalMaximumHeadEnvelopeGapStation,
            double finalIntegratedSquaredHeadEnvelopeGap,
            double finalLongitudinalHeadFeasibilityGap,
            double finalMaximumLocalEnvelopeConflict,
            double finalMaximumSourceEndpointEnvelopeConflict,
            double finalMaximumLongitudinalGradeConflict,
            double finalMaximumConfluenceCascadeGradeConflict,
            SourceEndpointSearchSummary sourceEndpointSearch,
            long lateralCandidateProposals,
            long lateralCandidateAdmissible,
            long lateralCandidateCorridorRejected,
            long lateralCandidateTerrainRejected,
            long lateralCandidateInteriorityRejected,
            long lateralCandidateCurvatureRejected,
            long lateralCandidateGapImproving,
            long selectedLateralMoves,
            long globalGapImprovementsBlockedByCurvature,
            int relaxationSweeps,
            long globalModeSearchProposals,
            long globalModeSearchAdmissible,
            long globalModeSearchAcceptedMoves,
            int globalModeSearchStages,
            double globalModeSearchMaximumBudget) {}

    private record RelaxationStep(
            List<SkyIslandLocalPosition> points,
            long lateralCandidateProposals,
            long lateralCandidateAdmissible,
            long lateralCandidateCorridorRejected,
            long lateralCandidateTerrainRejected,
            long lateralCandidateInteriorityRejected,
            long lateralCandidateCurvatureRejected,
            long lateralCandidateGapImproving,
            long selectedLateralMoves) {}

    private record AdmissionCheck(
            boolean insideSemanticCorridor,
            boolean withinTerrainRise,
            boolean insideIslandInteriority) {
        private boolean allowed() {
            return insideSemanticCorridor && withinTerrainRise && insideIslandInteriority;
        }
    }

    private record Vector(double x, double z) {}

    private record Projection(SkyIslandLocalPosition position, double distance) {}

    private record CoupledBlockOutcome(
            Candidate candidate, SourceEndpointSearchSummary sourceEndpointSearch) {}

    private record SourceEndpointSearchSummary(
            long proposals,
            long geometryAdmissible,
            long scoreImproving,
            long nonRegressing,
            long headGapRegressions,
            long integratedGapRegressions,
            long localEnvelopeRegressions,
            long longitudinalGradeRegressions,
            long confluenceGradeRegressions,
            long accepted) {
        private static SourceEndpointSearchSummary none() {
            return new SourceEndpointSearchSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record Candidate(
            List<SkyIslandLocalPosition> points,
            double pathLength,
            double maximumSearchDeviation,
            double maximumTurnAngle,
            double maximumCurvature,
            double maximumHeadEnvelopeGap,
            int maximumHeadEnvelopeGapIndex,
            double maximumHeadEnvelopeGapStation,
            double integratedSquaredHeadEnvelopeGap,
            double maximumLocalEnvelopeConflict,
            double maximumSourceEndpointEnvelopeConflict,
            double maximumLongitudinalGradeConflict,
            double maximumConfluenceCascadeGradeConflict,
            double longitudinalHeadFeasibilityGap,
            double maximumConfluenceCascadeGradeConflictStation) {
        private boolean refinementResidualsNoWorseThan(Candidate other) {
            return maximumHeadEnvelopeGap <= other.maximumHeadEnvelopeGap + EPSILON
                    && integratedSquaredHeadEnvelopeGap
                            <= other.integratedSquaredHeadEnvelopeGap + EPSILON
                    && maximumLocalEnvelopeConflict
                            <= other.maximumLocalEnvelopeConflict + EPSILON
                    && maximumSourceEndpointEnvelopeConflict
                            <= other.maximumSourceEndpointEnvelopeConflict + EPSILON
                    && maximumLongitudinalGradeConflict
                            <= other.maximumLongitudinalGradeConflict + EPSILON
                    && maximumConfluenceCascadeGradeConflict
                            <= other.maximumConfluenceCascadeGradeConflict + EPSILON;
        }

        private int compareTo(Candidate other, double minimumBendRadius) {
            boolean thisNoWorse = refinementResidualsNoWorseThan(other);
            boolean otherNoWorse = other.refinementResidualsNoWorseThan(this);
            if (thisNoWorse != otherNoWorse) {
                return thisNoWorse ? -1 : 1;
            }
            if (!thisNoWorse) {
                // Do not exchange a local-envelope, grade-propagation, or existing D2 objective
                // regression for an improvement in a different residual.
                return 0;
            }
            int sourceEndpoint = Double.compare(
                    maximumSourceEndpointEnvelopeConflict,
                    other.maximumSourceEndpointEnvelopeConflict);
            if (sourceEndpoint != 0) return sourceEndpoint;
            double curvatureExcess = curvatureExcess(minimumBendRadius);
            double otherCurvatureExcess = other.curvatureExcess(minimumBendRadius);
            int excess = Double.compare(curvatureExcess, otherCurvatureExcess);
            if (excess != 0) {
                return excess;
            }
            int maximumConfluenceCascade = Double.compare(
                    maximumConfluenceCascadeGradeConflict,
                    other.maximumConfluenceCascadeGradeConflict);
            if (maximumConfluenceCascade != 0) return maximumConfluenceCascade;
            int maximumLongitudinal = Double.compare(
                    maximumLongitudinalGradeConflict,
                    other.maximumLongitudinalGradeConflict);
            if (maximumLongitudinal != 0) return maximumLongitudinal;
            int maximumLocal = Double.compare(
                    maximumLocalEnvelopeConflict,
                    other.maximumLocalEnvelopeConflict);
            if (maximumLocal != 0) return maximumLocal;
            int maximumGap = Double.compare(maximumHeadEnvelopeGap, other.maximumHeadEnvelopeGap);
            if (maximumGap != 0) return maximumGap;
            int integratedGap =
                    Double.compare(
                            integratedSquaredHeadEnvelopeGap,
                            other.integratedSquaredHeadEnvelopeGap);
            if (integratedGap != 0) return integratedGap;
            int longitudinal = Double.compare(
                    longitudinalHeadFeasibilityGap,
                    other.longitudinalHeadFeasibilityGap);
            if (longitudinal != 0) return longitudinal;
            int curvature = Double.compare(maximumCurvature, other.maximumCurvature);
            if (curvature != 0) {
                return curvature;
            }
            int deviation = Double.compare(maximumSearchDeviation, other.maximumSearchDeviation);
            if (deviation != 0) {
                return deviation;
            }
            return Double.compare(pathLength, other.pathLength);
        }

        private double curvatureExcess(double minimumBendRadius) {
            return Math.max(0.0, maximumCurvature * minimumBendRadius - 1.0);
        }
    }
}
