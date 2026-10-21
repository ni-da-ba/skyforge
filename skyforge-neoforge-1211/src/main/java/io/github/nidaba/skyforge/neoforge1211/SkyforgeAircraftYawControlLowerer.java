package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Lowers the current production v0.12 specimen to the corrected static v0.13.1 yaw topology. */
public final class SkyforgeAircraftYawControlLowerer {
    private static final AircraftBlockspaceIR.LatticePoint EXPECTED_BEARING = point(18, 3, 0);
    private static final AircraftBlockspaceIR.LatticePoint EXPECTED_RUDDER_SEED = point(18, 4, 0);

    public SkyforgeAircraftYawControlIR lower(
            SkyforgeAircraftProbeManifestIR manifest,
            SkyforgeAircraftAssemblyFixtureIR fixture,
            SkyforgeAircraftGlueEncodingIR glue,
            SkyforgeAircraftPowertrainIR powertrain,
            String assetId,
            SkyforgeAircraftYawControlProfile profile) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(fixture, "fixture");
        Objects.requireNonNull(glue, "glue");
        Objects.requireNonNull(powertrain, "powertrain");
        Objects.requireNonNull(profile, "profile");
        if (assetId == null || assetId.isBlank()) throw new IllegalArgumentException("assetId must not be blank");

        requireAcceptedUpstream(manifest, fixture, glue, powertrain);
        requireDigest("assembly fixture -> manifest", fixture.sourceProbeManifestDigestSha256(), manifest.sha256());
        requireDigest("glue -> assembly fixture", glue.sourceAssemblyFixtureDigestSha256(), fixture.sha256());
        requireDigest("powertrain -> manifest", powertrain.sourceProbeManifestDigestSha256(), manifest.sha256());
        requireDigest("powertrain -> assembly fixture", powertrain.sourceAssemblyFixtureDigestSha256(), fixture.sha256());
        requireDigest("powertrain -> glue", powertrain.sourceGlueEncodingDigestSha256(), glue.sha256());

        Map<AircraftBlockspaceIR.LatticePoint, EffectivePlacement> effective = reconstructPowertrainManifest(manifest, powertrain);
        Set<AircraftBlockspaceIR.LatticePoint> propellerChild = new HashSet<>(fixture.nestedPropellerChild().coordinates());
        if (propellerChild.size() != fixture.nestedPropellerChild().placementCount()
                || propellerChild.size() != powertrain.metrics().nestedPropellerChildPlacementCount()) {
            throw new IllegalArgumentException("v0.12 propeller-child membership/count mismatch");
        }
        for (AircraftBlockspaceIR.LatticePoint coordinate : propellerChild) {
            if (!effective.containsKey(coordinate)) {
                throw new IllegalArgumentException("v0.12 propeller-child coordinate missing from effective manifest: " + coordinate);
            }
        }

        Set<AircraftBlockspaceIR.LatticePoint> parentMain = new HashSet<>(effective.keySet());
        parentMain.removeAll(propellerChild);
        int reconstructedParentWithAssembler = parentMain.size() + 1;
        int v012ParentWithAssembler = powertrain.metrics().resultingMovingMainBodyPlacementCount();
        if (reconstructedParentWithAssembler != v012ParentWithAssembler) {
            throw new IllegalArgumentException("v0.12 moving-parent metric mismatch: metric=" + v012ParentWithAssembler
                    + " reconstructed=" + reconstructedParentWithAssembler);
        }
        if (fixture.physicsAssemblerPlacement().point() == null || effective.containsKey(fixture.physicsAssemblerPlacement().point())) {
            throw new IllegalArgumentException("physics assembler must remain a fixture-only parent member");
        }
        if (powertrain.metrics().resultingManifestPlacementCount() != effective.size()) {
            throw new IllegalArgumentException("v0.12 resulting manifest metric mismatch");
        }

