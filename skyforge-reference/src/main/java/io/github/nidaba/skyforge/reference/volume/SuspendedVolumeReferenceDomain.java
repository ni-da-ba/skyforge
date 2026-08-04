package io.github.nidaba.skyforge.reference.volume;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;

/** Canonical signal-free descriptor and evidence domain accepted for the first volume proof. */
public final class SuspendedVolumeReferenceDomain {
    /** Stable textual declaration of the canonical storage traversal. */
    public static final String TRAVERSAL_ORDER = "x-fastest, then z, then y";

    private static final SkyIslandVolumeDescriptor DESCRIPTOR = new SkyIslandVolumeDescriptor(
            SkyIslandVolumeDescriptor.SCHEMA_VERSION,
            0L,
            0.0,
            0.0,
            256.0,
            256.0,
            96.0,
            128.0,
            64.0,
            Math.PI / 6.0,
            0.65,
            0.60,
            0.25,
            0.0,
            32.0);

    private static final VolumeGridSpec GRID =
            new VolumeGridSpec(-384.0, 384.0, 0.0, 512.0, -384.0, 384.0, 193, 129, 193);

    private SuspendedVolumeReferenceDomain() {}

    /** Returns the canonical descriptor for the first signal-free suspended-volume specimen. */
    public static SkyIslandVolumeDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /** Returns the exact inclusive evidence domain and resolution for the first volume specimen. */
    public static VolumeGridSpec grid() {
        return GRID;
    }
}
