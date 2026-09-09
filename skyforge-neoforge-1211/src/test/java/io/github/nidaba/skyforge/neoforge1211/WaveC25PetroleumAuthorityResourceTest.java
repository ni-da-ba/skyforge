package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC25PetroleumAuthorityResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void immutableDieselGeneratorsRuntimeIsPinnedWithoutProductionDependency() throws IOException {
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c25-mods.properties"));
        assertTrue(pins.contains("create.version=6.0.10+mc1.21.1"));
        assertTrue(pins.contains("create.coordinate=maven.modrinth:LNytGWDc:UjX6dr61"));
        assertTrue(pins.contains("createdieselgenerators.version=1.21.1-1.3.15"));
        assertTrue(pins.contains("createdieselgenerators.coordinate=maven.modrinth:ZM3tt6p1:UoPH8lO1"));
        assertTrue(pins.contains(
                "createdieselgenerators.auditedSourceSnapshot=b1cf0c63c10e4c1c906c08b973628949e5d5c829"));

        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        assertTrue(build.contains("waveC25Runtime"));
        assertTrue(build.contains("waveC25Pin(\"createdieselgenerators\", \"coordinate\")"));
        assertFalse(build.contains("implementation(\"maven.modrinth:ZM3tt6p1:UoPH8lO1\")"));
    }

    @Test
    void suppressedControlLivesOnlyInDisposableC25World() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        assertTrue(build.contains("run-wave-c25-petroleum-suppressed-server"));
        assertTrue(build.contains(
                "wave-c25-suppressed/serverconfig/createdieselgenerators-server.toml"));
        assertTrue(build.contains("\"Disable normal oil chunks\" = true"));
        assertTrue(build.contains("\"Disable high oil chunks\" = true"));

        Path resources = PROJECT_DIRECTORY.resolve("src/main/resources");
        assertFalse(Files.exists(resources.resolve("createdieselgenerators-server.toml")));
        assertFalse(Files.exists(resources.resolve("config/createdieselgenerators-server.toml")));
    }

    @Test
    void fixtureUsesBlackBoxReflectionAndChecksRetainedAssets() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC25PetroleumAuthorityAcceptance.java"));

        assertTrue(source.contains("DISABLE_NORMAL_OIL_CHUNKS"));
        assertTrue(source.contains("DISABLE_HIGH_OIL_CHUNKS"));
        assertTrue(source.contains("OilChunksSavedData"));
        assertTrue(source.contains("getBaseOilAmount"));
        assertTrue(source.contains("createdieselgenerators\", \"pumpjack_hole"));
        assertTrue(source.contains("createdieselgenerators\", \"distillation_tank"));
        assertTrue(source.contains("createdieselgenerators\", \"diesel_engine"));
        assertTrue(source.contains("createdieselgenerators\", \"crude_oil"));
        assertFalse(source.contains("import com.jesz.createdieselgenerators"));
    }

    @Test
    void productionPumpjackRedirectRetainsCdgMechanicsButReplacesChunkOilAuthority() throws IOException {
        String mixinConfig = Files.readString(PROJECT_DIRECTORY.resolve("src/main/resources/skyforge.mixins.json"));
        assertTrue(mixinConfig.contains("SkyforgeDieselPumpjackOilAuthorityMixin")); 
        assertTrue(mixinConfig.contains("SkyforgeDieselNativeOilSuppressionMixin"));

        String mixin = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/"
                        + "SkyforgeDieselPumpjackOilAuthorityMixin.java"));
        assertTrue(mixin.contains(
                "com.jesz.createdieselgenerators.content.pumpjack.PumpjackHoleBlockEntity"));
        assertTrue(mixin.contains("getChunkOilAmount"));
        assertTrue(mixin.contains("setChunkOilAmount"));
        assertTrue(mixin.contains("SkyforgePetroleumPumpjackBridge.oilAmountForPumpjack"));
        assertTrue(mixin.contains("SkyforgePetroleumPumpjackBridge.setOilAmountForPumpjack"));
        assertFalse(mixin.contains("import com.jesz.createdieselgenerators"));

        String suppressionMixin = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/"
                        + "SkyforgeDieselNativeOilSuppressionMixin.java"));
        assertTrue(suppressionMixin.contains("OilChunksSavedData"));
        assertTrue(suppressionMixin.contains("getChunkOilAmount(Lnet/minecraft/world/level/ChunkPos;)I"));
        assertTrue(suppressionMixin.contains("cir.setReturnValue(0)"));
        assertFalse(suppressionMixin.contains("import com.jesz.createdieselgenerators"));

        String depositTag = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/resources/data/createdieselgenerators/tags/block/oil_deposit.json"));
        assertTrue(depositTag.contains("\"replace\": true"));
        assertTrue(depositTag.contains("skyforge:petroleum_source"));
        assertFalse(depositTag.contains("minecraft:bedrock"));
    }

    @Test
    void productionEntrypointKeepsC25FixtureOptIn() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211Mod.java"));
        assertTrue(source.contains(
                "SkyforgeWaveC25PetroleumAuthorityAcceptance.installFromSystemProperty();"));
    }
}
