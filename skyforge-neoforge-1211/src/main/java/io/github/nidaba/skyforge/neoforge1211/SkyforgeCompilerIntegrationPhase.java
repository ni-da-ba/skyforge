package io.github.nidaba.skyforge.neoforge1211;

/** Explicit lifecycle phases shared by compiler-program integration fixtures. */
enum SkyforgeCompilerIntegrationPhase {
    DEPENDENCY_RESOLUTION,
    SERVER_BOOT,
    FIXTURE_PLACEMENT,
    ASSEMBLY,
    MECHANISM_INITIALIZATION,
    CLIENT_INTERACTION,
    PHYSICS_PROGRESSION,
    PERSISTENCE_RELOAD,
    TEARDOWN,
    COMPLETE
}
