package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Resolves the bounded horizontal/vertical regular-sail junction by translating the complete fin. */
public final class SkyforgeAircraftTailJunctionLowerer {
    public SkyforgeAircraftTailLoweringIR lower(
            SkyforgeAircraftTargetPreflightIR targetPreflight,
            SkyforgeAircraftSurfaceStateIR surfaceState,
            String tailAssetId,
            SkyforgeAircraftTailLoweringProfile profile) {
        Objects.requireNonNull(targetPreflight, "targetPreflight");
        Objects.requireNonNull(surfaceState, "surfaceState");
        Objects.requireNonNull(profile, "profile");
        if (!targetPreflight.validation().passed()) {
            throw new IllegalArgumentException("refusing tail lowering from invalid target preflight");
        }
        if (!surfaceState.validation().passed()) {
            throw new IllegalArgumentException("refusing tail lowering from invalid surface-state artifact");
        }
        if (!surfaceState.sourceTargetPreflightDigestSha256().equals(targetPreflight.sha256())) {
            throw new IllegalArgumentException("surface-state artifact does not match target-preflight digest");
        }
        if (!profile.targetProfileId().equals(targetPreflight.targetProfileId())) {
            throw new IllegalArgumentException("tail-lowering profile targetProfileId does not match target preflight");
        }
        if (!profile.providerId().equals(surfaceState.providerId())) {
            throw new IllegalArgumentException("tail-lowering provider does not match surface-state provider");
        }
        if (!profile.stateProperty().equals(surfaceState.stateProperty())) {
            throw new IllegalArgumentException("tail-lowering state property does not match surface-state property");
        }

        List<SkyforgeAircraftTargetPreflightIR.Placement> aerodynamic = targetPreflight.placements().stream()
                .filter(placement -> profile.providerId().equals(placement.providerId()))
                .toList();
        List<SkyforgeAircraftTargetPreflightIR.Placement> verticalSources = aerodynamic.stream()
                .filter(placement -> placement.roles().contains(profile.translatedRole()))
                .toList();
        if (verticalSources.isEmpty()) {
            throw new IllegalArgumentException("no vertical-tail placements found for bounded tail lowering");
        }
        if (verticalSources.stream().anyMatch(placement -> !profile.resourceId().equals(placement.resourceId()))) {
            throw new IllegalArgumentException("vertical-tail provider resource does not match lowering profile");
        }

        TreeSet<AircraftBlockspaceIR.LatticePoint> conflictCoordinates = new TreeSet<>();
        surfaceState.conflicts().forEach(conflict -> conflictCoordinates.add(conflict.point()));
        TreeSet<AircraftBlockspaceIR.LatticePoint> expectedConflicts = new TreeSet<>();
        verticalSources.stream()
                .filter(placement -> placement.roles().contains(profile.junctionRetainedRole()))
                .forEach(placement -> expectedConflicts.add(placement.point()));
        if (!conflictCoordinates.equals(expectedConflicts)) {
            throw new IllegalArgumentException(
                    "surface-state conflicts do not exactly match horizontal/vertical tail intersections");
        }

        Set<AircraftBlockspaceIR.LatticePoint> verticalOnlyCoordinates = new HashSet<>();
        verticalSources.stream()
                .filter(placement -> !placement.roles().contains(profile.junctionRetainedRole()))
                .forEach(placement -> verticalOnlyCoordinates.add(placement.point()));
        Set<AircraftBlockspaceIR.LatticePoint> immutableCoordinates = new HashSet<>();
        targetPreflight.placements().forEach(placement -> immutableCoordinates.add(placement.point()));
        immutableCoordinates.removeAll(verticalOnlyCoordinates);

        SkyforgeAircraftTailLoweringProfile.Translation shift = profile.translationBlocks();
        TreeSet<AircraftBlockspaceIR.LatticePoint> shiftedCoordinates = new TreeSet<>();
        verticalSources.forEach(placement -> shiftedCoordinates.add(shift(placement.point(), shift)));
        TreeSet<AircraftBlockspaceIR.LatticePoint> collisionCandidates = new TreeSet<>(immutableCoordinates);
        collisionCandidates.removeAll(conflictCoordinates);
        TreeSet<AircraftBlockspaceIR.LatticePoint> collisions = new TreeSet<>(shiftedCoordinates);
        collisions.retainAll(collisionCandidates);

        List<AircraftBlockspaceIR.LatticePoint> originalVertical = verticalSources.stream()
                .map(SkyforgeAircraftTargetPreflightIR.Placement::point)
                .sorted()
                .toList();
        List<AircraftBlockspaceIR.LatticePoint> shiftedVertical = List.copyOf(shiftedCoordinates);
        int xMomentBefore = originalVertical.stream().mapToInt(AircraftBlockspaceIR.LatticePoint::x).sum();
        int xMomentAfter = shiftedVertical.stream().mapToInt(AircraftBlockspaceIR.LatticePoint::x).sum();
        int yMomentBefore = originalVertical.stream().mapToInt(AircraftBlockspaceIR.LatticePoint::y).sum();
        int yMomentAfter = shiftedVertical.stream().mapToInt(AircraftBlockspaceIR.LatticePoint::y).sum();
        List<AircraftBlockspaceIR.LatticePoint> shapeBefore = relativeShape(originalVertical);
        List<AircraftBlockspaceIR.LatticePoint> shapeAfter = relativeShape(shiftedVertical);

        TreeSet<AircraftBlockspaceIR.LatticePoint> horizontalCoordinates = new TreeSet<>();
        aerodynamic.stream()
                .filter(placement -> placement.roles().contains(profile.junctionRetainedRole()))
                .forEach(placement -> horizontalCoordinates.add(placement.point()));
        int rootY = shiftedVertical.stream().mapToInt(AircraftBlockspaceIR.LatticePoint::y).min().orElseThrow();
        List<AircraftBlockspaceIR.LatticePoint> rootFaceContacts = shiftedVertical.stream()
                .filter(point -> point.y() == rootY)
                .filter(point -> horizontalCoordinates.contains(
                        new AircraftBlockspaceIR.LatticePoint(point.x(), point.y() - 1, point.z())))
                .sorted()
                .toList();

        Map<AircraftBlockspaceIR.LatticePoint, SkyforgeAircraftSurfaceStateIR.ResolvedPlacement> resolvedByPoint =
                new HashMap<>();
        for (SkyforgeAircraftSurfaceStateIR.ResolvedPlacement placement : surfaceState.resolvedPlacements()) {
            resolvedByPoint.put(placement.point(), placement);
        }

        List<SkyforgeAircraftTailLoweringIR.ResolvedAerodynamicPlacement> resolved = new ArrayList<>();
        for (SkyforgeAircraftTargetPreflightIR.Placement placement : aerodynamic) {
            boolean vertical = placement.roles().contains(profile.translatedRole());
            boolean horizontal = placement.roles().contains(profile.junctionRetainedRole());
            if (vertical && !horizontal) {
                continue;
            }

            ArrayList<AircraftBlockspaceIR.Role> roles = new ArrayList<>(placement.roles());
            String state;
            if (horizontal) {
                state = profile.junctionRetainedState();
                roles.remove(profile.translatedRole());
            } else {
                SkyforgeAircraftSurfaceStateIR.ResolvedPlacement source = resolvedByPoint.get(placement.point());
                if (source == null) {
                    throw new IllegalArgumentException(
                            "missing resolved surface state at " + placement.point());
                }
                state = source.blockState().get(profile.stateProperty());
                if (state == null) {
                    throw new IllegalArgumentException(
                            "resolved surface state lacks property " + profile.stateProperty());
                }
            }
            if (roles.isEmpty()) {
                throw new IllegalArgumentException("tail lowering would erase every semantic role at " + placement.point());
            }
            resolved.add(new SkyforgeAircraftTailLoweringIR.ResolvedAerodynamicPlacement(
                    placement.point(),
                    roles,
                    placement.resourceId(),
                    profile.providerId(),
                    Map.of(profile.stateProperty(), state)));
        }
        for (AircraftBlockspaceIR.LatticePoint point : shiftedVertical) {
            resolved.add(new SkyforgeAircraftTailLoweringIR.ResolvedAerodynamicPlacement(
                    point,
                    List.of(profile.translatedRole()),
                    profile.resourceId(),
                    profile.providerId(),
                    Map.of(profile.stateProperty(), profile.translatedState())));
        }
        resolved.sort(Comparator.comparing(SkyforgeAircraftTailLoweringIR.ResolvedAerodynamicPlacement::point));

        Set<AircraftBlockspaceIR.LatticePoint> outputCoordinates = new LinkedHashSet<>();
        boolean unique = resolved.stream().allMatch(placement -> outputCoordinates.add(placement.point()));
        SkyforgeAircraftTailLoweringIR.TopologyChecks checks = new SkyforgeAircraftTailLoweringIR.TopologyChecks(
                unique,
                collisions.isEmpty(),
                originalVertical.size() == shiftedVertical.size(),
                xMomentBefore == xMomentAfter,
                shapeBefore.equals(shapeAfter),
                connected(new HashSet<>(shiftedVertical)),
                !rootFaceContacts.isEmpty(),
                !conflictCoordinates.isEmpty());
        boolean passed = checks.passed();

        TreeSet<String> staticBlockers = new TreeSet<>();
        for (String blocker : surfaceState.readiness().staticBlockers()) {
            if (!"unresolved_surface_state_conflicts".equals(blocker)) {
                staticBlockers.add(blocker);
            }
        }
        if (!passed) {
            staticBlockers.add("tail_junction_lowering_failed");
        }

        List<SkyforgeAircraftTailLoweringIR.Translation> translations = new ArrayList<>();
        for (int index = 0; index < originalVertical.size(); index++) {
            translations.add(new SkyforgeAircraftTailLoweringIR.Translation(
                    originalVertical.get(index), shiftedVertical.get(index)));
        }

        return new SkyforgeAircraftTailLoweringIR(
                SkyforgeAircraftTailLoweringIR.SCHEMA_VERSION,
                tailAssetId,
                SkyforgeAircraftTailLoweringIR.COMPILER_VERSION,
                targetPreflight.sha256(),
                surfaceState.sha256(),
                profile.profileId(),
                shift,
                resolved,
                translations,
                rootFaceContacts,
                List.copyOf(collisions),
                checks,
                new SkyforgeAircraftTailLoweringIR.Metrics(
                        aerodynamic.size(),
                        resolved.size(),
                        shiftedVertical.size(),
                        horizontalCoordinates.size(),
                        conflictCoordinates.size(),
                        xMomentBefore,
                        xMomentAfter,
                        yMomentBefore,
                        yMomentAfter,
                        shift.dx(),
                        shift.dy(),
                        shift.dz()),
                new SkyforgeAircraftTailLoweringIR.Readiness(
                        passed,
                        passed,
                        passed && staticBlockers.isEmpty(),
                        false,
                        false,
                        List.copyOf(staticBlockers),
                        surfaceState.readiness().runtimeBlockers()),
                new SkyforgeAircraftTailLoweringIR.Validation(
                        passed,
                        "discrete_tail_junction_translation_with_cell_count_shape_and_longitudinal_moment_preservation",
                        List.of(
                                "Create/Sable attachment semantics at the fin root",
                                "runtime side-force sign or magnitude",
                                "dynamic stability",
                                "control authority",
                                "flight qualification")));
    }

