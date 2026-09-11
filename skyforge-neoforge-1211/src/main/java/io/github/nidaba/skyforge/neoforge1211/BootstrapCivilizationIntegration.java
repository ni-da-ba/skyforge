package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.content.BootstrapFreightOpportunity;
import java.util.Objects;

/**
 * Implementation binding for the one accepted Bootstrap freight opportunity.
 *
 * <p>This class deliberately consumes the Content-owned opportunity without selecting another
 * producer, consumer, commodity, route, payment, or service policy. It only maps the accepted
 * semantic settlement keys to persistent runtime identities and forwards the authoritative
 * custody transitions to the per-level state container.
 */
final class BootstrapCivilizationIntegration {
    static final String GUILD_CARGO_TRANSFER_CAPABILITY = "CARGO_TRANSFER";

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
        return Objects.requireNonNull(state, "state").deliver(opportunity.contractId(), cargoId);
    }

    /** Binds only the accepted Guild cargo interface, through one explicit tracked anchor. */
    void bindGuildCargoTransfer(SkyforgeCivilizationRuntimeState state, String anchorId) {
        Objects.requireNonNull(state, "state").bindCapability(
                settlements.guildConsumerSettlementId(), GUILD_CARGO_TRANSFER_CAPABILITY, anchorId);
    }

    void guildCargoAnchorAvailabilityChanged(SkyforgeCivilizationRuntimeState state, String anchorId, boolean available) {
        Objects.requireNonNull(state, "state").anchorAvailabilityChanged(
                settlements.guildConsumerSettlementId(), GUILD_CARGO_TRANSFER_CAPABILITY, anchorId, available);
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
