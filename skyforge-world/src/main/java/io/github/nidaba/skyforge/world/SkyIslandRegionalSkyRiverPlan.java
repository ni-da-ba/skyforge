package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * AUTH-0094 exact regional floating sky-river semantic plan.
 *
 * <p>The plan binds one exceptional cross-island hydrologic phenomenon to an exact AUTH-0087
 * published authored-realization region. It retains exact participating-island provenance and one
 * ordered backend-neutral 3D guide trajectory.
 *
 * <p>It does not define Minecraft fluid cells, physical terrain attachment, flow simulation,
 * rendering, rarity, reward, hazard, persistence, or runtime lifecycle.
 */
public final class SkyIslandRegionalSkyRiverPlan {
    private final SkyIslandPublishedAuthoredRealizationBinding binding;
    private final long phenomenonKey;
    private final SkyIslandAuthoredRealizationAssociation source;
    private final SkyIslandAuthoredRealizationAssociation sink;
    private final List<SkyIslandRegionalSkyRiverWaypoint> trajectory;

    SkyIslandRegionalSkyRiverPlan(
            SkyIslandPublishedAuthoredRealizationBinding binding,
            long phenomenonKey,
            SkyIslandAuthoredRealizationAssociation source,
            SkyIslandAuthoredRealizationAssociation sink,
            List<SkyIslandRegionalSkyRiverWaypoint> trajectory) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.phenomenonKey = phenomenonKey;
        this.source = requireExactAssociation(binding, "source", source);
        this.sink = requireExactAssociation(binding, "sink", sink);
        if (this.source.equals(this.sink)) {
            throw new IllegalArgumentException("regional sky-river source and sink must differ");
        }

        Objects.requireNonNull(trajectory, "trajectory");
        this.trajectory = List.copyOf(trajectory);
        if (this.trajectory.size() != 4) {
            throw new IllegalArgumentException(
                    "AUTH-0094 first-generation sky-river trajectory requires exactly four guide points");
        }
        for (int index = 0; index < this.trajectory.size(); index++) {
            Objects.requireNonNull(this.trajectory.get(index), "trajectory waypoint");
            if (index > 0
                    && !(this.trajectory.get(index).parameter()
                            > this.trajectory.get(index - 1).parameter())) {
                throw new IllegalArgumentException(
                        "regional sky-river trajectory parameters must be strictly increasing");
            }
        }
        if (Double.doubleToLongBits(this.trajectory.getFirst().parameter())
                        != Double.doubleToLongBits(0.0)
                || Double.doubleToLongBits(this.trajectory.getLast().parameter())
                        != Double.doubleToLongBits(1.0)) {
            throw new IllegalArgumentException(
                    "regional sky-river trajectory must begin at parameter 0 and end at 1");
        }

        requireReferenceEndpoint("source", this.source, this.trajectory.getFirst());
        requireReferenceEndpoint("sink", this.sink, this.trajectory.getLast());
    }

    public SkyIslandPublishedAuthoredRealizationBinding binding() {
        return binding;
    }

    public SkyIslandCompiledWorldPublicationId publicationId() {
        return binding.publication().id();
    }

    public long authoredWorldSeed() {
        return binding.authoredWorldSeed();
    }

    public long phenomenonKey() {
        return phenomenonKey;
    }

    public SkyIslandAuthoredRealizationAssociation source() {
        return source;
    }

    public SkyIslandAuthoredRealizationAssociation sink() {
        return sink;
    }

    public List<SkyIslandRegionalSkyRiverWaypoint> trajectory() {
        return trajectory;
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfskyRiver:v1:%s:%016x:%016x",
                publicationId().canonicalToken(),
                authoredWorldSeed(),
                phenomenonKey);
    }

    private static SkyIslandAuthoredRealizationAssociation requireExactAssociation(
            SkyIslandPublishedAuthoredRealizationBinding binding,
            String role,
            SkyIslandAuthoredRealizationAssociation association) {
        Objects.requireNonNull(association, role);
        SkyIslandAuthoredRealizationAssociation exact =
                binding.associationCatalog()
                        .associationFor(association.realizedVolumeId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "regional sky-river " + role
                                        + " is not part of the exact AUTH-0087 binding"));
        if (!exact.equals(association)) {
            throw new IllegalArgumentException(
                    "regional sky-river " + role
                            + " must retain the exact AUTH-0087 association value");
        }
        return association;
    }

    private static void requireReferenceEndpoint(
            String role,
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandRegionalSkyRiverWaypoint endpoint) {
        var descriptor = association.realizedVolume().compiledVolume().descriptor();
        double expectedX = descriptor.centerX();
        double expectedY = descriptor.suspensionElevation() + descriptor.upperElevation();
        double expectedZ = descriptor.centerZ();
        if (Double.doubleToLongBits(endpoint.worldX()) != Double.doubleToLongBits(expectedX)
                || Double.doubleToLongBits(endpoint.worldY()) != Double.doubleToLongBits(expectedY)
                || Double.doubleToLongBits(endpoint.worldZ()) != Double.doubleToLongBits(expectedZ)) {
            throw new IllegalArgumentException(
                    "regional sky-river " + role
                            + " endpoint must equal the participant upper nominal reference point");
        }
    }
}
