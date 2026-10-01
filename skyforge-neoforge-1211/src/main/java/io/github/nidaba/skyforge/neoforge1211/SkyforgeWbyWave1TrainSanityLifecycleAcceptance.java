package io.github.nidaba.skyforge.neoforge1211;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * WBY-INT-0002 train coexistence fixture.
 *
 * <p>Builds a minimal real Create train through StationBlockEntity.assemble while the ordinary
 * WBY distant-Sable fixture remains active. The train stays near the observer; the Sable craft
 * remains beyond vanilla render distance through DH/SSRD.
 */
final class SkyforgeWbyWave1TrainSanityLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1TrainSanity";

    private static final ResourceLocation TRACK_ID =
            ResourceLocation.fromNamespaceAndPath("create", "track");
    private static final ResourceLocation STATION_ID =
            ResourceLocation.fromNamespaceAndPath("create", "track_station");
    private static final ResourceLocation BOGEY_ID =
            ResourceLocation.fromNamespaceAndPath("create", "small_bogey");
    private static final ResourceLocation CONTROLS_ID =
            ResourceLocation.fromNamespaceAndPath("create", "controls");

    private static final long SETUP_TIMEOUT_TICKS = 180L;
    private static ServerLevel level;
    private static StationBlockEntity station;
    private static BlockPos stationPos;
    private static BlockPos targetTrackPos;
    private static BlockPos bogeyPos;
    private static UUID trainId;
    private static UUID carriageEntityId;
    private static Set<UUID> trainIdsBefore = Set.of();
    private static long firstPlayerTick = Long.MIN_VALUE;
    private static long lastAssemblyAttemptTick = Long.MIN_VALUE;
    private static boolean fixturePlaced;
    private static boolean fixtureReady;
    private static boolean clientComplete;

    record Snapshot(UUID trainId, UUID carriageEntityId, BlockPos targetTrackPos) {}

    private SkyforgeWbyWave1TrainSanityLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1TrainSanityLifecycleAcceptance::onServerTickPost);
    }

    static boolean fixtureReady() {
        return fixtureReady;
    }

    static Snapshot snapshot() {
        return fixtureReady && trainId != null
                ? new Snapshot(trainId, carriageEntityId, targetTrackPos)
                : null;
    }

    static void markClientComplete() {
        clientComplete = true;
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (clientComplete || fixtureReady) {
            return;
        }
        if (level == null) {
            level = event.getServer().overworld();
        }
        if (event.getServer().getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        long now = level.getGameTime();
        if (firstPlayerTick == Long.MIN_VALUE) {
            firstPlayerTick = now;
        }
        if (now - firstPlayerTick > SETUP_TIMEOUT_TICKS) {
            fail(event, "Create train fixture did not assemble within " + SETUP_TIMEOUT_TICKS
                    + " ticks; station=" + stationPos
                    + "; edgePoint=" + (station == null ? null : station.edgePoint.getEdgePoint())
                    + "; trainIds=" + Create.RAILWAYS.trains.keySet()
                    + "; lastException=" + stationException());
            return;
        }

        try {
            requireRuntimePreconditions();
            if (!fixturePlaced) {
                placeFixture(event);
                return;
            }

            if (station == null) {
                BlockEntity blockEntity = level.getBlockEntity(stationPos);
                if (!(blockEntity instanceof StationBlockEntity stationBlockEntity)) {
                    return;
                }
                station = stationBlockEntity;
                configureStationTarget(station);
            }

            // Track graphs are rebuilt asynchronously after the straight track is placed. Keep
            // asking the public behaviour to resolve its graph edge until Create owns it.
            if (station.edgePoint.getEdgePoint() == null) {
                station.edgePoint.createEdgePoint();
                if (station.edgePoint.getEdgePoint() == null) {
                    return;
                }
            }

            if (!station.isAssembling() && !station.enterAssemblyMode(null)) {
                return;
            }

            Set<UUID> created = new LinkedHashSet<>(Create.RAILWAYS.trains.keySet());
            created.removeAll(trainIdsBefore);
            if (created.isEmpty() && (lastAssemblyAttemptTick == Long.MIN_VALUE
                    || now - lastAssemblyAttemptTick >= 5L)) {
                lastAssemblyAttemptTick = now;
                station.assemble(event.getServer().getPlayerList().getPlayers().getFirst().getUUID());
                created = new LinkedHashSet<>(Create.RAILWAYS.trains.keySet());
                created.removeAll(trainIdsBefore);
            }

            if (created.size() > 1) {
                fail(event, "train sanity fixture created multiple trains: " + created);
                return;
            }
            if (created.size() != 1) {
                return;
            }

            trainId = created.iterator().next();
            CarriageContraptionEntity carriage = findCarriageEntity(trainId);
            if (carriage == null) {
                return;
            }
            carriageEntityId = carriage.getUUID();
            fixtureReady = true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail(event, "WBY Wave 1 train fixture failed: " + failure);
        }
    }

    private static void placeFixture(ServerTickEvent.Post event) throws ReflectiveOperationException {
        var viewer = event.getServer().getPlayerList().getPlayers().getFirst();
        BlockPos viewerPos = viewer.blockPosition();
        int y = viewerPos.getY() + 4;
        int z = viewerPos.getZ();
        int startX = viewerPos.getX() + 8;

        targetTrackPos = new BlockPos(startX, y, z);
        stationPos = targetTrackPos.offset(0, 0, -2);
        bogeyPos = targetTrackPos.offset(1, 1, 0);

        // Clear a compact local fixture volume and provide deterministic support.
        for (int x = startX - 3; x <= startX + 26; x++) {
            for (int yy = y; yy <= y + 4; yy++) {
                for (int zz = z - 3; zz <= z + 3; zz++) {
                    level.setBlock(new BlockPos(x, yy, zz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
            for (int zz = z - 3; zz <= z + 3; zz++) {
                level.setBlock(new BlockPos(x, y - 1, zz), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        Block trackBlock = requireBlock(TRACK_ID);
        if (!(trackBlock instanceof TrackBlock)) {
            throw new IllegalStateException("create:track did not resolve to TrackBlock");
        }
        BlockState trackState = trackBlock.defaultBlockState()
                .setValue(TrackBlock.SHAPE, TrackShape.XO)
                .setValue(TrackBlock.HAS_BE, false);
        for (int x = startX - 2; x <= startX + 24; x++) {
            if (!level.setBlock(new BlockPos(x, y, z), trackState, 3)) {
                throw new IllegalStateException("failed to place Create straight track at x=" + x);
            }
        }

        if (!level.setBlock(stationPos, requireBlock(STATION_ID).defaultBlockState(), 3)) {
            throw new IllegalStateException("failed to place Create train station");
        }

        BlockState bogeyState = requireBlock(BOGEY_ID).defaultBlockState();
        if (!level.setBlock(bogeyPos, bogeyState, 3)) {
            throw new IllegalStateException("failed to place Create small bogey");
        }

        BlockState controlsState = requireBlock(CONTROLS_ID).defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
                .setValue(ControlsBlock.OPEN, false)
                .setValue(ControlsBlock.VIRTUAL, false);
        BlockPos controlsPos = bogeyPos.above();
        if (!level.setBlock(controlsPos, controlsState, 3)) {
            throw new IllegalStateException("failed to place Create train controls");
        }
        addFixtureGlue(bogeyPos, controlsPos);

        trainIdsBefore = new LinkedHashSet<>(Create.RAILWAYS.trains.keySet());
        fixturePlaced = true;
    }

    private static void configureStationTarget(StationBlockEntity stationBlockEntity) {
        CompoundTag tag = new CompoundTag();
        tag.put("TargetTrack", NbtUtils.writeBlockPos(targetTrackPos.subtract(stationPos)));
        tag.putBoolean("TargetDirection", true);
        tag.putBoolean("Ortho", true);
        stationBlockEntity.edgePoint.read(tag, level.registryAccess(), false);
        stationBlockEntity.notifyUpdate();
    }

    private static CarriageContraptionEntity findCarriageEntity(UUID expectedTrainId) {
        AABB search = new AABB(targetTrackPos).inflate(64.0);
        for (CarriageContraptionEntity entity :
                level.getEntitiesOfClass(CarriageContraptionEntity.class, search)) {
            if (expectedTrainId.equals(entity.trainId) && entity.isAlive()) {
                return entity;
            }
        }
        return null;
    }

    private static void addFixtureGlue(BlockPos from, BlockPos to) throws ReflectiveOperationException {
        Class<?> glueClass = Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        AABB box = (AABB) glueClass.getMethod("span", BlockPos.class, BlockPos.class).invoke(null, from, to);
        Constructor<?> constructor = glueClass.getConstructor(Level.class, AABB.class);
        Entity glue = (Entity) constructor.newInstance(level, box);
        if (!level.addFreshEntity(glue)) {
            throw new IllegalStateException("failed to add train fixture Super Glue");
        }
    }

    private static void requireRuntimePreconditions() {
        for (String modId : java.util.List.of("create", "sable", "aeronautics", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY train-sanity server mod not loaded: " + modId);
            }
        }
        requireBlock(TRACK_ID);
        requireBlock(STATION_ID);
        requireBlock(BOGEY_ID);
        requireBlock(CONTROLS_ID);
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required train-sanity block not registered: " + id);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static Object stationException() {
        if (station == null) {
            return null;
        }
        try {
            Field field = StationBlockEntity.class.getDeclaredField("lastException");
            field.setAccessible(true);
            return field.get(station);
        } catch (ReflectiveOperationException ignored) {
            return "<unavailable>";
        }
    }

    private static void fail(ServerTickEvent.Post event, String reason) {
        clientComplete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(event.getServer(), reason);
    }
}
