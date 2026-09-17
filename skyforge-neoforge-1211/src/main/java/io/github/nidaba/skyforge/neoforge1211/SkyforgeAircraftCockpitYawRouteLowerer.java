package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/** AIRCRAFT-PROD-014 deterministic, collision-aware, sign-aware cockpit yaw-route lowering. */
public final class SkyforgeAircraftCockpitYawRouteLowerer {
    private static final List<Direction> HORIZONTAL = List.of(Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST);

    public SkyforgeAircraftCockpitYawRouteIR lower(
            SkyforgeAircraftPilotStationIR pilot,
            SkyforgeAircraftProbeManifestIR manifest,
            SkyforgeAircraftAssemblyFixtureIR fixture,
            SkyforgeAircraftGlueEncodingIR glue,
            SkyforgeAircraftPowertrainIR powertrain,
            SkyforgeAircraftYawControlIR yaw,
            SkyforgeAircraftSteeringYawSourceIR steering,
            String assetId,
            SkyforgeAircraftCockpitYawRouteProfile profile) {
        Objects.requireNonNull(pilot, "pilot");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(fixture, "fixture");
        Objects.requireNonNull(glue, "glue");
        Objects.requireNonNull(powertrain, "powertrain");
        Objects.requireNonNull(yaw, "yaw");
        Objects.requireNonNull(steering, "steering");
        Objects.requireNonNull(profile, "profile");
        if (assetId == null || assetId.isBlank()) throw new IllegalArgumentException("assetId must not be blank");
        requireAcceptedUpstream(pilot, manifest, fixture, glue, powertrain, yaw, steering);
        requireDigest("manifest -> pilot", manifest.sourcePilotStationDigestSha256(), pilot.sha256());
        requireDigest("fixture -> manifest", fixture.sourceProbeManifestDigestSha256(), manifest.sha256());
        requireDigest("glue -> fixture", glue.sourceAssemblyFixtureDigestSha256(), fixture.sha256());
        requireDigest("powertrain -> manifest", powertrain.sourceProbeManifestDigestSha256(), manifest.sha256());
        requireDigest("powertrain -> fixture", powertrain.sourceAssemblyFixtureDigestSha256(), fixture.sha256());
        requireDigest("powertrain -> glue", powertrain.sourceGlueEncodingDigestSha256(), glue.sha256());
        requireDigest("yaw -> manifest", yaw.sourceProbeManifestDigestSha256(), manifest.sha256());
        requireDigest("yaw -> fixture", yaw.sourceAssemblyFixtureDigestSha256(), fixture.sha256());
        requireDigest("yaw -> glue", yaw.sourceGlueEncodingDigestSha256(), glue.sha256());
        requireDigest("yaw -> powertrain", yaw.sourcePowertrainDigestSha256(), powertrain.sha256());
        requireDigest("steering -> yaw", steering.sourceYawControlDigestSha256(), yaw.sha256());

        AircraftBlockspaceIR.LatticePoint seat = pilot.placement().point();
        long manifestSeatCount = manifest.placements().stream().filter(value -> value.point().equals(seat)
                && value.resourceId().equals(pilot.placement().resourceId())).count();
        if (manifestSeatCount != 1) throw new IllegalArgumentException("current manifest does not contain exactly the accepted pilot station");
        if (profile.maxGlueSelectionDimensionBlocks() > glue.encodingPolicy().maxSelectionDimensionBlocks()) {
            throw new IllegalArgumentException("cockpit route glue bound exceeds accepted current Create glue policy");
        }

        Partition partition = reconstruct(manifest, fixture, powertrain, yaw);
        AircraftBlockspaceIR.LatticePoint driveCog = steering.driveCogCoordinate();
        AircraftBlockspaceIR.LatticePoint swivel = steering.swivelBearingCoordinate();
        if (!driveCog.equals(point(swivel.x(), swivel.y(), swivel.z() + 1))) {
            throw new IllegalArgumentException("cockpit route must preserve accepted +Z drive-cog/Swivel interface");
        }
        if (partition.effective().containsKey(driveCog)) {
            throw new IllegalArgumentException("accepted tail drive-cog coordinate is already occupied: " + driveCog);
        }
        if (!SkyforgeAircraftCockpitYawRouteProfile.EXACT_RESOURCES.equals(profile.resources())) {
            throw new IllegalArgumentException("cockpit route resources no longer match exact production contract");
        }

        Set<AircraftBlockspaceIR.LatticePoint> forbidden = new HashSet<>(profile.forbiddenCoordinates());
        forbidden.addAll(yaw.airGapCoordinates());
        Set<AircraftBlockspaceIR.LatticePoint> obstacles = new HashSet<>(partition.effective().keySet());
        obstacles.addAll(forbidden);
        obstacles.addAll(partition.propellerChild());
        obstacles.addAll(partition.rudderChild());

        List<AcceptedCandidate> accepted = new ArrayList<>();
        List<SkyforgeAircraftCockpitYawRouteIR.RejectedCandidate> rejected = new ArrayList<>();
        int candidateIndex = 0;
        for (SkyforgeAircraftCockpitYawRouteProfile.WheelCandidate candidate : profile.pilotWheelCandidates()) {
            AircraftBlockspaceIR.LatticePoint wheel = add(seat, candidate.offsetFromPilotSeat());
            String facing = candidate.blockState().get("facing");
            if (!"true".equals(candidate.blockState().get("on_floor")) || !"false".equals(candidate.blockState().get("waterlogged"))) {
                rejected.add(new SkyforgeAircraftCockpitYawRouteIR.RejectedCandidate(candidate.name(), "wheel must be dry and floor-mounted"));
                candidateIndex++;
                continue;
            }
            Direction towardSeat = directionBetween(wheel, seat);
            if (towardSeat == null || !towardSeat.name().equalsIgnoreCase(facing)) {
                rejected.add(new SkyforgeAircraftCockpitYawRouteIR.RejectedCandidate(candidate.name(), "wheel facing must point directly toward pilot seat"));
                candidateIndex++;
                continue;
            }
            AircraftBlockspaceIR.LatticePoint start = move(wheel, Direction.DOWN);
            AircraftBlockspaceIR.LatticePoint goal = move(driveCog, Direction.DOWN);
            if (obstacles.contains(wheel)) {
                rejected.add(new SkyforgeAircraftCockpitYawRouteIR.RejectedCandidate(candidate.name(), "wheel collision at " + wheel));
                candidateIndex++;
                continue;
            }
            if (obstacles.contains(start)) {
                rejected.add(new SkyforgeAircraftCockpitYawRouteIR.RejectedCandidate(candidate.name(), "drop gearbox collision at " + start));
                candidateIndex++;
                continue;
            }
            if (obstacles.contains(goal)) {
                rejected.add(new SkyforgeAircraftCockpitYawRouteIR.RejectedCandidate(candidate.name(), "tail rise gearbox collision at " + goal));
                candidateIndex++;
                continue;
            }
            Set<AircraftBlockspaceIR.LatticePoint> searchObstacles = new HashSet<>(obstacles);
            searchObstacles.add(wheel);
            searchObstacles.add(driveCog);
            SearchResult route = searchHorizontalRoute(start, goal, searchObstacles, profile.routeBounds(),
                    wheelGeneratedSign(candidate.blockState()), profile.expectedSwivelExtraCogSignForPositiveWheelCommand(), profile.turnPenalty());
            if (route == null) {
                rejected.add(new SkyforgeAircraftCockpitYawRouteIR.RejectedCandidate(candidate.name(), "no collision-free sign-correct horizontal route"));
                candidateIndex++;
                continue;
            }
            List<SkyforgeAircraftCockpitYawRouteIR.Placement> placements = routePlacements(
                    route.path(), wheel, candidate.blockState(), driveCog, profile.resources());
            Set<AircraftBlockspaceIR.LatticePoint> coords = new HashSet<>();
            for (SkyforgeAircraftCockpitYawRouteIR.Placement placement : placements) {
                if (!coords.add(placement.point())) throw new IllegalArgumentException("route materialization contains duplicate coordinate: " + placement.point());
                if (partition.effective().containsKey(placement.point())) {
                    throw new IllegalArgumentException("route materialization collides with current aircraft at " + placement.point());
                }
            }
            int pilotDistance = Math.abs(candidate.offsetFromPilotSeat().dx())
                    + Math.abs(candidate.offsetFromPilotSeat().dy()) + Math.abs(candidate.offsetFromPilotSeat().dz());
            int score = route.cost() + profile.pilotDistancePenalty() * pilotDistance + candidate.preferencePenalty();
            accepted.add(new AcceptedCandidate(score, pathKey(route.path()), candidate.name(), candidateIndex,
                    candidate, placements, route));
            candidateIndex++;
        }
        if (accepted.isEmpty()) throw new IllegalArgumentException("no cockpit-to-rudder route accepted; rejected=" + rejected);
        accepted.sort(Comparator.comparingInt(AcceptedCandidate::score)
                .thenComparing(AcceptedCandidate::pathKey)
                .thenComparing(AcceptedCandidate::name)
                .thenComparingInt(AcceptedCandidate::index));
        AcceptedCandidate selected = accepted.get(0);

        Set<AircraftBlockspaceIR.LatticePoint> routeCoords = new HashSet<>();
        selected.placements().forEach(value -> routeCoords.add(value.point()));
        if (!disjoint(routeCoords, partition.effective().keySet())) throw new IllegalArgumentException("selected route overlaps current aircraft placements");
        if (!disjoint(routeCoords, partition.propellerChild()) || !disjoint(routeCoords, partition.rudderChild()) || !disjoint(routeCoords, forbidden)) {
            throw new IllegalArgumentException("selected route crosses a forbidden child or air-gap boundary");
        }

        int priorMain = yaw.metrics().v0131MovingParentMainBodyPlacementCount();
        if (priorMain != partition.parentMain().size() + 1) {
            throw new IllegalArgumentException("v0.13.1 parent-main metric mismatch: metric=" + priorMain
                    + " reconstructed=" + (partition.parentMain().size() + 1));
        }
        int resultingMain = priorMain + selected.placements().size();
        int propellerCount = partition.propellerChild().size();
        int rudderCount = partition.rudderChild().size();
        int expectedPrimaryTransfer = resultingMain + propellerCount + rudderCount;

        Set<AircraftBlockspaceIR.LatticePoint> gluePoints = new HashSet<>(routeCoords);
        gluePoints.add(seat);
        SkyforgeAircraftCockpitYawRouteIR.GlueDomain routeGlue = glueDomain(gluePoints);
        int maxAllowed = Math.min(profile.maxGlueSelectionDimensionBlocks(), glue.encodingPolicy().maxSelectionDimensionBlocks());
        if (maxDimension(routeGlue.selectionSizeBlocks()) > maxAllowed) {
            throw new IllegalArgumentException("cockpit route glue domain exceeds accepted Create selection bound: " + routeGlue.selectionSizeBlocks());
        }
        if (containsAny(routeGlue, partition.propellerChild()) || containsAny(routeGlue, partition.rudderChild()) || containsAny(routeGlue, forbidden)) {
            throw new IllegalArgumentException("cockpit route glue domain crosses forbidden child or air-gap boundary");
        }

        int gearboxCount = (int) selected.placements().stream().filter(value -> "cockpit_yaw_route_gearbox".equals(value.kind())).count();
        int shaftCount = (int) selected.placements().stream().filter(value -> "cockpit_yaw_route_shaft".equals(value.kind())).count();
        int turnCount = (int) selected.placements().stream().filter(value -> value.role().startsWith("route_turn_gearbox_")).count();
        AircraftBlockspaceIR.LatticePoint wheelCoordinate = selected.placements().stream()
                .filter(value -> "cockpit_steering_wheel".equals(value.role())).findFirst().orElseThrow().point();
        int wheelSign = wheelGeneratedSign(selected.candidate().blockState());
        int tailCogSign = selected.route().swivelSign() * -1;

        List<SkyforgeAircraftCockpitYawRouteIR.RuntimeObligation> obligations = profile.runtimeObligations().stream()
                .map(value -> new SkyforgeAircraftCockpitYawRouteIR.RuntimeObligation(value.id(), value.method(), "unverified"))
                .toList();
        return new SkyforgeAircraftCockpitYawRouteIR(
                SkyforgeAircraftCockpitYawRouteIR.SCHEMA_VERSION,
                assetId,
                SkyforgeAircraftCockpitYawRouteIR.COMPILER_VERSION,
                pilot.sha256(),
                manifest.sha256(),
                fixture.sha256(),
                glue.sha256(),
                powertrain.sha256(),
                yaw.sha256(),
                steering.sha256(),
                profile.profileId(),
                seat,
                selected.name(),
                selected.score(),
                wheelCoordinate,
                selected.candidate().blockState(),
                driveCog,
                swivel,
                selected.route().path().get(0).y(),
                selected.route().path(),
                selected.placements(),
                routeGlue,
                rejected,
                new SkyforgeAircraftCockpitYawRouteIR.SignContract(
                        wheelSign,
                        tailCogSign,
                        selected.route().swivelSign(),
                        steering.expectedSteeringWheelRpmMagnitude(),
                        selected.route().trace(),
                        "Create 6.0.10 RotationPropagator.getAxisModifier source-face/output-direction equation plus final small-cog/Swivel mesh inversion",
                        profile.createSourceCommit()),
                new SkyforgeAircraftCockpitYawRouteIR.Metrics(
                        selected.placements().size(),
                        selected.route().path().size(),
                        turnCount,
                        gearboxCount,
                        shaftCount,
                        priorMain,
                        resultingMain,
                        propellerCount,
                        rudderCount,
                        expectedPrimaryTransfer),
                obligations,
                new SkyforgeAircraftCockpitYawRouteIR.Readiness(
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        List.of(
                                "cockpit_route_primary_sable_recapture_unverified",
                                "cockpit_route_network_sign_rpm_unverified",
                                "cockpit_route_command_return_unverified",
                                "post_route_mass_com_unverified",
                                "pilot_interaction_binding_unverified")),
                new SkyforgeAircraftCockpitYawRouteIR.Validation(
                        true,
                        "deterministic_collision_aware_sign_aware_discrete_create_kinetic_route_search_from_pilot_adjacent_steering_wheel_to_accepted_aft_swivel_interface",
                        List.of(
                                "that the route-modified aircraft recaptures as one Sable primary body",
                                "that the routed Create kinetic network propagates predicted sign/RPM in the exact stack",
                                "post-route runtime mass or center of mass",
                                "actual-client player interaction/network binding",
                                "seat occupancy, moving-player tracking, or cockpit ergonomics",
                                "pitch/roll or multi-axis control",
                                "stable flight, handling, or human flight feel")));
    }

