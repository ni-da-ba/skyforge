package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Joins supercritical and subcritical standard-step profiles at a localized hydraulic jump.
 *
 * <p>The jump station is bracketed by momentum/specific-force residuals, then refined within the
 * cross-section interval. Both GVF branches are independently solved from their named controls;
 * the jump is accepted only when its conjugate-depth/force balance and positive energy loss hold.
 */
public final class SkyIslandHydraulicJumpProfileSolver {
    private static final int STATION_SUBDIVISIONS = 8;
    private static final double FORCE_RELATIVE_TOLERANCE = 1.0e-7;

    private SkyIslandHydraulicJumpProfileSolver() {}

    public static SkyIslandGraduallyVariedFlowSolver.Result solve(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            double upstreamDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        return solveWithTailwater(sections, upstreamDepthMeters, null, parameters);
    }

    /**
     * Joins a supercritical source to a specified subcritical tailwater depth.
     *
     * <p>This is used when a downstream junction supplies the water level; unlike the critical
     * control overload, the caller must provide that boundary explicitly.
     */
    public static SkyIslandGraduallyVariedFlowSolver.Result solveToTailwater(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            double upstreamDepthMeters,
            double downstreamDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        if (!Double.isFinite(downstreamDepthMeters) || downstreamDepthMeters <= 0.0) {
            throw new IllegalArgumentException("tailwater depth must be finite and positive");
        }
        return solveWithTailwater(sections, upstreamDepthMeters, downstreamDepthMeters, parameters);
    }

    private static SkyIslandGraduallyVariedFlowSolver.Result solveWithTailwater(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            double upstreamDepthMeters,
            Double downstreamDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(parameters, "parameters");
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> reach = List.copyOf(sections);
        if (reach.size() < 4) {
            throw new IllegalArgumentException(
                    "hydraulic-jump profile requires at least four cross sections");
        }
        double upstreamFroude = SkyIslandGraduallyVariedFlowSolver.froudeNumber(
                reach.getFirst(), upstreamDepthMeters, parameters);
        if (!(upstreamFroude > 1.0)) {
            throw new IllegalArgumentException(
                    "hydraulic-jump profile requires a supercritical source state");
        }

        List<StationTrial> trials = new ArrayList<>(reach.size());
        trials.add(null);
        for (int station = 1; station < reach.size() - 1; station++) {
            try {
                trials.add(evaluateAtStation(reach, station, upstreamDepthMeters, downstreamDepthMeters, parameters));
            } catch (IllegalArgumentException | IllegalStateException noAdmissibleBranch) {
                trials.add(null);
            }
        }
        trials.add(null);

        for (int station = 1; station < reach.size() - 2; station++) {
            StationTrial lower = trials.get(station);
            StationTrial upper = trials.get(station + 1);
            if (lower == null || upper == null
                    || lower.forceResidual() * upper.forceResidual() > 0.0) {
                continue;
            }
            StationTrial upperOnInterval = new StationTrial(
                    station, 1.0, upper.section(), upper.upstreamDepth(), upper.downstreamDepth(),
                    upper.forceResidual(), upper.supercriticalProfile(), upper.subcriticalProfile());
            StationTrial jump = refineBracket(
                    reach, station, lower, upperOnInterval, upstreamDepthMeters, downstreamDepthMeters, parameters);
            if (jump != null) {
                return joinProfiles(reach, station, jump, upstreamDepthMeters, parameters);
            }
        }
        // The last section is the downstream critical control, so a supercritical branch cannot
        // be marched all the way to it. Search the open interior of the final interval explicitly
        // instead of silently excluding the reach immediately upstream of that control.
        int terminalInterval = reach.size() - 2;
        StationTrial previous = trials.get(terminalInterval);
        for (int subdivision = 1; subdivision <= STATION_SUBDIVISIONS; subdivision++) {
            double fraction = (double) subdivision / (STATION_SUBDIVISIONS + 1);
            StationTrial sample;
            try {
                sample = evaluateInsideInterval(
                        reach, terminalInterval, fraction, upstreamDepthMeters, downstreamDepthMeters, parameters);
            } catch (IllegalArgumentException | IllegalStateException noAdmissibleBranch) {
                previous = null;
                continue;
            }
            if (previous != null
                    && previous.forceResidual() * sample.forceResidual() <= 0.0) {
                StationTrial jump = refineBracket(
                        reach, terminalInterval, previous, sample, upstreamDepthMeters, downstreamDepthMeters, parameters);
                if (jump != null) {
                    return joinProfiles(reach, terminalInterval, jump, upstreamDepthMeters, parameters);
                }
            }
            previous = sample;
        }
        throw new IllegalStateException(
                "no admissible momentum-matched hydraulic jump connects source normal depth "
                        + "to the downstream critical control");
    }

