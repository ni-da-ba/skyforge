package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandEcologicalCandidateLatticeProfileTest {

    @Test
    void windowQueryIsDeterministicOrderedAndRetainsExactProvenance() {
        SkyIslandEcologicalCandidateLatticeProfile profile =
                profile(0x1225A11L, "ecology.query.primary", 12.0, 2.5);
        SkyIslandEcologicalCandidateQueryWindow window =
                new SkyIslandEcologicalCandidateQueryWindow(-35.0, 48.0, -27.0, 41.0);

        SkyIslandEcologicalCandidateWindowResult first = profile.query(window);
        SkyIslandEcologicalCandidateWindowResult second = profile.query(window);

        assertEquals(first, second);
        assertEquals(profile, first.latticeProfile());
        assertEquals(window, first.window());
        assertFalse(first.candidates().isEmpty());

        long previousX = Long.MIN_VALUE;
        long previousZ = Long.MIN_VALUE;
        boolean firstCandidate = true;
        for (SkyIslandEcologicalPlacementCandidate candidate : first.candidates()) {
            assertEquals(profile, candidate.latticeProfile());
            assertEquals(profile.candidate(candidate.cellX(), candidate.cellZ()), candidate);
            assertTrue(window.contains(candidate.position()));
            if (!firstCandidate) {
                assertTrue(candidate.cellX() > previousX
                        || candidate.cellX() == previousX
                                && candidate.cellZ() > previousZ);
            }
            previousX = candidate.cellX();
            previousZ = candidate.cellZ();
            firstCandidate = false;
        }
    }

    @Test
    void windowQueryMatchesWiderBruteForceCorpusExactly() {
        SkyIslandEcologicalCandidateLatticeProfile profile =
                profile(0x1225B22L, "ecology.query.brute", 10.0, 3.0);
        SkyIslandEcologicalCandidateQueryWindow window =
                new SkyIslandEcologicalCandidateQueryWindow(-24.0, 37.0, -31.0, 29.0);

        List<SkyIslandEcologicalPlacementCandidate> brute = new ArrayList<>();
        for (long cellX = -20L; cellX <= 20L; cellX++) {
            for (long cellZ = -20L; cellZ <= 20L; cellZ++) {
                SkyIslandEcologicalPlacementCandidate candidate =
                        profile.candidate(cellX, cellZ);
                if (window.contains(candidate.position())) {
                    brute.add(candidate);
                }
            }
        }

        assertEquals(brute, profile.query(window).candidates());
    }

    @Test
    void adjacentHalfOpenWindowsPartitionCandidatesWithoutDuplicates() {
        SkyIslandEcologicalCandidateLatticeProfile profile =
                profile(0x1225C33L, "ecology.query.partition", 9.0, 2.0);
        SkyIslandEcologicalCandidateQueryWindow left =
                new SkyIslandEcologicalCandidateQueryWindow(-50.0, 0.0, -30.0, 30.0);
        SkyIslandEcologicalCandidateQueryWindow right =
                new SkyIslandEcologicalCandidateQueryWindow(0.0, 50.0, -30.0, 30.0);
        SkyIslandEcologicalCandidateQueryWindow combined =
                new SkyIslandEcologicalCandidateQueryWindow(-50.0, 50.0, -30.0, 30.0);

        var leftCandidates = profile.query(left).candidates();
        var rightCandidates = profile.query(right).candidates();
        var combinedCandidates = profile.query(combined).candidates();

        var leftSet = new HashSet<>(leftCandidates);
        var rightSet = new HashSet<>(rightCandidates);
        var union = new HashSet<>(leftCandidates);
        union.addAll(rightCandidates);

        assertTrue(leftSet.stream().noneMatch(rightSet::contains));
        assertEquals(new HashSet<>(combinedCandidates), union);
        assertEquals(combinedCandidates.size(), union.size());
    }

    @Test
    void sharedBoundaryBelongsOnlyToWindowWhoseMinimumContainsIt() {
        SkyIslandEcologicalCandidateLatticeProfile profile =
                profile(0x1225D44L, "ecology.query.boundary", 16.0, 0.0);
        SkyIslandEcologicalPlacementCandidate candidate = profile.candidate(2L, -1L);
        double x = candidate.position().x();
        double z = candidate.position().z();

        SkyIslandEcologicalCandidateQueryWindow right =
                new SkyIslandEcologicalCandidateQueryWindow(x, x + 1.0, z - 1.0, z + 1.0);
        SkyIslandEcologicalCandidateQueryWindow left =
                new SkyIslandEcologicalCandidateQueryWindow(x - 1.0, x, z - 1.0, z + 1.0);

        assertTrue(profile.query(right).candidates().contains(candidate));
        assertFalse(profile.query(left).candidates().contains(candidate));
    }

    @Test
    void invalidOrUnsafeWindowsFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandEcologicalCandidateQueryWindow(
                        0.0, 0.0, 0.0, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandEcologicalCandidateQueryWindow(
                        1.0, 0.0, 0.0, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandEcologicalCandidateQueryWindow(
                        Double.NaN, 1.0, 0.0, 1.0));

        SkyIslandEcologicalCandidateLatticeProfile profile =
                profile(0x1225E55L, "ecology.query.unsafe", 8.0, 1.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> profile.query(new SkyIslandEcologicalCandidateQueryWindow(
                        Double.MAX_VALUE / 2.0,
                        Double.MAX_VALUE,
                        0.0,
                        1.0)));
    }

    @Test
    void oversizedMaterializedWindowFailsWithExplicitTechnicalBound() {
        SkyIslandEcologicalCandidateLatticeProfile profile =
                profile(0x1225F66L, "ecology.query.bound", 1.0, 0.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> profile.query(new SkyIslandEcologicalCandidateQueryWindow(
                        0.0,
                        2_000.0,
                        0.0,
                        2_000.0)));
    }

    @Test
    void resultRejectsUnorderedOrForeignCandidates() {
        SkyIslandEcologicalCandidateLatticeProfile profile =
                profile(0x1225077L, "ecology.query.result", 12.0, 2.0);
        SkyIslandEcologicalCandidateQueryWindow window =
                new SkyIslandEcologicalCandidateQueryWindow(-30.0, 30.0, -30.0, 30.0);
        List<SkyIslandEcologicalPlacementCandidate> canonical =
                profile.query(window).candidates();
        assertTrue(canonical.size() >= 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandEcologicalCandidateWindowResult(
                        profile,
                        window,
                        List.of(canonical.get(1), canonical.get(0))));

        SkyIslandEcologicalCandidateLatticeProfile foreign =
                profile(0x1225077L, "ecology.query.foreign", 12.0, 2.0);
        SkyIslandEcologicalPlacementCandidate foreignCandidate =
                foreign.query(window).candidates().getFirst();
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandEcologicalCandidateWindowResult(
                        profile,
                        window,
                        List.of(foreignCandidate)));
    }

    private static SkyIslandEcologicalCandidateLatticeProfile profile(
            long rootSeed,
            String namespace,
            double cellPitch,
            double maxAxisJitter) {
        return SkyIslandEcologicalCandidateLatticeProfile.current(
                rootSeed,
                namespace,
                cellPitch,
                maxAxisJitter);
    }
}
