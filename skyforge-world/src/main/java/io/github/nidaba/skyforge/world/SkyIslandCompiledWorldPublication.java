package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import java.util.List;
import java.util.Objects;

/**
 * AUTH-0058 immutable backend-neutral publication capability for one accepted compiled regional
 * world.
 *
 * <p>A publication can only be constructed from an AUTH-0057 accepted-convergence compilation.
 * Downstream adapters that require proof-backed authored world data should consume this type rather
 * than a raw world catalog.
 */
public record SkyIslandCompiledWorldPublication(
        SkyIslandCompiledWorldPublicationId id,
        SkyIslandAcceptedConvergenceCompilation compilation) {

    public SkyIslandCompiledWorldPublication {
        id = Objects.requireNonNull(id, "id");
        compilation = Objects.requireNonNull(compilation, "compilation");

        if (!compilation.convergence().accepted()) {
            throw new IllegalArgumentException(
                    "AUTH-0058 publication requires accepted convergence");
        }
        if (!compilation.reproducedPreflight().admitted()) {
            throw new IllegalArgumentException(
                    "AUTH-0058 publication requires admitted reproduced preflight");
        }
        if (!compilation.supportBundle().fullyCertified()) {
            throw new IllegalArgumentException(
                    "AUTH-0058 publication requires a fully certified support bundle");
        }
        if (id.archipelagoRootSeed()
                != compilation.supportBundle().catalog().rootSeed()) {
            throw new IllegalArgumentException(
                    "publication identity root differs from compiled world catalog root");
        }

        SkyIslandArchipelagoPlan acceptedPlan =
                compilation.convergence().freshPlan().orElseThrow();
        if (acceptedPlan.rootSeed() != id.archipelagoRootSeed()) {
            throw new IllegalArgumentException(
                    "publication identity root differs from accepted convergence root");
        }
        if (compilation.supportBundle().certifiedCount()
                != compilation.supportBundle().catalog().volumeCount()) {
            throw new IllegalArgumentException(
                    "publication support-certificate set is not complete");
        }
    }

    /** Exact accepted AUTH-0056 convergence report carried by the AUTH-0057 binding. */
    public SkyIslandSupportConvergenceReport acceptedConvergence() {
        return compilation.convergence();
    }

    /** Exact accepted fresh plan whose proof-backed compilation is being published. */
    public SkyIslandArchipelagoPlan acceptedPlan() {
        return compilation.convergence().freshPlan().orElseThrow();
    }

    /** Exact compiled world catalog exposed by this publication capability. */
    public SkyIslandWorldCatalog catalog() {
        return compilation.supportBundle().catalog();
    }

    /** Exact deterministic plan-order support-certificate set bound to the catalog. */
    public List<SkyIslandWorldVolumeSupportCertificate> supportCertificates() {
        return compilation.supportBundle().certificates();
    }

    /** Stable plan-order catalog identity independent of object allocation identity. */
    public List<SkyIslandWorldVolumeId> catalogIdentity() {
        return catalog().volumes().stream().map(SkyIslandWorldVolume::id).toList();
    }

    /** Number of independently compiled volumes carried by the publication. */
    public int volumeCount() {
        return catalog().volumeCount();
    }
}