        List<SkyforgeAircraftYawControlIR.Placement> emitted = new ArrayList<>();
        Set<AircraftBlockspaceIR.LatticePoint> rudderChild = new HashSet<>();
        int parentAdds = 0;
        int parentRemoves = 0;
        int controlAdds = 0;
        for (SkyforgeAircraftYawControlProfile.Placement placement : profile.placements()) {
            if (propellerChild.contains(placement.point())) {
                throw new IllegalArgumentException("yaw-control placement overlaps propeller child: " + placement.point());
            }
            switch (placement.mode()) {
                case ADD_MAIN -> {
                    requireEmpty(effective, placement.point(), "ADD_MAIN");
                    effective.put(placement.point(), EffectivePlacement.fromYaw(placement));
                    if (!parentMain.add(placement.point())) throw new IllegalArgumentException("duplicate parent add: " + placement.point());
                    parentAdds++;
                }
                case REMOVE_MAIN -> {
                    EffectivePlacement source = effective.get(placement.point());
                    if (source == null) throw new IllegalArgumentException("required yaw-control removal source is empty: " + placement.point());
                    requireSourceMatch(source, placement.replaces(), placement.point());
                    if (!parentMain.remove(placement.point())) throw new IllegalArgumentException("yaw-control removal source is not parent-main: " + placement.point());
                    effective.remove(placement.point());
                    parentRemoves++;
                }
                case ADD_CONTROL_CHILD -> {
                    requireEmpty(effective, placement.point(), "ADD_CONTROL_CHILD");
                    effective.put(placement.point(), EffectivePlacement.fromYaw(placement));
                    if (!rudderChild.add(placement.point())) throw new IllegalArgumentException("duplicate rudder child add: " + placement.point());
                    controlAdds++;
                }
            }
            emitted.add(new SkyforgeAircraftYawControlIR.Placement(
                    placement.role(), placement.mode(), placement.point(), placement.kind(), placement.resourceId(),
                    placement.blockState(), placement.replaces(), placement.sourceContract()));
        }

        AircraftBlockspaceIR.LatticePoint bearing = rolePoint(profile, "yaw_swivel_bearing");
        AircraftBlockspaceIR.LatticePoint rudderSeed = rolePoint(profile, "rudder_seed");
        EffectivePlacement bearingPlacement = effective.get(bearing);
        if (bearingPlacement == null) throw new IllegalArgumentException("corrected bearing is absent after lowering");

        boolean fixedFinStateRetained = profile.fixedFinCoordinates().stream().allMatch(coordinate -> {
            EffectivePlacement placement = effective.get(coordinate);
            return placement != null
                    && "airframe_aerodynamic_surface".equals(placement.kind())
                    && "create:white_sail".equals(placement.resourceId())
                    && Map.of("facing", "south").equals(placement.blockState());
        });
        boolean airGapEmpty = profile.airGapCoordinates().stream().noneMatch(effective::containsKey);
        boolean rudderSymmetricZ = rudderChild.stream().allMatch(coordinate -> {
            EffectivePlacement placement = effective.get(coordinate);
            return placement != null && "yaw_control_surface".equals(placement.kind())
                    && "simulated:white_symmetric_sail".equals(placement.resourceId())
                    && Map.of("axis", "z").equals(placement.blockState());
        });

        SkyforgeAircraftYawControlIR.TopologyChecks topologyChecks = new SkyforgeAircraftYawControlIR.TopologyChecks(
                parentAdds == 1 && EXPECTED_BEARING.equals(bearing),
                parentRemoves == 1 && !effective.containsKey(point(17, 4, 0)),
                controlAdds == 4 && rudderChild.size() == 4,
                "yaw_control_hinge".equals(bearingPlacement.kind())
                        && "simulated:swivel_bearing".equals(bearingPlacement.resourceId())
                        && Map.of("assembled", "false", "facing", "up", "powered", "false").equals(bearingPlacement.blockState()),
                EXPECTED_BEARING.equals(bearing) && EXPECTED_RUDDER_SEED.equals(rudderSeed)
                        && rudderSeed.equals(point(bearing.x(), bearing.y() + 1, bearing.z())),
                fixedFinStateRetained,
                airGapEmpty,
                connected(rudderChild, rudderSeed),
                rudderSymmetricZ,
                disjoint(rudderChild, parentMain),
                disjoint(rudderChild, propellerChild));
        if (!topologyChecks.passed()) throw new IllegalArgumentException("corrected yaw-control topology checks failed: " + topologyChecks);