    private static void requireAcceptedUpstream(
            SkyforgeAircraftPilotStationIR pilot,
            SkyforgeAircraftProbeManifestIR manifest,
            SkyforgeAircraftAssemblyFixtureIR fixture,
            SkyforgeAircraftGlueEncodingIR glue,
            SkyforgeAircraftPowertrainIR powertrain,
            SkyforgeAircraftYawControlIR yaw,
            SkyforgeAircraftSteeringYawSourceIR steering) {
        if (!pilot.validation().passed() || !pilot.topologyChecks().passed() || !pilot.readiness().pilotStationStaticPlacementPassed()) {
            throw new IllegalArgumentException("cockpit route requires accepted current pilot-station authority");
        }
        if (!manifest.validation().passed() || !manifest.validationChecks().passed() || !manifest.readiness().probePlacementManifestReady()) {
            throw new IllegalArgumentException("cockpit route requires accepted current probe manifest");
        }
        if (!fixture.validation().passed() || !fixture.topologyChecks().passed() || !fixture.readiness().assemblyFixtureTopologyPassed()) {
            throw new IllegalArgumentException("cockpit route requires accepted current assembly fixture");
        }
        if (!glue.validation().passed() || !glue.checks().passed() || !glue.readiness().adhesionApplicationEncodingReady()) {
            throw new IllegalArgumentException("cockpit route requires accepted current glue authority");
        }
        if (!powertrain.validation().passed() || !powertrain.topologyChecks().passed() || !powertrain.glueChecks().passed()) {
            throw new IllegalArgumentException("cockpit route requires accepted current powertrain authority");
        }
        if (!yaw.validation().passed() || !yaw.topologyChecks().passed() || !yaw.glueChecks().passed() || !yaw.countChecks().passed()) {
            throw new IllegalArgumentException("cockpit route requires accepted corrected yaw topology");
        }
        if (!steering.validation().passed() || !steering.topologyChecks().passed() || !steering.readiness().steeringWheelSourceStaticTopologyPassed()) {
            throw new IllegalArgumentException("cockpit route requires accepted Steering Wheel yaw-source authority");
        }
    }

