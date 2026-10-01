package io.github.nidaba.skyforge.recipes.skyisland.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nidaba.skyforge.kernel.graph.IntersectionNode;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import org.junit.jupiter.api.Test;

final class SkyIslandMorphologySpecCompilerTest {
    private final SkyIslandMorphologyProviderRegistry registry =
            SkyIslandMorphologyProviders.builtInRegistry();
    private final SkyIslandMorphologySpecCompiler compiler = new SkyIslandMorphologySpecCompiler();
    private final CanonicalGraphJson json = new CanonicalGraphJson();

    @Test
    void zeroEnrichmentSingleProviderPreservesExactProviderGeometryBytes() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L);
        var id = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.TABLELAND);
        CompiledSkyIslandVolume expected = registry.require(id).compilePrimary(descriptor).volume();
        CompiledSkyIslandVolume actual = compiler.compile(
                descriptor, new ProviderMorphologySpec(id, 0.0, 0.0), registry);
        assertGraphsEqual(expected, actual);
    }

    @Test
    void singleProviderFullEnrichmentIsNontrivialAndKeepsExactDensityIntersection() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0x534b59464f524745L);
        var id = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.SPINE);
        CompiledSkyIslandVolume primary = registry.require(id).compilePrimary(descriptor).volume();
        CompiledSkyIslandVolume enriched = compiler.compile(
                descriptor, ProviderMorphologySpec.full(id), registry);
        assertNotEquals(
                json.writeString(primary.upperSurfaceGraph()),
                json.writeString(enriched.upperSurfaceGraph()));
        assertIntersection(enriched);
    }

    @Test
    void pairwiseProviderBlendCompilesThroughAcceptedEnrichedHybridPath() {
        SkyIslandVolumeDescriptor descriptor = descriptor(Long.MIN_VALUE);
        var massif = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        var basin = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        MorphologyProviderBlend blend = new MorphologyProviderBlend(massif, basin, 0.35);
        CompiledSkyIslandVolume compiled = compiler.compile(
                descriptor, ProviderBlendMorphologySpec.full(blend), registry);
        assertIntersection(compiled);
    }

    @Test
    void unknownProviderFailsAtCompilationNotPlanning() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L);
        ProviderMorphologySpec missing = new ProviderMorphologySpec(
                new io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId("example", "missing"),
                1.0,
                1.0);
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(descriptor, missing, registry));
    }

    private static SkyIslandVolumeDescriptor descriptor(long seed) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                0.0,
                0.0,
                256.0,
                256.0,
                96.0,
                128.0,
                64.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                32.0);
    }

    private static void assertIntersection(CompiledSkyIslandVolume compiled) {
        IntersectionNode intersection = org.junit.jupiter.api.Assertions.assertInstanceOf(
                IntersectionNode.class,
                compiled.densityGraph().requireNode(compiled.densityGraph().output()));
        assertEquals("density.upper-constraint", intersection.left().value());
        assertEquals("density.lower-constraint", intersection.right().value());
    }

    private void assertGraphsEqual(CompiledSkyIslandVolume expected, CompiledSkyIslandVolume actual) {
        assertEquals(json.writeString(expected.upperSurfaceGraph()), json.writeString(actual.upperSurfaceGraph()));
        assertEquals(json.writeString(expected.undersideSurfaceGraph()), json.writeString(actual.undersideSurfaceGraph()));
        assertEquals(json.writeString(expected.densityGraph()), json.writeString(actual.densityGraph()));
    }
}
