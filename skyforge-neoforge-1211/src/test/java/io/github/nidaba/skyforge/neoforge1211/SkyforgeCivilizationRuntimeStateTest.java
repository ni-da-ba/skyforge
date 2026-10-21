package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompilation;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompiler;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublication;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublisher;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandPublishedAuthoredRealizationBinding;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceExecutor;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceOutcome;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceReport;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanMargin;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposal;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposalBuilder;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesis;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesizer;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandWorldVerticalReservation;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.content.BellancaOnboardingStateMachine;
import io.github.nidaba.skyforge.world.content.BootstrapFreightOpportunity;
import io.github.nidaba.skyforge.world.content.BootstrapGuildDestinationPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class SkyforgeCivilizationRuntimeStateTest {
    private static final String PRODUCER = SkyforgeCivilizationRuntimeState.stableSettlementId("bootstrap-copper-producer");
    private static final String CONSUMER = SkyforgeCivilizationRuntimeState.stableSettlementId("bootstrap-guild-consumer");
    private static final String GUILD_CARGO_TRANSFER_CAPABILITY =
            BootstrapFreightOpportunity.ConsumerCapability.CARGO_TRANSFER.capabilityId();
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    @Test
    void persistentDormancyAndBoundedReactivationDoNotWarmStartStock() {
        assertEquals(PRODUCER, SkyforgeCivilizationRuntimeState.stableSettlementId("bootstrap-copper-producer"));
        assertFalse(PRODUCER.equals(CONSUMER));
        SkyforgeCivilizationRuntimeState state = initialized();
        state.dormant(PRODUCER);
        assertEquals(SkyforgeCivilizationRuntimeState.Activity.DORMANT, state.settlement(PRODUCER).activity());
        assertEquals(SkyforgeCivilizationRuntimeState.MAX_RECONCILIATION_TICKS, state.reactivate(PRODUCER, 99_999));
        assertEquals(100, state.settlement(PRODUCER).stock());
        CompoundTag encoded = state.save();
        assertEquals(SkyforgeCivilizationRuntimeState.SCHEMA_VERSION, encoded.getInt("schema_version"));
        assertEquals(state.settlement(PRODUCER), SkyforgeCivilizationRuntimeState.load(encoded).settlement(PRODUCER));

        CompoundTag legacy = encoded.copy();
        legacy.putInt("schema_version", 1);
        SkyforgeCivilizationRuntimeState migrated = SkyforgeCivilizationRuntimeState.load(legacy);
        assertFalse(migrated.hasBellancaOnboarding());
        assertEquals(SkyforgeCivilizationRuntimeState.SCHEMA_VERSION, migrated.save().getInt("schema_version"));

        CompoundTag unknownVersion = encoded.copy();
        unknownVersion.putInt("schema_version", 99);
        assertThrows(IllegalStateException.class, () -> SkyforgeCivilizationRuntimeState.load(unknownVersion));
    }

    @Test
    void custodyAndPaymentAreExactlyOnceAcrossRetryAndReload() {
        SkyforgeCivilizationRuntimeState state = initialized();
        var authorized = state.authorizePickup("bootstrap-copper-freight-01", PRODUCER, CONSUMER, "copper", 10, 17);
        assertEquals(90, state.settlement(PRODUCER).stock());
        assertEquals(authorized, state.authorizePickup("bootstrap-copper-freight-01", PRODUCER, CONSUMER, "copper", 10, 17));
        var physical = state.materializePhysicalCargo(authorized.id());
        assertEquals(SkyforgeCivilizationRuntimeState.ShipmentStatus.PHYSICAL_CUSTODY, physical.status());
        SkyforgeCivilizationRuntimeState reloaded = SkyforgeCivilizationRuntimeState.load(state.save());
        var delivered = reloaded.deliver(physical.id(), physical.cargoId());
        assertEquals(SkyforgeCivilizationRuntimeState.ShipmentStatus.DELIVERED, delivered.status());
        assertEquals(10, reloaded.settlement(CONSUMER).stock());
        assertEquals(100, reloaded.settlement(PRODUCER).stock() + reloaded.settlement(CONSUMER).stock());
        assertEquals(17, reloaded.settledPaymentTotal());
        assertEquals(delivered, reloaded.deliver(physical.id(), physical.cargoId()));
        SkyforgeCivilizationRuntimeState afterReload = SkyforgeCivilizationRuntimeState.load(reloaded.save());
        assertEquals(delivered, afterReload.deliver(physical.id(), physical.cargoId()));
        assertEquals(17, afterReload.settledPaymentTotal());
    }

    @Test
    void explicitAnchorEventsDriveCapabilityWithoutSettlementScanning() {
        SkyforgeCivilizationRuntimeState state = initialized();
        state.bindCapability(CONSUMER, GUILD_CARGO_TRANSFER_CAPABILITY, "guild-cargo-terminal-01");
        state.anchorAvailabilityChanged(CONSUMER, GUILD_CARGO_TRANSFER_CAPABILITY, "guild-cargo-terminal-01", false);
        assertEquals(SkyforgeCivilizationRuntimeState.CapabilityStatus.OFFLINE,
                state.capability(CONSUMER, GUILD_CARGO_TRANSFER_CAPABILITY).status());
        SkyforgeCivilizationRuntimeState restored = SkyforgeCivilizationRuntimeState.load(state.save());
        restored.anchorAvailabilityChanged(CONSUMER, GUILD_CARGO_TRANSFER_CAPABILITY, "guild-cargo-terminal-01", true);
        assertEquals(SkyforgeCivilizationRuntimeState.CapabilityStatus.OPERATIONAL,
                restored.capability(CONSUMER, GUILD_CARGO_TRANSFER_CAPABILITY).status());
    }

    @Test
    void acceptedBootstrapFreightBindsStableSettlementsAndSettlesOnePhysicalDeliveryAcrossReload() {
        BootstrapCivilizationIntegration integration = new BootstrapCivilizationIntegration();
        BootstrapFreightOpportunity opportunity = integration.opportunity();
        var identities = integration.settlements();
        assertEquals(PRODUCER, identities.producerSettlementId());
        assertEquals(CONSUMER, identities.guildConsumerSettlementId());
        assertEquals(opportunity.contractId(), "bootstrap-copper-freight-01");
        assertEquals("copper", opportunity.commodity().name().toLowerCase(java.util.Locale.ROOT));

        SkyforgeCivilizationRuntimeState state = new SkyforgeCivilizationRuntimeState();
        integration.registerSettlements(state, opportunity.quantity(), 40L);
        integration.bindGuildCargoTransfer(state, "bootstrap-guild-cargo-terminal-01");
        assertEquals(SkyforgeCivilizationRuntimeState.CapabilityStatus.OPERATIONAL,
                state.capability(CONSUMER, GUILD_CARGO_TRANSFER_CAPABILITY).status());

        var authorized = integration.authorizePickup(state, 17L);
        assertEquals(SkyforgeCivilizationRuntimeState.ShipmentStatus.SOURCE_COMMITTED, authorized.status());
        assertEquals(0L, state.settlement(PRODUCER).stock());
        assertEquals(authorized, integration.authorizePickup(state, 17L));
        var cargo = integration.materializePhysicalCargo(state);
        assertEquals(SkyforgeCivilizationRuntimeState.ShipmentStatus.PHYSICAL_CUSTODY, cargo.status());
        assertFalse(cargo.cargoId().isBlank());
        assertEquals(cargo, integration.materializePhysicalCargo(state));

        SkyforgeCivilizationRuntimeState reloaded = SkyforgeCivilizationRuntimeState.load(state.save());
        integration.guildCargoAnchorAvailabilityChanged(reloaded, "bootstrap-guild-cargo-terminal-01", false);
        assertEquals(SkyforgeCivilizationRuntimeState.CapabilityStatus.OFFLINE,
                reloaded.capability(CONSUMER, GUILD_CARGO_TRANSFER_CAPABILITY).status());
        assertThrows(IllegalStateException.class, () -> integration.deliver(reloaded, cargo.cargoId()));
        assertEquals(SkyforgeCivilizationRuntimeState.ShipmentStatus.PHYSICAL_CUSTODY,
                reloaded.materializePhysicalCargo(cargo.id()).status());
        assertEquals(0L, reloaded.settlement(CONSUMER).stock());
        assertEquals(0L, reloaded.settledPaymentTotal());
        integration.guildCargoAnchorAvailabilityChanged(reloaded, "bootstrap-guild-cargo-terminal-01", true);
        var delivered = integration.deliver(reloaded, cargo.cargoId());
        assertEquals(SkyforgeCivilizationRuntimeState.ShipmentStatus.DELIVERED, delivered.status());
        assertTrue(delivered.paymentSettled());
        assertEquals(opportunity.quantity(), reloaded.settlement(CONSUMER).stock());
        assertEquals(opportunity.quantity(), reloaded.settlement(PRODUCER).stock() + reloaded.settlement(CONSUMER).stock());
        assertEquals(17L, reloaded.settledPaymentTotal());
        assertEquals(delivered, integration.deliver(reloaded, cargo.cargoId()));

        SkyforgeCivilizationRuntimeState settledReload = SkyforgeCivilizationRuntimeState.load(reloaded.save());
        assertEquals(delivered, integration.deliver(settledReload, cargo.cargoId()));
        assertEquals(17L, settledReload.settledPaymentTotal());
        integration.guildCargoAnchorAvailabilityChanged(settledReload, "bootstrap-guild-cargo-terminal-01", false);
        assertThrows(IllegalStateException.class, () -> integration.deliver(settledReload, cargo.cargoId()));
        integration.guildCargoAnchorAvailabilityChanged(settledReload, "bootstrap-guild-cargo-terminal-01", true);
        assertEquals(SkyforgeCivilizationRuntimeState.CapabilityStatus.OPERATIONAL,
                settledReload.capability(CONSUMER, GUILD_CARGO_TRANSFER_CAPABILITY).status());
    }

    @Test
    void bellancaAuthorityPersistsAndDuplicateCallbacksCannotChangeRestitution() {
        BootstrapCivilizationIntegration integration = new BootstrapCivilizationIntegration();
        SkyforgeCivilizationRuntimeState state = new SkyforgeCivilizationRuntimeState();
        assertFalse(state.hasBellancaOnboarding());

        var crash = integration.initializeBellancaOnboarding(state);
        assertEquals(BellancaOnboardingStateMachine.State.CRASHED_BELLANCA, crash.state());
        assertEquals(crash, integration.initializeBellancaOnboarding(state));
        var recovered = integration.recoverBellancaRecorder(state);
        assertTrue(recovered.recorderRecovered());
        assertEquals(recovered, integration.recoverBellancaRecorder(state));

        SkyforgeCivilizationRuntimeState afterRecoveryReload = SkyforgeCivilizationRuntimeState.load(state.save());
        assertEquals(recovered, afterRecoveryReload.bellancaOnboarding());

        BootstrapGuildDestinationPolicy.Candidate hall = eligibleGuildHall();
        var unfavorable = integration.initiateBellancaClaim(afterRecoveryReload, hall);
        assertEquals(BellancaOnboardingStateMachine.State.INITIAL_CLAIM_UNFAVORABLE, unfavorable.state());
        assertEquals(unfavorable, integration.initiateBellancaClaim(afterRecoveryReload, hall));

        SkyforgeCivilizationRuntimeState afterClaimReload =
                SkyforgeCivilizationRuntimeState.load(afterRecoveryReload.save());
        var liability = integration.submitBellancaRecorderEvidence(afterClaimReload);
        assertEquals(BellancaOnboardingStateMachine.State.GUILD_LIABILITY_ESTABLISHED, liability.state());
        assertEquals(liability, integration.submitBellancaRecorderEvidence(afterClaimReload));

        var complete = integration.chooseBellancaRestitution(
                afterClaimReload, BellancaOnboardingStateMachine.Restitution.RETAIN_WRECK_WITH_PARTIAL_PAYOUT);
        assertTrue(complete.tutorialComplete());
        assertEquals(complete, integration.chooseBellancaRestitution(
                afterClaimReload, BellancaOnboardingStateMachine.Restitution.RETAIN_WRECK_WITH_PARTIAL_PAYOUT));
        assertThrows(IllegalArgumentException.class, () -> integration.chooseBellancaRestitution(
                afterClaimReload, BellancaOnboardingStateMachine.Restitution.SCRIP_PAYOUT));

        SkyforgeCivilizationRuntimeState settledReload = SkyforgeCivilizationRuntimeState.load(afterClaimReload.save());
        assertEquals(complete, settledReload.bellancaOnboarding());
        assertEquals(complete, integration.recoverBellancaRecorder(settledReload));
        assertEquals(complete, integration.initiateBellancaClaim(settledReload, hall));
        assertEquals(complete, integration.submitBellancaRecorderEvidence(settledReload));
        assertEquals(complete, integration.chooseBellancaRestitution(
                settledReload, BellancaOnboardingStateMachine.Restitution.RETAIN_WRECK_WITH_PARTIAL_PAYOUT));
    }

    private static BootstrapGuildDestinationPolicy.Candidate eligibleGuildHall() {
        SkyIslandPublishedAuthoredRealizationBinding binding = fixture(467_001L);
        SkyIslandSurfaceAccessCapabilityProfile access = binding.associationCatalog().associations().stream()
                .map(SkyforgeCivilizationRuntimeStateTest::access)
                .filter(profile -> profile.cells().stream().anyMatch(cell ->
                        cell.sourceCell().physicalSurfacePresent()
                                && cell.rays().stream().anyMatch(ray -> ray.observedOpenSample())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("#467 fixture has no AUTH-0096/0097 Guild candidate"));
        return new BootstrapGuildDestinationPolicy().candidates("bootstrap-hall", access, true).getFirst();
    }

    private static SkyIslandSurfaceAccessCapabilityProfile access(SkyIslandAuthoredRealizationAssociation association) {
        return new SkyIslandSurfaceAccessCapabilityProfiler()
                .profile(new SkyIslandSurfaceSiteCapabilityProfiler().profile(association));
    }

    private static SkyIslandPublishedAuthoredRealizationBinding fixture(long rootSeed) {
        SkyIslandCompiledWorldPublication publication = new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(rootSeed), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(authored(80_000L + ordinal++, volume), volume));
        }
        Collections.reverse(associations);
        return new SkyIslandPublishedAuthoredRealizationBinding(publication,
                new SkyIslandAuthoredRealizationCatalog(0x434956343637L, publication.catalog().rootSeed(), associations));
    }

    private static SkyIslandDescriptor authored(long islandKey, SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(0x434956343637L, 467L, 1L, islandKey));
        var realized = volume.compiledVolume().descriptor();
        return new SkyIslandDescriptor(base.schemaVersion(), base.identity(), base.authorshipSeed(),
                realized.hasSemanticMorphologyFamily() ? realized.morphologyFamily() : base.morphologyFamily(),
                realized.nominalRadius(), base.reliefBudget(), 0.72, 0.68, 0.70, 0.70,
                base.exposureTendency(), 0.60, 0.76, base.ecologicalPotential());
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        ProviderMorphologySpec morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF), 0.0, 0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer().synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal = new SkyIslandSupportReplanProposalBuilder().propose(
                request, original, synthesis, ADEQUATE_VERTICAL, SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence = new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("#467 fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(long rootSeed, ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies = List.of(morphology, morphology, morphology);
        SkyIslandGroupTemplate template = new SkyIslandGroupTemplate(
                "civ467", SkyIslandGroupRole.ANCHOR, descriptor(), 360.0, 48.0, 0.0,
                morphologies, new SkyIslandGroupLayout.Chain(0.15, 800.0, 0.0, 0.0, 0.0, 0.0), 1_400.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed, 0.0, 0.0, 320.0, 500.0, List.of(template),
                new SkyIslandArchipelagoLayout.Hub(1_600.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                96.0,
                48.0,
                64.0,
                24.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                24.0);
    }

    private static SkyforgeCivilizationRuntimeState initialized() {
        SkyforgeCivilizationRuntimeState state = new SkyforgeCivilizationRuntimeState();
        state.registerSettlement(PRODUCER, 100, 0);
        state.registerSettlement(CONSUMER, 0, 0);
        return state;
    }
}
