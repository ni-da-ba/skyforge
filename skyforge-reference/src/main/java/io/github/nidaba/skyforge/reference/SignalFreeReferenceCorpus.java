package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.island.IslandDescriptor;

/** Stable descriptors used by the signal-free v1 evidence and acceptance corpus. */
public final class SignalFreeReferenceCorpus {
    /** Stable identifier recorded by the evidence package. */
    public static final String STANDARD_ISLAND_ID = "signal-free-island-v1";

    private SignalFreeReferenceCorpus() {}

    /** Returns the canonical descriptor used by the 1024-square golden evidence package. */
    public static IslandDescriptor standardDescriptor() {
        return new IslandDescriptor(
                IslandDescriptor.SCHEMA_VERSION,
                0x534b59464f524745L,
                0.0,
                0.0,
                256.0,
                96.0,
                64.0,
                Math.PI / 6.0,
                0.65,
                0.0,
                32.0);
    }
}
