package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Continuous backend-neutral AUTH-0035 field around realized AUTH-0034 lithologic contacts.
 *
 * <p>The field has compact support around finite contact faces. It never creates material inside
 * authored cave void and carries explicit parent-contact/assemblage provenance for the strongest
 * local transition.
 */
public final class SkyIslandLithologicContactRealizationField {
    private static final double SUPPORT_RADIUS_MULTIPLIER = 2.25;
    private static final double MIN_REPORTED_INFLUENCE = 0.015;

    private final SkyIslandLithologicContactRealizationPlan plan;
    private final SkyIslandSubsurfaceMaterialFieldSet material;
    private final double radius;
    private final List<PatchContext> patches;

    private SkyIslandLithologicContactRealizationField(
            SkyIslandDescriptor descriptor) {
        this.plan = SkyIslandLithologicContactRealizationPlanner.plan(
                Objects.requireNonNull(descriptor, "descriptor"));
        this.material = SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
        this.radius = descriptor.nominalRadius();
        this.patches = flatten(plan);
    }

    public static SkyIslandLithologicContactRealizationField create(
            SkyIslandDescriptor descriptor) {
        return new SkyIslandLithologicContactRealizationField(descriptor);
    }

    public SkyIslandLithologicContactRealizationPlan plan() {
        return plan;
    }

    public SkyIslandLithologicContactRealizationSample sample(
            SkyIslandSubsurfacePosition position) {
        Objects.requireNonNull(position, "position");
        SkyIslandSubsurfaceMaterialSample materialSample = material.sample(position);
        if (!materialSample.owned()) {
            return SkyIslandLithologicContactRealizationSample.outside();
        }
        if (!materialSample.materialPresent()) {
            return SkyIslandLithologicContactRealizationSample.authoredVoid();
        }

        PatchContext best = null;
        double bestInfluence = 0.0;
        double bestSignedNormal = 0.0;

        for (PatchContext context : patches) {
            SkyIslandLithologicContactPatch patch = context.patch();
            double distance = distanceToPatch(position, patch);
            double supportRadius =
                    SUPPORT_RADIUS_MULTIPLIER * patch.normalizedHalfWidth();
            if (distance >= supportRadius) {
                continue;
            }
            double geometric = compactInfluence(distance / supportRadius);
            double contrastWeight = 0.54
                    + 0.46 * SkyIslandLithologicContactRealizationPlanner.primaryContrast(
                            context.contact());
            double influence = clamp01(geometric * contrastWeight);
            if (influence > bestInfluence) {
                best = context;
                bestInfluence = influence;
                bestSignedNormal = signedNormalDistance(position, patch);
            }
        }

        if (best == null || bestInfluence < MIN_REPORTED_INFLUENCE) {
            return SkyIslandLithologicContactRealizationSample.hostWithoutContact();
        }

        SkyIslandLithologicContactPatch patch = best.patch();
        SkyIslandLithologicContact contact = best.contact();
        double positiveSideWeight = smoothstep(
                -patch.normalizedHalfWidth(),
                patch.normalizedHalfWidth(),
                bestSignedNormal);
        double firstWeight = patch.firstAssemblageOnNegativeSide()
                ? 1.0 - positiveSideWeight
                : positiveSideWeight;
        double secondWeight = 1.0 - firstWeight;

        return new SkyIslandLithologicContactRealizationSample(
                true,
                true,
                contact.contactId(),
                contact.kind(),
                contact.firstAssemblageId(),
                contact.secondAssemblageId(),
                bestInfluence,
                firstWeight,
                secondWeight,
                clamp01(bestInfluence * contact.hostFabricContrast()),
                clamp01(bestInfluence * contact.alterationContrast()),
                clamp01(bestInfluence * contact.hydrologicContrast()),
                clamp01(bestInfluence * contact.mineralizationContrast()),
                clamp01(bestInfluence * patch.caveExposureInfluence()));
    }

    private double distanceToPatch(
            SkyIslandSubsurfacePosition position,
            SkyIslandLithologicContactPatch patch) {
        double dx = (position.x() - patch.center().x()) / radius;
        double dz = (position.z() - patch.center().z()) / radius;
        double dd = position.depthFraction() - patch.center().depthFraction();
        double horizontalSpan = patch.horizontalHalfSpanNormalized();
        double depthSpan = patch.depthHalfSpan();

        return switch (patch.axis()) {
            case X -> Math.sqrt(
                    square(dx)
                            + square(excess(Math.abs(dz), horizontalSpan))
                            + square(excess(Math.abs(dd), depthSpan)));
            case DEPTH -> Math.sqrt(
                    square(dd)
                            + square(excess(Math.abs(dx), horizontalSpan))
                            + square(excess(Math.abs(dz), horizontalSpan)));
            case Z -> Math.sqrt(
                    square(dz)
                            + square(excess(Math.abs(dx), horizontalSpan))
                            + square(excess(Math.abs(dd), depthSpan)));
        };
    }

    private double signedNormalDistance(
            SkyIslandSubsurfacePosition position,
            SkyIslandLithologicContactPatch patch) {
        return switch (patch.axis()) {
            case X -> (position.x() - patch.center().x()) / radius;
            case DEPTH -> position.depthFraction() - patch.center().depthFraction();
            case Z -> (position.z() - patch.center().z()) / radius;
        };
    }

    private static List<PatchContext> flatten(
            SkyIslandLithologicContactRealizationPlan plan) {
        List<PatchContext> result = new ArrayList<>(plan.patchCount());
        for (SkyIslandLithologicContactRealization realization : plan.realizations()) {
            for (SkyIslandLithologicContactPatch patch : realization.patches()) {
                result.add(new PatchContext(realization.contact(), patch));
            }
        }
        return List.copyOf(result);
    }

    private static double compactInfluence(double normalizedDistance) {
        double t = clamp01(1.0 - normalizedDistance);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double excess(double value, double span) {
        return Math.max(0.0, value - span);
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record PatchContext(
            SkyIslandLithologicContact contact,
            SkyIslandLithologicContactPatch patch) {}
}
