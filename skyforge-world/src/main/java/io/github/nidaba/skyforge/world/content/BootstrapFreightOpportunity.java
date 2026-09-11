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

    public BootstrapFreightOpportunity {
        contractId = requireId(contractId, "contractId");
        producerSettlementId = requireId(producerSettlementId, "producerSettlementId");
        consumerSettlementId = requireId(consumerSettlementId, "consumerSettlementId");
        producerSpecialization = Objects.requireNonNull(producerSpecialization, "producerSpecialization");
        consumerNeed = Objects.requireNonNull(consumerNeed, "consumerNeed");
        commodity = Objects.requireNonNull(commodity, "commodity");
        if (producerSettlementId.equals(consumerSettlementId) || quantity <= 0 || !routine || !recognizedRoute
                || !physicalCargoRequired || mandatoryTutorialChore) {
            throw new IllegalArgumentException("Bootstrap freight must be a positive, post-tutorial routine physical delivery between settlements");
        }
        if (producerSpecialization != ProducerSpecialization.COPPER_EXTRACTION
                || consumerNeed != ConsumerNeed.ENGINEERING_COPPER_INPUT
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
