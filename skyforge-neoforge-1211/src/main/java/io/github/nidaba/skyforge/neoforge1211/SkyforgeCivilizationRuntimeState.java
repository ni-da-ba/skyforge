package io.github.nidaba.skyforge.neoforge1211;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Small authoritative Bootstrap civilization specimen.
 *
 * <p>This is deliberately an implementation-owned state container, not a civilization planner or
 * a Minecraft inventory adapter. A later physical cargo adapter must realize the {@code cargoId}
 * supplied here exactly once; it must not manufacture a second semantic shipment.
 */
final class SkyforgeCivilizationRuntimeState {
    static final int SCHEMA_VERSION = 1;
    static final long MAX_RECONCILIATION_TICKS = 24_000L;
    private static final int MAX_SETTLEMENTS = 128;
    private static final int MAX_SHIPMENTS = 512;
    private static final int MAX_CAPABILITIES = 16;

    enum Activity { ACTIVATED, DORMANT }
    enum CapabilityStatus { OPERATIONAL, OFFLINE }
    enum ShipmentStatus { SOURCE_COMMITTED, PHYSICAL_CUSTODY, DELIVERED }

    record SettlementSnapshot(String id, long stock, Activity activity, long lastAuthoritativeTime) {}
    record ShipmentSnapshot(
            String id,
            String sourceSettlementId,
            String destinationSettlementId,
            String commodityId,
            long quantity,
            long payment,
            ShipmentStatus status,
            String cargoId,
            boolean paymentSettled) {}
    record CapabilitySnapshot(String settlementId, String capabilityId, String anchorId, CapabilityStatus status) {}

    private final TreeMap<String, Settlement> settlements = new TreeMap<>();
    private final TreeMap<String, Shipment> shipments = new TreeMap<>();
    private final TreeMap<String, Capability> capabilities = new TreeMap<>();
    private long settledPaymentTotal;

    /** Stable across process, chunk and entity lifecycle; the input is a semantic cluster key. */
    static String stableSettlementId(String clusterKey) {
        clusterKey = requireId(clusterKey, "clusterKey");
        return "settlement:" + UUID.nameUUIDFromBytes(("skyforge:civilization:v1:" + clusterKey)
                .getBytes(StandardCharsets.UTF_8));
    }

    void registerSettlement(String settlementId, long initialStock, long authoritativeTime) {
        settlementId = requireId(settlementId, "settlementId");
        if (initialStock < 0 || authoritativeTime < 0) throw new IllegalArgumentException("invalid Bootstrap settlement state");
        Settlement next = new Settlement(initialStock, Activity.ACTIVATED, authoritativeTime);
        Settlement prior = settlements.putIfAbsent(settlementId, next);
        if (prior != null && !prior.equals(next)) throw new IllegalArgumentException("settlement identity already has authoritative state: " + settlementId);
        if (settlements.size() > MAX_SETTLEMENTS) throw new IllegalStateException("Bootstrap settlement safety cap exceeded");
    }

    SettlementSnapshot settlement(String settlementId) {
        Settlement state = settlementRequired(settlementId);
        return new SettlementSnapshot(settlementId, state.stock, state.activity, state.lastAuthoritativeTime);
    }

    void dormant(String settlementId) {
        Settlement state = settlementRequired(settlementId);
        state.activity = Activity.DORMANT;
    }

    /** Performs no continuous simulation; one activation consumes at most the explicit bounded interval. */
    long reactivate(String settlementId, long now) {
        Settlement state = settlementRequired(settlementId);
        if (now < state.lastAuthoritativeTime) throw new IllegalArgumentException("authoritative time cannot move backwards");
        long reconciled = Math.min(now - state.lastAuthoritativeTime, MAX_RECONCILIATION_TICKS);
        state.lastAuthoritativeTime += reconciled;
        state.activity = Activity.ACTIVATED;
        return reconciled;
    }