    private static StationTrial evaluateAtStation(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            int station,
            double upstreamDepthMeters,
            Double downstreamDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> prefix =
                sections.subList(0, station + 1);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> suffix =
                sections.subList(station, sections.size());
        SkyIslandGraduallyVariedFlowSolver.Result supercritical =
                SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstream(
                        prefix, upstreamDepthMeters, parameters);
        SkyIslandGraduallyVariedFlowSolver.Result subcritical =
                solveSubcriticalProfile(suffix, downstreamDepthMeters, parameters);
        double upstreamDepth = supercritical.points().getLast().depthMeters();
        double downstreamDepth = subcritical.points().getFirst().depthMeters();
        return trial(
                sections.get(station),
                station,
                0.0,
                upstreamDepth,
                downstreamDepth,
                supercritical,
                subcritical,
                parameters);
    }

    private static StationTrial evaluateInsideInterval(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            int interval,
            double fraction,
            double upstreamDepthMeters,
            Double downstreamDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        SkyIslandGraduallyVariedFlowSolver.CrossSection jumpSection =
                interpolate(sections.get(interval), sections.get(interval + 1), fraction);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> prefix =
                new ArrayList<>(sections.subList(0, interval + 1));
        prefix.add(jumpSection);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> suffix =
                new ArrayList<>();
        suffix.add(jumpSection);
        suffix.addAll(sections.subList(interval + 1, sections.size()));
        SkyIslandGraduallyVariedFlowSolver.Result supercritical =
                SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstream(
                        prefix, upstreamDepthMeters, parameters);
        SkyIslandGraduallyVariedFlowSolver.Result subcritical =
                solveSubcriticalProfile(suffix, downstreamDepthMeters, parameters);
        return trial(
                jumpSection,
                interval,
                fraction,
                supercritical.points().getLast().depthMeters(),
                subcritical.points().getFirst().depthMeters(),
                supercritical,
                subcritical,
                parameters);
    }

    private static SkyIslandGraduallyVariedFlowSolver.Result solveSubcriticalProfile(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            Double downstreamDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        return downstreamDepthMeters == null
                ? SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstreamFromCriticalControl(
                        sections, parameters)
                : SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstream(
                        sections, downstreamDepthMeters, parameters);
    }

    private static StationTrial trial(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            int interval,
            double fraction,
            double upstreamDepth,
            double downstreamDepth,
            SkyIslandGraduallyVariedFlowSolver.Result supercritical,
            SkyIslandGraduallyVariedFlowSolver.Result subcritical,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        double upstreamForce = SkyIslandHydraulicJumpSolver.specificForce(
                section, upstreamDepth, parameters.gravityMetersPerSecondSquared());
        double downstreamForce = SkyIslandHydraulicJumpSolver.specificForce(
                section, downstreamDepth, parameters.gravityMetersPerSecondSquared());
        double residual = upstreamForce - downstreamForce;
        return new StationTrial(
                interval, fraction, section, upstreamDepth, downstreamDepth, residual,
                supercritical, subcritical);
    }

    private static StationTrial refineBracket(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            int interval,
            StationTrial lower,
            StationTrial upper,
            double upstreamDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        StationTrial best = Math.abs(lower.forceResidual()) < Math.abs(upper.forceResidual())
                ? lower : upper;
        for (int subdivision = 1; subdivision <= STATION_SUBDIVISIONS; subdivision++) {
            double fraction = 0.5 * (lower.fraction() + upper.fraction());
            StationTrial sample;
            try {
                sample = evaluateInsideInterval(
                        sections, interval, fraction, upstreamDepthMeters, downstreamDepthMeters, parameters);
            } catch (IllegalArgumentException | IllegalStateException noAdmissibleBranch) {
                continue;
            }
            if (Math.abs(sample.forceResidual()) < Math.abs(best.forceResidual())) {
                best = sample;
            }
            if (lower.forceResidual() * sample.forceResidual() <= 0.0) {
                upper = sample;
            } else {
                lower = sample;
            }
        }

        double left = lower.fraction();
        double right = upper.fraction();
        if (!(right > left)) {
            return admissible(best, parameters) ? best : null;
        }
        int refinementLimit = Math.min(parameters.maximumIterations(), 48);
        for (int iteration = 0; iteration < refinementLimit; iteration++) {
            double middle = 0.5 * (left + right);
            StationTrial candidate;
            try {
                candidate = evaluateInsideInterval(
                        sections, interval, middle, upstreamDepthMeters, downstreamDepthMeters, parameters);
            } catch (IllegalArgumentException | IllegalStateException noAdmissibleBranch) {
                return admissible(best, parameters) ? best : null;
            }
            if (Math.abs(candidate.forceResidual()) < Math.abs(best.forceResidual())) {
                best = candidate;
            }
            if (admissible(candidate, parameters)) {
                return candidate;
            }
            if (lower.forceResidual() * candidate.forceResidual() <= 0.0) {
                right = middle;
                upper = candidate;
            } else {
                left = middle;
                lower = candidate;
            }
            if (right - left <= parameters.relativeTolerance()) {
                break;
            }
        }
        return admissible(best, parameters) ? best : null;
    }

