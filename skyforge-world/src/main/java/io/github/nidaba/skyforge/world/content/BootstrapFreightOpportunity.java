package io.github.nidaba.skyforge.world.content;

import io.github.nidaba.skyforge.world.SkyIslandBaseMetalKind;
import java.util.Objects;

/** CONTENT C27 first post-tutorial, routine copper freight contract semantic. */
public record BootstrapFreightOpportunity(
        String contractId,
        String producerSettlementId,
        String consumerSettlementId,
        ProducerSpecialization producerSpecialization,
        ConsumerNeed consumerNeed,
        ConsumerCapability requiredConsumerCapability,
        SkyIslandBaseMetalKind commodity,
        int quantity,
        boolean routine,
        boolean recognizedRoute,
        boolean physicalCargoRequired,
        boolean mandatoryTutorialChore) {
    public enum ProducerSpecialization {
        COPPER_EXTRACTION
    }

    public enum ConsumerNeed {
        ENGINEERING_COPPER_INPUT
    }

    /**
     * Content-owned consumer capability required to complete the physical-cargo contract.
     *
     * <p>The concrete tracked anchor and its lifecycle remain Implementation-owned. The stable
     * capability id lets that adapter consume this semantic requirement without making a quest or
     * an active Hall UI the cargo authority.
     */
    public enum ConsumerCapability {
        CARGO_TRANSFER("CARGO_TRANSFER");

        private final String capabilityId;

        ConsumerCapability(String capabilityId) {
            this.capabilityId = capabilityId;
        }

        public String capabilityId() {
            return capabilityId;
        }
    }

    public BootstrapFreightOpportunity {
        contractId = requireId(contractId, "contractId");
        producerSettlementId = requireId(producerSettlementId, "producerSettlementId");
        consumerSettlementId = requireId(consumerSettlementId, "consumerSettlementId");
        producerSpecialization = Objects.requireNonNull(producerSpecialization, "producerSpecialization");
        consumerNeed = Objects.requireNonNull(consumerNeed, "consumerNeed");
        requiredConsumerCapability = Objects.requireNonNull(requiredConsumerCapability, "requiredConsumerCapability");
        commodity = Objects.requireNonNull(commodity, "commodity");
        if (producerSettlementId.equals(consumerSettlementId) || quantity <= 0 || !routine || !recognizedRoute
                || !physicalCargoRequired || mandatoryTutorialChore) {
            throw new IllegalArgumentException("Bootstrap freight must be a positive, post-tutorial routine physical delivery between settlements");
        }
        if (producerSpecialization != ProducerSpecialization.COPPER_EXTRACTION
                || consumerNeed != ConsumerNeed.ENGINEERING_COPPER_INPUT
                || requiredConsumerCapability != ConsumerCapability.CARGO_TRANSFER
                || commodity != SkyIslandBaseMetalKind.COPPER
                || SkyIslandBaseMetalContentPolicy.policyFor(commodity).firstFlightCritical()) {
            throw new IllegalArgumentException("first freight uses the accepted post-flight copper policy");
        }
    }

    public static BootstrapFreightOpportunity firstOpportunity() {
        return new BootstrapFreightOpportunity(
                "bootstrap-copper-freight-01",
                "bootstrap-copper-producer",
                "bootstrap-guild-consumer",
                ProducerSpecialization.COPPER_EXTRACTION,
                ConsumerNeed.ENGINEERING_COPPER_INPUT,
                ConsumerCapability.CARGO_TRANSFER,
                SkyIslandBaseMetalKind.COPPER,
                1,
                true,
                true,
                true,
                false);
    }

    private static String requireId(String value, String name) {
        value = Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
