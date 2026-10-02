package io.github.nidaba.skyforge.neoforge1211;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.PlaybackMode;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteId;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RoutePoint;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.route.TransportMode;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RoutePlaybackService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteStorageService;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;

/**
 * SF-IMP-0084 Stage-2A validation-only proof.
 *
 * <p>This class deliberately lives outside the production source set. It proves that the exact
 * released AAL 0.6.2 API can construct a generated route from deterministic Skyforge-owned
 * identities without a ServerPlayer recording pass. It also compile-links the public route
 * storage/playback operations that the later real-ServerLevel fixture must execute.</p>
 */
public final class SkyforgeAalGeneratedRouteValidation {
    private static final UUID SKYFORGE_ROUTE_UUID =
            UUID.fromString("7f4274ef-3b69-5f27-ae5e-8e91528f843d");
    private static final UUID SKYFORGE_VEHICLE_UUID =
            UUID.fromString("9109cbee-a2e7-52fe-b741-49e463f70edb");
    private static final UUID ORIGIN_STOP_UUID =
            UUID.fromString("7be66265-55b5-54ca-b5d0-4d47a3c6c020");
    private static final UUID DESTINATION_STOP_UUID =
            UUID.fromString("c6061136-119a-5718-b53a-677b21d34474");

    private SkyforgeAalGeneratedRouteValidation() {
    }

    public static void main(String[] args) {
        Route first = generatedRoute();
        Route second = generatedRoute();

        require(first.equals(second), "deterministic regeneration changed the generated AAL route");
        require(first.id().equals(second.id()), "deterministic regeneration changed the AAL route id");
        require(first.id().value().equals(SKYFORGE_ROUTE_UUID), "AAL route id is not derived from the canonical Skyforge id");
        require(first.transportMode() == TransportMode.AIRSHIP, "generated route is not AIRSHIP transport");
        require(first.dimension().equals(Level.OVERWORLD), "generated route dimension changed");
        require(first.points().size() == 3, "generated route point count changed");
        require(first.stops().size() == 2, "generated route stop count changed");
        require(first.stops().get(0).id().equals(ORIGIN_STOP_UUID), "origin stop identity changed");
        require(first.stops().get(1).id().equals(DESTINATION_STOP_UUID), "destination stop identity changed");
        require(first.ownerId().isEmpty(), "validation route unexpectedly acquired an AAL owner identity");

        System.out.println("SF-IMP-0084 AAL GENERATED ROUTE PASS");
    }

    static Route generatedRoute() {
        RouteId routeId = new RouteId(SKYFORGE_ROUTE_UUID);
        VehicleControllerRef controller = new VehicleControllerRef(
                ResourceLocation.fromNamespaceAndPath("skyforge", "sf_imp_0084_fixture"),
                Level.OVERWORLD,
                Optional.of(SKYFORGE_VEHICLE_UUID),
                Optional.empty()
        );

        List<RoutePoint> points = List.of(
                RoutePoint.withoutYaw(new Vec3(0.5D, 160.0D, 0.5D), 0L, Level.OVERWORLD),
                RoutePoint.withYaw(new Vec3(32.5D, 168.0D, 16.5D), 35.0F, 80L, Level.OVERWORLD),
                RoutePoint.withYaw(new Vec3(64.5D, 160.0D, 32.5D), 90.0F, 160L, Level.OVERWORLD)
        );

        List<RouteStop> stops = List.of(
                new RouteStop(
                        ORIGIN_STOP_UUID,
                        "skyforge-origin",
                        0,
                        WaitCondition.none(),
                        Optional.of(new BlockPos(0, 160, 0)),
                        List.of()
                ),
                new RouteStop(
                        DESTINATION_STOP_UUID,
                        "skyforge-destination",
                        2,
                        WaitCondition.none(),
                        Optional.of(new BlockPos(64, 160, 32)),
                        List.of()
                )
        );

        return new Route(
                routeId,
                "skyforge-sf-imp-0084-generated-route",
                TransportMode.AIRSHIP,
                Level.OVERWORLD,
                points,
                controller,
                PlaybackMode.ONE_WAY,
                RouteStatus.RECORDED,
                stops,
                Optional.empty()
        );
    }

    /**
     * Compile-link contract for the exact released storage seam. The later headless runtime fixture
     * supplies a real ServerLevel/station and executes these calls.
     */
    static void storageContract(
            RouteStorageService storage,
            ServerLevel level,
            BlockPos stationPos,
            Route route
    ) {
        storage.saveRoute(level, stationPos, route);
        storage.loadRoute(level, stationPos, route.id());
        storage.deleteRoute(level, stationPos, route.id());
    }

    /**
     * Compile-link contract for the exact released playback seam. This is intentionally not called
     * by the pure generated-route proof because start/stop semantics require a real loaded station
     * and vehicle controller.
     */
    static void playbackContract(
            RoutePlaybackService playback,
            ServerLevel level,
            BlockPos stationPos,
            Route route,
            FailureReason stopReason
    ) {
        playback.startPlayback(level, stationPos, route);
        playback.stopPlayback(level, route.id(), stopReason);
        playback.tickPlayback(level);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
