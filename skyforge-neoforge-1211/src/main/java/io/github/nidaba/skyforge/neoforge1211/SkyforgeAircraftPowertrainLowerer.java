package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Lowers the accepted manifest/fixture/glue chain to the frozen v0.12 governed-powertrain patch. */
public final class SkyforgeAircraftPowertrainLowerer {
    public SkyforgeAircraftPowertrainIR lower(
            SkyforgeAircraftProbeManifestIR manifest,
            SkyforgeAircraftAssemblyFixtureIR fixture,
            SkyforgeAircraftGlueEncodingIR glue,
            String powertrainAssetId,
            SkyforgeAircraftPowertrainProfile profile) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(fixture, "fixture");
        Objects.requireNonNull(glue, "glue");
        Objects.requireNonNull(profile, "profile");
        if (!manifest.validation().passed() || !manifest.readiness().probePlacementManifestReady()) {
            throw new IllegalArgumentException("refusing powertrain lowering from non-ready probe manifest");
        }
        if (!fixture.validation().passed() || !fixture.readiness().assemblyFixtureTopologyPassed()) {
            throw new IllegalArgumentException("refusing powertrain lowering from failed assembly fixture");
        }
        if (!glue.validation().passed() || !glue.readiness().physicsAssemblyProbeReady()) {
            throw new IllegalArgumentException("refusing powertrain lowering from non-ready glue IR");
        }
        if (!fixture.sourceProbeManifestDigestSha256().equals(manifest.sha256())) {
            throw new IllegalArgumentException("assembly fixture does not match probe-manifest digest");
        }
        if (!glue.sourceAssemblyFixtureDigestSha256().equals(fixture.sha256())) {
            throw new IllegalArgumentException("glue encoding does not match assembly-fixture digest");
        }

        Map<AircraftBlockspaceIR.LatticePoint, SkyforgeAircraftProbeManifestIR.Placement> byCoordinate = new HashMap<>();
        for (SkyforgeAircraftProbeManifestIR.Placement placement : manifest.placements()) {
            if (byCoordinate.put(placement.point(), placement) != null) {
                throw new IllegalArgumentException("duplicate manifest coordinate: " + placement.point());
            }
        }
        Set<AircraftBlockspaceIR.LatticePoint> childCoordinates = Set.copyOf(fixture.nestedPropellerChild().coordinates());
        if (!childCoordinates.equals(Set.copyOf(manifest.placements().stream()
                .filter(placement -> "propeller_hub".equals(placement.kind()) || "propeller_sail".equals(placement.kind()))
                .map(SkyforgeAircraftProbeManifestIR.Placement::point)
                .toList()))) {
            throw new IllegalArgumentException("assembly-fixture nested child does not match manifest propeller child");
        }

        List<SkyforgeAircraftPowertrainProfile.Placement> declared = profile.placements();
        ArrayList<SkyforgeAircraftPowertrainProfile.Placement> replacements = new ArrayList<>();
        ArrayList<SkyforgeAircraftPowertrainProfile.Placement> additions = new ArrayList<>();
        Set<AircraftBlockspaceIR.LatticePoint> emittedCoordinates = new HashSet<>();
        Map<String, SkyforgeAircraftPowertrainProfile.Placement> roles = new HashMap<>();
        for (SkyforgeAircraftPowertrainProfile.Placement placement : declared) {
            if (!emittedCoordinates.add(placement.point())) {
                throw new IllegalArgumentException("duplicate powertrain coordinate: " + placement.point());
            }
            if (childCoordinates.contains(placement.point())) {
                throw new IllegalArgumentException("powertrain placement crosses propeller child boundary: " + placement.point());
            }
            roles.put(placement.role(), placement);
            SkyforgeAircraftProbeManifestIR.Placement existing = byCoordinate.get(placement.point());
            if (placement.mode() == SkyforgeAircraftPowertrainProfile.Mode.REPLACE) {
                if (existing == null) throw new IllegalArgumentException("replacement coordinate is empty in probe manifest: " + placement.point());
                SkyforgeAircraftPowertrainProfile.ReplaceExpectation expected = placement.replaces();
                if (!existing.kind().equals(expected.kind()) || !existing.resourceId().equals(expected.resourceId())) {
                    throw new IllegalArgumentException("replacement source mismatch at " + placement.point());
                }
                replacements.add(placement);
            } else {
                if (existing != null) throw new IllegalArgumentException("addition collides with manifest at " + placement.point());
                additions.add(placement);
            }
        }