    private static boolean admissible(
            StationTrial trial,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        double forceScale = Math.max(
                1.0,
                Math.max(
                        Math.abs(SkyIslandHydraulicJumpSolver.specificForce(
                                trial.section(), trial.upstreamDepth(),
                                parameters.gravityMetersPerSecondSquared())),
                        Math.abs(SkyIslandHydraulicJumpSolver.specificForce(
                                trial.section(), trial.downstreamDepth(),
                                parameters.gravityMetersPerSecondSquared()))));
        if (Math.abs(trial.forceResidual()) > FORCE_RELATIVE_TOLERANCE * forceScale) {
            return false;
        }
        try {
            SkyIslandHydraulicJumpSolver.Result conjugate =
                    SkyIslandHydraulicJumpSolver.solveConjugateDepth(
                            trial.section(), trial.upstreamDepth(),
                            parameters.gravityMetersPerSecondSquared(),
                            parameters.relativeTolerance(),
                            parameters.maximumIterations());
            double depthTolerance = Math.max(
                    1.0e-6,
                    100.0 * parameters.relativeTolerance()
                            * Math.max(1.0, conjugate.downstreamDepthMeters()));
            return conjugate.energyLossMeters() > 0.0
                    && Math.abs(conjugate.downstreamDepthMeters() - trial.downstreamDepth())
                            <= depthTolerance;
        } catch (IllegalArgumentException | IllegalStateException invalidJump) {
            return false;
        }
    }

    private static SkyIslandGraduallyVariedFlowSolver.Result joinProfiles(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            int interval,
            StationTrial jump,
            double upstreamDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        StationTrial resolved = jump;
        if (jump.fraction() == 0.0) {
            return joinAtStation(sections, jump.interval(), upstreamDepthMeters, parameters);
        }
        if (jump.fraction() == 1.0) {
            return joinAtStation(sections, jump.interval() + 1, upstreamDepthMeters, parameters);
        }
        List<SkyIslandGraduallyVariedFlowSolver.ProfilePoint> points = new ArrayList<>();
        points.addAll(resolved.supercriticalProfile().points()
                .subList(0, resolved.supercriticalProfile().points().size() - 1));
        points.addAll(resolved.subcriticalProfile().points().subList(
                1, resolved.subcriticalProfile().points().size()));
        if (points.size() != sections.size()) {
            throw new IllegalStateException(
                    "joined hydraulic-jump profile must preserve original cross sections");
        }
        return new SkyIslandGraduallyVariedFlowSolver.Result(
                points,
                Math.max(
                        resolved.supercriticalProfile().maximumEnergyResidualMeters(),
                        resolved.subcriticalProfile().maximumEnergyResidualMeters()));
    }

    private static SkyIslandGraduallyVariedFlowSolver.Result joinAtStation(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            int station,
            double upstreamDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        if (station <= 0 || station >= sections.size() - 1) {
            throw new IllegalStateException("hydraulic jump must lie inside the modeled reach");
        }
        SkyIslandGraduallyVariedFlowSolver.Result supercritical =
                SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstream(
                        sections.subList(0, station + 1), upstreamDepthMeters, parameters);
        SkyIslandGraduallyVariedFlowSolver.Result subcritical =
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstreamFromCriticalControl(
                        sections.subList(station, sections.size()), parameters);
        List<SkyIslandGraduallyVariedFlowSolver.ProfilePoint> points = new ArrayList<>();
        points.addAll(supercritical.points().subList(0, supercritical.points().size() - 1));
        points.addAll(subcritical.points());
        return new SkyIslandGraduallyVariedFlowSolver.Result(
                points,
                Math.max(supercritical.maximumEnergyResidualMeters(),
                        subcritical.maximumEnergyResidualMeters()));
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection interpolate(
            SkyIslandGraduallyVariedFlowSolver.CrossSection first,
            SkyIslandGraduallyVariedFlowSolver.CrossSection second,
            double fraction) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                lerp(first.chainageMeters(), second.chainageMeters(), fraction),
                lerp(first.bedElevationMeters(), second.bedElevationMeters(), fraction),
                lerp(first.dischargeCubicMetersPerSecond(),
                        second.dischargeCubicMetersPerSecond(), fraction),
                lerp(first.bottomWidthMeters(), second.bottomWidthMeters(), fraction),
                lerp(first.sideSlopeHorizontalToVertical(),
                        second.sideSlopeHorizontalToVertical(), fraction));
    }

    private static double lerp(double first, double second, double fraction) {
        return first + (second - first) * fraction;
    }

    private record StationTrial(
            int interval,
            double fraction,
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            double upstreamDepth,
            double downstreamDepth,
            double forceResidual,
            SkyIslandGraduallyVariedFlowSolver.Result supercriticalProfile,
            SkyIslandGraduallyVariedFlowSolver.Result subcriticalProfile) {}
}
