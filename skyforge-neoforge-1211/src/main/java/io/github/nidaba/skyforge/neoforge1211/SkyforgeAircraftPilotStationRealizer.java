package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Lowers the semantic pilot anchor into one statically validated Create seat placement. */
public final class SkyforgeAircraftPilotStationRealizer {
    public SkyforgeAircraftPilotStationIR realize(
            SkyforgeAircraftTargetPreflightIR targetPreflight,
            SkyforgeAircraftPropulsionIR propulsion,
            SkyforgeAircraftTailLoweringIR tailLowering,
            String pilotAssetId,
            SkyforgeAircraftPilotStationProfile profile) {
        Objects.requireNonNull(targetPreflight, "targetPreflight");
        Objects.requireNonNull(propulsion, "propulsion");
        Objects.requireNonNull(tailLowering, "tailLowering");
        Objects.requireNonNull(profile, "profile");
        if (!targetPreflight.validation().passed()) throw new IllegalArgumentException("refusing pilot lowering from invalid target preflight");
        if (!propulsion.validation().passed()) throw new IllegalArgumentException("refusing pilot lowering from invalid propulsion artifact");
        if (!tailLowering.validation().passed()) throw new IllegalArgumentException("refusing pilot lowering from invalid tail artifact");
        if (!propulsion.sourceTargetPreflightDigestSha256().equals(targetPreflight.sha256())) {
            throw new IllegalArgumentException("propulsion artifact does not match target-preflight digest");
        }
        if (!tailLowering.sourceTargetPreflightDigestSha256().equals(targetPreflight.sha256())) {
            throw new IllegalArgumentException("tail artifact does not match target-preflight digest");
        }
        if (!profile.targetProfileId().equals(targetPreflight.targetProfileId())) {
            throw new IllegalArgumentException("pilot profile targetProfileId does not match target preflight");
        }

        List<SkyforgeAircraftTargetPreflightIR.Station> stations = targetPreflight.stations().stream()
                .filter(station -> station.type() == profile.stationType())
                .toList();
        if (stations.size() != 1) throw new IllegalArgumentException("expected exactly one PILOT_STATION semantic station");
        List<SkyforgeAircraftTargetPreflightIR.Station> unresolvedPilot = targetPreflight.unresolvedRequiredStations().stream()
                .filter(station -> station.type() == profile.stationType())
                .toList();
        if (unresolvedPilot.size() != 1 || targetPreflight.unresolvedRequiredStations().size() != 1) {
            throw new IllegalArgumentException("pilot lowering requires PILOT_STATION to be the only unresolved required station provider");
        }
        if (unresolvedPilot.getFirst().status() != SkyforgeAircraftTargetProfile.StationStatus.UNRESOLVED) {
            throw new IllegalArgumentException("pilot station must remain target-preflight unresolved before v0.8 lowering");
        }
        if (tailLowering.resolvedAerodynamicPlacements().stream()
                .anyMatch(placement -> !profile.airframeAerodynamicProviderId().equals(placement.providerId()))) {
            throw new IllegalArgumentException("pilot profile aerodynamic provider does not match tail-lowering placements");
        }

        SkyforgeAircraftTargetPreflightIR.Station station = stations.getFirst();
        AircraftBlockspaceIR.LatticePoint anchor = station.lattice();
        SkyforgeAircraftPilotStationProfile.Offset offset = profile.installationOffsetBlocks();
        AircraftBlockspaceIR.LatticePoint seat = new AircraftBlockspaceIR.LatticePoint(
                anchor.x() + offset.dx(), anchor.y() + offset.dy(), anchor.z() + offset.dz());

        Set<AircraftBlockspaceIR.LatticePoint> structuralCoordinates = new HashSet<>();
        targetPreflight.placements().stream()
                .filter(placement -> !profile.airframeAerodynamicProviderId().equals(placement.providerId()))
                .forEach(placement -> structuralCoordinates.add(placement.point()));
        Set<AircraftBlockspaceIR.LatticePoint> aerodynamicCoordinates = new HashSet<>();
        tailLowering.resolvedAerodynamicPlacements().forEach(placement -> aerodynamicCoordinates.add(placement.point()));
        Set<AircraftBlockspaceIR.LatticePoint> propulsionCoordinates = new HashSet<>();
        propulsionCoordinates.add(propulsion.bearing().point());
        propulsionCoordinates.add(propulsion.hub().point());
        propulsion.sails().forEach(placement -> propulsionCoordinates.add(placement.point()));
        Set<AircraftBlockspaceIR.LatticePoint> occupied = new HashSet<>(structuralCoordinates);
        occupied.addAll(aerodynamicCoordinates);
        occupied.addAll(propulsionCoordinates);

        AircraftBlockspaceIR.LatticePoint below = new AircraftBlockspaceIR.LatticePoint(seat.x(), seat.y() - 1, seat.z());
        SkyforgeAircraftPilotStationIR.TopologyChecks checks = new SkyforgeAircraftPilotStationIR.TopologyChecks(
                offset.equals(new SkyforgeAircraftPilotStationProfile.Offset(0, 1, 0)),
                structuralCoordinates.contains(anchor),
                !occupied.contains(seat),
                below.equals(anchor) && structuralCoordinates.contains(below),
                tailLowering.readiness().tailJunctionLoweringPassed(),
                tailLowering.readiness().surfaceStateResolutionComplete());
        boolean staticPassed = checks.passed();

        TreeSet<String> staticBlockers = new TreeSet<>(tailLowering.readiness().staticBlockers());
        List<String> resolvedUpstream = List.of();
        if (staticPassed) {
            staticBlockers.remove("unresolved_required_station_providers");
            resolvedUpstream = List.of("unresolved_required_station_providers");
        } else {
            staticBlockers.add("pilot_station_static_placement_failed");
        }
        TreeSet<String> runtimeBlockers = new TreeSet<>(tailLowering.readiness().runtimeBlockers());
        runtimeBlockers.add("pilot_occupancy_runtime_unverified");
        runtimeBlockers.add("control_binding_runtime_unverified");

        List<SkyforgeAircraftPilotStationIR.RuntimeObligation> obligations = profile.runtimeObligations().stream()
                .map(obligation -> new SkyforgeAircraftPilotStationIR.RuntimeObligation(
                        obligation.id(), obligation.method(), "unverified"))
                .toList();
        boolean probeReady = staticPassed && staticBlockers.isEmpty();

        return new SkyforgeAircraftPilotStationIR(
                SkyforgeAircraftPilotStationIR.SCHEMA_VERSION,
                pilotAssetId,
                SkyforgeAircraftPilotStationIR.COMPILER_VERSION,
                targetPreflight.sha256(),
                propulsion.sha256(),
                tailLowering.sha256(),
                profile.profileId(),
                new SkyforgeAircraftPilotStationIR.SemanticStation(profile.stationType(), anchor, offset),
                new SkyforgeAircraftPilotStationIR.Placement(
                        "pilot_occupancy_station",
                        seat,
                        profile.seatResourceId(),
                        profile.seatBlockState(),
                        "moving_occupant_anchor_candidate",
                        "unverified_on_sable_physics_body"),
                profile.controlBindingContract(),
                checks,
                obligations,
                new SkyforgeAircraftPilotStationIR.Metrics(
                        structuralCoordinates.contains(anchor),
                        1,
                        obligations.size(),
                        0),
                new SkyforgeAircraftPilotStationIR.Readiness(
                        staticPassed,
                        probeReady,
                        false,
                        false,
                        resolvedUpstream,
                        List.copyOf(staticBlockers),
                        List.copyOf(runtimeBlockers)),
                new SkyforgeAircraftPilotStationIR.Validation(
                        staticPassed,
                        "pilot_occupancy_block_placement_and_station_decomposition_only",
                        List.of(
                                "Create seat passenger behavior on an assembled Sable physics body",
                                "pilot input binding",
                                "pitch yaw or roll control authority",
                                "throttle or propeller governor binding",
                                "runtime mass or center of mass",
                                "save/reload persistence",
                                "flight qualification")));
    }
}
