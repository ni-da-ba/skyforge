package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Couples source-controlled incoming reaches to a physically controlled subcritical confluence outlet.
 *
 * <p>Each incoming reach is solved from its Manning normal-depth source against the shared junction
 * water-surface elevation. The resulting branch states are then closed against the outlet section
 * by discharge-weighted energy and an explicit junction-loss coefficient. This is a local hydraulic
 * component only; geomorphic/D2 qualification and larger-network terminal closure remain separate.
 */
public final class SkyIslandHydraulicEnergyConfluenceComponentSolver {
    private SkyIslandHydraulicEnergyConfluenceComponentSolver() {}

    public record IncomingReach(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections) {
        public IncomingReach {
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
            if (sections.size() < 3) {
                throw new IllegalArgumentException(
                        "source-controlled incoming reach requires at least three sections");
            }
        }
    }

    public record Result(
            List<SkyIslandGraduallyVariedFlowSolver.Result> incomingProfiles,
            SkyIslandHydraulicConfluenceEnergySolver.Result confluence,
            double outletDepthMeters,
            double junctionDepthResidualMeters,
            double maximumReachEnergyResidualMeters) {
        public Result {
            incomingProfiles = List.copyOf(incomingProfiles);
            incomingProfiles.forEach(value -> Objects.requireNonNull(value, "incoming profile"));
            confluence = Objects.requireNonNull(confluence, "confluence");
            if (incomingProfiles.size() < 2
                    || !Double.isFinite(outletDepthMeters)
                    || outletDepthMeters <= 0.0
                    || !Double.isFinite(junctionDepthResidualMeters)
                    || junctionDepthResidualMeters < 0.0
                    || !Double.isFinite(maximumReachEnergyResidualMeters)
                    || maximumReachEnergyResidualMeters < 0.0) {
                throw new IllegalArgumentException("energy-confluence component result is invalid");
            }
        }
    }

    /**
     * Solves a combining junction when downstream control has supplied the outlet depth.
     *
     * <p>Incoming tailwater depths are derived from the common node water-surface elevation, not
     * copied as depths across channels with different bed elevations. Every source normal-depth
     * boundary, reach energy equation, discharge continuity check, subcritical outlet state, and
     * confluence energy balance must close; otherwise the component is rejected.
     */
    public static Result solve(
            List<IncomingReach> incomingReaches,
            SkyIslandGraduallyVariedFlowSolver.CrossSection outletAtJunction,
            double outletDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters,
            double junctionLossCoefficient) {
        Objects.requireNonNull(incomingReaches, "incomingReaches");
        Objects.requireNonNull(outletAtJunction, "outletAtJunction");
        Objects.requireNonNull(parameters, "parameters");
        if (incomingReaches.size() < 2) {
            throw new IllegalArgumentException("combining junction requires at least two incoming reaches");
        }
        if (!Double.isFinite(outletDepthMeters) || outletDepthMeters <= 0.0) {
            throw new IllegalArgumentException("outlet depth must be finite and positive");
        }

        double nodeWaterSurface = outletAtJunction.bedElevationMeters() + outletDepthMeters;
        List<SkyIslandHydraulicConfluenceEnergySolver.IncomingState> incomingStates =
                new ArrayList<>(incomingReaches.size());
        List<SkyIslandGraduallyVariedFlowSolver.Result> profiles =
                new ArrayList<>(incomingReaches.size());
        double maximumReachResidual = 0.0;
        for (IncomingReach incoming : incomingReaches) {
            Objects.requireNonNull(incoming, "incoming reach");
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = incoming.sections();
            SkyIslandGraduallyVariedFlowSolver.CrossSection terminalSection = sections.getLast();
            double tailwaterDepth = nodeWaterSurface - terminalSection.bedElevationMeters();
            if (!(tailwaterDepth > 0.0) || !Double.isFinite(tailwaterDepth)) {
                throw new IllegalStateException(
                        "junction water surface must lie above every incoming channel bed");
            }
            SkyIslandGraduallyVariedFlowSolver.Result profile =
                    SkyIslandOpenChannelOrdinarySpanSolver.solveSourceNormalDepthToTailwater(
                            sections, tailwaterDepth, parameters);
            SkyIslandGraduallyVariedFlowSolver.ProfilePoint terminalPoint =
                    profile.points().getLast();
            profiles.add(profile);
            maximumReachResidual = Math.max(
                    maximumReachResidual, profile.maximumEnergyResidualMeters());
            incomingStates.add(new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                    terminalPoint.section(), terminalPoint.depthMeters()));
        }

        SkyIslandHydraulicConfluenceEnergySolver.Result confluence =
                SkyIslandHydraulicConfluenceEnergySolver.solve(
                        incomingStates,
                        outletAtJunction,
                        parameters,
                        junctionLossCoefficient);
        double depthResidual = Math.abs(confluence.downstreamDepthMeters() - outletDepthMeters);
        double depthTolerance = Math.max(
                1.0e-6,
                100.0 * parameters.relativeTolerance()
                        * Math.max(1.0, Math.max(
                                confluence.downstreamDepthMeters(), outletDepthMeters)));
        if (depthResidual > depthTolerance) {
            throw new IllegalStateException(
                    "downstream-controlled junction depth conflicts with the energy closure"
                            + "; solvedDepthMeters=" + confluence.downstreamDepthMeters()
                            + "; controlledDepthMeters=" + outletDepthMeters
                            + "; toleranceMeters=" + depthTolerance);
        }
        return new Result(
                profiles,
                confluence,
                outletDepthMeters,
                depthResidual,
                maximumReachResidual);
    }
}
