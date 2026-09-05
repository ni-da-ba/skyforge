package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoGroupPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupMemberPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompiler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compiles an accepted archipelago hierarchy into a backend-queryable bounded world catalog. */
public final class SkyIslandWorldCatalogCompiler {
    private final SkyIslandMorphologySpecCompiler morphologyCompiler =
            new SkyIslandMorphologySpecCompiler();

    /**
     * Compiles every island independently and wraps it in conservative backend query bounds.
     *
     * <p>Horizontal bounds use the accepted explicit member reservation. Vertical bounds are
     * supplied explicitly because arbitrary morphology providers are not required to obey built-in
     * descriptor height semantics. No provider or morphology-family switch occurs in this layer.
     */
    public SkyIslandWorldCatalog compile(
            SkyIslandArchipelagoPlan plan,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandWorldVerticalReservation verticalReservation) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(verticalReservation, "verticalReservation");
        ArrayList<SkyIslandWorldVolume> volumes = new ArrayList<>(plan.totalMemberCount());

        for (SkyIslandArchipelagoGroupPlan group : plan.groups()) {
            List<CompiledSkyIslandVolume> compiledGroup =
                    morphologyCompiler.compile(group.groupPlan(), registry);
            for (int memberOrdinal = 0; memberOrdinal < compiledGroup.size(); memberOrdinal++) {
                SkyIslandGroupMemberPlan member = group.groupPlan().members().get(memberOrdinal);
                CompiledSkyIslandVolume compiled = compiledGroup.get(memberOrdinal);
                var descriptor = member.descriptor();
                double radius = member.reservedHorizontalRadius();
                WorldBounds bounds = new WorldBounds(
                        descriptor.centerX() - radius,
                        descriptor.centerX() + radius,
                        descriptor.suspensionElevation() - verticalReservation.belowSuspension(),
                        descriptor.suspensionElevation() + verticalReservation.aboveSuspension(),
                        descriptor.centerZ() - radius,
                        descriptor.centerZ() + radius);
                SkyIslandWorldVolumeId id = new SkyIslandWorldVolumeId(
                        plan.rootSeed(),
                        group.identifier(),
                        group.ordinal(),
                        memberOrdinal,
                        descriptor.seed());
                volumes.add(new SkyIslandWorldVolume(id, bounds, compiled));
            }
        }
        return new SkyIslandWorldCatalog(plan.rootSeed(), volumes);
    }

    /**
     * Compiles the same world catalog while carrying every available AUTH-0052 provider-spec
     * support certificate.
     *
     * <p>The existing query reservation remains unchanged. If a certified support envelope exceeds
     * that reservation, compilation fails rather than publishing a query catalog that could cull
     * real geometry.
     */
    public SkyIslandWorldCatalogSupportBundle compileWithSupport(
            SkyIslandArchipelagoPlan plan,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandWorldVerticalReservation verticalReservation) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(verticalReservation, "verticalReservation");

        ArrayList<SkyIslandWorldVolume> volumes =
                new ArrayList<>(plan.totalMemberCount());
        ArrayList<SkyIslandWorldVolumeSupportCertificate> certificates =
                new ArrayList<>();

        for (SkyIslandArchipelagoGroupPlan group : plan.groups()) {
            for (int memberOrdinal = 0;
                    memberOrdinal < group.groupPlan().memberCount();
                    memberOrdinal++) {
                SkyIslandGroupMemberPlan member =
                        group.groupPlan().members().get(memberOrdinal);
                var compilation =
                        morphologyCompiler.compileWithSupport(
                                member.descriptor(),
                                member.morphology(),
                                registry);
                CompiledSkyIslandVolume compiled = compilation.volume();
                var descriptor = member.descriptor();
                double radius = member.reservedHorizontalRadius();
                WorldBounds bounds =
                        new WorldBounds(
                                descriptor.centerX() - radius,
                                descriptor.centerX() + radius,
                                descriptor.suspensionElevation()
                                        - verticalReservation.belowSuspension(),
                                descriptor.suspensionElevation()
                                        + verticalReservation.aboveSuspension(),
                                descriptor.centerZ() - radius,
                                descriptor.centerZ() + radius);
                SkyIslandWorldVolumeId id =
                        new SkyIslandWorldVolumeId(
                                plan.rootSeed(),
                                group.identifier(),
                                group.ordinal(),
                                memberOrdinal,
                                descriptor.seed());
                SkyIslandWorldVolume volume =
                        new SkyIslandWorldVolume(id, bounds, compiled);
                volumes.add(volume);
                compilation.supportEnvelope()
                        .ifPresent(
                                envelope ->
                                        certificates.add(
                                                new SkyIslandWorldVolumeSupportCertificate(
                                                        volume, envelope)));
            }
        }

        SkyIslandWorldCatalog catalog =
                new SkyIslandWorldCatalog(plan.rootSeed(), volumes);
        return new SkyIslandWorldCatalogSupportBundle(catalog, certificates);
    }

    /**
     * Builds an AUTH-0055 immutable re-plan proposal from matching original intent, exact plan,
     * AUTH-0054 synthesis, current vertical reservation, and explicit author margin.
     *
     * <p>The builder validates provenance by replaying the original request only. It does not
     * execute the candidate request.
     */
    public SkyIslandSupportReplanProposal proposeSupportAwareReplan(
            SkyIslandArchipelagoRequest originalRequest,
            SkyIslandArchipelagoPlan originalPlan,
            SkyIslandSupportReservationRequirementSynthesis synthesis,
            SkyIslandWorldVerticalReservation originalVerticalReservation,
            SkyIslandSupportReplanMargin authorMargin) {
        return new SkyIslandSupportReplanProposalBuilder()
                .propose(
                        originalRequest,
                        originalPlan,
                        synthesis,
                        originalVerticalReservation,
                        authorMargin);
    }

    /**
     * Synthesizes AUTH-0054 admission-safe reservation minima for the exact deterministic plan.
     *
     * <p>The result is advisory and immutable. Applying larger horizontal/group reservations
     * requires constructing a fresh planning request and re-running deterministic placement.
     */
    public SkyIslandSupportReservationRequirementSynthesis synthesizeSupportReservationRequirements(
            SkyIslandArchipelagoPlan plan,
            SkyIslandMorphologyProviderRegistry registry) {
        return new SkyIslandSupportReservationRequirementSynthesizer()
                .synthesize(plan, registry);
    }

    /**
     * Evaluates AUTH-0053 support/reservation admission without compiling procedural graphs.
     */
    public SkyIslandSupportReservationPreflightReport preflightSupportReservations(
            SkyIslandArchipelagoPlan plan,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandWorldVerticalReservation verticalReservation) {
        return new SkyIslandSupportReservationPreflight()
                .evaluate(plan, registry, verticalReservation);
    }

    /**
     * Compiles a fully proof-backed world catalog only after AUTH-0053 accepts every exact member
     * and consumed reservation assumption.
     *
     * <p>Preflight runs before primary/full-volume compilation. Provider support certification may
     * still construct a secondary-factor contribution in order to consume its declared analytical
     * envelope.
     */
    public SkyIslandWorldCatalogSupportBundle compileProofBacked(
            SkyIslandArchipelagoPlan plan,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandWorldVerticalReservation verticalReservation) {
        SkyIslandSupportReservationPreflightReport preflight =
                preflightSupportReservations(plan, registry, verticalReservation);
        preflight.requireAdmitted();

        SkyIslandWorldCatalogSupportBundle bundle =
                compileWithSupport(plan, registry, verticalReservation);
        if (!bundle.fullyCertified()) {
            throw new IllegalStateException(
                    "AUTH-0053 admitted plan produced a partially certified world bundle");
        }
        return bundle;
    }
}
