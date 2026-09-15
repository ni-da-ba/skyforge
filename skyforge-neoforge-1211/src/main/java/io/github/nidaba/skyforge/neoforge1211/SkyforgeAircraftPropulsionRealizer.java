package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftAssemblyPlanIR;
import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Generates deterministic static propeller companion geometry from production aircraft anchors. */
public final class SkyforgeAircraftPropulsionRealizer {
    public SkyforgeAircraftPropulsionIR realize(
            AircraftAssemblyPlanIR assembly,
            SkyforgeAircraftTargetPreflightIR targetPreflight,
            String propulsionAssetId,
            SkyforgeAircraftPropulsionProfile profile) {
        Objects.requireNonNull(assembly, "assembly");
        Objects.requireNonNull(targetPreflight, "targetPreflight");
        Objects.requireNonNull(profile, "profile");
        if (!assembly.validation().passed()) {
            throw new IllegalArgumentException("refusing propulsion realization from invalid assembly plan");
        }
        if (!targetPreflight.validation().passed()) {
            throw new IllegalArgumentException("refusing propulsion realization from invalid target preflight");
        }
        if (!assembly.assetId().equals(targetPreflight.sourceAssemblyAssetId())
                || !assembly.sha256().equals(targetPreflight.sourceAssemblyDigestSha256())) {
            throw new IllegalArgumentException("target preflight does not match source assembly identity");
        }
        if (!profile.targetProfileId().equals(targetPreflight.targetProfileId())) {
            throw new IllegalArgumentException("propulsion profile targetProfileId does not match target preflight");
        }

        AircraftAssemblyPlanIR.Station propellerStation = assembly.stations().stream()
                .filter(station -> station.type() == AircraftBlockspaceIR.AnchorType.PROPELLER_AXIS)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("assembly has no propeller-axis station"));
        SkyforgeAircraftTargetPreflightIR.Station targetStation = targetPreflight.stations().stream()
                .filter(station -> station.type() == AircraftBlockspaceIR.AnchorType.PROPELLER_AXIS)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("target preflight has no propeller-axis station"));
        if (!propellerStation.lattice().equals(targetStation.lattice())) {
            throw new IllegalArgumentException("target propeller station does not match assembly lattice");
        }
        if (!Objects.equals(targetStation.resourceId(), profile.bearing().resourceId())) {
            throw new IllegalArgumentException("propulsion bearing resource does not match target station provider");
        }

        SkyforgeAircraftPropulsionProfile.Facing facing = profile.bearing().facing();
        if (profile.propeller().sailAxis() != facing.axis()) {
            throw new IllegalArgumentException("symmetric-sail axis must match bearing axis");
        }
        AircraftBlockspaceIR.LatticePoint bearingPoint = propellerStation.lattice();
        AircraftBlockspaceIR.LatticePoint hubPoint = new AircraftBlockspaceIR.LatticePoint(
                bearingPoint.x() + facing.dx(),
                bearingPoint.y() + facing.dy(),
                bearingPoint.z() + facing.dz());

        List<SkyforgeAircraftPropulsionProfile.BladeOffset> offsets = new ArrayList<>(profile.propeller().bladeOffsets());
        offsets.sort(Comparator.comparingInt(SkyforgeAircraftPropulsionProfile.BladeOffset::a)
                .thenComparingInt(SkyforgeAircraftPropulsionProfile.BladeOffset::b));
        List<AircraftBlockspaceIR.LatticePoint> sailPoints = offsets.stream()
                .map(offset -> applyOffset(hubPoint, facing.axis(), offset))
                .sorted()
                .toList();

        Set<AircraftBlockspaceIR.LatticePoint> occupied = new HashSet<>();
        for (AircraftAssemblyPlanIR.Site site : assembly.sites()) {
            occupied.add(site.point());
        }
        LinkedHashSet<AircraftBlockspaceIR.LatticePoint> generated = new LinkedHashSet<>();
        generated.add(bearingPoint);
        generated.add(hubPoint);
        generated.addAll(sailPoints);
        TreeSet<AircraftBlockspaceIR.LatticePoint> collisions = new TreeSet<>(generated);
        collisions.retainAll(occupied);

        int firstMomentA = offsets.stream().mapToInt(SkyforgeAircraftPropulsionProfile.BladeOffset::a).sum();
        int firstMomentB = offsets.stream().mapToInt(SkyforgeAircraftPropulsionProfile.BladeOffset::b).sum();
        Set<SkyforgeAircraftPropulsionProfile.BladeOffset> offsetSet = Set.copyOf(offsets);
        boolean centralSymmetry = offsets.stream()
                .allMatch(offset -> offsetSet.contains(new SkyforgeAircraftPropulsionProfile.BladeOffset(-offset.a(), -offset.b())));
        boolean connected = connectedToHub(offsetSet);
        int axisIndex = axisIndex(facing.axis());
        boolean coplanar = sailPoints.stream().allMatch(point -> coordinate(point, axisIndex) == coordinate(hubPoint, axisIndex));
        int sailPower = profile.propeller().sailPowerPerBlock() * sailPoints.size();

        SkyforgeAircraftPropulsionIR.TopologyChecks topology = new SkyforgeAircraftPropulsionIR.TopologyChecks(
                !occupied.contains(bearingPoint),
                !occupied.contains(hubPoint),
                generated.size() == 2 + sailPoints.size(),
                collisions.isEmpty(),
                coplanar,
                connected,
                firstMomentA == 0 && firstMomentB == 0,
                centralSymmetry,
                sailPower >= profile.propeller().minimumSailPower(),
                sailPower == profile.propeller().targetSailPower());
        boolean staticPassed = topology.passed();

        List<SkyforgeAircraftPropulsionIR.SailPlacement> sailPlacements = sailPoints.stream()
                .map(point -> new SkyforgeAircraftPropulsionIR.SailPlacement(
                        point,
                        profile.propeller().sailResourceId(),
                        java.util.Map.of("axis", profile.propeller().sailAxis().name().toLowerCase(java.util.Locale.ROOT)),
                        profile.propeller().sailPowerPerBlock()))
                .toList();
        List<SkyforgeAircraftPropulsionIR.RuntimeObligation> obligations = profile.runtimeObligations().stream()
                .map(value -> new SkyforgeAircraftPropulsionIR.RuntimeObligation(value.id(), value.method(), "unverified"))
                .toList();

        List<String> resolved = new ArrayList<>();
        TreeSet<String> blockers = new TreeSet<>();
        for (String blocker : targetPreflight.readiness().blockers()) {
            if ("unsatisfied_contraption_companion_requirements".equals(blocker)
                    && topology.minimumSailPowerSatisfied()) {
                resolved.add(blocker);
            } else {
                blockers.add(blocker);
            }
        }
        if (!staticPassed) {
            blockers.add("propulsion_static_topology_failed");
        }
        if (!obligations.isEmpty()) {
            blockers.add("propulsion_runtime_obligations_unverified");
        }
        resolved.sort(String::compareTo);

        return new SkyforgeAircraftPropulsionIR(
                SkyforgeAircraftPropulsionIR.SCHEMA_VERSION,
                propulsionAssetId,
                SkyforgeAircraftPropulsionIR.COMPILER_VERSION,
                assembly.sha256(),
                targetPreflight.sha256(),
                profile.profileId(),
                new SkyforgeAircraftPropulsionIR.BearingPlacement(
                        bearingPoint,
                        profile.bearing().resourceId(),
                        java.util.Map.of("facing", facing.id()),
                        profile.bearing().evidenceLevel()),
                new SkyforgeAircraftPropulsionIR.HubPlacement(
                        hubPoint,
                        profile.propeller().hubResourceId(),
                        profile.propeller().hubBlockState(),
                        "bearing_facing_neighbor; live capture still required"),
                sailPlacements,
                new SkyforgeAircraftPropulsionIR.Geometry(
                        facing.axis(),
                        facing,
                        hubPoint,
                        offsets,
                        firstMomentA,
                        firstMomentB,
                        offsets.stream().map(offset -> offset.a() * offset.a() + offset.b() * offset.b()).sorted().toList(),
                        sailPower,
                        profile.propeller().minimumSailPower(),
                        profile.propeller().targetSailPower()),
                topology,
                List.copyOf(collisions),
                obligations,
                new SkyforgeAircraftPropulsionIR.Metrics(
                        2 + sailPlacements.size(),
                        sailPlacements.size(),
                        sailPower,
                        obligations.size(),
                        0),
                new SkyforgeAircraftPropulsionIR.Readiness(
                        staticPassed,
                        false,
                        staticPassed && blockers.isEmpty(),
                        false,
                        resolved,
                        List.copyOf(blockers)),
                new SkyforgeAircraftPropulsionIR.Validation(
                        staticPassed,
                        "deterministic_propeller_geometry_and_source_backed_static_topology",
                        List.of(
                                "bearing contraption capture in the exact runtime",
                                "kinetic connectivity, RPM, or stress margin",
                                "thrust sign or magnitude",
                                "nested-contraption attachment persistence",
                                "vehicle mass or center of mass",
                                "control binding",
                                "stable flight")));
    }

    private static AircraftBlockspaceIR.LatticePoint applyOffset(
            AircraftBlockspaceIR.LatticePoint hub,
            SkyforgeAircraftPropulsionProfile.Axis axis,
            SkyforgeAircraftPropulsionProfile.BladeOffset offset) {
        return switch (axis) {
            case X -> new AircraftBlockspaceIR.LatticePoint(hub.x(), hub.y() + offset.a(), hub.z() + offset.b());
            case Y -> new AircraftBlockspaceIR.LatticePoint(hub.x() + offset.a(), hub.y(), hub.z() + offset.b());
            case Z -> new AircraftBlockspaceIR.LatticePoint(hub.x() + offset.a(), hub.y() + offset.b(), hub.z());
        };
    }

    private static boolean connectedToHub(Set<SkyforgeAircraftPropulsionProfile.BladeOffset> offsets) {
        Set<SkyforgeAircraftPropulsionProfile.BladeOffset> allowed = new HashSet<>(offsets);
        SkyforgeAircraftPropulsionProfile.BladeOffset hub = new SkyforgeAircraftPropulsionProfile.BladeOffset(0, 0);
        allowed.add(hub);
        Set<SkyforgeAircraftPropulsionProfile.BladeOffset> seen = new HashSet<>();
        ArrayDeque<SkyforgeAircraftPropulsionProfile.BladeOffset> queue = new ArrayDeque<>();
        seen.add(hub);
        queue.add(hub);
        while (!queue.isEmpty()) {
            SkyforgeAircraftPropulsionProfile.BladeOffset current = queue.removeFirst();
            for (SkyforgeAircraftPropulsionProfile.BladeOffset next : List.of(
                    new SkyforgeAircraftPropulsionProfile.BladeOffset(current.a() + 1, current.b()),
                    new SkyforgeAircraftPropulsionProfile.BladeOffset(current.a() - 1, current.b()),
                    new SkyforgeAircraftPropulsionProfile.BladeOffset(current.a(), current.b() + 1),
                    new SkyforgeAircraftPropulsionProfile.BladeOffset(current.a(), current.b() - 1))) {
                if (allowed.contains(next) && seen.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return seen.containsAll(offsets);
    }

    private static int axisIndex(SkyforgeAircraftPropulsionProfile.Axis axis) {
        return switch (axis) {
            case X -> 0;
            case Y -> 1;
            case Z -> 2;
        };
    }

    private static int coordinate(AircraftBlockspaceIR.LatticePoint point, int axisIndex) {
        return switch (axisIndex) {
            case 0 -> point.x();
            case 1 -> point.y();
            case 2 -> point.z();
            default -> throw new IllegalArgumentException("invalid axis index: " + axisIndex);
        };
    }
}