    private static AircraftBlockspaceIR.LatticePoint shift(
            AircraftBlockspaceIR.LatticePoint point,
            SkyforgeAircraftTailLoweringProfile.Translation translation) {
        return new AircraftBlockspaceIR.LatticePoint(
                point.x() + translation.dx(),
                point.y() + translation.dy(),
                point.z() + translation.dz());
    }

    private static List<AircraftBlockspaceIR.LatticePoint> relativeShape(
            List<AircraftBlockspaceIR.LatticePoint> points) {
        if (points.isEmpty()) {
            return List.of();
        }
        AircraftBlockspaceIR.LatticePoint origin = points.getFirst();
        return points.stream()
                .map(point -> new AircraftBlockspaceIR.LatticePoint(
                        point.x() - origin.x(),
                        point.y() - origin.y(),
                        point.z() - origin.z()))
                .sorted()
                .toList();
    }

    private static boolean connected(Set<AircraftBlockspaceIR.LatticePoint> points) {
        if (points.isEmpty()) {
            return false;
        }
        Set<AircraftBlockspaceIR.LatticePoint> unseen = new HashSet<>(points);
        AircraftBlockspaceIR.LatticePoint start = unseen.iterator().next();
        unseen.remove(start);
        ArrayDeque<AircraftBlockspaceIR.LatticePoint> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            AircraftBlockspaceIR.LatticePoint point = queue.removeFirst();
            for (AircraftBlockspaceIR.LatticePoint neighbor : List.of(
                    new AircraftBlockspaceIR.LatticePoint(point.x() + 1, point.y(), point.z()),
                    new AircraftBlockspaceIR.LatticePoint(point.x() - 1, point.y(), point.z()),
                    new AircraftBlockspaceIR.LatticePoint(point.x(), point.y() + 1, point.z()),
                    new AircraftBlockspaceIR.LatticePoint(point.x(), point.y() - 1, point.z()),
                    new AircraftBlockspaceIR.LatticePoint(point.x(), point.y(), point.z() + 1),
                    new AircraftBlockspaceIR.LatticePoint(point.x(), point.y(), point.z() - 1))) {
                if (unseen.remove(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        return unseen.isEmpty();
    }
}
