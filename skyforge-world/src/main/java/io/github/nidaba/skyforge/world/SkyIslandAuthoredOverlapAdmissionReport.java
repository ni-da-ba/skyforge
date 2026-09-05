package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** AUTH-0050 immutable catalog-level overlap admission report. */
public record SkyIslandAuthoredOverlapAdmissionReport(
        long authoredWorldSeed,
        long realizationRootSeed,
        List<SkyIslandAuthoredOverlapPairAudit> pairAudits) {

    public SkyIslandAuthoredOverlapAdmissionReport {
        Objects.requireNonNull(pairAudits, "pairAudits");
        ArrayList<SkyIslandAuthoredOverlapPairAudit> ordered =
                new ArrayList<>(pairAudits.size());
        for (SkyIslandAuthoredOverlapPairAudit audit : pairAudits) {
            ordered.add(Objects.requireNonNull(audit, "pair audit"));
        }
        ordered.sort(
                Comparator.comparing(
                        audit ->
                                audit.pair().firstAssociationToken()
                                        + "\n"
                                        + audit.pair().secondAssociationToken()));
        pairAudits = List.copyOf(ordered);
    }

    /** Whether every explicit association pair is admitted by AUTH-0050 policy. */
    public boolean admitted() {
        return pairAudits.stream().allMatch(SkyIslandAuthoredOverlapPairAudit::admitted);
    }

    public long rejectedPairCount() {
        return pairAudits.stream().filter(audit -> !audit.admitted()).count();
    }

    public long witnessedOverlapCount() {
        return pairAudits.stream().filter(audit -> audit.witness().isPresent()).count();
    }
}