    private static Partition reconstruct(
            SkyforgeAircraftProbeManifestIR manifest,
            SkyforgeAircraftAssemblyFixtureIR fixture,
            SkyforgeAircraftPowertrainIR powertrain,
            SkyforgeAircraftYawControlIR yaw) {
        Map<AircraftBlockspaceIR.LatticePoint, EffectivePlacement> effective = new LinkedHashMap<>();
        for (SkyforgeAircraftProbeManifestIR.Placement placement : manifest.placements()) {
            if (effective.put(placement.point(), new EffectivePlacement(placement.kind(), placement.resourceId(), placement.blockState())) != null) {
                throw new IllegalArgumentException("duplicate production manifest coordinate: " + placement.point());
            }
        }
        Set<AircraftBlockspaceIR.LatticePoint> propeller = new HashSet<>(fixture.nestedPropellerChild().coordinates());
        if (propeller.size() != fixture.nestedPropellerChild().placementCount()
                || propeller.size() != powertrain.metrics().nestedPropellerChildPlacementCount()) {
            throw new IllegalArgumentException("propeller-child membership/count mismatch");
        }
        for (SkyforgeAircraftPowertrainIR.Placement placement : powertrain.placements()) {
            switch (placement.mode()) {
                case REPLACE -> {
                    EffectivePlacement prior = effective.get(placement.point());
                    if (prior == null || placement.replaces() == null
                            || !placement.replaces().kind().equals(prior.kind())
                            || !placement.replaces().resourceId().equals(prior.resourceId())) {
                        throw new IllegalArgumentException("powertrain replacement source mismatch at " + placement.point());
                    }
                    effective.put(placement.point(), new EffectivePlacement("powertrain_main_body", placement.resourceId(), placement.blockState()));
                }
                case ADD -> {
                    if (effective.putIfAbsent(placement.point(), new EffectivePlacement("powertrain_main_body", placement.resourceId(), placement.blockState())) != null) {
                        throw new IllegalArgumentException("powertrain addition collision at " + placement.point());
                    }
                }
            }
        }
        Set<AircraftBlockspaceIR.LatticePoint> rudder = new HashSet<>();
        for (SkyforgeAircraftYawControlIR.Placement placement : yaw.placements()) {
            switch (placement.mode()) {
                case REMOVE_MAIN -> {
                    if (effective.remove(placement.point()) == null) throw new IllegalArgumentException("yaw removal source missing at " + placement.point());
                }
                case ADD_MAIN -> {
                    if (effective.putIfAbsent(placement.point(), new EffectivePlacement(placement.kind(), placement.resourceId(), placement.blockState())) != null) {
                        throw new IllegalArgumentException("yaw parent addition collision at " + placement.point());
                    }
                }
                case ADD_CONTROL_CHILD -> {
                    if (effective.putIfAbsent(placement.point(), new EffectivePlacement(placement.kind(), placement.resourceId(), placement.blockState())) != null) {
                        throw new IllegalArgumentException("yaw child addition collision at " + placement.point());
                    }
                    rudder.add(placement.point());
                }
            }
        }
        if (!rudder.equals(new HashSet<>(yaw.rudderChildCoordinates()))) throw new IllegalArgumentException("rudder child membership mismatch");
        for (AircraftBlockspaceIR.LatticePoint coordinate : propeller) {
            if (!effective.containsKey(coordinate)) throw new IllegalArgumentException("propeller child missing from effective topology: " + coordinate);
        }
        Set<AircraftBlockspaceIR.LatticePoint> parent = new HashSet<>(effective.keySet());
        parent.removeAll(propeller);
        parent.removeAll(rudder);
        return new Partition(Map.copyOf(effective), Set.copyOf(parent), Set.copyOf(propeller), Set.copyOf(rudder));
    }

