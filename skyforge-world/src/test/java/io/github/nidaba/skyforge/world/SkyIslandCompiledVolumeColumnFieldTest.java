package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import org.junit.jupiter.api.Test;

class SkyIslandCompiledVolumeColumnFieldTest {
    @Test
    void compiledColumnAdapterMatchesAuthoritativeTerrainInterpreterSurfaces() {
        SkyIslandVolumeDescriptor descriptor = SkyIslandVolumeDescriptor.schema2(
                0x534B59464F524745L,
                120.5,
                -40.25,
                256.0,
                128.0,
                72.0,
                104.0,
                32.0,
                0.43,
                0.62,
                0.57,
                0.18,
                SkyIslandMorphologyFamily.MASSIF,
                0.22,
                38.0,
                0.31);
        CompiledSkyIslandVolume compiled = new SemanticSkyIslandVolumeRecipe().compile(descriptor);
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(compiled);
        SkyIslandTerrainInterpreter interpreter =
                new SkyIslandTerrainInterpreter(compiled, SkyIslandTerrainProfile.defaults());

        for (SkyIslandLocalPosition local : new SkyIslandLocalPosition[] {
            new SkyIslandLocalPosition(0.0, 0.0),
            new SkyIslandLocalPosition(24.0, 18.0),
            new SkyIslandLocalPosition(-36.0, 10.0),
            new SkyIslandLocalPosition(52.0, -22.0)
        }) {
            SkyIslandVerticalColumn column = columns.columnAt(local).orElseThrow();
            double worldX = descriptor.centerX() + local.x();
            double worldZ = descriptor.centerZ() + local.z();

            assertEquals(interpreter.upperSurfaceHeight(worldX, worldZ), column.upperY(), 0.0);
            assertEquals(interpreter.undersideSurfaceHeight(worldX, worldZ), column.undersideY(), 0.0);
            assertTrue(column.thickness() > 0.0);
        }
    }

    @Test
    void compiledColumnSupportsExactEndpointAndInteriorRoundTrip() {
        SkyIslandVolumeDescriptor descriptor = SkyIslandVolumeDescriptor.schema2(
                77L,
                -320.0,
                95.0,
                300.0,
                96.0,
                58.0,
                88.0,
                24.0,
                0.81,
                0.48,
                0.63,
                -0.27,
                SkyIslandMorphologyFamily.LOBED,
                0.18,
                31.0,
                0.42);
        SkyIslandCompiledVolumeColumnField columns = new SkyIslandCompiledVolumeColumnField(
                new SemanticSkyIslandVolumeRecipe().compile(descriptor));
        SkyIslandSemanticDepthRealizationTransform transform =
                new SkyIslandSemanticDepthRealizationTransform(columns);

        for (SkyIslandLocalPosition horizontal : new SkyIslandLocalPosition[] {
            new SkyIslandLocalPosition(0.0, 0.0),
            new SkyIslandLocalPosition(20.0, -15.0),
            new SkyIslandLocalPosition(-26.0, 18.0)
        }) {
            for (double depth : new double[] {0.0, 0.25, 0.5, 0.75, 1.0}) {
                SkyIslandSubsurfacePosition semantic =
                        new SkyIslandSubsurfacePosition(horizontal, depth);
                SkyIslandRealizedSubsurfacePosition physical =
                        transform.toPhysical(semantic).orElseThrow();
                SkyIslandSubsurfacePosition recovered =
                        transform.toSemantic(physical).orElseThrow();

                assertEquals(depth, recovered.depthFraction(), 1.0e-12);
                assertEquals(horizontal, recovered.surfacePosition());
            }
        }
    }
}
