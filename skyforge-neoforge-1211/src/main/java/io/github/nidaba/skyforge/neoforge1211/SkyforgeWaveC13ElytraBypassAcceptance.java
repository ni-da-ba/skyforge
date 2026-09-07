package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Black-box C13 acceptance for vanilla-vs-suppressed Elytra firework propulsion.
 *
 * <p>The class intentionally knows nothing about the suppression mod API. The suppressed run differs
 * from baseline only by placing the exact pinned mod jar on the FML-discovered runtime source set.
 */
final class SkyforgeWaveC13ElytraBypassAcceptance {
    static final String MODE_PROPERTY = "skyforge.dev.waveC13ElytraAcceptance";
    private static final double BASELINE_MIN_BOOST_DELTA = 0.05;
    private static final double SUPPRESSED_MAX_BOOST_DELTA = 1.0e-9;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC13ElytraBypassAcceptance.class.getName());

    private enum Phase {
        IDLE,
        BOOST_SETTLE,
        ORDINARY_SETTLE,
        COMPLETE
    }

    private static String mode;
    private static ServerLevel level;
    private static FakePlayer player;
    private static Phase phase = Phase.IDLE;
    private static long phaseStartTick;
    private static FireworkRocketEntity boostRocket;
    private static int boostRocketCount;
    private static int ordinaryRocketCount;
    private static double boostDelta;
    private static boolean fallFlyingAfterAttempt;

    private SkyforgeWaveC13ElytraBypassAcceptance() {}

    static void installFromSystemProperty() {
        String configured = System.getProperty(MODE_PROPERTY, "").trim();
        if (configured.isEmpty()) {
            return;
        }
        if (!configured.equals("baseline") && !configured.equals("suppressed")) {
            throw new IllegalArgumentException("unknown Wave C13 acceptance mode: " + configured);
        }

        mode = configured;
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC13ElytraBypassAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC13ElytraBypassAcceptance::onServerTickPost);
        // Observe only entity joins that survived every higher-priority cancellation listener.
        // This captures the real rocket object without relying on a FakePlayer-created chunk ticket.
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                SkyforgeWaveC13ElytraBypassAcceptance::onEntityJoinLevel);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        // A real player would hold an entity-ticking chunk ticket. The headless FakePlayer does not,
        // so admit the test arena explicitly and capture spawned rockets through EntityJoinLevelEvent.
        level.getChunkAt(new BlockPos(0, 120, 0));
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

        player.setDeltaMovement(Vec3.ZERO);
        boostRocket = null;
        boostRocketCount = 0;
        phase = Phase.BOOST_SETTLE;
        phaseStartTick = level.getGameTime();

        ItemStack rocketStack = new ItemStack(Items.FIREWORK_ROCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, rocketStack);

        // Exercise the same server interaction route as an actual player's right click. Calling the
        // item method directly would bypass NeoForge interaction hooks where a server-side
        // compatibility mod is allowed to cancel or transform the action.
        var boostUseResult =
                player.gameMode.useItem(player, level, rocketStack, InteractionHand.MAIN_HAND);

        LOGGER.log(
                System.Logger.Level.INFO,
                "Wave C13 "
                        + mode
                        + " boost invocation fallFlying="
                        + player.isFallFlying()
                        + " held="
                        + player.getItemInHand(InteractionHand.MAIN_HAND)
                        + " result="
                        + boostUseResult
                        + " capturedRockets="
                        + boostRocketCount);

        if (mode.equals("baseline") && boostRocket == null) {
            fail("vanilla baseline interaction did not spawn an attached boost rocket");
            return;
        }

        // Tick the actual rocket emitted by vanilla. This avoids making the acceptance depend on
        // the FakePlayer's absent chunk ticket while still exercising the rocket implementation
        // (including any loaded compatibility transformation) rather than a synthetic velocity.
        if (boostRocket != null) {
            boostRocket.tick();
        }
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (level == null
                || event.getLevel() != level
                || !(event.getEntity() instanceof FireworkRocketEntity rocket)) {
            return;
        }

        if (phase == Phase.BOOST_SETTLE && boostRocket == null) {
            boostRocket = rocket;
            boostRocketCount++;
        } else if (phase == Phase.ORDINARY_SETTLE) {
            ordinaryRocketCount++;
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (phase == Phase.IDLE || phase == Phase.COMPLETE || level == null || player == null) {
            return;
        }

        long elapsed = level.getGameTime() - phaseStartTick;
        if (phase == Phase.BOOST_SETTLE && elapsed >= 1L) {
            evaluateBoostAndLaunchOrdinaryFirework();
        } else if (phase == Phase.ORDINARY_SETTLE && elapsed >= 1L) {
            evaluateOrdinaryFireworkAndPass();
        }
    }

    private static void evaluateBoostAndLaunchOrdinaryFirework() {
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

        if (boostRocket != null) {
            boostRocket.discard();
        }

        // Ordinary firework launch must remain available for signaling/celebration and other
        // non-propulsion vanilla uses. Exercise the real server block-use path while not fall-flying.
        player.stopFallFlying();
        BlockPos launchBlock = new BlockPos(0, 119, 0);
        level.setBlockAndUpdate(launchBlock, Blocks.STONE.defaultBlockState());

        ItemStack ordinaryRocket = new ItemStack(Items.FIREWORK_ROCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, ordinaryRocket);
        ordinaryRocketCount = 0;
        phase = Phase.ORDINARY_SETTLE;
        phaseStartTick = level.getGameTime();

        BlockHitResult hit = new BlockHitResult(
                new Vec3(0.5, 120.0, 0.5),
                Direction.UP,
                launchBlock,
                false);
        var ordinaryResult =
                player.gameMode.useItemOn(
                        player,
                        level,
                        ordinaryRocket,
                        InteractionHand.MAIN_HAND,
                        hit);

        LOGGER.log(
                System.Logger.Level.INFO,
                "Wave C13 "
                        + mode
                        + " ordinary firework result="
                        + ordinaryResult
                        + " capturedRockets="
                        + ordinaryRocketCount);
    }

    private static void evaluateOrdinaryFireworkAndPass() {
        if (ordinaryRocketCount <= 0) {
            fail("ordinary block-launched firework no longer spawns");
            return;
        }

        phase = Phase.COMPLETE;
        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C13_"
                        + mode.toUpperCase()
                        + " PASS boostDelta="
                        + boostDelta
                        + " boostRockets="
                        + boostRocketCount
                        + " fallFlying="
                        + fallFlyingAfterAttempt
                        + " ordinaryRockets="
                        + ordinaryRocketCount);
    }

    private static void fail(String reason) {
        phase = Phase.COMPLETE;
        String marker = "WAVE_C13_" + mode.toUpperCase() + " FAIL " + reason;
        LOGGER.log(System.Logger.Level.ERROR, marker);
        throw new IllegalStateException("Wave C13 Elytra acceptance failed: " + reason);
    }
}