    private static SearchResult searchHorizontalRoute(
            AircraftBlockspaceIR.LatticePoint start,
            AircraftBlockspaceIR.LatticePoint goal,
            Set<AircraftBlockspaceIR.LatticePoint> obstacles,
            SkyforgeAircraftCockpitYawRouteProfile.RouteBounds bounds,
            int wheelSign,
            int expectedSwivelSign,
            int turnPenalty) {
        if (start.y() != goal.y()) throw new IllegalArgumentException("cockpit route start/goal must share one horizontal service plane");
        if (obstacles.contains(start) || obstacles.contains(goal)) return null;
        Comparator<SearchNode> order = Comparator.comparingInt(SearchNode::cost)
                .thenComparing(SearchNode::pathKey)
                .thenComparing(node -> node.incoming().name())
                .thenComparingInt(SearchNode::sign);
        PriorityQueue<SearchNode> queue = new PriorityQueue<>(order);
        Map<StateKey, Integer> best = new HashMap<>();
        for (Direction first : HORIZONTAL) {
            AircraftBlockspaceIR.LatticePoint next = move(start, first);
            if (obstacles.contains(next) || !bounds.contains(next) || next.y() != start.y()) continue;
            int modifier = gearboxModifier(Direction.UP, first);
            int sign = wheelSign * modifier;
            List<AircraftBlockspaceIR.LatticePoint> path = List.of(start, next);
            List<SkyforgeAircraftCockpitYawRouteIR.SignStep> trace = List.of(new SkyforgeAircraftCockpitYawRouteIR.SignStep(
                    start, "start_gearbox", "up", first.name().toLowerCase(java.util.Locale.ROOT), modifier, sign));
            SearchNode node = new SearchNode(2, 0, path, pathKey(path), next, first, sign, trace);
            StateKey key = new StateKey(next, first, sign);
            if (node.cost() < best.getOrDefault(key, Integer.MAX_VALUE)) {
                best.put(key, node.cost());
                queue.add(node);
            }
        }
        while (!queue.isEmpty()) {
            SearchNode node = queue.remove();
            StateKey key = new StateKey(node.current(), node.incoming(), node.sign());
            if (node.cost() != best.getOrDefault(key, Integer.MAX_VALUE)) continue;
            if (node.current().equals(goal)) {
                Direction source = node.incoming().opposite();
                int terminalModifier = gearboxModifier(source, Direction.UP);
                int tailSign = node.sign() * terminalModifier;
                int swivelSign = tailSign * -1;
                if (swivelSign == expectedSwivelSign) {
                    List<SkyforgeAircraftCockpitYawRouteIR.SignStep> trace = new ArrayList<>(node.trace());
                    trace.add(new SkyforgeAircraftCockpitYawRouteIR.SignStep(goal, "terminal_gearbox",
                            source.name().toLowerCase(java.util.Locale.ROOT), "up", terminalModifier, tailSign));
                    trace.add(new SkyforgeAircraftCockpitYawRouteIR.SignStep(move(goal, Direction.UP), "small_cog_to_swivel_mesh",
                            "cog", "swivel_extra_cog", -1, swivelSign));
                    return new SearchResult(node.cost(), node.turns(), node.path(), swivelSign, List.copyOf(trace));
                }
                continue;
            }
            for (Direction outgoing : HORIZONTAL) {
                if (outgoing == node.incoming().opposite()) continue;
                AircraftBlockspaceIR.LatticePoint next = move(node.current(), outgoing);
                if (node.path().contains(next) || obstacles.contains(next) || !bounds.contains(next) || next.y() != start.y()) continue;
                boolean turned = outgoing.axis() != node.incoming().axis();
                int nextSign = node.sign();
                int nextTurns = node.turns();
                int extraCost = 1;
                List<SkyforgeAircraftCockpitYawRouteIR.SignStep> trace = new ArrayList<>(node.trace());
                if (turned) {
                    Direction source = node.incoming().opposite();
                    int modifier = gearboxModifier(source, outgoing);
                    nextSign *= modifier;
                    nextTurns++;
                    extraCost += turnPenalty;
                    trace.add(new SkyforgeAircraftCockpitYawRouteIR.SignStep(node.current(), "turn_gearbox",
                            source.name().toLowerCase(java.util.Locale.ROOT), outgoing.name().toLowerCase(java.util.Locale.ROOT), modifier, nextSign));
                }
                List<AircraftBlockspaceIR.LatticePoint> path = new ArrayList<>(node.path());
                path.add(next);
                SearchNode nextNode = new SearchNode(node.cost() + extraCost, nextTurns, List.copyOf(path), pathKey(path),
                        next, outgoing, nextSign, List.copyOf(trace));
                StateKey nextKey = new StateKey(next, outgoing, nextSign);
                if (nextNode.cost() < best.getOrDefault(nextKey, Integer.MAX_VALUE)) {
                    best.put(nextKey, nextNode.cost());
                    queue.add(nextNode);
                }
            }
        }
        return null;
    }

