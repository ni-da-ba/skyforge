package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Resolves regular Create sail facings from semantic aircraft surface roles. */
public final class SkyforgeAircraftSurfaceStateResolver {
    public SkyforgeAircraftSurfaceStateIR resolve(
            SkyforgeAircraftTargetPreflightIR targetPreflight,
            SkyforgeAircraftPropulsionIR propulsion,
            String surfaceStateAssetId,
            SkyforgeAircraftSurfaceStateProfile profile) {
        Objects.requireNonNull(targetPreflight, "targetPreflight");
        Objects.requireNonNull(propulsion, "propulsion");
        Objects.requireNonNull(profile, "profile");
        if (!targetPreflight.validation().passed()) {
            throw new IllegalArgumentException("refusing surface-state resolution from invalid target preflight");
        }
        if (!propulsion.validation().passed() || !propulsion.readiness().propulsionStaticTopologyPassed()) {
            throw new IllegalArgumentException("refusing surface-state resolution from invalid propulsion topology");
        }
        if (!propulsion.sourceTargetPreflightDigestSha256().equals(targetPreflight.sha256())) {
            throw new IllegalArgumentException("propulsion artifact does not match target-preflight digest");
        }
        if (!profile.targetProfileId().equals(targetPreflight.targetProfileId())) {
            throw new IllegalArgumentException("surface-state profile targetProfileId does not match target preflight");
        }

        List<SkyforgeAircraftTargetPreflightIR.Placement> providerPlacements = targetPreflight.placements().stream()
                .filter(placement -> profile.providerId().equals(placement.providerId()))
                .toList();
        if (providerPlacements.isEmpty()) {
            throw new IllegalArgumentException(
                    "target preflight contains no placements for surface provider: " + profile.providerId());
        }

        List<SkyforgeAircraftSurfaceStateIR.ResolvedPlacement> resolved = new ArrayList<>();
        List<SkyforgeAircraftSurfaceStateIR.Conflict> conflicts = new ArrayList<>();
        for (SkyforgeAircraftTargetPreflightIR.Placement placement : providerPlacements) {
            TreeSet<String> demandedStates = new TreeSet<>();
            for (AircraftBlockspaceIR.Role role : placement.roles()) {
                String state = profile.roleStates().get(role);
                if (state != null) {
                    demandedStates.add(state);
                }
            }
            List<String> demanded = List.copyOf(demandedStates);
            if (demanded.size() == 1) {
                resolved.add(new SkyforgeAircraftSurfaceStateIR.ResolvedPlacement(
                        placement.point(),
                        placement.roles(),
                        placement.resourceId(),
                        placement.providerId(),
                        demanded,
                        Map.of(profile.stateProperty(), demanded.getFirst()),
                        "resolved"));
            } else if (demanded.size() > 1) {
                conflicts.add(new SkyforgeAircraftSurfaceStateIR.Conflict(
                        placement.point(),
                        placement.roles(),
                        placement.resourceId(),
                        placement.providerId(),
                        demanded,
                        "conflict",
                        "one_lattice_coordinate_cannot_realize_multiple_surface_orientations"));
            } else {
                conflicts.add(new SkyforgeAircraftSurfaceStateIR.Conflict(
                        placement.point(),
                        placement.roles(),
                        placement.resourceId(),
                        placement.providerId(),
                        demanded,
                        "conflict",
                        "aerodynamic_provider_has_no_declared_surface_role_state"));
            }
        }

        int inputUnresolved = targetPreflight.metrics().unresolvedStateOrResourceCount();
        int remainingUnresolved = Math.max(inputUnresolved - resolved.size(), 0);
        boolean complete = conflicts.isEmpty() && remainingUnresolved == 0;

        TreeSet<String> staticBlockers = new TreeSet<>();
        for (String blocker : propulsion.readiness().blockers()) {
            if ("propulsion_runtime_obligations_unverified".equals(blocker)) {
                continue;
            }
            if ("unresolved_blockstate_or_resource_rules".equals(blocker)) {
                if (!conflicts.isEmpty() || remainingUnresolved > 0) {
                    staticBlockers.add("unresolved_surface_state_conflicts");
                }
            } else {
                staticBlockers.add(blocker);
            }
        }
        if (!conflicts.isEmpty()) {
            staticBlockers.add("unresolved_surface_state_conflicts");
        }

        List<String> runtimeBlockers = List.of(
                "propulsion_runtime_obligations_unverified",
                "aircraft_runtime_obligations_unverified");
        boolean probeSchematicReady = propulsion.readiness().propulsionStaticTopologyPassed()
                && complete
                && staticBlockers.isEmpty();

        return new SkyforgeAircraftSurfaceStateIR(
                SkyforgeAircraftSurfaceStateIR.SCHEMA_VERSION,
                surfaceStateAssetId,
                SkyforgeAircraftSurfaceStateIR.COMPILER_VERSION,
                targetPreflight.sha256(),
                propulsion.sha256(),
                profile.profileId(),
                profile.providerId(),
                profile.stateProperty(),
                resolved,
                conflicts,
                new SkyforgeAircraftSurfaceStateIR.Metrics(
                        providerPlacements.size(),
                        resolved.size(),
                        conflicts.size(),
                        inputUnresolved,
                        remainingUnresolved),
                new SkyforgeAircraftSurfaceStateIR.Readiness(
                        complete,
                        probeSchematicReady,
                        false,
                        false,
                        List.copyOf(staticBlockers),
                        runtimeBlockers),
                new SkyforgeAircraftSurfaceStateIR.Validation(
                        true,
                        "source_backed_regular_create_sail_state_lowering_and_conflict_detection",
                        List.of(
                                "live Sable force sign for the emitted facing state",
                                "contraption capture or persistence",
                                "runtime mass or center of mass",
                                "control binding",
                                "stable flight")));
    }
}
