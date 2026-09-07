package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Development-gated Wave C7 adapter: trusted A4MC vertical air raises the final Reliable Gliders
 * vertical result after the player's native glider tick has completed.
 */
final class SkyforgeWaveC7GliderLiftDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.waveC7GliderLift";
    static final String ACCEPTANCE_PROPERTY = "skyforge.dev.waveC7Acceptance";
    private static final String RELIABLE_GLIDERS_MOD_ID = "reliable_gliders";
    private static final String A4MC_MOD_ID = "aerodynamics4mc";
    private static final ResourceLocation GLIDER_ID =
            ResourceLocation.fromNamespaceAndPath(RELIABLE_GLIDERS_MOD_ID, "glider");
    private static final double EPSILON = 1.0e-9;

    @FunctionalInterface
    private interface LiftSampler {
        SkyforgeA4mcLiftBridge.Sample sample(ServerLevel level, Vec3 position);
    }

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC7GliderLiftDevRuntime.class.getName());

    private static boolean installed;
    private static boolean disabled;
    private static boolean acceptanceMode;
    private static SkyforgeA4mcLiftBridge atmosphere;
    private static SkyforgeReliableGlidersBridge gliders;
    private static LiftSampler liftSampler;

    private SkyforgeWaveC7GliderLiftDevRuntime() {}

    static synchronized void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || installed) {
            return;
        }
        if (!ModList.get().isLoaded(RELIABLE_GLIDERS_MOD_ID) || !ModList.get().isLoaded(A4MC_MOD_ID)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Wave C7 requested but Reliable Gliders/A4MC are absent; glider-lift bridge remains inert.");
            return;
        }

        acceptanceMode = Boolean.getBoolean(ACCEPTANCE_PROPERTY);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC7GliderLiftDevRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC7GliderLiftDevRuntime::onEntityTickPost);
        installed = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge Wave C7 glider-lift hooks armed; optional API binding deferred until server start.");
    }

    private static synchronized void onServerStarted(ServerStartedEvent event) {
        if (disabled || (atmosphere != null && gliders != null)) {
            return;
        }

        try {
            atmosphere = SkyforgeA4mcLiftBridge.create();
            gliders = SkyforgeReliableGlidersBridge.create();
            liftSampler = atmosphere::sample;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "Wave C7 optional API surface no longer matches pinned Reliable Gliders/A4MC stack",
                    failure);
        }

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge Wave C7 Reliable Gliders/A4MC shared-lift compatibility enabled.");

        if (acceptanceMode) {
            runAcceptance(event.getServer().overworld());
        }
    }

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        if (disabled
                || gliders == null
                || atmosphere == null
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        try {
            if (!gliders.isGliding(player)) {
                return;
            }
        } catch (ReflectiveOperationException failure) {
            disable(failure);
            return;
        }

        applyLift(player, sampleLift(level, player.position()));
    }

    private static void applyLift(ServerPlayer player, SkyforgeA4mcLiftBridge.Sample sample) {
        Vec3 current = player.getDeltaMovement();
        double newY = SkyforgeGliderLiftCoupling.apply(
                current.y, sample.trusted(), sample.updraftMetersPerSecond());

        if (Double.doubleToLongBits(newY) != Double.doubleToLongBits(current.y)) {
            player.setDeltaMovement(current.x, newY, current.z);
        }
    }

    private static SkyforgeA4mcLiftBridge.Sample sampleLift(ServerLevel level, Vec3 position) {
        LiftSampler sampler = liftSampler;
        return sampler == null
                ? SkyforgeA4mcLiftBridge.Sample.unavailable()
                : sampler.sample(level, position);
    }

    private static void runAcceptance(ServerLevel level) {
        try {
            if (!BuiltInRegistries.ITEM.containsKey(GLIDER_ID)) {
                failAcceptance("missing exact reliable_gliders:glider registry item");
                return;
            }

            FakePlayer player = FakePlayerFactory.getMinecraft(level);
            player.setPos(0.0, 120.0, 0.0);
            player.setOnGround(false);
            player.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    new ItemStack(BuiltInRegistries.ITEM.get(GLIDER_ID)));

            // Reliable Gliders itself must admit this equipped, airborne, descending player.
            player.setDeltaMovement(0.0, -0.05, 0.0);
            if (!gliders.isGliding(player)) {
                failAcceptance("Reliable Gliders did not classify equipped airborne FakePlayer as gliding");
                return;
            }

            // Case A: trusted 4 m/s thermal raises the completed stock baseline through the actual
            // EntityTickEvent.Post handler. target=+0.15, smoothed result=-0.01.
            liftSampler = (ignoredLevel, ignoredPosition) ->
                    new SkyforgeA4mcLiftBridge.Sample(true, 4.0);
            double baseline = player.getDeltaMovement().y;
            onEntityTickPost(new EntityTickEvent.Post(player));
            double lifted = player.getDeltaMovement().y;
            double expectedLifted = SkyforgeGliderLiftCoupling.apply(baseline, true, 4.0);
            requireNear("trusted thermal", lifted, expectedLifted);

            // Case B: existing stronger local/block updraft result wins; atmospheric target is not
            // added on top of it.
            player.setDeltaMovement(0.0, 0.70, 0.0);
            onEntityTickPost(new EntityTickEvent.Post(player));
            requireNear("stronger native updraft", player.getDeltaMovement().y, 0.70);

            // Case C: untrusted atmosphere cannot affect authoritative player motion.
            player.setDeltaMovement(0.0, -0.05, 0.0);
            liftSampler = (ignoredLevel, ignoredPosition) ->
                    new SkyforgeA4mcLiftBridge.Sample(false, 8.0);
            onEntityTickPost(new EntityTickEvent.Post(player));
            requireNear("untrusted atmosphere", player.getDeltaMovement().y, -0.05);

            // Case D: without an active glider, even trusted lift is irrelevant.
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setDeltaMovement(0.0, -0.05, 0.0);
            if (gliders.isGliding(player)) {
                failAcceptance("Reliable Gliders remained active after exact glider item was removed");
                return;
            }
            liftSampler = (ignoredLevel, ignoredPosition) ->
                    new SkyforgeA4mcLiftBridge.Sample(true, 8.0);
            onEntityTickPost(new EntityTickEvent.Post(player));
            requireNear("non-gliding player", player.getDeltaMovement().y, -0.05);

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "WAVE_C7_ACCEPTANCE PASS baseline="
                            + baseline
                            + " lifted="
                            + lifted
                            + " expected="
                            + expectedLifted
                            + " strongerNative=0.7 untrusted=-0.05 inactive=-0.05");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            failAcceptance("exception " + failure);
        } finally {
            liftSampler = atmosphere::sample;
        }
    }

    private static void requireNear(String label, double actual, double expected) {
        if (Math.abs(actual - expected) > EPSILON) {
            throw new IllegalStateException(
                    label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void failAcceptance(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C7_ACCEPTANCE FAIL " + reason);
        throw new IllegalStateException("Wave C7 acceptance failed: " + reason);
    }

    private static void disable(ReflectiveOperationException failure) {
        disabled = true;
        LOGGER.log(
                System.Logger.Level.WARNING,
                "Wave C7 glider compatibility failed closed; Reliable Gliders remains stock: " + failure);
    }
}
