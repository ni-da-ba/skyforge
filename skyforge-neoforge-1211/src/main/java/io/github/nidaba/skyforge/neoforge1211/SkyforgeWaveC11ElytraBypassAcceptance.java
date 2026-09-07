package io.github.nidaba.skyforge.neoforge1211;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Black-box C11 acceptance for vanilla-vs-suppressed Elytra firework propulsion.
 *
 * <p>The class intentionally knows nothing about the suppression mod API. The suppressed run differs
 * from baseline only by placing the exact pinned mod jar on the FML-discovered runtime source set.
 */
final class SkyforgeWaveC11ElytraBypassAcceptance {
    static final String MODE_PROPERTY = "skyforge.dev.waveC11ElytraAcceptance";
    private static final double BASELINE_MIN_BOOST_DELTA = 0.05;
    private static final double SUPPRESSED_MAX_BOOST_DELTA = 1.0e-9;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC11ElytraBypassAcceptance.class.getName());

    private SkyforgeWaveC11ElytraBypassAcceptance() {}

    static void installFromSystemProperty() {
        String mode = System.getProperty(MODE_PROPERTY, "").trim();
        if (mode.isEmpty()) {
            return;
        }
        if (!mode.equals("baseline") && !mode.equals("suppressed")) {
            throw new IllegalArgumentException("unknown Wave C11 acceptance mode: " + mode);
        }

        NeoForge.EVENT_BUS.addListener(
                (ServerStartedEvent event) -> run(event.getServer().overworld(), mode));
    }

    private static void run(ServerLevel level, String mode) {
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.setPos(0.0, 120.0, 0.0);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setOnGround(false);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        player.startFallFlying();

        if (!player.isFallFlying()) {
            fail(mode, "vanilla Elytra fall-flying state did not remain active");
            return;
        }

        AABB rocketArea = new AABB(-16.0, 96.0, -16.0, 16.0, 144.0, 16.0);
        discardRockets(level, rocketArea);

        player.setDeltaMovement(Vec3.ZERO);
        ItemStack boostRocket = new ItemStack(Items.FIREWORK_ROCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, boostRocket);
        var boostUseResult = Items.FIREWORK_ROCKET.use(level, player, InteractionHand.MAIN_HAND);

        LOGGER.log(
                System.Logger.Level.INFO,
                "Wave C11 "
                        + mode
                        + " boost invocation fallFlying="
                        + player.isFallFlying()
                        + " held="
                        + player.getItemInHand(InteractionHand.MAIN_HAND)
                        + " result="
                        + boostUseResult.getResult());

        List<FireworkRocketEntity> boostRockets =
                level.getEntitiesOfClass(FireworkRocketEntity.class, rocketArea);
        for (int tick = 0; tick < 5; tick++) {
            for (FireworkRocketEntity rocket : List.copyOf(boostRockets)) {
                if (!rocket.isRemoved()) {
                    rocket.tick();
                }
            }
        }

        double boostDelta = player.getDeltaMovement().length();
        int attachedCandidateCount = boostRockets.size();
        boolean fallFlyingAfterAttempt = player.isFallFlying();

        if (mode.equals("baseline")) {
            if (boostDelta < BASELINE_MIN_BOOST_DELTA) {
                fail(
                        mode,
                        "vanilla baseline did not produce material rocket propulsion; delta="
                                + boostDelta
                                + " rockets="
                                + attachedCandidateCount);
                return;
            }
        } else if (boostDelta > SUPPRESSED_MAX_BOOST_DELTA) {
            fail(
                    mode,
                    "suppressed run still produced Elytra rocket propulsion; delta="
                            + boostDelta
                            + " rockets="
                            + attachedCandidateCount);
            return;
        }

        if (!fallFlyingAfterAttempt) {
            fail(mode, "suppression disabled Elytra flight instead of only rocket propulsion");
            return;
        }

        discardRockets(level, rocketArea);

        // Ordinary firework launch must remain available for signaling/celebration/crossbow-adjacent
        // vanilla uses. Exercise the normal block-use path while the player is not fall-flying.
        player.stopFallFlying();
        BlockPos launchBlock = new BlockPos(0, 119, 0);
        level.setBlockAndUpdate(launchBlock, Blocks.STONE.defaultBlockState());

        ItemStack ordinaryRocket = new ItemStack(Items.FIREWORK_ROCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, ordinaryRocket);
        int ordinaryBefore = level.getEntitiesOfClass(FireworkRocketEntity.class, rocketArea).size();
        BlockHitResult hit = new BlockHitResult(
                new Vec3(0.5, 120.0, 0.5),
                Direction.UP,
                launchBlock,
                false);
        UseOnContext context =
                new UseOnContext(level, player, InteractionHand.MAIN_HAND, ordinaryRocket, hit);
        ordinaryRocket.useOn(context);
        int ordinaryAfter = level.getEntitiesOfClass(FireworkRocketEntity.class, rocketArea).size();

        if (ordinaryAfter <= ordinaryBefore) {
            fail(mode, "ordinary block-launched firework no longer spawns");
            return;
        }

        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C11_"
                        + mode.toUpperCase()
                        + " PASS boostDelta="
                        + boostDelta
                        + " boostRockets="
                        + attachedCandidateCount
                        + " fallFlying="
                        + fallFlyingAfterAttempt
                        + " ordinaryRockets="
                        + (ordinaryAfter - ordinaryBefore));
    }

    private static void discardRockets(ServerLevel level, AABB area) {
        for (FireworkRocketEntity rocket :
                level.getEntitiesOfClass(FireworkRocketEntity.class, area)) {
            rocket.discard();
        }
    }

    private static void fail(String mode, String reason) {
        String marker = "WAVE_C11_" + mode.toUpperCase() + " FAIL " + reason;
        LOGGER.log(System.Logger.Level.ERROR, marker);
        throw new IllegalStateException("Wave C11 Elytra acceptance failed: " + reason);
    }
}