    ShipmentSnapshot authorizePickup(
            String shipmentId,
            String sourceSettlementId,
            String destinationSettlementId,
            String commodityId,
            long quantity,
            long payment) {
        shipmentId = requireId(shipmentId, "shipmentId");
        sourceSettlementId = requireId(sourceSettlementId, "sourceSettlementId");
        destinationSettlementId = requireId(destinationSettlementId, "destinationSettlementId");
        commodityId = requireId(commodityId, "commodityId");
        if (sourceSettlementId.equals(destinationSettlementId) || quantity <= 0 || payment < 0) {
            throw new IllegalArgumentException("invalid Bootstrap shipment authority");
        }
        Shipment existing = shipments.get(shipmentId);
        if (existing != null) {
            if (!existing.matches(sourceSettlementId, destinationSettlementId, commodityId, quantity, payment)) {
                throw new IllegalArgumentException("shipment retry changes authoritative terms: " + shipmentId);
            }
            return existing.snapshot(shipmentId);
        }
        Settlement source = settlementRequired(sourceSettlementId);
        settlementRequired(destinationSettlementId);
        if (source.stock < quantity) throw new IllegalStateException("insufficient authoritative source stock");
        source.stock -= quantity;
        Shipment shipment = new Shipment(sourceSettlementId, destinationSettlementId, commodityId, quantity, payment);
        shipments.put(shipmentId, shipment);
        if (shipments.size() > MAX_SHIPMENTS) throw new IllegalStateException("Bootstrap shipment safety cap exceeded");
        return shipment.snapshot(shipmentId);
    }

    /** Idempotently creates the one physical-custody token for a source-committed shipment. */
    ShipmentSnapshot materializePhysicalCargo(String shipmentId) {
        Shipment shipment = shipmentRequired(shipmentId);
        if (shipment.status == ShipmentStatus.SOURCE_COMMITTED) {
            shipment.cargoId = "cargo:" + shipmentId;
            shipment.status = ShipmentStatus.PHYSICAL_CUSTODY;
        }
        return shipment.snapshot(shipmentId);
    }

    /** Consumes the sole physical-custody token, then settles destination stock and payment once. */
    ShipmentSnapshot deliver(String shipmentId, String cargoId) {
        Shipment shipment = shipmentRequired(shipmentId);
        cargoId = requireId(cargoId, "cargoId");
        if (!cargoId.equals(shipment.cargoId)) throw new IllegalArgumentException("cargo does not own this shipment");
        if (shipment.status == ShipmentStatus.SOURCE_COMMITTED) throw new IllegalStateException("physical cargo has not been realized");
        if (shipment.status == ShipmentStatus.PHYSICAL_CUSTODY) {
            Settlement destination = settlementRequired(shipment.destinationSettlementId);
            destination.stock = Math.addExact(destination.stock, shipment.quantity);
            shipment.status = ShipmentStatus.DELIVERED;
            // The identifier remains durable provenance for retry/reload. Its status proves the physical
            // token is consumed; it is not an active cargo authority after delivery.
            if (!shipment.paymentSettled) {
                settledPaymentTotal = Math.addExact(settledPaymentTotal, shipment.payment);
                shipment.paymentSettled = true;
            }
        }
        return shipment.snapshot(shipmentId);
    }

    void bindCapability(String settlementId, String capabilityId, String anchorId) {
        settlementRequired(settlementId);
        capabilityId = requireId(capabilityId, "capabilityId");
        anchorId = requireId(anchorId, "anchorId");
        String key = capabilityKey(settlementId, capabilityId);
        Capability next = new Capability(anchorId, CapabilityStatus.OPERATIONAL);
        Capability prior = capabilities.putIfAbsent(key, next);
        if (prior != null && !prior.equals(next)) throw new IllegalArgumentException("capability already has a different explicit anchor");
        if (capabilities.size() > MAX_CAPABILITIES) throw new IllegalStateException("Bootstrap capability safety cap exceeded");
    }

    /** Called by the tracked anchor lifecycle only; this class performs no settlement/chunk scan. */
    void anchorAvailabilityChanged(String settlementId, String capabilityId, String anchorId, boolean available) {
        Capability capability = capabilityRequired(settlementId, capabilityId);
        if (!capability.anchorId.equals(requireId(anchorId, "anchorId"))) throw new IllegalArgumentException("unregistered capability anchor");
        capability.status = available ? CapabilityStatus.OPERATIONAL : CapabilityStatus.OFFLINE;
    }

    CapabilitySnapshot capability(String settlementId, String capabilityId) {
        Capability state = capabilityRequired(settlementId, capabilityId);
        return new CapabilitySnapshot(settlementId, capabilityId, state.anchorId, state.status);
    }

