package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class SkyforgeCivilizationRuntimeStateTest {
    private static final String PRODUCER = SkyforgeCivilizationRuntimeState.stableSettlementId("bootstrap-copper-producer");
    private static final String CONSUMER = SkyforgeCivilizationRuntimeState.stableSettlementId("bootstrap-guild-consumer");

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
        assertEquals(100, reloaded.settlement(CONSUMER).stock());
        assertEquals(17, reloaded.settledPaymentTotal());
        assertEquals(delivered, reloaded.deliver(physical.id(), physical.cargoId()));
        SkyforgeCivilizationRuntimeState afterReload = SkyforgeCivilizationRuntimeState.load(reloaded.save());
        assertEquals(delivered, afterReload.deliver(physical.id(), physical.cargoId()));
        assertEquals(17, afterReload.settledPaymentTotal());
    }

    @Test
    void explicitAnchorEventsDriveCapabilityWithoutSettlementScanning() {
        SkyforgeCivilizationRuntimeState state = initialized();
        state.bindCapability(CONSUMER, "CARGO_TRANSFER", "guild-cargo-terminal-01");
        state.anchorAvailabilityChanged(CONSUMER, "CARGO_TRANSFER", "guild-cargo-terminal-01", false);
        assertEquals(SkyforgeCivilizationRuntimeState.CapabilityStatus.OFFLINE, state.capability(CONSUMER, "CARGO_TRANSFER").status());
        SkyforgeCivilizationRuntimeState restored = SkyforgeCivilizationRuntimeState.load(state.save());
        restored.anchorAvailabilityChanged(CONSUMER, "CARGO_TRANSFER", "guild-cargo-terminal-01", true);
        assertEquals(SkyforgeCivilizationRuntimeState.CapabilityStatus.OPERATIONAL, restored.capability(CONSUMER, "CARGO_TRANSFER").status());
    }

    private static SkyforgeCivilizationRuntimeState initialized() {
        SkyforgeCivilizationRuntimeState state = new SkyforgeCivilizationRuntimeState();
        state.registerSettlement(PRODUCER, 100, 0);
        state.registerSettlement(CONSUMER, 0, 0);
        return state;
    }
}
