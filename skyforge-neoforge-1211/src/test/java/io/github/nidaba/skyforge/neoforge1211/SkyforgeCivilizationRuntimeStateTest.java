package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.content.BootstrapFreightOpportunity;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class SkyforgeCivilizationRuntimeStateTest {
    private static final String PRODUCER = SkyforgeCivilizationRuntimeState.stableSettlementId("bootstrap-copper-producer");
    private static final String CONSUMER = SkyforgeCivilizationRuntimeState.stableSettlementId("bootstrap-guild-consumer");
    private static final String GUILD_CARGO_TRANSFER_CAPABILITY =
            BootstrapFreightOpportunity.ConsumerCapability.CARGO_TRANSFER.capabilityId();

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

    private static SkyforgeCivilizationRuntimeState initialized() {
        SkyforgeCivilizationRuntimeState state = new SkyforgeCivilizationRuntimeState();
        state.registerSettlement(PRODUCER, 100, 0);
        state.registerSettlement(CONSUMER, 0, 0);
        return state;
    }
}