        SkyforgeAircraftPowertrainProfile.Placement enginePort = requiredRole(roles, "engine_port");
        SkyforgeAircraftPowertrainProfile.Placement engineStarboard = requiredRole(roles, "engine_starboard");
        SkyforgeAircraftPowertrainProfile.Placement governor = requiredRole(roles, "governor");
        SkyforgeAircraftPowertrainProfile.Placement cog = requiredRole(roles, "governor_cog");
        SkyforgeAircraftPowertrainProfile.Placement shaft = requiredRole(roles, "prop_shaft");
        AircraftBlockspaceIR.LatticePoint bearing = profile.existingPropellerBearingCoordinate();
        long bearingCount = manifest.placements().stream()
                .filter(placement -> "propeller_bearing".equals(placement.kind()) && bearing.equals(placement.point()))
                .count();
        if (bearingCount != 1) throw new IllegalArgumentException("v0.12 expected exactly one propeller bearing at " + bearing);

        SkyforgeAircraftPowertrainIR.TopologyChecks topology = new SkyforgeAircraftPowertrainIR.TopologyChecks(
                enginePort.point().equals(new AircraftBlockspaceIR.LatticePoint(governor.point().x(), governor.point().y(), governor.point().z() - 1))
                        && engineStarboard.point().equals(new AircraftBlockspaceIR.LatticePoint(governor.point().x(), governor.point().y(), governor.point().z() + 1)),
                cog.point().equals(new AircraftBlockspaceIR.LatticePoint(governor.point().x(), governor.point().y() + 1, governor.point().z())),
                shaft.point().y() == cog.point().y() && shaft.point().z() == cog.point().z()
                        && bearing.y() == cog.point().y() && bearing.z() == cog.point().z()
                        && Math.abs(shaft.point().x() - cog.point().x()) == 1
                        && Math.abs(shaft.point().x() - bearing.x()) == 1,
                replacements.size() == 1 && governor.mode() == SkyforgeAircraftPowertrainProfile.Mode.REPLACE,
                additions.size() == declared.size() - 1,
                emittedCoordinates.stream().noneMatch(childCoordinates::contains));

        AircraftBlockspaceIR.LatticePoint rawFrom = profile.powerplantGlueDomain().from();
        AircraftBlockspaceIR.LatticePoint rawTo = profile.powerplantGlueDomain().to();
        AircraftBlockspaceIR.LatticePoint min = boundsMin(rawFrom, rawTo);
        AircraftBlockspaceIR.LatticePoint max = boundsMax(rawFrom, rawTo);
        AircraftBlockspaceIR.LatticePoint size = new AircraftBlockspaceIR.LatticePoint(
                max.x() - min.x() + 1, max.y() - min.y() + 1, max.z() - min.z() + 1);
        int maxDimension = glue.encodingPolicy().maxSelectionDimensionBlocks();
        boolean withinLimit = size.x() <= maxDimension && size.y() <= maxDimension && size.z() <= maxDimension;
        boolean containsPowertrain = emittedCoordinates.stream().allMatch(point -> contains(min, max, point));
        boolean excludesChild = childCoordinates.stream().noneMatch(point -> contains(min, max, point));
        boolean overlapsMainGlue = glue.glueDomains().stream()
                .anyMatch(domain -> boxesOverlap(min, max, domain.boundsMin(), domain.boundsMax()));
        SkyforgeAircraftPowertrainIR.GlueChecks glueChecks = new SkyforgeAircraftPowertrainIR.GlueChecks(
                withinLimit, containsPowertrain, excludesChild, overlapsMainGlue);
        if (!glueChecks.passed()) throw new IllegalArgumentException("powerplant glue validation failed: " + glueChecks);

        boolean passed = topology.passed() && glueChecks.passed();
        int priorMain = fixture.metrics().mainBodyPlacementCount();
        int resultingManifest = manifest.placements().size() + additions.size();
        int resultingMain = priorMain + additions.size();
        int nestedChild = fixture.metrics().nestedChildPlacementCount();
        int expectedTransfer = resultingMain + nestedChild;
        List<SkyforgeAircraftPowertrainIR.RuntimeObligation> obligations = profile.runtimeObligations().stream()
                .map(value -> new SkyforgeAircraftPowertrainIR.RuntimeObligation(value.id(), value.method(), "unverified"))
                .toList();
        List<SkyforgeAircraftPowertrainIR.Placement> emitted = declared.stream()
                .map(value -> new SkyforgeAircraftPowertrainIR.Placement(
                        value.role(), value.mode(), value.point(), value.resourceId(), value.blockState(), value.replaces(), value.sourceContract()))
                .toList();