    static int gearboxModifier(Direction sourceDirection, Direction outputDirection) {
        if (outputDirection.axis() == sourceDirection.axis()) return outputDirection == sourceDirection ? 1 : -1;
        return outputDirection.axisDirection() == sourceDirection.axisDirection() ? -1 : 1;
    }

    private static int wheelGeneratedSign(Map<String, String> state) {
        String facing = state.get("facing");
        boolean floor = Boolean.parseBoolean(state.get("on_floor"));
        if (!Set.of("north", "south", "east", "west").contains(facing)) throw new IllegalArgumentException("unsupported Steering Wheel facing: " + facing);
        return ((Set.of("north", "west").contains(facing)) == floor) ? -1 : 1;
    }

    private static List<SkyforgeAircraftCockpitYawRouteIR.Placement> routePlacements(
            List<AircraftBlockspaceIR.LatticePoint> path,
            AircraftBlockspaceIR.LatticePoint wheel,
            Map<String, String> wheelState,
            AircraftBlockspaceIR.LatticePoint driveCog,
            Map<String, String> resources) {
        if (path.size() < 2) throw new IllegalArgumentException("route path must contain at least start and terminal gearbox");
        List<SkyforgeAircraftCockpitYawRouteIR.Placement> out = new ArrayList<>();
        out.add(placement("cockpit_yaw_control_source", wheel, resources.get("steeringWheel"), wheelState, "cockpit_steering_wheel"));
        Direction first = requireDirection(path.get(0), path.get(1));
        out.add(placement("cockpit_yaw_route_gearbox", path.get(0), resources.get("gearbox"),
                Map.of("axis", thirdAxis(Axis.Y, first.axis()).id()), "cockpit_drop_gearbox"));
        for (int i = 1; i < path.size() - 1; i++) {
            Direction previous = requireDirection(path.get(i - 1), path.get(i));
            Direction next = requireDirection(path.get(i), path.get(i + 1));
            if (previous.axis() == next.axis()) {
                out.add(placement("cockpit_yaw_route_shaft", path.get(i), resources.get("shaft"),
                        Map.of("axis", next.axis().id()), "route_shaft_%02d".formatted(i)));
            } else {
                out.add(placement("cockpit_yaw_route_gearbox", path.get(i), resources.get("gearbox"),
                        Map.of("axis", thirdAxis(previous.axis(), next.axis()).id()), "route_turn_gearbox_%02d".formatted(i)));
            }
        }
        Direction incoming = requireDirection(path.get(path.size() - 2), path.get(path.size() - 1));
        out.add(placement("cockpit_yaw_route_gearbox", path.get(path.size() - 1), resources.get("gearbox"),
                Map.of("axis", thirdAxis(incoming.axis(), Axis.Y).id()), "tail_rise_gearbox"));
        out.add(placement("cockpit_yaw_route_drive_cog", driveCog, resources.get("cogwheel"), Map.of("axis", "y"), "tail_swivel_drive_cog"));
        return List.copyOf(out);
    }

