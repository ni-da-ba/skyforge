package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandAuthoredRealizationOwnershipResolverTest {
    private static final long AUTHORED_WORLD = 0x4155544830303438L;
    private static final long REALIZATION_ROOT = 0x5245414C30303438L;
    private static final double RADIUS = 120.0;

    @Test
    void sameXZStackedAssociationsResolveIndependentlyByExactPhysicalY() {
        SkyIslandAuthoredRealizationAssociation lower =
                association(
                        authored(101L, SkyIslandMorphologyFamily.MASSIF),
                        0.0,
                        0.0,
                        150.0,
                        1001L,
                        "stacked-lower",
                        0,
                        0);
        SkyIslandAuthoredRealizationAssociation upper =
                association(
                        authored(102L, SkyIslandMorphologyFamily.TABLELAND),
                        0.0,
                        0.0,
                        310.0,
                        1002L,
                        "stacked-upper",
                        0,
                        1);

        SkyIslandAuthoredRealizationOwnershipResolver resolver =
                resolver(List.of(upper, lower));

        Coordinate3 lowerPoint = centerInterior(lower, 0.50);
        SkyIslandAuthoredRealizationOwnershipSelection lowerSelection =
                resolver.resolve(lowerPoint);
        assertEquals(2, lowerSelection.conservativeCandidateCount());
        assertEquals(1, lowerSelection.exactPhysicalOccupants().size());
        assertEquals(1, lowerSelection.authoredOwners().size());
        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.UNIQUE,
                lowerSelection.status());
        assertEquals(
                lower.authoredIdentity(),
                lowerSelection
                        .uniqueOwner()
                        .orElseThrow()
                        .association()
                        .authoredIdentity());

        Coordinate3 upperPoint = centerInterior(upper, 0.50);
        SkyIslandAuthoredRealizationOwnershipSelection upperSelection =
                resolver.resolve(upperPoint);
        assertEquals(2, upperSelection.conservativeCandidateCount());
        assertEquals(1, upperSelection.exactPhysicalOccupants().size());
        assertEquals(1, upperSelection.authoredOwners().size());
        assertEquals(
                upper.authoredIdentity(),
                upperSelection
                        .uniqueOwner()
                        .orElseThrow()
                        .association()
                        .authoredIdentity());

        SkyIslandVerticalColumn lowerColumn = centerColumn(lower);
        SkyIslandVerticalColumn upperColumn = centerColumn(upper);
        assertTrue(upperColumn.undersideY() > lowerColumn.upperY());
        Coordinate3 gap =
                new Coordinate3(
                        0.0,
                        0.5
                                * (lowerColumn.upperY()
                                        + upperColumn.undersideY()),
                        0.0);
        SkyIslandAuthoredRealizationOwnershipSelection gapSelection =
                resolver.resolve(gap);
        assertEquals(2, gapSelection.conservativeCandidateCount());
        assertTrue(gapSelection.exactPhysicalOccupants().isEmpty());
        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.NONE,
                gapSelection.status());
    }

    @Test
    void catalogInputOrderCannotRankAnOwner() {
        SkyIslandAuthoredRealizationAssociation first =
                association(
                        authored(201L, SkyIslandMorphologyFamily.MASSIF),
                        -180.0,
                        0.0,
                        220.0,
                        2001L,
                        "order-first",
                        1,
                        0);
        SkyIslandAuthoredRealizationAssociation second =
                association(
                        authored(202L, SkyIslandMorphologyFamily.LOBED),
                        180.0,
                        0.0,
                        220.0,
                        2002L,
                        "order-second",
                        1,
                        1);
        Coordinate3 query = centerInterior(second, 0.55);

        SkyIslandAuthoredRealizationOwnershipSelection forward =
                resolver(List.of(first, second)).resolve(query);
        SkyIslandAuthoredRealizationOwnershipSelection reversed =
                resolver(List.of(second, first)).resolve(query);

        assertEquals(forward.status(), reversed.status());
        assertEquals(
                forward.uniqueOwner()
                        .orElseThrow()
                        .association()
                        .canonicalToken(),
                reversed.uniqueOwner()
                        .orElseThrow()
                        .association()
                        .canonicalToken());
        assertEquals(
                forward.conservativeCandidates().stream()
                        .map(candidate -> candidate.association().canonicalToken())
                        .toList(),
                reversed.conservativeCandidates().stream()
                        .map(candidate -> candidate.association().canonicalToken())
                        .toList());
    }

    @Test
    void exactPhysicalSolidOutsideNativeDomainIsNotAnAuthoredOwner() {
        SkyIslandAuthoredRealizationAssociation association =
                association(
                        authored(301L, SkyIslandMorphologyFamily.SPINE),
                        700.0,
                        -400.0,
                        240.0,
                        3001L,
                        "physical-fringe",
                        2,
                        0);
        FringePoint fringe = findPhysicalUnowned(association);
        SkyIslandAuthoredRealizationOwnershipSelection selection =
                resolver(List.of(association)).resolve(fringe.world());

        assertEquals(1, selection.conservativeCandidateCount());
        assertEquals(1, selection.exactPhysicalOccupants().size());
        assertTrue(selection.authoredOwners().isEmpty());
        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.NONE,
                selection.status());
        assertTrue(selection.uniqueOwner().isEmpty());
    }

    @Test
    void trueNativeOverlapIsReportedAsAmbiguousRatherThanRanked() {
        SkyIslandAuthoredRealizationAssociation first =
                association(
                        authored(401L, SkyIslandMorphologyFamily.MASSIF),
                        1200.0,
                        900.0,
                        260.0,
                        4001L,
                        "overlap-a",
                        3,
                        0);
        SkyIslandAuthoredRealizationAssociation second =
                association(
                        authored(402L, SkyIslandMorphologyFamily.TABLELAND),
                        1200.0,
                        900.0,
                        260.0,
                        4002L,
                        "overlap-b",
                        3,
                        1);
        Coordinate3 query = commonInterior(first, second, 0.0, 0.0);

        SkyIslandAuthoredRealizationOwnershipSelection selection =
                resolver(List.of(first, second)).resolve(query);

        assertEquals(2, selection.conservativeCandidateCount());
        assertEquals(2, selection.exactPhysicalOccupants().size());
        assertEquals(2, selection.authoredOwners().size());
        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.AMBIGUOUS,
                selection.status());
        assertTrue(selection.uniqueOwner().isEmpty());
    }

    @Test
    void physicalOverlapDoesNotCreateAmbiguityWhenOnlyOneNativeDomainOwnsPoint() {
        SkyIslandAuthoredRealizationAssociation broad =
                association(
                        authored(501L, SkyIslandMorphologyFamily.MASSIF),
                        -1100.0,
                        800.0,
                        250.0,
                        5001L,
                        "domain-broad",
                        4,
                        0);
        SkyIslandAuthoredRealizationAssociation narrow =
                association(
                        authored(502L, SkyIslandMorphologyFamily.SPINE),
                        -1100.0,
                        800.0,
                        250.0,
                        5002L,
                        "domain-narrow",
                        4,
                        1);
        Coordinate3 query = findOneNativeOwnerTwoPhysical(broad, narrow);

        SkyIslandAuthoredRealizationOwnershipSelection selection =
                resolver(List.of(narrow, broad)).resolve(query);

        assertEquals(2, selection.conservativeCandidateCount());
        assertEquals(2, selection.exactPhysicalOccupants().size());
        assertEquals(1, selection.authoredOwners().size());
        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.UNIQUE,
                selection.status());
        assertEquals(
                broad.authoredIdentity(),
                selection
                        .uniqueOwner()
                        .orElseThrow()
                        .association()
                        .authoredIdentity());
    }

    @Test
    void emptySkyHasNoCandidatesAndNoOwner() {
        SkyIslandAuthoredRealizationAssociation association =
                association(
                        authored(601L, SkyIslandMorphologyFamily.BASIN),
                        0.0,
                        0.0,
                        220.0,
                        6001L,
                        "empty-sky",
                        5,
                        0);
        SkyIslandAuthoredRealizationOwnershipSelection selection =
                resolver(List.of(association))
                        .resolve(new Coordinate3(50_000.0, 50_000.0, 50_000.0));

        assertTrue(selection.conservativeCandidates().isEmpty());
        assertTrue(selection.exactPhysicalOccupants().isEmpty());
        assertTrue(selection.authoredOwners().isEmpty());
        assertEquals(
                SkyIslandAuthoredRealizationOwnershipStatus.NONE,
                selection.status());
    }

    @Test
    void ownershipEnvelopeRejectsForgedWorldLocalFrame() {
        SkyIslandAuthoredRealizationAssociation association =
                association(
                        authored(701L, SkyIslandMorphologyFamily.LOBED),
                        300.0,
                        400.0,
                        230.0,
                        7001L,
                        "forged",
                        6,
                        0);
        Coordinate3 validWorld = centerInterior(association, 0.50);
        SkyIslandAuthoredRealizationOwnershipCandidate valid =
                resolver(List.of(association))
                        .resolve(validWorld)
                        .uniqueOwner()
                        .orElseThrow();
        Coordinate3 forgedWorld =
                new Coordinate3(
                        validWorld.x() + 1.0,
                        validWorld.y(),
                        validWorld.z());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandAuthoredRealizationOwnershipSelection(
                        forgedWorld, List.of(valid)));
    }

    @Test
    void candidateCannotClaimAuthoredOwnershipWithoutPhysicalInterior() {
        SkyIslandAuthoredRealizationAssociation association =
                association(
                        authored(801L, SkyIslandMorphologyFamily.MASSIF),
                        0.0,
                        0.0,
                        220.0,
                        8001L,
                        "invalid-candidate",
                        7,
                        0);
        SkyIslandRealizedSubsurfacePosition realized =
                new SkyIslandRealizedSubsurfacePosition(
                        new SkyIslandLocalPosition(0.0, 0.0), 10_000.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandAuthoredRealizationOwnershipCandidate(
                        association, realized, null, true));
    }

    private static SkyIslandAuthoredRealizationOwnershipResolver resolver(
            List<SkyIslandAuthoredRealizationAssociation> associations) {
        return new SkyIslandAuthoredRealizationOwnershipResolver(
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD, REALIZATION_ROOT, associations));
    }

    private static Coordinate3 centerInterior(
            SkyIslandAuthoredRealizationAssociation association,
            double depth) {
        SkyIslandVerticalColumn column = centerColumn(association);
        var descriptor =
                association.realizedVolume().compiledVolume().descriptor();
        return new Coordinate3(
                descriptor.centerX(),
                column.physicalYAt(depth),
                descriptor.centerZ());
    }

    private static SkyIslandVerticalColumn centerColumn(
            SkyIslandAuthoredRealizationAssociation association) {
        return new SkyIslandCompiledVolumeColumnField(
                        association.realizedVolume().compiledVolume())
                .columnAt(new SkyIslandLocalPosition(0.0, 0.0))
                .orElseThrow();
    }

    private static Coordinate3 commonInterior(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second,
            double localX,
            double localZ) {
        SkyIslandLocalPosition local =
                new SkyIslandLocalPosition(localX, localZ);
        SkyIslandVerticalColumn a =
                new SkyIslandCompiledVolumeColumnField(
                                first.realizedVolume().compiledVolume())
                        .columnAt(local)
                        .orElseThrow();
        SkyIslandVerticalColumn b =
                new SkyIslandCompiledVolumeColumnField(
                                second.realizedVolume().compiledVolume())
                        .columnAt(local)
                        .orElseThrow();
        double upper = Math.min(a.upperY(), b.upperY());
        double lower = Math.max(a.undersideY(), b.undersideY());
        if (!(upper > lower)) {
            throw new IllegalStateException("fixture physical volumes do not overlap");
        }
        var descriptor =
                first.realizedVolume().compiledVolume().descriptor();
        return new Coordinate3(
                descriptor.centerX() + localX,
                0.5 * (upper + lower),
                descriptor.centerZ() + localZ);
    }

    private static FringePoint findPhysicalUnowned(
            SkyIslandAuthoredRealizationAssociation association) {
        var descriptor =
                association.realizedVolume().compiledVolume().descriptor();
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(
                        association.realizedVolume().compiledVolume());
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(
                                association.authoredDescriptor())
                        .interiority();

        for (int iz = 0; iz < 61; iz++) {
            double z = -1.20 * RADIUS + iz * (2.40 * RADIUS / 60.0);
            for (int ix = 0; ix < 61; ix++) {
                double x = -1.20 * RADIUS + ix * (2.40 * RADIUS / 60.0);
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(x, z);
                var column = columns.columnAt(local);
                if (column.isPresent() && interiority.sample(local) <= 0.0) {
                    return new FringePoint(
                            new Coordinate3(
                                    descriptor.centerX() + x,
                                    column.orElseThrow().physicalYAt(0.50),
                                    descriptor.centerZ() + z),
                            local);
                }
            }
        }
        throw new IllegalStateException(
                "fixture produced no exact physical fringe outside native ownership");
    }

    private static Coordinate3 findOneNativeOwnerTwoPhysical(
            SkyIslandAuthoredRealizationAssociation broad,
            SkyIslandAuthoredRealizationAssociation narrow) {
        var descriptor =
                broad.realizedVolume().compiledVolume().descriptor();
        SkyIslandCompiledVolumeColumnField broadColumns =
                new SkyIslandCompiledVolumeColumnField(
                        broad.realizedVolume().compiledVolume());
        SkyIslandCompiledVolumeColumnField narrowColumns =
                new SkyIslandCompiledVolumeColumnField(
                        narrow.realizedVolume().compiledVolume());
        SkyIslandSemanticField broadDomain =
                SkyIslandSemanticFieldSet.create(broad.authoredDescriptor())
                        .interiority();
        SkyIslandSemanticField narrowDomain =
                SkyIslandSemanticFieldSet.create(narrow.authoredDescriptor())
                        .interiority();

        for (int iz = 0; iz < 81; iz++) {
            double z = -RADIUS + iz * (2.0 * RADIUS / 80.0);
            for (int ix = 0; ix < 81; ix++) {
                double x = -RADIUS + ix * (2.0 * RADIUS / 80.0);
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(x, z);
                if (!(broadDomain.sample(local) > 0.0)
                        || narrowDomain.sample(local) > 0.0) {
                    continue;
                }
                var broadColumn = broadColumns.columnAt(local);
                var narrowColumn = narrowColumns.columnAt(local);
                if (broadColumn.isEmpty() || narrowColumn.isEmpty()) {
                    continue;
                }
                double upper =
                        Math.min(
                                broadColumn.orElseThrow().upperY(),
                                narrowColumn.orElseThrow().upperY());
                double lower =
                        Math.max(
                                broadColumn.orElseThrow().undersideY(),
                                narrowColumn.orElseThrow().undersideY());
                if (upper > lower) {
                    return new Coordinate3(
                            descriptor.centerX() + x,
                            0.5 * (upper + lower),
                            descriptor.centerZ() + z);
                }
            }
        }
        throw new IllegalStateException(
                "fixture produced no two-physical/one-native ownership point");
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            SkyIslandDescriptor authored,
            double centerX,
            double centerZ,
            double suspension,
            long geometrySeed,
            String group,
            int groupOrdinal,
            int memberOrdinal) {
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        suspension,
                        authored.nominalRadius(),
                        32.0,
                        44.0,
                        30.0,
                        0.15,
                        0.46,
                        0.61,
                        0.08,
                        authored.morphologyFamily(),
                        0.12,
                        32.0,
                        0.22);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolume volume =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                group,
                                groupOrdinal,
                                memberOrdinal,
                                geometrySeed),
                        new WorldBounds(
                                centerX - 1.35 * RADIUS,
                                centerX + 1.35 * RADIUS,
                                suspension - 180.0,
                                suspension + 180.0,
                                centerZ - 1.35 * RADIUS,
                                centerZ + 1.35 * RADIUS),
                        compiled);
        return SkyIslandAuthoredRealizationAssociation.of(authored, volume);
    }

    private static SkyIslandDescriptor authored(
            long islandKey,
            SkyIslandMorphologyFamily morphology) {
        return new SkyIslandDescriptor(
                SkyIslandDescriptor.SCHEMA_VERSION,
                SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 81L, islandKey),
                0x4800000000000000L ^ islandKey,
                morphology,
                RADIUS,
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

    private record FringePoint(
            Coordinate3 world,
            SkyIslandLocalPosition local) {}
}
