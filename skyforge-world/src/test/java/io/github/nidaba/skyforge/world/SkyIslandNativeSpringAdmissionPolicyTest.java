package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

final class SkyIslandNativeSpringAdmissionPolicyTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final long[] REPRESENTATIVE_KEYS = {
        653L, 3670L, 1051L, 1439L, 913L, 512L, 811L, 83L, 118L, 241L, 7L, 10L
    };

    @Test
    void aquiferSupportedAuthoredCaveWaterIsAdmittedWithExactProvenance() {
        Fixture fixture = admittedFixture();

        SkyIslandNativeSpringAdmission admission =
                SkyIslandNativeSpringAdmissionPolicy.evaluate(
                        fixture.regions(),
                        fixture.caves(),
                        fixture.position(),
                        SkyIslandNativeSpringFluidKind.WATER);

        assertTrue(admission.admitted());
        assertEquals(
                SkyIslandNativeSpringAdmissionStatus.ADMITTED_AQUIFER_CAVE_WATER,
                admission.status());
        assertNotEquals(
                SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.NONE,
                admission.caveSourceKind());
        assertTrue(admission.caveSystemId() >= 0);
        assertTrue(admission.aquiferRegionId() >= 0);
        assertEquals(fixture.aquiferCell().index(), admission.aquiferCellIndex());
        assertEquals(
                fixture.aquiferCell().membership(),
                admission.aquiferMembership(),
                0.0);
    }

    @Test
    void sameAuthoredLocationCannotTurnIntoMoltenFluidWithoutGeothermalSemantics() {
        Fixture fixture = admittedFixture();

        SkyIslandNativeSpringAdmission admission =
                SkyIslandNativeSpringAdmissionPolicy.evaluate(
                        fixture.regions(),
                        fixture.caves(),
                        fixture.position(),
                        SkyIslandNativeSpringFluidKind.MOLTEN);

        assertFalse(admission.admitted());
        assertEquals(
                SkyIslandNativeSpringAdmissionStatus.MISSING_GEOTHERMAL_SEMANTICS,
                admission.status());
        assertNotEquals(
                SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.NONE,
                admission.caveSourceKind());
        assertTrue(admission.caveSystemId() >= 0);
        assertEquals(-1, admission.aquiferRegionId());
        assertEquals(-1, admission.aquiferCellIndex());
    }

    @Test
    void outsideIslandAndOwnedNonCaveWaterFailClosed() {
        Fixture fixture = admittedFixture();
        double radius = fixture.regions().descriptor().nominalRadius();

        SkyIslandNativeSpringAdmission outside =
                SkyIslandNativeSpringAdmissionPolicy.evaluate(
                        fixture.regions(),
                        fixture.caves(),
                        new SkyIslandSubsurfacePosition(radius * 1.5, 0.0, 0.5),
                        SkyIslandNativeSpringFluidKind.WATER);
        assertEquals(
                SkyIslandNativeSpringAdmissionStatus.OUTSIDE_AUTHORED_ISLAND,
                outside.status());
        assertFalse(outside.admitted());

        SkyIslandSubsurfacePosition nonCave = nonCaveOwnedPosition(fixture);
        SkyIslandNativeSpringAdmission rock =
                SkyIslandNativeSpringAdmissionPolicy.evaluate(
                        fixture.regions(),
                        fixture.caves(),
                        nonCave,
                        SkyIslandNativeSpringFluidKind.WATER);
        assertEquals(
                SkyIslandNativeSpringAdmissionStatus.NOT_AUTHORED_CAVE_INTERIOR,
                rock.status());
        assertFalse(rock.admitted());
    }

    @Test
    void geologyAndCaveSemanticsFromDifferentIslandsCannotBeCombined() {
        Fixture fixture = admittedFixture();
        SkyIslandDescriptor other = descriptor(2211L);
        SkyIslandExteriorConnectedCaveVolumeField otherCaves =
                SkyIslandExteriorConnectedCaveVolumeField.create(other);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkyIslandNativeSpringAdmissionPolicy.evaluate(
                                fixture.regions(),
                                otherCaves,
                                fixture.position(),
                                SkyIslandNativeSpringFluidKind.WATER));
    }

    @Test
    void admittedDecisionCannotBeForgedWithoutCaveAndAquiferEvidence() {
        Fixture fixture = admittedFixture();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SkyIslandNativeSpringAdmission(
                                fixture.position(),
                                SkyIslandNativeSpringFluidKind.WATER,
                                SkyIslandNativeSpringAdmissionStatus.ADMITTED_AQUIFER_CAVE_WATER,
                                SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.NONE,
                                -1,
                                -1,
                                -1,
                                0.0));
    }

    private static Fixture admittedFixture() {
        for (long key : REPRESENTATIVE_KEYS) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandGeologicRegionPlan regions =
                    SkyIslandGeologicRegionPlanner.plan(descriptor);
            SkyIslandExteriorConnectedCaveVolumeField caves =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);

            for (SkyIslandGeologicRegion region : regions.regions()) {
                if (region.kind() != SkyIslandGeologicRegionKind.AQUIFER_BODY) {
                    continue;
                }
                for (SkyIslandGeologicRegionCell cell : region.cells()) {
                    if (caves.contains(cell.position())) {
                        return new Fixture(
                                regions,
                                caves,
                                region,
                                cell,
                                cell.position());
                    }
                }
            }
        }
        throw new AssertionError(
                "representative AUTH-0023/AUTH-0030 corpus contains no aquifer/cave overlap");
    }

    private static SkyIslandSubsurfacePosition nonCaveOwnedPosition(Fixture fixture) {
        double radius = fixture.regions().descriptor().nominalRadius();
        SkyIslandGeologyFieldSet geology =
                SkyIslandGeologyFieldSet.create(fixture.regions().descriptor());

        for (int iz = 1; iz < 12; iz++) {
            double z = -radius + 2.0 * radius * iz / 12.0;
            for (int ix = 1; ix < 12; ix++) {
                double x = -radius + 2.0 * radius * ix / 12.0;
                for (int id = 1; id < 8; id++) {
                    SkyIslandSubsurfacePosition candidate =
                            new SkyIslandSubsurfacePosition(x, z, id / 8.0);
                    if (geology.sample(candidate).owned()
                            && !fixture.caves().contains(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        throw new AssertionError("representative island has no owned non-cave sample");
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }

    private record Fixture(
            SkyIslandGeologicRegionPlan regions,
            SkyIslandExteriorConnectedCaveVolumeField caves,
            SkyIslandGeologicRegion aquiferRegion,
            SkyIslandGeologicRegionCell aquiferCell,
            SkyIslandSubsurfacePosition position) {}
}
