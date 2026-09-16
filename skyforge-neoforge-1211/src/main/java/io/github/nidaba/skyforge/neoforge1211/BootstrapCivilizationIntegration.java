package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.content.BellancaOnboardingStateMachine;
import io.github.nidaba.skyforge.world.content.BootstrapFreightOpportunity;
import io.github.nidaba.skyforge.world.content.BootstrapGuildDestinationPolicy;
import java.util.Objects;

/**
 * Implementation binding for the accepted Bootstrap civilization transactions.
 *
 * <p>This class deliberately consumes Content-owned semantics without selecting another producer,
 * consumer, commodity, route, payment, Guild service, Bellanca outcome, or restitution value. It
 * maps accepted semantic identities to persistent runtime authority and makes retries non-regressive.
 */
final class BootstrapCivilizationIntegration {
    record SettlementIdentities(String producerSettlementId, String guildConsumerSettlementId) {
        SettlementIdentities {
            producerSettlementId = requireId(producerSettlementId, "producerSettlementId");
            guildConsumerSettlementId = requireId(guildConsumerSettlementId, "guildConsumerSettlementId");
            if (producerSettlementId.equals(guildConsumerSettlementId)) {
                throw new IllegalArgumentException("Bootstrap producer and Guild consumer must remain distinct");
            }
        }
    }

    private final BootstrapFreightOpportunity opportunity;
    private final SettlementIdentities settlements;
    private final BellancaOnboardingStateMachine bellanca = new BellancaOnboardingStateMachine();

    BootstrapCivilizationIntegration() {
        this.opportunity = BootstrapFreightOpportunity.firstOpportunity();
        this.settlements = new SettlementIdentities(
                SkyforgeCivilizationRuntimeState.stableSettlementId(opportunity.producerSettlementId()),
                SkyforgeCivilizationRuntimeState.stableSettlementId(opportunity.consumerSettlementId()));
    }

    SettlementIdentities settlements() {
        return settlements;
    }

    BootstrapFreightOpportunity opportunity() {
        return opportunity;
    }

    /** Registers the accepted producer and Guild consumer under stable runtime identity. */
    void registerSettlements(SkyforgeCivilizationRuntimeState state, long producerInitialStock, long authoritativeTime) {
        state = Objects.requireNonNull(state, "state");
        state.registerSettlement(settlements.producerSettlementId(), producerInitialStock, authoritativeTime);
        state.registerSettlement(settlements.guildConsumerSettlementId(), 0L, authoritativeTime);
    }

    /** Authorizes the Content-owned contract with a caller-supplied, untuned payment amount. */
    SkyforgeCivilizationRuntimeState.ShipmentSnapshot authorizePickup(
            SkyforgeCivilizationRuntimeState state, long payment) {
        state = Objects.requireNonNull(state, "state");
        return state.authorizePickup(
                opportunity.contractId(),
                settlements.producerSettlementId(),
                settlements.guildConsumerSettlementId(),
                commodityId(),
                opportunity.quantity(),
                payment);
    }

    SkyforgeCivilizationRuntimeState.ShipmentSnapshot materializePhysicalCargo(
            SkyforgeCivilizationRuntimeState state) {
        return Objects.requireNonNull(state, "state").materializePhysicalCargo(opportunity.contractId());
    }

    SkyforgeCivilizationRuntimeState.ShipmentSnapshot deliver(
            SkyforgeCivilizationRuntimeState state, String cargoId) {
        state = Objects.requireNonNull(state, "state");
        if (state.capability(settlements.guildConsumerSettlementId(), cargoTransferCapabilityId()).status()
                != SkyforgeCivilizationRuntimeState.CapabilityStatus.OPERATIONAL) {
            throw new IllegalStateException("Bootstrap Guild cargo transfer is not operational");
        }
        return state.deliver(opportunity.contractId(), cargoId);
    }

