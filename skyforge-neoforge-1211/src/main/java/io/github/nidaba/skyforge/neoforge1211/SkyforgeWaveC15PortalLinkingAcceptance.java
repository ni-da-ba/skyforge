package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * C15 black-box acceptance for ordinary vanilla Nether portal linking and placement under the
 * accepted C10 1:1 coordinate-scale datapack.
 *
 * <p>The fixture calls the real vanilla {@link NetherPortalBlock#getPortalDestination} path at
 * non-origin coordinates while the retained Create/Sable/Aeronautics stack is loaded. It neither
 * patches portal code nor assumes assembled contraptions can traverse portals.
 */
final class SkyforgeWaveC15PortalLinkingAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.waveC15PortalLinking";
    private static final double EPSILON = 1.0e-12;
    private static final double EXISTING_LINK_MAX_HORIZONTAL_DISTANCE = 4.0;
    private static final double CREATED_PORTAL_MAX_HORIZONTAL_OFFSET = 24.0;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC15PortalLinkingAcceptance.class.getName());

    private static final BlockPos LINK_SOURCE_FRAME = new BlockPos(640, 80, -384);
    private static final BlockPos LINK_TARGET_FRAME = new BlockPos(640, 70, -384);
    private static final BlockPos VANILLA_EIGHT_TO_ONE_DISTRACTOR_FRAME = new BlockPos(80, 70, -48);
    private static final BlockPos CREATE_SOURCE_FRAME = new BlockPos(-704, 80, 448);
    private static final BlockPos CREATE_EXPECTED_TARGET = new BlockPos(-704, 70, 448);

    private SkyforgeWaveC15PortalLinkingAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC15PortalLinkingAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        ServerLevel nether = event.getServer().getLevel(Level.NETHER);
        if (nether == null) {
            fail("Nether ServerLevel was not present after server start");
            return;
        }

        double overworldScale = overworld.dimensionType().coordinateScale();
        double netherScale = nether.dimensionType().coordinateScale();
        if (Math.abs(overworldScale - 1.0) > EPSILON || Math.abs(netherScale - 1.0) > EPSILON) {
            fail("C10 live 1:1 scale regressed: overworld=" + overworldScale + " nether=" + netherScale);
            return;
        }

        BlockPos sourcePortal = buildPortal(overworld, LINK_SOURCE_FRAME);
        BlockPos sameCoordinateTarget = buildPortal(nether, LINK_TARGET_FRAME);
        BlockPos distractor = buildPortal(nether, VANILLA_EIGHT_TO_ONE_DISTRACTOR_FRAME);

        FakePlayer overworldPlayer = FakePlayerFactory.getMinecraft(overworld);
        placeInPortal(overworldPlayer, sourcePortal);
        DimensionTransition outbound = portalDestination(overworld, overworldPlayer, sourcePortal);

        if (outbound.newLevel() != nether) {
            fail("Overworld portal did not target the Nether");
            return;
        }

        double sameTargetDistance = horizontalDistance(outbound.pos(), Vec3.atCenterOf(sameCoordinateTarget));
        double distractorDistance = horizontalDistance(outbound.pos(), Vec3.atCenterOf(distractor));
        if (sameTargetDistance > EXISTING_LINK_MAX_HORIZONTAL_DISTANCE) {
            fail("1:1 outbound portal did not link to same-coordinate target; distance=" + sameTargetDistance
                    + " destination=" + outbound.pos());
            return;
        }
        if (distractorDistance <= sameTargetDistance) {
            fail("outbound portal preferred vanilla 8:1 distractor; sameDistance=" + sameTargetDistance
                    + " distractorDistance=" + distractorDistance);
            return;
        }

        FakePlayer netherPlayer = FakePlayerFactory.getMinecraft(nether);
        placeInPortal(netherPlayer, sameCoordinateTarget);
        DimensionTransition inbound = portalDestination(nether, netherPlayer, sameCoordinateTarget);
        if (inbound.newLevel() != overworld) {
            fail("Nether return portal did not target the Overworld");
            return;
        }

        double returnDistance = horizontalDistance(inbound.pos(), Vec3.atCenterOf(sourcePortal));
        if (returnDistance > EXISTING_LINK_MAX_HORIZONTAL_DISTANCE) {
            fail("1:1 return portal did not resolve to same-coordinate Overworld portal; distance="
                    + returnDistance + " destination=" + inbound.pos());
            return;
        }

        BlockPos createSourcePortal = buildPortal(overworld, CREATE_SOURCE_FRAME);
        var beforeCreate = nether.getPortalForcer().findClosestPortalPosition(
                CREATE_EXPECTED_TARGET, true, nether.getWorldBorder());
        if (beforeCreate.isPresent()) {
            fail("placement fixture unexpectedly found a pre-existing target portal at " + beforeCreate.get());
            return;
        }

        placeInPortal(overworldPlayer, createSourcePortal);
        DimensionTransition createdTransition =
                portalDestination(overworld, overworldPlayer, createSourcePortal);
        if (createdTransition.newLevel() != nether) {
            fail("missing-target portal creation did not target the Nether");
            return;
        }

        var createdPortal = nether.getPortalForcer().findClosestPortalPosition(
                CREATE_EXPECTED_TARGET, true, nether.getWorldBorder());
        if (createdPortal.isEmpty()) {
            fail("vanilla portal destination logic did not create a searchable Nether portal");
            return;
        }

        double createdOffset = horizontalDistance(
                Vec3.atCenterOf(createdPortal.get()), Vec3.atCenterOf(CREATE_EXPECTED_TARGET));
        double createdTransitionOffset =
                horizontalDistance(createdTransition.pos(), Vec3.atCenterOf(CREATE_EXPECTED_TARGET));
        if (createdOffset > CREATED_PORTAL_MAX_HORIZONTAL_OFFSET
                || createdTransitionOffset > CREATED_PORTAL_MAX_HORIZONTAL_OFFSET) {
            fail("created Nether portal was not placed near the live 1:1 target; portalOffset="
                    + createdOffset + " transitionOffset=" + createdTransitionOffset
                    + " portal=" + createdPortal.get() + " transition=" + createdTransition.pos());
            return;
        }

        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C15 PASS overworldScale="
                        + overworldScale
                        + " netherScale="
                        + netherScale
                        + " outboundSameDistance="
                        + sameTargetDistance
                        + " outboundDistractorDistance="
                        + distractorDistance
                        + " returnDistance="
                        + returnDistance
                        + " createdPortal="
                        + createdPortal.get()
                        + " createdOffset="
                        + createdOffset
                        + " createdTransitionOffset="
                        + createdTransitionOffset);
    }

    private static DimensionTransition portalDestination(
            ServerLevel sourceLevel, FakePlayer player, BlockPos portalPos) {
        DimensionTransition transition =
                ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(sourceLevel, player, portalPos);
        if (transition == null) {
            throw new IllegalStateException("vanilla Nether portal returned no dimension transition");
        }
        return transition;
    }

    private static void placeInPortal(FakePlayer player, BlockPos portalPos) {
        player.setPos(portalPos.getX() + 0.5, portalPos.getY() + 0.1, portalPos.getZ() + 0.5);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static BlockPos buildPortal(ServerLevel level, BlockPos frameMin) {
        level.getChunkAt(frameMin);

        for (int x = -2; x <= 5; x++) {
            for (int y = -2; y <= 6; y++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlockAndUpdate(frameMin.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }

        for (int x = 0; x <= 3; x++) {
            level.setBlockAndUpdate(frameMin.offset(x, 0, 0), Blocks.OBSIDIAN.defaultBlockState());
            level.setBlockAndUpdate(frameMin.offset(x, 4, 0), Blocks.OBSIDIAN.defaultBlockState());
        }
        for (int y = 1; y <= 3; y++) {
            level.setBlockAndUpdate(frameMin.offset(0, y, 0), Blocks.OBSIDIAN.defaultBlockState());
            level.setBlockAndUpdate(frameMin.offset(3, y, 0), Blocks.OBSIDIAN.defaultBlockState());
        }

        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X);
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                level.setBlockAndUpdate(frameMin.offset(x, y, 0), portal);
            }
        }

        return frameMin.offset(1, 1, 0);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C15 FAIL " + reason);
        throw new IllegalStateException("Wave C15 portal-linking acceptance failed: " + reason);
    }
}