        List<SkyforgeAircraftYawControlIR.GlueDomain> runtimeGlueDomains = lowerRuntimeGlueDomains(glue, powertrain, profile);
        Map<String, SkyforgeAircraftYawControlIR.GlueDomain> byGlueName = new HashMap<>();
        for (SkyforgeAircraftYawControlIR.GlueDomain domain : runtimeGlueDomains) {
            if (byGlueName.put(domain.name(), domain) != null) throw new IllegalArgumentException("duplicate runtime glue domain: " + domain.name());
        }
        int maxSelectionDimension = glue.encodingPolicy().maxSelectionDimensionBlocks();
        SkyforgeAircraftYawControlIR.GlueDomain fixedTail = requireDomain(byGlueName, "vertical_tail_fixed");
        SkyforgeAircraftYawControlIR.GlueDomain bearingMount = requireDomain(byGlueName, "yaw_bearing_mount");
        SkyforgeAircraftYawControlIR.GlueDomain childGlue = requireDomain(byGlueName, "rudder_child");
        boolean noParentGlueContainsRudder = runtimeGlueDomains.stream()
                .filter(domain -> domain.owner() == SkyforgeAircraftYawControlProfile.GlueOwner.PARENT)
                .noneMatch(domain -> rudderChild.stream().anyMatch(coordinate -> contains(domain, coordinate)));
        boolean noGlueContainsGap = runtimeGlueDomains.stream()
                .noneMatch(domain -> profile.airGapCoordinates().stream().anyMatch(coordinate -> contains(domain, coordinate)));

        SkyforgeAircraftYawControlIR.GlueChecks glueChecks = new SkyforgeAircraftYawControlIR.GlueChecks(
                glue.glueDomains().stream().filter(domain -> profile.supersededGlueDomainName().equals(domain.name())).count() == 1,
                runtimeGlueDomains.size() == 7,
                runtimeGlueDomains.stream().allMatch(domain -> maxDimension(domain.selectionSizeBlocks()) <= maxSelectionDimension),
                noParentGlueContainsRudder,
                noGlueContainsGap,
                profile.fixedFinCoordinates().stream().allMatch(coordinate -> contains(fixedTail, coordinate)),
                contains(bearingMount, bearing) && contains(bearingMount, point(bearing.x() - 1, bearing.y(), bearing.z())),
                rudderChild.stream().allMatch(coordinate -> contains(childGlue, coordinate)) && !contains(childGlue, bearing));
        if (!glueChecks.passed()) throw new IllegalArgumentException("corrected yaw-control glue checks failed: " + glueChecks);

        int v0131ParentWithAssembler = v012ParentWithAssembler + parentAdds - parentRemoves;
        int resultingManifestCount = powertrain.metrics().resultingManifestPlacementCount() + parentAdds - parentRemoves + controlAdds;
        int expectedPrimaryTransferCount = v0131ParentWithAssembler + propellerChild.size() + rudderChild.size();
        SkyforgeAircraftYawControlIR.CountChecks countChecks = new SkyforgeAircraftYawControlIR.CountChecks(
                v012ParentWithAssembler == reconstructedParentWithAssembler,
                parentAdds == 1 && parentRemoves == 1,
                v0131ParentWithAssembler == 118,
                propellerChild.size() == 9,
                rudderChild.size() == 4,
                resultingManifestCount == 130,
                expectedPrimaryTransferCount == 131);
        if (!countChecks.passed()) throw new IllegalArgumentException("corrected yaw-control count/provenance checks failed: " + countChecks);