    /** Binds only the accepted Guild cargo interface, through one explicit tracked anchor. */
    void bindGuildCargoTransfer(SkyforgeCivilizationRuntimeState state, String anchorId) {
        Objects.requireNonNull(state, "state").bindCapability(
                settlements.guildConsumerSettlementId(), cargoTransferCapabilityId(), anchorId);
    }

    void guildCargoAnchorAvailabilityChanged(SkyforgeCivilizationRuntimeState state, String anchorId, boolean available) {
        Objects.requireNonNull(state, "state").anchorAvailabilityChanged(
                settlements.guildConsumerSettlementId(), cargoTransferCapabilityId(), anchorId, available);
    }

    BellancaOnboardingStateMachine.Snapshot initializeBellancaOnboarding(
            SkyforgeCivilizationRuntimeState state) {
        state = Objects.requireNonNull(state, "state");
        BellancaOnboardingStateMachine.Snapshot crash = bellanca.crash();
        state.initializeBellancaOnboarding(crash);
        return state.bellancaOnboarding();
    }

    BellancaOnboardingStateMachine.Snapshot recoverBellancaRecorder(
            SkyforgeCivilizationRuntimeState state) {
        state = Objects.requireNonNull(state, "state");
        BellancaOnboardingStateMachine.Snapshot current = state.bellancaOnboarding();
        if (current.recorderRecovered()) return current;
        BellancaOnboardingStateMachine.Snapshot next = bellanca.recoverRecorder(current);
        state.advanceBellancaOnboarding(current, next);
        return next;
    }

    BellancaOnboardingStateMachine.Snapshot initiateBellancaClaim(
            SkyforgeCivilizationRuntimeState state,
            BootstrapGuildDestinationPolicy.Candidate hall) {
        state = Objects.requireNonNull(state, "state");
        hall = Objects.requireNonNull(hall, "hall");
        if (!hall.resolvesBellancaClaim()) {
            throw new IllegalArgumentException("Bellanca claim requires an eligible Guild Hall");
        }
        BellancaOnboardingStateMachine.Snapshot current = state.bellancaOnboarding();
        if (current.state() != BellancaOnboardingStateMachine.State.CRASHED_BELLANCA) return current;
        BellancaOnboardingStateMachine.Snapshot next = bellanca.initiateClaim(current, hall);
        state.advanceBellancaOnboarding(current, next);
        return next;
    }

    BellancaOnboardingStateMachine.Snapshot submitBellancaRecorderEvidence(
            SkyforgeCivilizationRuntimeState state) {
        state = Objects.requireNonNull(state, "state");
        BellancaOnboardingStateMachine.Snapshot current = state.bellancaOnboarding();
        if (current.state() == BellancaOnboardingStateMachine.State.GUILD_LIABILITY_ESTABLISHED
                || current.state() == BellancaOnboardingStateMachine.State.TUTORIAL_COMPLETE) {
            return current;
        }
        BellancaOnboardingStateMachine.Snapshot next = bellanca.submitRecorderEvidence(current);
        state.advanceBellancaOnboarding(current, next);
        return next;
    }

    BellancaOnboardingStateMachine.Snapshot chooseBellancaRestitution(
            SkyforgeCivilizationRuntimeState state,
            BellancaOnboardingStateMachine.Restitution restitution) {
        state = Objects.requireNonNull(state, "state");
        restitution = Objects.requireNonNull(restitution, "restitution");
        BellancaOnboardingStateMachine.Snapshot current = state.bellancaOnboarding();
        if (current.state() == BellancaOnboardingStateMachine.State.TUTORIAL_COMPLETE) {
            if (current.restitution() != restitution) {
                throw new IllegalArgumentException("Bellanca restitution retry changes authoritative outcome");
            }
            return current;
        }
        BellancaOnboardingStateMachine.Snapshot next = bellanca.chooseRestitution(current, restitution);
        state.advanceBellancaOnboarding(current, next);
        return next;
    }

    private String cargoTransferCapabilityId() {
        return opportunity.requiredConsumerCapability().capabilityId();
    }

    private String commodityId() {
        return opportunity.commodity().name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String requireId(String value, String name) {
        value = Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