    long settledPaymentTotal() { return settledPaymentTotal; }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", SCHEMA_VERSION);
        tag.putLong("settled_payment_total", settledPaymentTotal);
        tag.put("settlements", saveSettlements());
        tag.put("shipments", saveShipments());
        tag.put("capabilities", saveCapabilities());
        return tag;
    }

    static SkyforgeCivilizationRuntimeState load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tag.getInt("schema_version") != SCHEMA_VERSION) throw new IllegalStateException("unsupported civilization state schema");
        SkyforgeCivilizationRuntimeState state = new SkyforgeCivilizationRuntimeState();
        state.settledPaymentTotal = tag.getLong("settled_payment_total");
        if (state.settledPaymentTotal < 0) throw new IllegalStateException("invalid settled payment total");
        loadSettlements(state, tag.getList("settlements", Tag.TAG_COMPOUND));
        loadShipments(state, tag.getList("shipments", Tag.TAG_COMPOUND));
        loadCapabilities(state, tag.getList("capabilities", Tag.TAG_COMPOUND));
        return state;
    }

    private ListTag saveSettlements() {
        ListTag entries = new ListTag();
        settlements.forEach((id, state) -> { CompoundTag entry = new CompoundTag(); entry.putString("id", id); entry.putLong("stock", state.stock); entry.putString("activity", state.activity.name()); entry.putLong("time", state.lastAuthoritativeTime); entries.add(entry); });
        return entries;
    }
    private ListTag saveShipments() {
        ListTag entries = new ListTag();
        shipments.forEach((id, state) -> { CompoundTag entry = new CompoundTag(); entry.putString("id", id); entry.putString("source", state.sourceSettlementId); entry.putString("destination", state.destinationSettlementId); entry.putString("commodity", state.commodityId); entry.putLong("quantity", state.quantity); entry.putLong("payment", state.payment); entry.putString("status", state.status.name()); entry.putString("cargo", state.cargoId); entry.putBoolean("payment_settled", state.paymentSettled); entries.add(entry); });
        return entries;
    }
    private ListTag saveCapabilities() {
        ListTag entries = new ListTag();
        capabilities.forEach((key, state) -> { CompoundTag entry = new CompoundTag(); entry.putString("key", key); entry.putString("anchor", state.anchorId); entry.putString("status", state.status.name()); entries.add(entry); });
        return entries;
    }
    private static void loadSettlements(SkyforgeCivilizationRuntimeState state, ListTag entries) {
        cap(entries, MAX_SETTLEMENTS, "settlements");
        for (int i = 0; i < entries.size(); i++) { CompoundTag entry = entries.getCompound(i); String id = requireId(entry.getString("id"), "persisted settlement id"); long stock = entry.getLong("stock"); long time = entry.getLong("time"); if (stock < 0 || time < 0 || state.settlements.putIfAbsent(id, new Settlement(stock, enumValue(Activity.class, entry.getString("activity")), time)) != null) throw new IllegalStateException("invalid persisted settlement"); }
    }
    private static void loadShipments(SkyforgeCivilizationRuntimeState state, ListTag entries) {
        cap(entries, MAX_SHIPMENTS, "shipments");
        for (int i = 0; i < entries.size(); i++) { CompoundTag entry = entries.getCompound(i); String id = requireId(entry.getString("id"), "persisted shipment id"); Shipment shipment = new Shipment(requireId(entry.getString("source"), "persisted source"), requireId(entry.getString("destination"), "persisted destination"), requireId(entry.getString("commodity"), "persisted commodity"), entry.getLong("quantity"), entry.getLong("payment")); shipment.status = enumValue(ShipmentStatus.class, entry.getString("status")); shipment.cargoId = entry.getString("cargo"); shipment.paymentSettled = entry.getBoolean("payment_settled"); if (shipment.quantity <= 0 || shipment.payment < 0 || shipment.sourceSettlementId.equals(shipment.destinationSettlementId) || !state.settlements.containsKey(shipment.sourceSettlementId) || !state.settlements.containsKey(shipment.destinationSettlementId) || (shipment.status == ShipmentStatus.SOURCE_COMMITTED && !shipment.cargoId.isBlank()) || (shipment.status == ShipmentStatus.PHYSICAL_CUSTODY && shipment.cargoId.isBlank()) || (shipment.status == ShipmentStatus.DELIVERED && (shipment.cargoId.isBlank() || !shipment.paymentSettled)) || state.shipments.putIfAbsent(id, shipment) != null) throw new IllegalStateException("invalid persisted shipment"); }
    }
    private static void loadCapabilities(SkyforgeCivilizationRuntimeState state, ListTag entries) {
        cap(entries, MAX_CAPABILITIES, "capabilities");
        for (int i = 0; i < entries.size(); i++) { CompoundTag entry = entries.getCompound(i); String key = entry.getString("key"); int separator = key.indexOf('|'); if (separator <= 0 || separator == key.length() - 1 || !state.settlements.containsKey(key.substring(0, separator))) throw new IllegalStateException("invalid persisted capability"); if (state.capabilities.putIfAbsent(key, new Capability(requireId(entry.getString("anchor"), "persisted anchor"), enumValue(CapabilityStatus.class, entry.getString("status")))) != null) throw new IllegalStateException("duplicate persisted capability"); }
    }
    private static void cap(ListTag entries, int maximum, String label) { if (entries.size() > maximum) throw new IllegalStateException("civilization " + label + " safety cap exceeded"); }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) { try { return Enum.valueOf(type, value); } catch (IllegalArgumentException exception) { throw new IllegalStateException("invalid persisted " + type.getSimpleName(), exception); } }
    private Settlement settlementRequired(String id) { Settlement state = settlements.get(requireId(id, "settlementId")); if (state == null) throw new IllegalArgumentException("unknown settlement: " + id); return state; }
    private Shipment shipmentRequired(String id) { Shipment state = shipments.get(requireId(id, "shipmentId")); if (state == null) throw new IllegalArgumentException("unknown shipment: " + id); return state; }
    private Capability capabilityRequired(String settlementId, String capabilityId) { Capability state = capabilities.get(capabilityKey(settlementId, requireId(capabilityId, "capabilityId"))); if (state == null) throw new IllegalArgumentException("unbound capability"); return state; }
    private static String capabilityKey(String settlementId, String capabilityId) { return requireId(settlementId, "settlementId") + "|" + capabilityId; }
    private static String requireId(String value, String name) { value = Objects.requireNonNull(value, name); if (value.isBlank() || value.indexOf('|') >= 0) throw new IllegalArgumentException(name + " must be a nonblank stable identifier"); return value; }
    private static final class Settlement { long stock; Activity activity; long lastAuthoritativeTime; Settlement(long stock, Activity activity, long lastAuthoritativeTime) { this.stock = stock; this.activity = activity; this.lastAuthoritativeTime = lastAuthoritativeTime; } @Override public boolean equals(Object o) { return o instanceof Settlement other && stock == other.stock && activity == other.activity && lastAuthoritativeTime == other.lastAuthoritativeTime; } }
    private static final class Shipment { final String sourceSettlementId; final String destinationSettlementId; final String commodityId; final long quantity; final long payment; ShipmentStatus status = ShipmentStatus.SOURCE_COMMITTED; String cargoId = ""; boolean paymentSettled; Shipment(String sourceSettlementId, String destinationSettlementId, String commodityId, long quantity, long payment) { this.sourceSettlementId = sourceSettlementId; this.destinationSettlementId = destinationSettlementId; this.commodityId = commodityId; this.quantity = quantity; this.payment = payment; } boolean matches(String source, String destination, String commodity, long candidateQuantity, long candidatePayment) { return sourceSettlementId.equals(source) && destinationSettlementId.equals(destination) && commodityId.equals(commodity) && quantity == candidateQuantity && payment == candidatePayment; } ShipmentSnapshot snapshot(String id) { return new ShipmentSnapshot(id, sourceSettlementId, destinationSettlementId, commodityId, quantity, payment, status, cargoId, paymentSettled); } }
    private static final class Capability { final String anchorId; CapabilityStatus status; Capability(String anchorId, CapabilityStatus status) { this.anchorId = anchorId; this.status = status; } @Override public boolean equals(Object o) { return o instanceof Capability other && anchorId.equals(other.anchorId) && status == other.status; } }
}
