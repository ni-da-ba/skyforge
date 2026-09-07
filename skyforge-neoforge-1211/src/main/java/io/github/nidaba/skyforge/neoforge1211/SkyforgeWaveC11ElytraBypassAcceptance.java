package io.github.nidaba.skyforge.neoforge1211;

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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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

    private enum Phase {
        IDLE,
        BOOST_SETTLE,
        ORDINARY_SETTLE,
        COMPLETE
    }

    private static String mode;
    private static ServerLevel level;
    private static FakePlayer player;
    private static AABB rocketArea;
    private static Phase phase = Phase.IDLE;
    private static long phaseStartTick;
    private static int boostRocketCount;
    private static double boostDelta;
    private static boolean fallFlyingAfterAttempt;
    private static int ordinaryBefore;

    private SkyforgeWaveC11ElytraBypassAcceptance() {}

    static void installFromSystemProperty() {
        String configured = System.getProperty(MODE_PROPERTY, "").trim();
        if (configured.isEmpty()) {
            return;
        }
        if (!configured.equals("baseline") && !configured.equals("suppressed")) {
            throw new IllegalArgumentException("unknown Wave C11 acceptance mode: " + configured);
        }

        mode = configured;
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC11ElytraBypassAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC11ElytraBypassAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        player = FakePlayerFactory.getMinecraft(level);
        player.setPos(0.0, 120.0, 0.0);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setOnGround(false);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        player.startFallFlying();

        if (!player.isFallFlying()) {
            fail("vanilla Elytra fall-flying state did not remain active");
            return;
        }

        rocketArea = new AABB(-16.0, 96.0, -16.0, 16.0, 144.0, 16.0);
        discardRockets();

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

        phase = Phase.BOOST_SETTLE;
        phaseStartTick = level.getGameTime();
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (phase == Phase.IDLE || phase == Phase.COMPLETE || level == null || player == null) {
            return;
        }

        long elapsed = level.getGameTime() - phaseStartTick;
        if (phase == Phase.BOOST_SETTLE && elapsed >= 5L) {
            evaluateBoostAndLaunchOrdinaryFirework();
        } else if (phase == Phase.ORDINARY_SETTLE && elapsed >= 2L) {
            evaluateOrdinaryFireworkAndPass();
        }
    }

    private static void evaluateBoostAndLaunchOrdinaryFirework() {
        boostRocketCount = level.getEntitiesOfClass(FireworkRocketEntity.class, rocketArea).size();
        boostDelta = player.getDeltaMovement().length();
        fallFlyingAfterAttempt = player.isFallFlying();

        if (mode.equals("baseline")) {
            if (boostDelta < BASELINE_MIN_BOOST_DELTA) {
                fail(
                        "vanilla baseline did not produce material rocket propulsion; delta="
                                + boostDelta
                                + " rockets="
                                + boostRocketCount);
                return;
            }
        } else if (boostDelta > SUPPRESSED_MAX_BOOST_DELTA) {
            fail(
                    "suppressed run still produced Elytra rocket propulsion; delta="
                            + boostDelta
                            + " rockets="
                            + boostRocketCount);
            return;
        }

        if (!fallFlyingAfterAttempt) {
            fail("suppression disabled Elytra flight instead of only rocket propulsion");
            return;
        }

        discardRockets();

        // Ordinary firework launch must remain available for signaling/celebration and other
        // non-propulsion vanilla uses. Exercise the block-use path while not fall-flying.
        player.stopFallFlying();
        BlockPos launchBlock = new BlockPos(0, 119, 0);
        level.setBlockAndUpdate(launchBlock, Blocks.STONE.defaultBlockState());

        ItemStack ordinaryRocket = new ItemStack(Items.FIREWORK_ROCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, ordinaryRocket);
        ordinaryBefore = level.getEntitiesOfClass(FireworkRocketEntity.class, rocketArea).size();
        BlockHitResult hit = new BlockHitResult(
                new Vec3(0.5, 120.0, 0.5),
                Direction.UP,
                launchBlock,
                false);
        UseOnContext context =
                new UseOnContext(level, player, InteractionHand.MAIN_HAND, ordinaryRocket, hit);
        ordinaryRocket.useOn(context);

        phase = Phase.ORDINARY_SETTLE;
        phaseStartTick = level.getGameTime();
    }

    private static void evaluateOrdinaryFireworkAndPass() {
        int ordinaryAfter = level.getEntitiesOfClass(FireworkRocketEntity.class, rocketArea).size();
        if (ordinaryAfter <= ordinaryBefore) {
            fail("ordinary block-launched firework no longer spawns");
            return;
        }

        phase = Phase.COMPLETE;
        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C11_"
                        + mode.toUpperCase()
                        + " PASS boostDelta="
                        + boostDelta
                        + " boostRockets="
                        + boostRocketCount
                        + " fallFlying="
                        + fallFlyingAfterAttempt
                        + " ordinaryRockets="
                        + (ordinaryAfter - ordinaryBefore));
    }

    private static void discardRockets() {
        for (FireworkRocketEntity rocket :
                level.getEntitiesOfClass(FireworkRocketEntity.class, rocketArea)) {
            rocket.discard();
        }
    }

    private static void fail(String reason) {
        phase = Phase.COMPLETE;
        String marker = "WAVE_C11_" + mode.toUpperCase() + " FAIL " + reason;
        LOGGER.log(System.Logger.Level.ERROR, marker);
        throw new IllegalStateException("Wave C11 Elytra acceptance failed: " + reason);
    }
}