    private static SkyforgeAircraftCockpitYawRouteIR.GlueDomain glueDomain(Set<AircraftBlockspaceIR.LatticePoint> points) {
        int minX = points.stream().mapToInt(AircraftBlockspaceIR.LatticePoint::x).min().orElseThrow();
        int minY = points.stream().mapToInt(AircraftBlockspaceIR.LatticePoint::y).min().orElseThrow();
        int minZ = points.stream().mapToInt(AircraftBlockspaceIR.LatticePoint::z).min().orElseThrow();
        int maxX = points.stream().mapToInt(AircraftBlockspaceIR.LatticePoint::x).max().orElseThrow();
        int maxY = points.stream().mapToInt(AircraftBlockspaceIR.LatticePoint::y).max().orElseThrow();
        int maxZ = points.stream().mapToInt(AircraftBlockspaceIR.LatticePoint::z).max().orElseThrow();
        AircraftBlockspaceIR.LatticePoint min = point(minX, minY, minZ);
        AircraftBlockspaceIR.LatticePoint max = point(maxX, maxY, maxZ);
        AircraftBlockspaceIR.LatticePoint size = point(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        return new SkyforgeAircraftCockpitYawRouteIR.GlueDomain("cockpit_yaw_route", min, max, min, max, size);
    }

    private static boolean containsAny(SkyforgeAircraftCockpitYawRouteIR.GlueDomain domain, Set<AircraftBlockspaceIR.LatticePoint> points) {
        return points.stream().anyMatch(point -> contains(domain, point));
    }
    private static boolean contains(SkyforgeAircraftCockpitYawRouteIR.GlueDomain domain, AircraftBlockspaceIR.LatticePoint p) {
        return domain.boundsMin().x() <= p.x() && p.x() <= domain.boundsMax().x()
                && domain.boundsMin().y() <= p.y() && p.y() <= domain.boundsMax().y()
                && domain.boundsMin().z() <= p.z() && p.z() <= domain.boundsMax().z();
    }
    private static int maxDimension(AircraftBlockspaceIR.LatticePoint p) { return Math.max(p.x(), Math.max(p.y(), p.z())); }
    private static boolean disjoint(Set<AircraftBlockspaceIR.LatticePoint> a, Set<AircraftBlockspaceIR.LatticePoint> b) {
        return a.stream().noneMatch(b::contains);
    }
    private static String pathKey(List<AircraftBlockspaceIR.LatticePoint> path) {
        StringBuilder out = new StringBuilder();
        path.forEach(p -> out.append("%04d,%04d,%04d;".formatted(p.x(), p.y(), p.z())));
        return out.toString();
    }
    private static SkyforgeAircraftCockpitYawRouteIR.Placement placement(String kind, AircraftBlockspaceIR.LatticePoint point,
            String resource, Map<String, String> state, String role) {
        return new SkyforgeAircraftCockpitYawRouteIR.Placement(kind, point, resource, state, role);
    }
    private static AircraftBlockspaceIR.LatticePoint add(AircraftBlockspaceIR.LatticePoint p, SkyforgeAircraftCockpitYawRouteProfile.Offset o) {
        return point(p.x() + o.dx(), p.y() + o.dy(), p.z() + o.dz());
    }
    private static AircraftBlockspaceIR.LatticePoint move(AircraftBlockspaceIR.LatticePoint p, Direction d) {
        return point(p.x() + d.dx(), p.y() + d.dy(), p.z() + d.dz());
    }
    private static Direction directionBetween(AircraftBlockspaceIR.LatticePoint a, AircraftBlockspaceIR.LatticePoint b) {
        int dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        for (Direction d : Direction.values()) if (d.dx() == dx && d.dy() == dy && d.dz() == dz) return d;
        return null;
    }
    private static Direction requireDirection(AircraftBlockspaceIR.LatticePoint a, AircraftBlockspaceIR.LatticePoint b) {
        Direction d = directionBetween(a, b);
        if (d == null) throw new IllegalArgumentException("route coordinates are not face-adjacent: " + a + " -> " + b);
        return d;
    }
    private static Axis thirdAxis(Axis a, Axis b) {
        if (a == b) throw new IllegalArgumentException("gearbox turn requires distinct axes");
        for (Axis axis : Axis.values()) if (axis != a && axis != b) return axis;
        throw new IllegalStateException("no third axis");
    }
    private static void requireDigest(String edge, String actual, String expected) {
        if (!Objects.equals(actual, expected)) throw new IllegalArgumentException(edge + " digest mismatch: actual=" + actual + " expected=" + expected);
    }
    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) { return new AircraftBlockspaceIR.LatticePoint(x, y, z); }

