package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * AUTH-0050 backend-neutral post-realization authored-overlap admission auditor.
 *
 * <p>Safe admission is proof-based. Finite AUTH-0048 witness search may prove that overlap exists,
 * but failure to find a witness is never treated as proof that continuous volumes are disjoint.
 */
public final class SkyIslandAuthoredOverlapAdmissionAuditor {
    private static final int WITNESS_AXIS_SAMPLES = 17;

    private final SkyIslandAuthoredRealizationCatalog catalog;
    private final SkyIslandAuthoredOverlapAdmissionPolicy policy;
    private final SkyIslandAuthoredRealizationSupportCatalog supportCatalog;

    public SkyIslandAuthoredOverlapAdmissionAuditor(
            SkyIslandAuthoredRealizationCatalog catalog,
            SkyIslandAuthoredOverlapAdmissionPolicy policy) {
        this(
                catalog,
                policy,
                new SkyIslandAuthoredRealizationSupportCatalog(
                        Objects.requireNonNull(catalog, "catalog"),
                        java.util.List.of()));
    }

    /**
     * Creates a support-aware AUTH-0050 auditor.
     *
     * <p>AUTH-0051 support bounds may tighten proof geometry, but never replace the original
     * associated WorldBounds used for candidate/witness search.
     */
    public SkyIslandAuthoredOverlapAdmissionAuditor(
            SkyIslandAuthoredRealizationCatalog catalog,
            SkyIslandAuthoredOverlapAdmissionPolicy policy,
            SkyIslandAuthoredRealizationSupportCatalog supportCatalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.supportCatalog = Objects.requireNonNull(supportCatalog, "supportCatalog");
        validateSupportCatalog();
        validatePolicyReferences();
    }

    public SkyIslandAuthoredOverlapAdmissionReport audit() {
        ArrayList<SkyIslandAuthoredOverlapPairAudit> audits = new ArrayList<>();
        var associations = catalog.associations();
        for (int firstIndex = 0; firstIndex < associations.size(); firstIndex++) {
            SkyIslandAuthoredRealizationAssociation first =
                    associations.get(firstIndex);
            for (int secondIndex = firstIndex + 1;
                    secondIndex < associations.size();
                    secondIndex++) {
                SkyIslandAuthoredRealizationAssociation second =
                        associations.get(secondIndex);
                audits.add(auditPair(first, second));
            }
        }
        return new SkyIslandAuthoredOverlapAdmissionReport(
                catalog.authoredWorldSeed(),
                catalog.realizationRootSeed(),
                audits);
    }

    private SkyIslandAuthoredOverlapPairAudit auditPair(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        SkyIslandAuthoredOverlapPairRule rule = policy.ruleFor(first, second);
        WorldBounds firstBounds = first.realizedVolume().bounds();
        WorldBounds secondBounds = second.realizedVolume().bounds();
        WorldBounds firstProofBounds = proofBounds(first);
        WorldBounds secondProofBounds = proofBounds(second);
        boolean boundsIntersect = firstBounds.intersects(secondBounds);
        boolean proofBoundsIntersect = firstProofBounds.intersects(secondProofBounds);
        boolean supportDisjoint = nativeSupportDiscsDisjoint(first, second);
        double verticalGap =
                conservativeVerticalGap(firstProofBounds, secondProofBounds);

        if (rule.mode() == SkyIslandAuthoredOverlapMode.COMPOSE) {
            Coordinate3 witness =
                    boundsIntersect && !supportDisjoint
                            ? findOverlapWitness(first, second, firstBounds, secondBounds)
                            : null;
            return new SkyIslandAuthoredOverlapPairAudit(
                    first,
                    second,
                    rule,
                    SkyIslandAuthoredOverlapPairStatus.ACCEPTED_EXPLICIT_COMPOSITION,
                    boundsIntersect,
                    supportDisjoint,
                    verticalGap,
                    witness);
        }

        if (rule.mode() == SkyIslandAuthoredOverlapMode.STACKED) {
            boolean sameHorizontalCenter = sameHorizontalCenter(first, second);
            if (sameHorizontalCenter
                    && verticalGap >= rule.minimumVerticalSeparation()) {
                return new SkyIslandAuthoredOverlapPairAudit(
                        first,
                        second,
                        rule,
                        SkyIslandAuthoredOverlapPairStatus.CERTIFIED_STACKED,
                        boundsIntersect,
                        supportDisjoint,
                        verticalGap,
                        null);
            }
            Coordinate3 witness =
                    boundsIntersect
                            ? findOverlapWitness(first, second, firstBounds, secondBounds)
                            : null;
            return new SkyIslandAuthoredOverlapPairAudit(
                    first,
                    second,
                    rule,
                    witness == null
                            ? SkyIslandAuthoredOverlapPairStatus.REJECTED_STACK_REQUIREMENT
                            : SkyIslandAuthoredOverlapPairStatus.REJECTED_WITNESSED_OVERLAP,
                    boundsIntersect,
                    supportDisjoint,
                    verticalGap,
                    witness);
        }

        if (!proofBoundsIntersect || supportDisjoint) {
            return new SkyIslandAuthoredOverlapPairAudit(
                    first,
                    second,
                    rule,
                    SkyIslandAuthoredOverlapPairStatus.CERTIFIED_SEPARATE,
                    boundsIntersect,
                    supportDisjoint,
                    verticalGap,
                    null);
        }

        Coordinate3 witness =
                findOverlapWitness(first, second, firstBounds, secondBounds);
        return new SkyIslandAuthoredOverlapPairAudit(
                first,
                second,
                rule,
                witness == null
                        ? SkyIslandAuthoredOverlapPairStatus.REJECTED_UNCERTIFIED_SEPARATION
                        : SkyIslandAuthoredOverlapPairStatus.REJECTED_WITNESSED_OVERLAP,
                boundsIntersect,
                supportDisjoint,
                verticalGap,
                witness);
    }

