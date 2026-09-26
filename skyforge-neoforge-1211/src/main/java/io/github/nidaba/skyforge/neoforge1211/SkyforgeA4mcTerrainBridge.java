package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * Optional public-API adapter exposing realized Skyforge terrain to Aerodynamics4MC L0/L1.
 *
 * <p>The adapter binds only when the A4MC public terrain-provider API is present. Stock A4MC
 * 0.2.1 therefore remains untouched. No A4MC runtime/internal class is reflected. The provider
 * claims only a uniquely owned Skyforge semantic top surface; all other X/Z columns decline and
 * remain under A4MC's ordinary worldgen terrain provider.
 */
final class SkyforgeA4mcTerrainBridge {
    static final String ENABLE_PROPERTY = "skyforge.dev.a4mcTerrainProvider";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeA4mcTerrainBridge.class.getName());
    private static final LongAdder QUERIES = new LongAdder();
    private static final LongAdder CLAIMS = new LongAdder();
    private static final LongAdder DECLINES = new LongAdder();
    private static final LongAdder AGGREGATE_NANOS = new LongAdder();
    private static final AtomicLong MAX_NANOS = new AtomicLong();
    private static volatile AutoCloseable registration;

    private SkyforgeA4mcTerrainBridge() {}

    static synchronized void installIfAvailable() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || registration != null) {
            return;
        }
        try {
            Class<?> terrainApi = Class.forName("com.aerodynamics4mc.api.AeroTerrainApi");
            Class<?> terrainProvider = Class.forName("com.aerodynamics4mc.api.AeroTerrainProvider");
            Class<?> terrainSample = Class.forName("com.aerodynamics4mc.api.AeroTerrainSample");
            Class<?> surfaceClass = Class.forName("com.aerodynamics4mc.api.AeroTerrainSurfaceClass");
            Class<?> worldRef = Class.forName("com.aerodynamics4mc.api.A4mcWorldRef");
            Class<?> a4mcId = Class.forName("com.aerodynamics4mc.api.A4mcId");

            Method worldPlatformHandle = worldRef.getMethod("platformHandle");
            Method availableSample = terrainSample.getMethod(
                    "available", float.class, float.class, float.class, surfaceClass);
            Field unavailableSample = terrainSample.getField("UNAVAILABLE");
            Method idOf = a4mcId.getMethod("of", String.class, String.class);
            Method registerProvider = terrainApi.getMethod(
                    "registerProvider", a4mcId, terrainProvider);

            Object provider = Proxy.newProxyInstance(
                    terrainProvider.getClassLoader(),
                    new Class<?>[] {terrainProvider},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> "SkyforgeA4mcTerrainProvider";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default -> throw new UnsupportedOperationException(method.toString());
                            };
                        }
                        if (!"sample".equals(method.getName()) || args == null || args.length != 3) {
                            throw new UnsupportedOperationException(
                                    "unexpected A4MC terrain-provider method: " + method);
                        }
                        Object world = args[0];
                        int blockX = ((Number) args[1]).intValue();
                        int blockZ = ((Number) args[2]).intValue();
                        Object handle = worldPlatformHandle.invoke(world);
                        if (!(handle instanceof ServerLevel level)) {
                            DECLINES.increment();
                            return unavailableSample.get(null);
                        }
                        long started = System.nanoTime();
                        QUERIES.increment();
                        try {
                            TerrainForcing forcing = sample(level, blockX, blockZ);
                            if (forcing == null) {
                                DECLINES.increment();
                                return unavailableSample.get(null);
                            }
                            CLAIMS.increment();
                            @SuppressWarnings({"rawtypes", "unchecked"})
                            Object surface = Enum.valueOf(
                                    (Class<? extends Enum>) surfaceClass.asSubclass(Enum.class),
                                    forcing.surfaceClass());
                            return availableSample.invoke(
                                    null,
                                    (float) forcing.height(),
                                    forcing.temperature(),
                                    forcing.roughnessMeters(),
                                    surface);
                        } finally {
                            long elapsed = Math.max(0L, System.nanoTime() - started);
                            AGGREGATE_NANOS.add(elapsed);
                            MAX_NANOS.accumulateAndGet(elapsed, Math::max);
                        }
                    });

            Object providerId = idOf.invoke(null, "skyforge", "semantic_terrain");
            registration = (AutoCloseable) registerProvider.invoke(null, providerId, provider);
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Registered Skyforge semantic terrain with public A4MC terrain-provider API");
        } catch (ClassNotFoundException missingPublicSeam) {
            // Expected with stock A4MC 0.2.1. The atmosphere remains entirely provider-owned.
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException(
                    "failed to bind Skyforge semantic terrain to public A4MC terrain API", failure);
        }
    }

    private static TerrainForcing sample(ServerLevel level, int blockX, int blockZ) {
        var semantic = SkyforgeAtmosphereTerrainAuthority.sample(
                blockX,
                blockZ,
                level.getMinBuildHeight(),
                level.getHeight());
        if (semantic.isEmpty()) {
            return null;
        }
        var surface = semantic.orElseThrow();
        Holder<Biome> biomeHolder = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(surface.biome())
                .orElseThrow(() -> new IllegalStateException(
                        "Skyforge atmosphere terrain biome absent from registry: "
                                + surface.biome().location()));
        float temperature = biomeHolder.value().getBaseTemperature();
        int seaLevel = level.getSeaLevel();

        String surfaceClass;
        float roughness;
        if (biomeHolder.is(BiomeTags.IS_OCEAN)
                || biomeHolder.is(BiomeTags.IS_RIVER)
                || surface.firstFreeHeight() < seaLevel - 1) {
            surfaceClass = "WATER";
            roughness = 0.0003f;
        } else if (temperature < 0.20f && surface.firstFreeHeight() > seaLevel + 4) {
            surfaceClass = "SNOW";
            roughness = 0.003f;
        } else if (biomeHolder.is(BiomeTags.IS_FOREST)
                || biomeHolder.is(BiomeTags.IS_JUNGLE)
                || biomeHolder.is(BiomeTags.IS_TAIGA)) {
            surfaceClass = "FOREST";
            roughness = 0.90f;
        } else if (biomeHolder.is(BiomeTags.IS_BADLANDS)
                || biomeHolder.is(BiomeTags.IS_MOUNTAIN)
                || surface.firstFreeHeight() > seaLevel + 72) {
            surfaceClass = "ROCK";
            roughness = 0.08f;
        } else {
            surfaceClass = "PLAINS";
            roughness = 0.05f;
        }
        return new TerrainForcing(
                surface.firstFreeHeight(),
                temperature,
                roughness,
                surfaceClass);
    }

    static Diagnostics diagnostics() {
        long queries = QUERIES.sum();
        long aggregate = AGGREGATE_NANOS.sum();
        return new Diagnostics(
                registration != null,
                queries,
                CLAIMS.sum(),
                DECLINES.sum(),
                aggregate,
                queries == 0 ? 0.0 : aggregate / (double) queries,
                MAX_NANOS.get());
    }

    record Diagnostics(
            boolean registered,
            long queries,
            long claims,
            long declines,
            long aggregateNanos,
            double meanNanos,
            long maxNanos) {}

    private record TerrainForcing(
            double height,
            float temperature,
            float roughnessMeters,
            String surfaceClass) {}
}