        return new SkyforgeAircraftPowertrainIR(
                SkyforgeAircraftPowertrainIR.SCHEMA_VERSION,
                powertrainAssetId,
                SkyforgeAircraftPowertrainIR.COMPILER_VERSION,
                manifest.sha256(),
                fixture.sha256(),
                glue.sha256(),
                profile.profileId(),
                new SkyforgeAircraftPowertrainIR.Governor(
                        profile.portableEngineSourceRpm(),
                        profile.governorTargetRpm(),
                        "unaccepted_until_128_rpm_runtime_gate_passes",
                        profile.laterGovernorRpmPoints()),
                emitted,
                replacements.size(),
                additions.size(),
                new SkyforgeAircraftPowertrainIR.GlueDomain(
                        profile.powerplantGlueDomain().name(), rawFrom, rawTo, min, max, size),
                topology,
                glueChecks,
                obligations,
                new SkyforgeAircraftPowertrainIR.Metrics(
                        manifest.placements().size(), resultingManifest, replacements.size(), additions.size(),
                        priorMain, resultingMain, nestedChild, expectedTransfer, obligations.size(), 0),
                new SkyforgeAircraftPowertrainIR.Readiness(
                        passed, passed, passed, false, false,
                        List.of(
                                "powertrain_primary_sable_recapture_unverified",
                                "portable_engine_shared_network_unverified",
                                "governor_128_rpm_unverified",
                                "kinetic_stress_margin_unverified",
                                "propeller_thrust_sign_magnitude_unverified",
                                "pilot_occupancy_runtime_unverified",
                                "control_binding_runtime_unverified")),
                new SkyforgeAircraftPowertrainIR.Validation(
                        passed,
                        "source_constrained_dual_portable_engine_rotation_speed_controller_powertrain_topology_and_manifest_patch_only",
                        List.of(
                                "that the modified fixture reassembles into one Sable sublevel",
                                "that both Portable Engines join one live kinetic network",
                                "that the Rotation Speed Controller realizes 128 RPM at the Propeller Bearing",
                                "kinetic stress sufficiency",
                                "propeller thrust sign or magnitude",
                                "post-powertrain runtime mass or center of mass",
                                "stable flight or control authority")));
    }

    private static SkyforgeAircraftPowertrainProfile.Placement requiredRole(Map<String, SkyforgeAircraftPowertrainProfile.Placement> roles, String role) {
        SkyforgeAircraftPowertrainProfile.Placement placement = roles.get(role);
        if (placement == null) throw new IllegalArgumentException("missing required powertrain role: " + role);
        return placement;
    }
    private static AircraftBlockspaceIR.LatticePoint boundsMin(AircraftBlockspaceIR.LatticePoint a, AircraftBlockspaceIR.LatticePoint b) {
        return new AircraftBlockspaceIR.LatticePoint(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
    }
    private static AircraftBlockspaceIR.LatticePoint boundsMax(AircraftBlockspaceIR.LatticePoint a, AircraftBlockspaceIR.LatticePoint b) {
        return new AircraftBlockspaceIR.LatticePoint(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
    }
    private static boolean contains(AircraftBlockspaceIR.LatticePoint min, AircraftBlockspaceIR.LatticePoint max, AircraftBlockspaceIR.LatticePoint point) {
        return min.x() <= point.x() && point.x() <= max.x()
                && min.y() <= point.y() && point.y() <= max.y()
                && min.z() <= point.z() && point.z() <= max.z();
    }
    private static boolean boxesOverlap(AircraftBlockspaceIR.LatticePoint aMin, AircraftBlockspaceIR.LatticePoint aMax, AircraftBlockspaceIR.LatticePoint bMin, AircraftBlockspaceIR.LatticePoint bMax) {
        return !(aMax.x() < bMin.x() || aMin.x() > bMax.x())
                && !(aMax.y() < bMin.y() || aMin.y() > bMax.y())
                && !(aMax.z() < bMin.z() || aMin.z() > bMax.z());
    }
}