    private Coordinate3 findOverlapWitness(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second,
            WorldBounds firstBounds,
            WorldBounds secondBounds) {
        double minimumX = Math.max(firstBounds.minimumX(), secondBounds.minimumX());
        double maximumX = Math.min(firstBounds.maximumX(), secondBounds.maximumX());
        double minimumY = Math.max(firstBounds.minimumY(), secondBounds.minimumY());
        double maximumY = Math.min(firstBounds.maximumY(), secondBounds.maximumY());
        double minimumZ = Math.max(firstBounds.minimumZ(), secondBounds.minimumZ());
        double maximumZ = Math.min(firstBounds.maximumZ(), secondBounds.maximumZ());

        if (minimumX > maximumX || minimumY > maximumY || minimumZ > maximumZ) {
            return null;
        }

        SkyIslandAuthoredRealizationCatalog pairCatalog =
                new SkyIslandAuthoredRealizationCatalog(
                        catalog.authoredWorldSeed(),
                        catalog.realizationRootSeed(),
                        java.util.List.of(first, second));
        SkyIslandAuthoredRealizationOwnershipResolver resolver =
                new SkyIslandAuthoredRealizationOwnershipResolver(pairCatalog);

        for (int yi = 0; yi < WITNESS_AXIS_SAMPLES; yi++) {
            double y = sampleAxis(minimumY, maximumY, yi);
            for (int zi = 0; zi < WITNESS_AXIS_SAMPLES; zi++) {
                double z = sampleAxis(minimumZ, maximumZ, zi);
                for (int xi = 0; xi < WITNESS_AXIS_SAMPLES; xi++) {
                    double x = sampleAxis(minimumX, maximumX, xi);
                    Coordinate3 point = new Coordinate3(x, y, z);
                    if (resolver.resolve(point).authoredOwners().size() == 2) {
                        return point;
                    }
                }
            }
        }
        return null;
    }

    private static double sampleAxis(double minimum, double maximum, int index) {
        if (minimum == maximum) {
            return minimum;
        }
        return minimum
                + (maximum - minimum)
                        * index
                        / (WITNESS_AXIS_SAMPLES - 1.0);
    }

    private static boolean nativeSupportDiscsDisjoint(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        var firstPhysical = first.realizedVolume().compiledVolume().descriptor();
        var secondPhysical = second.realizedVolume().compiledVolume().descriptor();
        double centerDistance =
                Math.hypot(
                        firstPhysical.centerX() - secondPhysical.centerX(),
                        firstPhysical.centerZ() - secondPhysical.centerZ());
        double maximumNativeReach =
                first.authoredDescriptor().nominalRadius()
                        + second.authoredDescriptor().nominalRadius();
        return centerDistance >= maximumNativeReach;
    }

    private static boolean sameHorizontalCenter(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        var firstPhysical = first.realizedVolume().compiledVolume().descriptor();
        var secondPhysical = second.realizedVolume().compiledVolume().descriptor();
        return Double.doubleToLongBits(firstPhysical.centerX())
                        == Double.doubleToLongBits(secondPhysical.centerX())
                && Double.doubleToLongBits(firstPhysical.centerZ())
                        == Double.doubleToLongBits(secondPhysical.centerZ());
    }

    private static double conservativeVerticalGap(
            WorldBounds first,
            WorldBounds second) {
        if (first.maximumY() < second.minimumY()) {
            return second.minimumY() - first.maximumY();
        }
        if (second.maximumY() < first.minimumY()) {
            return first.minimumY() - second.maximumY();
        }
        return 0.0;
    }

    private WorldBounds proofBounds(
            SkyIslandAuthoredRealizationAssociation association) {
        return supportCatalog.certificateFor(association)
                .map(SkyIslandAuthoredRealizationSupportCertificate::supportBounds)
                .orElseGet(() -> association.realizedVolume().bounds());
    }

    private void validateSupportCatalog() {
        SkyIslandAuthoredRealizationCatalog supportAssociations =
                supportCatalog.associationCatalog();
        if (supportAssociations.authoredWorldSeed() != catalog.authoredWorldSeed()
                || supportAssociations.realizationRootSeed() != catalog.realizationRootSeed()
                || !supportAssociations.associations().equals(catalog.associations())) {
            throw new IllegalArgumentException(
                    "AUTH-0051 support catalog must describe the exact AUTH-0050 association catalog");
        }
    }

    private void validatePolicyReferences() {
        Set<String> tokens = new HashSet<>();
        for (SkyIslandAuthoredRealizationAssociation association :
                catalog.associations()) {
            tokens.add(association.canonicalToken());
        }
        for (SkyIslandAuthoredOverlapPairKey key :
                policy.explicitRules().keySet()) {
            if (!tokens.contains(key.firstAssociationToken())
                    || !tokens.contains(key.secondAssociationToken())) {
                throw new IllegalArgumentException(
                        "AUTH-0050 policy references an association absent from the catalog");
            }
        }
    }
}
