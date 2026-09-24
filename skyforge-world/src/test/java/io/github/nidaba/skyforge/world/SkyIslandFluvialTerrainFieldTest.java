package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandFluvialTerrainFieldTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void fluvialFieldIsDeterministicAndNormalized() {
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandFluvialTerrainField a = SkyIslandFluvialTerrainField.create(descriptor);
            SkyIslandFluvialTerrainField b = SkyIslandFluvialTerrainField.create(descriptor);
            double radius = descriptor.nominalRadius();

            assertEquals(a.reaches(), b.reaches());
            for (int z = 0; z <= 48; z++) {
                for (int x = 0; x <= 48; x++) {
                    SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                            -radius + 2.0 * radius * x / 48.0,
                            -radius + 2.0 * radius * z / 48.0);
                    assertEquals(a.sample(position), b.sample(position), EPSILON);
                    assertTrue(a.sample(position) >= 0.0 && a.sample(position) <= 1.0);
                    assertTrue(
                            a.adjustment(position)
                                    >= -SkyIslandFluvialTerrainField.MAX_FLUVIAL_LOWERING - EPSILON);
                }
            }
        }
    }

    @Test
    void acceptedCenterlinesBecomeReadableDryIncisionsWithContainedWater() {
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandFluvialTerrainField field = SkyIslandFluvialTerrainField.create(descriptor(key));
            int materiallyLowered = 0;
            for (SkyIslandFluvialReachGeometry reach : field.reaches()) {
                SkyIslandLocalPosition center = reach.path().points().get(
                        reach.path().points().size() / 2);
                double base = field.baseTerrain().sample(center);
                double shaped = field.sample(center);

                assertTrue(shaped <= base + EPSILON);
                if (base - shaped > 0.01) {
                    materiallyLowered++;
                }
                assertTrue(field.waterSurfacePotential(center).isPresent());
                assertTrue(field.waterSurfacePotential(center).orElseThrow() > shaped);
                assertTrue(reach.wetHalfWidth() < reach.bankfullHalfWidth());
                assertTrue(reach.bankfullHalfWidth() < reach.valleyHalfWidth());
                assertTrue(reach.confinementPotential() >= 0.0);
                assertTrue(reach.confinementPotential() <= 1.0);
            }
            if (!field.reaches().isEmpty()) {
                assertTrue(materiallyLowered > 0, "at least one reach must remain legible with water hidden");
            }
        }
    }

    @Test
    void corridorScaleRespondsToGeomorphicProfileInsteadOfOneFixedTrenchWidth() {
        Map<SkyIslandChannelProfileKind, Double> maxValleyRatio =
                new EnumMap<>(SkyIslandChannelProfileKind.class);
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandFluvialTerrainField field = SkyIslandFluvialTerrainField.create(descriptor(key));
            for (SkyIslandFluvialReachGeometry reach : field.reaches()) {
                double ratio = reach.valleyHalfWidth() / reach.bankfullHalfWidth();
                maxValleyRatio.merge(reach.profile().kind(), ratio, Math::max);
            }
        }

        assertFalse(maxValleyRatio.isEmpty());
        if (maxValleyRatio.containsKey(SkyIslandChannelProfileKind.ALLUVIAL)
                && maxValleyRatio.containsKey(SkyIslandChannelProfileKind.INCISED)) {
            assertTrue(maxValleyRatio.get(SkyIslandChannelProfileKind.ALLUVIAL)
                    > maxValleyRatio.get(SkyIslandChannelProfileKind.INCISED));
        }
        if (maxValleyRatio.containsKey(SkyIslandChannelProfileKind.INCISED)
                && maxValleyRatio.containsKey(SkyIslandChannelProfileKind.CASCADE)) {
            assertTrue(maxValleyRatio.get(SkyIslandChannelProfileKind.INCISED)
                    > maxValleyRatio.get(SkyIslandChannelProfileKind.CASCADE));
        }
    }


    @Test
    void confinementNarrowsValleyEnvelopeWithoutChangingBankfullDischargeScale() {
        boolean compared = false;
        for (long key : new long[] {287L, 2266L, 2936L, 1227L, 811L}) {
            SkyIslandFluvialTerrainField field = SkyIslandFluvialTerrainField.create(
                    SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(SEED, 8L, 81L, key)));
            for (SkyIslandChannelProfileKind kind : SkyIslandChannelProfileKind.values()) {
                var reaches = field.reaches().stream()
                        .filter(reach -> reach.profile().kind() == kind)
                        .sorted(java.util.Comparator.comparingDouble(
                                SkyIslandFluvialReachGeometry::confinementPotential))
                        .toList();
                if (reaches.size() < 2) {
                    continue;
                }
                SkyIslandFluvialReachGeometry open = reaches.getFirst();
                SkyIslandFluvialReachGeometry confined = reaches.getLast();
                if (confined.confinementPotential() - open.confinementPotential() < 0.20) {
                    continue;
                }
                double openRatio = open.valleyHalfWidth() / open.bankfullHalfWidth();
                double confinedRatio = confined.valleyHalfWidth() / confined.bankfullHalfWidth();
                assertTrue(openRatio > confinedRatio);
                compared = true;
            }
        }
        assertTrue(compared, "reference corpus should exercise materially different valley confinement");
    }


    @Test
    void fineSurfaceZonesExposeWetCenterlinesAndRejectExterior() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 287L));
        SkyIslandFluvialTerrainField field = SkyIslandFluvialTerrainField.create(descriptor);
        assertFalse(field.reaches().isEmpty());
        for (SkyIslandFluvialReachGeometry reach : field.reaches()) {
            SkyIslandLocalPosition center =
                    reach.path().points().get(reach.path().points().size() / 2);
            assertEquals(SkyIslandFluvialSurfaceZone.WET_CHANNEL, field.surfaceZone(center));
        }
        double radius = descriptor.nominalRadius();
        assertEquals(
                SkyIslandFluvialSurfaceZone.NONE,
                field.surfaceZone(new SkyIslandLocalPosition(radius * 1.10, 0.0)));
    }


    @Test
    void acceptedHeadwatersRemainRasterizableWithoutLatticeSizedRivers() {
        for (long key : new long[] {287L, 649L, 811L, 83L}) {
            SkyIslandFluvialTerrainField field = SkyIslandFluvialTerrainField.create(descriptor(key));
            for (SkyIslandFluvialReachGeometry reach : field.reaches()) {
                assertTrue(
                        reach.bankfullHalfWidth() >= 3.0 - EPSILON,
                        "accepted reaches need a stable voxel-quantization floor");
                assertTrue(
                        reach.bankfullHalfWidth() < field.descriptor().nominalRadius() * 0.08,
                        "quantization floor must not turn headwaters into lattice-scale trenches");
            }
        }
    }


    @Test
    void confluenceExpansionAndAsymmetryRemainTopologyDerivedAndBounded() {
        for (long key : new long[] {287L, 649L, 811L, 512L, 83L}) {
            SkyIslandFluvialTerrainField field = SkyIslandFluvialTerrainField.create(descriptor(key));
            var incoming = new java.util.HashMap<Integer, Integer>();
            for (SkyIslandFluvialReachGeometry reach : field.reaches()) {
                incoming.merge(
                        reach.profile().segment().downstreamCellIndex(),
                        1,
                        Integer::sum);
            }

            for (SkyIslandFluvialReachGeometry reach : field.reaches()) {
                assertTrue(reach.lateralAsymmetryPotential() >= -1.0 - EPSILON);
                assertTrue(reach.lateralAsymmetryPotential() <= 1.0 + EPSILON);
                int inbound = incoming.getOrDefault(
                        reach.profile().segment().sourceCellIndex(),
                        0);
                if (inbound >= 2) {
                    assertTrue(
                            reach.confluenceScale() > 1.0,
                            "a reach immediately below a confluence must widen");
                } else {
                    assertEquals(1.0, reach.confluenceScale(), EPSILON);
                }
            }
        }
    }

    @Test
    void confluenceExpansionIsLocalizedToTheJunctionThroat() {
        boolean exercised = false;
        for (long key : new long[] {287L, 649L, 811L, 512L, 83L}) {
            SkyIslandFluvialTerrainField field = SkyIslandFluvialTerrainField.create(descriptor(key));
            for (SkyIslandFluvialReachGeometry reach : field.reaches()) {
                if (reach.confluenceScale() <= 1.0 + EPSILON) {
                    continue;
                }
                assertTrue(field.wetHalfWidthAt(reach, 0.0) > field.wetHalfWidthAt(reach, 1.0));
                assertTrue(field.bankfullHalfWidthAt(reach, 0.0) > field.bankfullHalfWidthAt(reach, 1.0));
                assertTrue(field.valleyHalfWidthAt(reach, 0.0) > field.valleyHalfWidthAt(reach, 1.0));
                if (field.terminalDrop(reach).isEmpty()) {
                    assertEquals(reach.wetHalfWidth(), field.wetHalfWidthAt(reach, 1.0), EPSILON);
                } else {
                    assertTrue(field.wetHalfWidthAt(reach, 1.0) < reach.wetHalfWidth());
                }
                assertEquals(reach.bankfullHalfWidth(), field.bankfullHalfWidthAt(reach, 1.0), EPSILON);
                assertEquals(reach.valleyHalfWidth(), field.valleyHalfWidthAt(reach, 1.0), EPSILON);
                exercised = true;
            }
        }
        assertTrue(exercised, "reference corpus must exercise at least one routed confluence");
    }

    @Test
    void interiorDropReachesConvergeToANarrowVisibleThroatWithoutChangingDryBankScale() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 287L));
        SkyIslandFluvialTerrainField field = SkyIslandFluvialTerrainField.create(descriptor);

        boolean exercised = false;
        for (SkyIslandFluvialReachGeometry reach : field.reaches()) {
            if (field.terminalDrop(reach).isEmpty()) {
                continue;
            }
            double upstreamWet = field.wetHalfWidthAt(reach, 0.50);
            double terminalWet = field.wetHalfWidthAt(reach, 1.0);
            assertTrue(terminalWet < upstreamWet);
            assertTrue(terminalWet >= reach.wetHalfWidth() * 0.40 - EPSILON);
            assertEquals(
                    reach.bankfullHalfWidth(),
                    field.bankfullHalfWidthAt(reach, 1.0),
                    EPSILON);
            assertEquals(
                    reach.valleyHalfWidth(),
                    field.valleyHalfWidthAt(reach, 1.0),
                    EPSILON);
            exercised = true;
        }
        assertTrue(exercised, "key-287 must exercise at least one accepted interior drop");
    }

    @Test
    void authoredHydraulicGradeNeverClimbsDownstreamWithinAReach() {
        for (long key : new long[] {287L, 632L, 649L, 811L, 83L}) {
            SkyIslandFluvialTerrainField field = SkyIslandFluvialTerrainField.create(
                    SkyIslandDescriptorGenerator.derive(
                            SkyIslandIdentity.of(SEED, 8L, 81L, key)));
            for (SkyIslandFluvialReachGeometry reach : field.reaches()) {
                double previous = Double.POSITIVE_INFINITY;
                for (int sample = 0; sample <= 64; sample++) {
                    double fraction = sample / 64.0;
                    double surface = field.reachWaterSurfacePotential(reach, fraction);
                    assertTrue(
                            surface <= previous + EPSILON,
                            "one reach's authored free surface must not climb downstream");
                    previous = surface;
                }
            }
        }
    }

    @Test
    void fieldLeavesFarExteriorUntouched() {
        SkyIslandDescriptor descriptor = descriptor(83L);
        SkyIslandFluvialTerrainField field = SkyIslandFluvialTerrainField.create(descriptor);
        double radius = descriptor.nominalRadius();

        for (SkyIslandLocalPosition outside : new SkyIslandLocalPosition[] {
            new SkyIslandLocalPosition(radius * 1.10, 0.0),
            new SkyIslandLocalPosition(-radius * 1.10, 0.0),
            new SkyIslandLocalPosition(0.0, radius * 1.10),
            new SkyIslandLocalPosition(0.0, -radius * 1.10)
        }) {
            assertEquals(field.baseTerrain().sample(outside), field.sample(outside), EPSILON);
            assertTrue(field.waterSurfacePotential(outside).isEmpty());
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