        List<SkyforgeAircraftYawControlIR.RuntimeObligation> obligations = profile.runtimeObligations().stream()
                .map(value -> new SkyforgeAircraftYawControlIR.RuntimeObligation(value.id(), value.method(), "unverified"))
                .toList();
        boolean passed = topologyChecks.passed() && glueChecks.passed() && countChecks.passed();
        List<AircraftBlockspaceIR.LatticePoint> rudderCoordinates = rudderChild.stream().sorted().toList();
        return new SkyforgeAircraftYawControlIR(
                SkyforgeAircraftYawControlIR.SCHEMA_VERSION,
                assetId,
                SkyforgeAircraftYawControlIR.COMPILER_VERSION,
                manifest.sha256(),
                fixture.sha256(),
                glue.sha256(),
                powertrain.sha256(),
                profile.supersededYawDigestSha256(),
                profile.correctionReason(),
                profile.profileId(),
                emitted,
                profile.fixedFinCoordinates(),
                profile.airGapCoordinates(),
                rudderCoordinates,
                bearing,
                rudderSeed,
                runtimeGlueDomains,
                topologyChecks,
                glueChecks,
                countChecks,
                obligations,
                new SkyforgeAircraftYawControlIR.AerodynamicAccounting(
                        profile.fixedFinCoordinates().size(),
                        19,
                        rudderChild.size(),
                        profile.airGapCoordinates().size(),
                        "runtime-driven v0.13.1 engineering-mule correction; analytical v0.1 evidence is not retuned to match this discrete mechanism"),
                new SkyforgeAircraftYawControlIR.Metrics(
                        v012ParentWithAssembler,
                        v0131ParentWithAssembler,
                        propellerChild.size(),
                        rudderChild.size(),
                        resultingManifestCount,
                        expectedPrimaryTransferCount,
                        parentAdds,
                        parentRemoves,
                        controlAdds,
                        runtimeGlueDomains.size(),
                        obligations.size(),
                        0),
                new SkyforgeAircraftYawControlIR.Readiness(
                        passed,
                        passed,
                        false,
                        false,
                        false,
                        List.of(
                                "pilot_control_binding_unverified",
                                "rudder_actuation_unverified",
                                "rudder_neutral_constraint_unverified",
                                "rudder_swivel_child_capture_unverified",
                                "rudder_yaw_force_unverified",
                                "v0131_primary_sable_recapture_unverified")),
                new SkyforgeAircraftYawControlIR.Validation(
                        passed,
                        "runtime_corrected_one_axis_yaw_topology_with_seven_cell_fixed_fin_one_block_air_hinge_gap_aft_up_facing_swivel_bearing_and_four_cell_symmetric_sail_rudder",
                        List.of(
                                "that the corrected 131-block primary fixture reassembles into one Sable sublevel",
                                "that the aft Swivel Bearing captures exactly four rudder cells",
                                "that the rotary constraint begins neutral",
                                "signed rudder actuation or inverse neutral return behavior",
                                "runtime rudder side force or yaw moment",
                                "pilot input binding",
                                "stable flight or flight qualification")));
    }

    private static void requireAcceptedUpstream(
            SkyforgeAircraftProbeManifestIR manifest,
            SkyforgeAircraftAssemblyFixtureIR fixture,
            SkyforgeAircraftGlueEncodingIR glue,
            SkyforgeAircraftPowertrainIR powertrain) {
        if (!manifest.validation().passed() || !manifest.validationChecks().passed() || !manifest.readiness().probePlacementManifestReady()) {
            throw new IllegalArgumentException("v0.13.1 requires accepted production probe-manifest authority");
        }
        if (!fixture.validation().passed() || !fixture.topologyChecks().passed() || !fixture.readiness().assemblyFixtureTopologyPassed()) {
            throw new IllegalArgumentException("v0.13.1 requires accepted production assembly-fixture authority");
        }
        if (!glue.validation().passed() || !glue.checks().passed() || !glue.readiness().adhesionApplicationEncodingReady()) {
            throw new IllegalArgumentException("v0.13.1 requires accepted production glue authority");
        }
        if (!powertrain.validation().passed() || !powertrain.topologyChecks().passed() || !powertrain.glueChecks().passed()
                || !powertrain.readiness().powertrainStaticTopologyPassed()) {
            throw new IllegalArgumentException("v0.13.1 requires accepted production powertrain authority");
        }
    }

    private static Map<AircraftBlockspaceIR.LatticePoint, EffectivePlacement> reconstructPowertrainManifest(
            SkyforgeAircraftProbeManifestIR manifest,
            SkyforgeAircraftPowertrainIR powertrain) {
        Map<AircraftBlockspaceIR.LatticePoint, EffectivePlacement> effective = new LinkedHashMap<>();
        for (SkyforgeAircraftProbeManifestIR.Placement placement : manifest.placements()) {
            EffectivePlacement prior = effective.put(placement.point(), new EffectivePlacement(
                    placement.kind(), placement.resourceId(), placement.blockState()));
            if (prior != null) throw new IllegalArgumentException("duplicate production manifest coordinate: " + placement.point());
        }
        for (SkyforgeAircraftPowertrainIR.Placement placement : powertrain.placements()) {
            switch (placement.mode()) {
                case REPLACE -> {
                    EffectivePlacement prior = effective.get(placement.point());
                    if (prior == null) throw new IllegalArgumentException("v0.12 replacement source missing: " + placement.point());
                    if (!placement.replaces().kind().equals(prior.kind()) || !placement.replaces().resourceId().equals(prior.resourceId())) {
                        throw new IllegalArgumentException("v0.12 replacement source mismatch: " + placement.point());
                    }
                    effective.put(placement.point(), new EffectivePlacement("powertrain_main_body", placement.resourceId(), placement.blockState()));
                }
                case ADD -> {
                    if (effective.putIfAbsent(placement.point(), new EffectivePlacement(
                            "powertrain_main_body", placement.resourceId(), placement.blockState())) != null) {
                        throw new IllegalArgumentException("v0.12 addition collision: " + placement.point());
                    }
                }
            }
        }
        return effective;
    }

    private static List<SkyforgeAircraftYawControlIR.GlueDomain> lowerRuntimeGlueDomains(
            SkyforgeAircraftGlueEncodingIR glue,
            SkyforgeAircraftPowertrainIR powertrain,
            SkyforgeAircraftYawControlProfile profile) {
        List<SkyforgeAircraftYawControlIR.GlueDomain> domains = new ArrayList<>();
        int removed = 0;
        for (SkyforgeAircraftGlueEncodingIR.GlueDomain domain : glue.glueDomains()) {
            if (profile.supersededGlueDomainName().equals(domain.name())) {
                removed++;
                continue;
            }
            domains.add(domain(domain.name(), SkyforgeAircraftYawControlProfile.GlueOwner.PARENT, domain.from(), domain.to()));
        }
        if (removed != 1) throw new IllegalArgumentException("expected exactly one superseded vertical_tail glue domain, removed=" + removed);
        SkyforgeAircraftPowertrainIR.GlueDomain powerplant = powertrain.powerplantGlueDomain();
        domains.add(domain(powerplant.name(), SkyforgeAircraftYawControlProfile.GlueOwner.PARENT, powerplant.from(), powerplant.to()));
        for (SkyforgeAircraftYawControlProfile.GlueDomain replacement : profile.replacementGlueDomains()) {
            domains.add(domain(replacement.name(), replacement.owner(), replacement.from(), replacement.to()));
        }
        domains.sort(Comparator.comparing(SkyforgeAircraftYawControlIR.GlueDomain::name));
        return List.copyOf(domains);
    }

    private static SkyforgeAircraftYawControlIR.GlueDomain domain(
            String name,
            SkyforgeAircraftYawControlProfile.GlueOwner owner,
            AircraftBlockspaceIR.LatticePoint from,
            AircraftBlockspaceIR.LatticePoint to) {
        AircraftBlockspaceIR.LatticePoint min = point(Math.min(from.x(), to.x()), Math.min(from.y(), to.y()), Math.min(from.z(), to.z()));
        AircraftBlockspaceIR.LatticePoint max = point(Math.max(from.x(), to.x()), Math.max(from.y(), to.y()), Math.max(from.z(), to.z()));
        AircraftBlockspaceIR.LatticePoint size = point(max.x() - min.x() + 1, max.y() - min.y() + 1, max.z() - min.z() + 1);
        return new SkyforgeAircraftYawControlIR.GlueDomain(name, owner, from, to, min, max, size);
    }

    private static void requireSourceMatch(
            EffectivePlacement source,
            SkyforgeAircraftYawControlProfile.ReplaceExpectation expected,
            AircraftBlockspaceIR.LatticePoint coordinate) {
        if (expected == null || !expected.kind().equals(source.kind()) || !expected.resourceId().equals(source.resourceId())
                || !expected.blockState().equals(source.blockState())) {
            throw new IllegalArgumentException("yaw-control source resource/state mismatch at " + coordinate
                    + ": actual=" + source + " expected=" + expected);
        }
    }

    private static void requireEmpty(
            Map<AircraftBlockspaceIR.LatticePoint, EffectivePlacement> effective,
            AircraftBlockspaceIR.LatticePoint coordinate,
            String mode) {
        if (effective.containsKey(coordinate)) throw new IllegalArgumentException(mode + " collides with occupied coordinate: " + coordinate);
    }

    private static AircraftBlockspaceIR.LatticePoint rolePoint(SkyforgeAircraftYawControlProfile profile, String role) {
        return profile.placements().stream().filter(value -> role.equals(value.role())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("missing yaw-control role: " + role)).point();
    }

    private static SkyforgeAircraftYawControlIR.GlueDomain requireDomain(
            Map<String, SkyforgeAircraftYawControlIR.GlueDomain> domains, String name) {
        SkyforgeAircraftYawControlIR.GlueDomain domain = domains.get(name);
        if (domain == null) throw new IllegalArgumentException("missing corrected glue domain: " + name);
        return domain;
    }

    private static boolean connected(Set<AircraftBlockspaceIR.LatticePoint> cells, AircraftBlockspaceIR.LatticePoint seed) {
        if (cells.isEmpty() || !cells.contains(seed)) return false;
        Set<AircraftBlockspaceIR.LatticePoint> reached = new HashSet<>();
        ArrayDeque<AircraftBlockspaceIR.LatticePoint> queue = new ArrayDeque<>();
        reached.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            AircraftBlockspaceIR.LatticePoint current = queue.removeFirst();
            for (AircraftBlockspaceIR.LatticePoint neighbor : neighbors(current)) {
                if (cells.contains(neighbor) && reached.add(neighbor)) queue.addLast(neighbor);
            }
        }
        return reached.equals(cells);
    }

    private static List<AircraftBlockspaceIR.LatticePoint> neighbors(AircraftBlockspaceIR.LatticePoint p) {
        return List.of(
                point(p.x() + 1, p.y(), p.z()), point(p.x() - 1, p.y(), p.z()),
                point(p.x(), p.y() + 1, p.z()), point(p.x(), p.y() - 1, p.z()),
                point(p.x(), p.y(), p.z() + 1), point(p.x(), p.y(), p.z() - 1));
    }

    private static boolean contains(SkyforgeAircraftYawControlIR.GlueDomain domain, AircraftBlockspaceIR.LatticePoint p) {
        return domain.boundsMin().x() <= p.x() && p.x() <= domain.boundsMax().x()
                && domain.boundsMin().y() <= p.y() && p.y() <= domain.boundsMax().y()
                && domain.boundsMin().z() <= p.z() && p.z() <= domain.boundsMax().z();
    }

    private static boolean disjoint(Set<AircraftBlockspaceIR.LatticePoint> a, Set<AircraftBlockspaceIR.LatticePoint> b) {
        return a.stream().noneMatch(b::contains);
    }

    private static int maxDimension(AircraftBlockspaceIR.LatticePoint p) { return Math.max(p.x(), Math.max(p.y(), p.z())); }
    private static void requireDigest(String edge, String actual, String expected) {
        if (!Objects.equals(actual, expected)) throw new IllegalArgumentException(edge + " digest mismatch: actual=" + actual + " expected=" + expected);
    }
    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) { return new AircraftBlockspaceIR.LatticePoint(x, y, z); }

    private record EffectivePlacement(String kind, String resourceId, Map<String, String> blockState) {
        EffectivePlacement {
            kind = Objects.requireNonNull(kind, "kind");
            resourceId = Objects.requireNonNull(resourceId, "resourceId");
            blockState = Map.copyOf(Objects.requireNonNull(blockState, "blockState"));
        }
        static EffectivePlacement fromYaw(SkyforgeAircraftYawControlProfile.Placement placement) {
            return new EffectivePlacement(placement.kind(), placement.resourceId(), placement.blockState());
        }
    }
}
