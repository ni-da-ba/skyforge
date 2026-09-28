package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-only player-facing atmosphere read-through.
 *
 * <p>The server samples the installed A4MC gameplay authority in the player's horizontal column,
 * using its trusted wind layer when the player is below it, then emits vanilla client-visible gust
 * particles and a rate-limited ambient wind cue. No client atmosphere state or second weather
 * authority is created.
 */
final class SkyforgeAtmospherePresentationDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.atmospherePresentation";
    static final String OUTPUT_PROPERTY = "skyforge.dev.atmospherePresentationOutput";
    private static final String A4MC_MOD_ID = "aerodynamics4mc";
    private static final long SAMPLE_PERIOD_TICKS = 10L;
    // A4MC's gameplay field becomes authoritative above its low-altitude transition. Keep the
    // player's horizontal column, while taking the visible cue from that trusted wind layer so a
    // grounded player can still perceive the air moving overhead.
    private static final double MINIMUM_TRUSTED_WIND_Y = 120.0;
    private static final int MIN_PROOF_SAMPLES = 6;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAtmospherePresentationDevRuntime.class.getName());

    private static final Map<UUID, Long> NEXT_AUDIO_TICK = new HashMap<>();
    private static boolean installed;
    private static SkyforgeAtmosphereView atmosphere;
    private static int trustedSamples;
    private static int activeCues;
    private static int visualEmissions;
    private static int audioEmissions;
    private static double maximumIntensity;
    private static String lastSourceLevel = "NONE";
    private static String lastAuthority = "NONE";
    private static boolean evidenceWritten;

    private SkyforgeAtmospherePresentationDevRuntime() {}

    static synchronized void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || installed) {
            return;
        }
        if (!ModList.get().isLoaded(A4MC_MOD_ID)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Atmosphere presentation requested but A4MC is absent; presentation remains inert.");
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeAtmospherePresentationDevRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeAtmospherePresentationDevRuntime::onServerTickPost);
        installed = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge atmosphere presentation read-through armed; A4MC binding deferred until server start.");
    }

    private static synchronized void onServerStarted(ServerStartedEvent event) {
        if (atmosphere != null) {
            return;
        }
        try {
            atmosphere = SkyforgeA4mcAtmosphereBridge.create();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "Atmosphere presentation could not bind the pinned A4MC gameplay API", failure);
        }
        NEXT_AUDIO_TICK.clear();
        trustedSamples = 0;
        activeCues = 0;
        visualEmissions = 0;
        audioEmissions = 0;
        maximumIntensity = 0.0;
        lastSourceLevel = "NONE";
        lastAuthority = "NONE";
        evidenceWritten = false;
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge atmosphere presentation read-through enabled from A4MC gameplay authority.");
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        SkyforgeAtmosphereView sampler = atmosphere;
        if (sampler == null) {
            return;
        }
        long gameTime = event.getServer().overworld().getGameTime();
        if (gameTime % SAMPLE_PERIOD_TICKS != 0L) {
            return;
        }

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            SkyforgeAtmosphereView.Sample sample = sampler.sample(level, presentationPosition(player));
            if (sample.trustedForGameplay()) {
                trustedSamples++;
                lastSourceLevel = sample.sourceLevel();
                lastAuthority = sample.authority();
            }
            SkyforgeAtmospherePresentationCue.Cue cue =
                    SkyforgeAtmospherePresentationCue.from(sample);
            if (!cue.active()) {
                writeEvidenceIfReady();
                continue;
            }
            activeCues++;
            maximumIntensity = Math.max(maximumIntensity, cue.intensity());
            if (emitVisualCue(level, player, cue)) {
                visualEmissions++;
            }
            if (emitAudioCue(player, gameTime, cue)) {
                audioEmissions++;
            }
            writeEvidenceIfReady();
        }
    }

    private static Vec3 presentationPosition(ServerPlayer player) {
        return new Vec3(
                player.getX(), Math.max(player.getY(), MINIMUM_TRUSTED_WIND_Y), player.getZ());
    }

    private static boolean emitVisualCue(
            ServerLevel level, ServerPlayer player, SkyforgeAtmospherePresentationCue.Cue cue) {
        Vec3 direction = new Vec3(cue.horizontalX(), cue.vertical(), cue.horizontalZ()).normalize();
        if (!direction.equals(Vec3.ZERO)) {
            Vec3 origin = player.getEyePosition().subtract(direction.scale(2.0)).add(0.0, -0.35, 0.0);
            level.sendParticles(
                    ParticleTypes.GUST,
                    origin.x,
                    origin.y,
                    origin.z,
                    cue.particleCount(),
                    direction.x * cue.particleSpeed(),
                    direction.y * cue.particleSpeed(),
                    direction.z * cue.particleSpeed(),
                    0.0);
            return true;
        }
        return false;
    }

    private static boolean emitAudioCue(
            ServerPlayer player, long gameTime, SkyforgeAtmospherePresentationCue.Cue cue) {
        UUID playerId = player.getUUID();
        long nextAudioTick = NEXT_AUDIO_TICK.getOrDefault(playerId, Long.MIN_VALUE);
        if (gameTime < nextAudioTick) {
            return false;
        }
        player.playNotifySound(
                SoundEvents.ELYTRA_FLYING,
                SoundSource.AMBIENT,
                cue.audioVolume(),
                cue.audioPitch());
        NEXT_AUDIO_TICK.put(playerId, gameTime + cue.audioCooldownTicks());
        return true;
    }

    private static void writeEvidenceIfReady() {
        if (evidenceWritten || trustedSamples < MIN_PROOF_SAMPLES) {
            return;
        }
        String configuredOutput = System.getProperty(OUTPUT_PROPERTY);
        if (configuredOutput == null || configuredOutput.isBlank()) {
            return;
        }

        boolean pass = activeCues > 0 && visualEmissions > 0 && audioEmissions > 0;
        Path output = Path.of(configuredOutput);
        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    output,
                    "{\n"
                            + "  \"status\": \"" + (pass ? "PASS" : "FAIL") + "\",\n"
                            + "  \"trusted_samples\": " + trustedSamples + ",\n"
                            + "  \"active_cues\": " + activeCues + ",\n"
                            + "  \"visual_emissions\": " + visualEmissions + ",\n"
                            + "  \"audio_emissions\": " + audioEmissions + ",\n"
                            + "  \"maximum_intensity\": " + maximumIntensity + ",\n"
                            + "  \"source_level\": \"" + lastSourceLevel + "\",\n"
                            + "  \"authority\": \"" + lastAuthority + "\"\n"
                            + "}\n",
                    StandardCharsets.UTF_8);
            evidenceWritten = true;
            LOGGER.log(
                    pass ? System.Logger.Level.INFO : System.Logger.Level.ERROR,
                    "SKYFORGE_ATMOSPHERE_PRESENTATION "
                            + (pass ? "PASS" : "FAIL")
                            + " trustedSamples="
                            + trustedSamples
                            + " activeCues="
                            + activeCues
                            + " visualEmissions="
                            + visualEmissions
                            + " audioEmissions="
                            + audioEmissions
                            + " source="
                            + lastSourceLevel
                            + " authority="
                            + lastAuthority);
        } catch (IOException | RuntimeException failure) {
            evidenceWritten = true;
            throw new IllegalStateException(
                    "atmosphere presentation evidence could not be written to " + output, failure);
        }
    }
}
