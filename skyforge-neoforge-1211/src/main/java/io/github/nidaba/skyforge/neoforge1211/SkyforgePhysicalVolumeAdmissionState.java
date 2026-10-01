package io.github.nidaba.skyforge.neoforge1211;

/** Lifecycle state for one planned Skyforge volume before destructive Minecraft realization. */
enum SkyforgePhysicalVolumeAdmissionState {
    /** The volume has not yet accumulated sufficient native-occupancy evidence to be committed. */
    PLANNED,

    /** Complete evidence is clear; the volume may be physically realized. */
    ADMITTED,

    /** A physical conflict was observed; the volume must remain absent. */
    REJECTED
}
