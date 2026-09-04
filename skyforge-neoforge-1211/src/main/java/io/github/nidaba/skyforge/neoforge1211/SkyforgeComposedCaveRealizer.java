package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * Orders registry-native AIR carving before AUTH-0030 authored cave realization in one exact
 * Skyforge volume.
 *
 * <p>The contract is intentionally additive:
 *
 * <pre>
 * final cave AIR = native-carver result union authored AUTH-0030 result
 * </pre>
 *
 * <p>This coordinator does not reinterpret either cave source. Native configured carvers execute
 * through the accepted SF-IMP-0061 runner. Authored topology executes through the accepted
 * SF-IMP-0066 realizer. Running the authored pass last guarantees that AUTH-0030 remains a required
 * subset of final cave topology without refilling or suppressing native AIR outside that field.
 */
final class SkyforgeComposedCaveRealizer {
    private SkyforgeComposedCaveRealizer() {}

    static Result realize(
            ServerLevel level,
            NoiseBasedChunkGenerator generator,
            SkyforgeExactVolumeBiomeResolver biomeResolver,
            SkyIslandWorldVolume volume,
            SkyIslandExteriorConnectedCaveVolumeField authoredField,
            LevelChunk chunk,
            BlockPos biomeSample,
            int nativeTargetMinimumY,
            int nativeTargetMaximumY) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(biomeResolver, "biomeResolver");
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(authoredField, "authoredField");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(biomeSample, "biomeSample");

        var nativeResult = SkyforgeNativeCarverRunner.carveAir(
                level,
                generator,
                biomeResolver,
                volume.id(),
                chunk,
                biomeSample,
                nativeTargetMinimumY,
                nativeTargetMaximumY);

        var authoredResult = SkyforgeExteriorConnectedCaveRealizer.realize(
                level,
                volume,
                authoredField,
                chunk);
        if (!authoredResult.accepted()) {
            throw new IllegalStateException(
                    "AUTH-0030 authored pass rejected after native carving: unsafePositiveSamples="
                            + authoredResult.unsafePositiveSamples()
                            + ", firstUnsafe=" + authoredResult.firstUnsafePosition());
        }

        return new Result(nativeResult, authoredResult);
    }

    record Result(
            SkyforgeNativeCarverRunner.Result nativeResult,
            SkyforgeExteriorConnectedCaveRealizer.Result authoredResult) {
        Result {
            Objects.requireNonNull(nativeResult, "nativeResult");
            Objects.requireNonNull(authoredResult, "authoredResult");
        }
    }
}
