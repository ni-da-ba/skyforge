package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * F3D partitioned ordinary-span compatibility evidence.
 *
 * <p>This plan grants no terrain authority. It proves whether ordinary geometry between explicit
 * F3B/F3C transitions can solve and independently re-pass D2.
 */
public record SkyIslandOrdinarySpanPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandConfluenceHeadCompatibilityPlan confluencePlan,
        SkyIslandCascadeHeadCompatibilityPlan cascadePlan,
        SkyIslandConfluenceCascadeHeadCompatibilityPlan jointPlan,
        List<SkyIslandOrdinarySpanOutcome> outcomes) {

    public SkyIslandOrdinarySpanPlan(
            SkyIslandDescriptor descriptor,
            SkyIslandConfluenceHeadCompatibilityPlan confluencePlan,
            SkyIslandCascadeHeadCompatibilityPlan cascadePlan,
            List<SkyIslandOrdinarySpanOutcome> outcomes) {
        this(
                descriptor,
                confluencePlan,
                cascadePlan,
                new SkyIslandConfluenceCascadeHeadCompatibilityPlan(
                        descriptor, cascadePlan.transitionGeometry(), List.of()),
                outcomes);
    }


    public SkyIslandOrdinarySpanPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        confluencePlan = Objects.requireNonNull(confluencePlan, "confluencePlan");
        cascadePlan = Objects.requireNonNull(cascadePlan, "cascadePlan");
        jointPlan = Objects.requireNonNull(jointPlan, "jointPlan");
        outcomes = List.copyOf(outcomes);
        outcomes.forEach(value -> Objects.requireNonNull(value, "ordinary span outcome"));
        if (!confluencePlan.transitionGeometry().equals(cascadePlan.transitionGeometry())
                || !jointPlan.transitionGeometry().equals(cascadePlan.transitionGeometry())) {
            throw new IllegalArgumentException(
                    "ordinary-span transition plans must share geometry");
        }
        if (!descriptor.equals(confluencePlan.descriptor())
                || !descriptor.equals(cascadePlan.descriptor())
                || !descriptor.equals(jointPlan.descriptor())) {
            throw new IllegalArgumentException(
                    "ordinary-span plan descriptor must match transition plans");
        }
    }
}
