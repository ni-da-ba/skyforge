package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandWorldCatalogSupportBundleTest {
    private static final long ROOT_SEED = 0x534b594641303532L;
    private static final long AUTHORED_WORLD = 0x4155544830303532L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void supportAwareCompilePreservesExactWorldCatalogAndAttachesProof() {
        var plan = new SkyIslandArchipelagoPlanner().plan(
                request(
                        direct(MorphologyFamily.MASSIF),
                        direct(MorphologyFamily.SPINE),
                        360.0));
        var compiler = new SkyIslandWorldCatalogCompiler();
        var registry = SkyIslandMorphologyProviders.builtInRegistry();

        SkyIslandWorldCatalog ordinary =
                compiler.compile(plan, registry, ADEQUATE_VERTICAL);
        SkyIslandWorldCatalogSupportBundle supported =
                compiler.compileWithSupport(plan, registry, ADEQUATE_VERTICAL);

        assertEquals(ordinary.rootSeed(), supported.catalog().rootSeed());
        assertEquals(ordinary.volumes(), supported.catalog().volumes());
        assertEquals(2, supported.certifiedCount());
        assertEquals(0, supported.uncertifiedCount());
        for (SkyIslandWorldVolume volume : supported.catalog().volumes()) {
            SkyIslandWorldVolumeSupportCertificate certificate =
                    supported.certificateFor(volume).orElseThrow();
            assertTrue(volume.bounds().contains(certificate.supportBounds()));
            assertTrue(certificate.queryBoundsContainSupport());
            assertFalse(volume.bounds().equals(certificate.supportBounds()));
        }
    }

    @Test
    void undersizedLegacyReservationIsRejectedBySupportAwareCompileOnly() {
        var plan = new SkyIslandArchipelagoPlanner().plan(
                request(
                        direct(MorphologyFamily.MASSIF),
                        direct(MorphologyFamily.SPINE),
                        256.0));
        var compiler = new SkyIslandWorldCatalogCompiler();
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var legacyVertical = new SkyIslandWorldVerticalReservation(180.0, 140.0);

        SkyIslandWorldCatalog ordinary =
                compiler.compile(plan, registry, legacyVertical);
        assertEquals(2, ordinary.volumeCount());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compileWithSupport(
                        plan, registry, legacyVertical));
    }

    @Test
    void endpointBlendIsCertifiedWhileInteriorBlendRemainsExplicitlyAbsent() {
        var massif =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        var basin =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);

        SkyIslandMorphologySpec endpoint =
                ProviderBlendMorphologySpec.full(
                        new MorphologyProviderBlend(massif, basin, 1.0));
        SkyIslandMorphologySpec interior =
                ProviderBlendMorphologySpec.full(
                        new MorphologyProviderBlend(massif, basin, 0.35));

        var compiler = new SkyIslandWorldCatalogCompiler();
        var registry = SkyIslandMorphologyProviders.builtInRegistry();

        SkyIslandWorldCatalogSupportBundle endpointBundle =
                compiler.compileWithSupport(
                        new SkyIslandArchipelagoPlanner().plan(
                                request(endpoint, direct(MorphologyFamily.TABLELAND), 360.0)),
                        registry,
                        ADEQUATE_VERTICAL);
        assertEquals(2, endpointBundle.certifiedCount());

        SkyIslandWorldCatalogSupportBundle interiorBundle =
                compiler.compileWithSupport(
                        new SkyIslandArchipelagoPlanner().plan(
                                request(interior, direct(MorphologyFamily.TABLELAND), 360.0)),
                        registry,
                        ADEQUATE_VERTICAL);
        assertEquals(1, interiorBundle.certifiedCount());
        assertEquals(1, interiorBundle.uncertifiedCount());
        assertTrue(
                interiorBundle.certificateFor(
                                interiorBundle.catalog().volumes().get(0))
                        .isEmpty());
        assertTrue(
                interiorBundle.certificateFor(
                                interiorBundle.catalog().volumes().get(1))
                        .isPresent());
    }

    @Test
    void worldSupportBridgesOnlyThroughExplicitAuth0046Associations() {
        var plan = new SkyIslandArchipelagoPlanner().plan(
                request(
                        direct(MorphologyFamily.TABLELAND),
                        direct(MorphologyFamily.LOBED),
                        360.0));
        SkyIslandWorldCatalogSupportBundle worldSupport =
                new SkyIslandWorldCatalogCompiler()
                        .compileWithSupport(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry(),
                                ADEQUATE_VERTICAL);

        ArrayList<SkyIslandAuthoredRealizationAssociation> associations =
                new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : worldSupport.catalog().volumes()) {
            associations.add(
                    SkyIslandAuthoredRealizationAssociation.of(
                            authoredDescriptor(volume, ordinal),
                            volume));
            ordinal++;
        }
        SkyIslandAuthoredRealizationCatalog authoredCatalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        ROOT_SEED,
                        associations);

        SkyIslandAuthoredRealizationSupportCatalog support =
                SkyIslandAuthoredRealizationSupportCatalog.fromWorldSupport(
                        authoredCatalog, worldSupport);

        assertEquals(2, support.certifiedCount());
        assertEquals(0, support.uncertifiedCount());
        for (SkyIslandAuthoredRealizationAssociation association :
                authoredCatalog.associations()) {
            assertEquals(
                    worldSupport
                            .certificateFor(association.realizedVolume())
                            .orElseThrow()
                            .envelope(),
                    support.certificateFor(association).orElseThrow().envelope());
        }
    }

    @Test
    void authoredBridgeRejectsSameIdentityWithDifferentWorldVolumeContent() {
        var plan = new SkyIslandArchipelagoPlanner().plan(
                request(
                        direct(MorphologyFamily.MASSIF),
                        direct(MorphologyFamily.SPINE),
                        360.0));
        SkyIslandWorldCatalogSupportBundle worldSupport =
                new SkyIslandWorldCatalogCompiler()
                        .compileWithSupport(
                                plan,
                                SkyIslandMorphologyProviders.builtInRegistry(),
                                ADEQUATE_VERTICAL);
        SkyIslandWorldVolume source = worldSupport.catalog().volumes().get(0);
        WorldBounds shiftedBounds =
                new WorldBounds(
                        source.bounds().minimumX() - 1.0,
                        source.bounds().maximumX(),
                        source.bounds().minimumY(),
                        source.bounds().maximumY(),
                        source.bounds().minimumZ(),
                        source.bounds().maximumZ());
        SkyIslandWorldVolume altered =
                new SkyIslandWorldVolume(
                        source.id(),
                        shiftedBounds,
                        source.compiledVolume());
        SkyIslandAuthoredRealizationAssociation forged =
                SkyIslandAuthoredRealizationAssociation.of(
                        authoredDescriptor(altered, 0),
                        altered);
        SkyIslandAuthoredRealizationCatalog authoredCatalog =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        ROOT_SEED,
                        List.of(forged));

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandAuthoredRealizationSupportCatalog.fromWorldSupport(
                        authoredCatalog, worldSupport));
    }

    @Test
    void closedBoundsContainmentRequiresEveryAxis() {
        WorldBounds outer = new WorldBounds(-10, 10, -20, 20, -30, 30);
        assertTrue(outer.contains(new WorldBounds(-10, 10, -20, 20, -30, 30)));
        assertTrue(outer.contains(new WorldBounds(-5, 5, -10, 10, -15, 15)));
        assertFalse(outer.contains(new WorldBounds(-11, 5, -10, 10, -15, 15)));
        assertFalse(outer.contains(new WorldBounds(-5, 5, -21, 10, -15, 15)));
        assertFalse(outer.contains(new WorldBounds(-5, 5, -10, 10, -15, 31)));
    }

    private static ProviderMorphologySpec direct(MorphologyFamily family) {
        return ProviderMorphologySpec.full(
                SkyIslandMorphologyProviders.builtInId(family));
    }

    private static SkyIslandArchipelagoRequest request(
            SkyIslandMorphologySpec first,
            SkyIslandMorphologySpec second,
            double reservedHorizontalRadius) {
        SkyIslandVolumeDescriptor descriptor =
                new SkyIslandVolumeDescriptor(
                        SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                        0L,
                        0.0,
                        0.0,
                        320.0,
                        192.0,
                        76.0,
                        100.0,
                        48.0,
                        Math.PI / 6.0,
                        0.65,
                        0.60,
                        0.25,
                        0.0,
                        28.0);
        SkyIslandGroupTemplate anchor =
                new SkyIslandGroupTemplate(
                        "anchor",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor,
                        reservedHorizontalRadius,
                        96.0,
                        0.0,
                        List.of(first),
                        new SkyIslandGroupLayout.Cluster(
                                600.0, 0.0, 0.0, 0.0),
                        420.0);
        SkyIslandGroupTemplate outlier =
                new SkyIslandGroupTemplate(
                        "outlier",
                        SkyIslandGroupRole.OUTLIER,
                        descriptor,
                        reservedHorizontalRadius,
                        96.0,
                        0.0,
                        List.of(second),
                        new SkyIslandGroupLayout.Cluster(
                                600.0, 0.0, 0.0, 0.0),
                        420.0);
        return new SkyIslandArchipelagoRequest(
                ROOT_SEED,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(anchor, outlier),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static SkyIslandDescriptor authoredDescriptor(
            SkyIslandWorldVolume volume, int ordinal) {
        double radius = volume.compiledVolume().descriptor().nominalRadius();
        SkyIslandMorphologyFamily family =
                ordinal % 2 == 0
                        ? SkyIslandMorphologyFamily.MASSIF
                        : SkyIslandMorphologyFamily.SPINE;
        return new SkyIslandDescriptor(
                SkyIslandDescriptor.SCHEMA_VERSION,
                SkyIslandIdentity.of(
                        AUTHORED_WORLD, 8L, 82L, 5200L + ordinal),
                0x5200000000000000L ^ ordinal,
                family,
                radius,
                82.0,
                0.72,
                0.42,
                0.54,
                0.58,
                0.50,
                0.46,
                0.57,
                0.63);
    }
}