    enum Axis { X("x"), Y("y"), Z("z"); private final String id; Axis(String id) { this.id = id; } String id() { return id; } }
    enum Direction {
        EAST(1, 0, 0, Axis.X, 1), WEST(-1, 0, 0, Axis.X, -1),
        SOUTH(0, 0, 1, Axis.Z, 1), NORTH(0, 0, -1, Axis.Z, -1),
        UP(0, 1, 0, Axis.Y, 1), DOWN(0, -1, 0, Axis.Y, -1);
        private final int dx, dy, dz, axisDirection; private final Axis axis;
        Direction(int dx, int dy, int dz, Axis axis, int axisDirection) { this.dx = dx; this.dy = dy; this.dz = dz; this.axis = axis; this.axisDirection = axisDirection; }
        int dx() { return dx; } int dy() { return dy; } int dz() { return dz; } Axis axis() { return axis; } int axisDirection() { return axisDirection; }
        Direction opposite() { return switch (this) { case EAST -> WEST; case WEST -> EAST; case SOUTH -> NORTH; case NORTH -> SOUTH; case UP -> DOWN; case DOWN -> UP; }; }
    }

    private record EffectivePlacement(String kind, String resourceId, Map<String, String> blockState) {
        EffectivePlacement { blockState = Map.copyOf(blockState); }
    }
    private record Partition(Map<AircraftBlockspaceIR.LatticePoint, EffectivePlacement> effective,
            Set<AircraftBlockspaceIR.LatticePoint> parentMain, Set<AircraftBlockspaceIR.LatticePoint> propellerChild,
            Set<AircraftBlockspaceIR.LatticePoint> rudderChild) {}
    private record StateKey(AircraftBlockspaceIR.LatticePoint current, Direction incoming, int sign) {}
    private record SearchNode(int cost, int turns, List<AircraftBlockspaceIR.LatticePoint> path, String pathKey,
            AircraftBlockspaceIR.LatticePoint current, Direction incoming, int sign,
            List<SkyforgeAircraftCockpitYawRouteIR.SignStep> trace) {}
    private record SearchResult(int cost, int turns, List<AircraftBlockspaceIR.LatticePoint> path, int swivelSign,
            List<SkyforgeAircraftCockpitYawRouteIR.SignStep> trace) {}
    private record AcceptedCandidate(int score, String pathKey, String name, int index,
            SkyforgeAircraftCockpitYawRouteProfile.WheelCandidate candidate,
            List<SkyforgeAircraftCockpitYawRouteIR.Placement> placements, SearchResult route) {}
}
