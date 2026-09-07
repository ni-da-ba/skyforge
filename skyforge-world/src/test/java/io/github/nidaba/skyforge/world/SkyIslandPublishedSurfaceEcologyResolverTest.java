package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandPublishedSurfaceEcologyResolverTest {
    private static final long AUTHORED_WORLD = 0x4155544830303838L;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void exactPublishedVolumeProjectionEqualsDirectAuth0003Ecology() {
        Fixture fixture = fixture(88001L, 2332L);
        SkyIslandPublishedSurfaceEcologyResolver resolver =
                new SkyIslandPublishedSurfaceEcologyResolver(fixture.binding());
        SkyIslandLocalPosition local = firstSupportedAuthoredPosition(fixture.association());
        Coordinate2 world = toWorld(fixture.association(), local);

        SkyIslandPublishedSurfaceEcologySample first =
                resolver.sample(fixture.association().realizedVolumeId(), world);
        SkyIslandPublishedSurfaceEcologySample second =
                resolver.sample(fixture.association().realizedVolumeId(), world);
        SkyIslandEcologySample direct = SkyIslandEcologyField
                .create(fixture.association().authoredDescriptor())
                .sample(local);

        assertEquals(first, second);
        assertTrue(first.physicalColumnPresent());
        assertTrue(first.authoredInteriority() > 0.0);
        assertTrue(first.authoredSurfacePresent());
        assertEquals(direct, first.ecologySample().orElseThrow());
        assertEquals(local, first.localPosition());
        assertEquals(world, first.worldPosition());
    }

    @Test
    void unknownPublishedVolumeIsRejectedRatherThanSpatiallyInferred() {
        Fixture fixture = fixture(88002L, 653L);
        SkyIslandPublishedSurfaceEcologyResolver resolver =
                new SkyIslandPublishedSurfaceEcologyResolver(fixture.binding());
        SkyIslandWorldVolumeId source = fixture.association().realizedVolumeId();
        SkyIslandWorldVolumeId unknown = new SkyIslandWorldVolumeId(
                source.archipelagoRootSeed(),
                source.groupIdentifier(),
                source.groupOrdinal(),
                source.memberOrdinal() + 1,
                source.geometrySeed());

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.sample(unknown, toWorld(
                        fixture.association(),
                        firstSupportedAuthoredPosition(fixture.association()))));
    }

    @Test
    void absentPhysicalHorizontalSupportProducesNoAuthoredSurfaceEcology() {
        Fixture fixture = fixture(88003L, 1051L);
        var realized = fixture.association()
                .realizedVolume()
                .compiledVolume()
                .descriptor();
        double radius = realized.nominalRadius();
        Coordinate2 farOutside = new Coordinate2(
                realized.centerX() + 3.0 * radius,
                realized.centerZ());
        SkyIslandPublishedSurfaceEcologySample sample =
                new SkyIslandPublishedSurfaceEcologyResolver(fixture.binding())
                        .sample(fixture.association().realizedVolumeId(), farOutside);

        assertFalse(sample.physicalColumnPresent());
        assertFalse(sample.authoredSurfacePresent());
        assertTrue(sample.ecologySample().isEmpty());
    }

    @Test
    void physicalFringeOutsideCurrentAuthoredDomainDoesNotBecomeBarrenBiomeAuthorship() {
        FringeFixture fringe = firstPhysicalUnownedFringe(88004L);
        SkyIslandPublishedSurfaceEcologySample sample =
                new SkyIslandPublishedSurfaceEcologyResolver(fringe.fixture().binding())
                        .sample(
                                fringe.fixture().association().realizedVolumeId(),
                                toWorld(fringe.fixture().association(), fringe.local()));

        assertTrue(sample.physicalColumnPresent());
        assertEquals(0.0, sample.authoredInteriority());
        assertFalse(sample.authoredSurfacePresent());
        assertTrue(sample.ecologySample().isEmpty());
    }

    @Test
    void sampleEnvelopeRejectsEcologyWhenPhysicalOrAuthoredGateIsAbsent() {
        Fixture fixture = fixture(88005L, 1439L);
        SkyIslandLocalPosition local = firstSupportedAuthoredPosition(fixture.association());
        Coordinate2 world = toWorld(fixture.association(), local);
        SkyIslandEcologySample ecology = SkyIslandEcologyField
                .create(fixture.association().authoredDescriptor())
                .sample(local);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPublishedSurfaceEcologySample(
                        fixture.association(),
                        world,
                        local,
                        false,
                        1.0,
                        ecology));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPublishedSurfaceEcologySample(
                        fixture.association(),
                        world,
                        local,
                        true,
                        0.0,
                        ecology));
    }

    @Test
    void publicQuerySurfaceRequiresExactVolumeAndHorizontalWorldCoordinateOnly() {
        Method[] publicDeclared = Arrays.stream(
                        SkyIslandPublishedSurfaceEcologyResolver.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("sample"))
                .toArray(Method[]::new);

        assertEquals(1, publicDeclared.length);
        assertEquals(
                List.of(SkyIslandWorldVolumeId.class, Coordinate2.class),
                List.of(publicDeclared[0].getParameterTypes()));
        assertEquals(
                SkyIslandPublishedSurfaceEcologySample.class,
                publicDeclared[0].getReturnType());
    }

    private static FringeFixture firstPhysicalUnownedFringe(long rootSeed) {
        SkyIslandCompiledWorldPublication publication = new SkyIslandCompiledWorldPublisher()
                .publish(acceptedCompilation(rootSeed), 1L);
        SkyIslandWorldVolume volume = publication.catalog().volumes().getFirst();
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume());
        double radius = volume.compiledVolume().descriptor().nominalRadius();

        for (long key = 0L; key < 96L; key++) {
            SkyIslandDescriptor authored = authored(key, radius);
            SkyIslandAuthoredRealizationAssociation association =
                    SkyIslandAuthoredRealizationAssociation.of(authored, volume);
            SkyIslandSemanticField interiority =
                    SkyIslandSemanticFieldSet.create(authored).interiority();
            for (int iz = 0; iz <= 40; iz++) {
                double z = -radius + iz * (2.0 * radius / 40.0);
                for (int ix = 0; ix <= 40; ix++) {
                    double x = -radius + ix * (2.0 * radius / 40.0);
                    SkyIslandLocalPosition local = new SkyIslandLocalPosition(x, z);
                    if (columns.columnAt(local).isPresent()
                            && interiority.sample(local) == 0.0) {
                        SkyIslandAuthoredRealizationCatalog catalog =
                                new SkyIslandAuthoredRealizationCatalog(
                                        AUTHORED_WORLD,
                                        publication.catalog().rootSeed(),
                                        List.of(association));
                        return new FringeFixture(
                                new Fixture(
                                        new SkyIslandPublishedAuthoredRealizationBinding(
                                                publication, catalog),
                                        association),
                                local);
                    }
                }
            }
        }
        throw new IllegalStateException(
                "AUTH-0088 fixture search found no physical fringe outside current authored ownership");
    }

    private static SkyIslandLocalPosition firstSupportedAuthoredPosition(
            SkyIslandAuthoredRealizationAssociation association) {
        SkyIslandCompiledVolumeColumnField columns = new SkyIslandCompiledVolumeColumnField(
                association.realizedVolume().compiledVolume());
        SkyIslandSemanticField interiority = SkyIslandSemanticFieldSet
                .create(association.authoredDescriptor())
                .interiority();
        double radius = association.authoredDescriptor().nominalRadius();
        for (int iz = 0; iz <= 32; iz++) {
            double z = -radius + iz * (2.0 * radius / 32.0);
            for (int ix = 0; ix <= 32; ix++) {
                double x = -radius + ix * (2.0 * radius / 32.0);
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(x, z);
                if (columns.columnAt(local).isPresent()
                        && interiority.sample(local) > 0.0) {
                    return local;
                }
            }
        }
        throw new IllegalStateException("AUTH-0088 fixture has no shared physical/authored surface");
    }

    private static Coordinate2 toWorld(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandLocalPosition local) {
        var realized = association.realizedVolume().compiledVolume().descriptor();
        return new Coordinate2(
                realized.centerX() + local.x(),
                realized.centerZ() + local.z());
    }

    private static Fixture fixture(long rootSeed, long authoredIslandKey) {
        SkyIslandCompiledWorldPublication publication = new SkyIslandCompiledWorldPublisher()
                .publish(acceptedCompilation(rootSeed), 1L);
        SkyIslandWorldVolume volume = publication.catalog().volumes().getFirst();
        SkyIslandDescriptor authored = authored(
                authoredIslandKey,
                volume.compiledVolume().descriptor().nominalRadius());
        SkyIslandAuthoredRealizationAssociation association =
                SkyIslandAuthoredRealizationAssociation.of(authored, volume);
        SkyIslandAuthoredRealizationCatalog catalog = new SkyIslandAuthoredRealizationCatalog(
                AUTHORED_WORLD,
                publication.catalog().rootSeed(),
                List.of(association));
        return new Fixture(
                new SkyIslandPublishedAuthoredRealizationBinding(publication, catalog),
                association);
    }

    private static SkyIslandDescriptor authored(long islandKey, double radius) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 88L, islandKey));
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                radius,
                base.reliefBudget(),
                base.rockCompetence(),
                base.permeability(),
                base.temperatureTendency(),
                base.moistureTendency(),
                base.exposureTendency(),
                base.erosionMaturity(),
                base.hydrologicalPotential(),
                base.ecologicalPotential());
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0,
                0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("AUTH-0088 test fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template = new SkyIslandGroupTemplate(
                "auth88",
                SkyIslandGroupRole.ANCHOR,
                descriptor(),
                360.0,
                0.0,
                0.0,
                List.of(morphology),
                new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                440.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor() {
        return new SkyIslandVolumeDescriptor(
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
    }

    private record Fixture(
            SkyIslandPublishedAuthoredRealizationBinding binding,
            SkyIslandAuthoredRealizationAssociation association) {}

    private record FringeFixture(Fixture fixture, SkyIslandLocalPosition local) {}
}
