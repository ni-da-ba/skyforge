package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;

/** Immutable wait/failure diagnostic emitted by bounded compiler-program integration fixtures. */
record SkyforgeCompilerIntegrationDiagnostic(
        String capability,
        SkyforgeCompilerIntegrationPhase phase,
        String expectedCondition,
        long startTick,
        long deadlineTick,
        String relevantIds,
        String serverState,
        String clientState,
        String finalDump) {

    SkyforgeCompilerIntegrationDiagnostic {
        capability = requireText(capability, "capability");
        phase = Objects.requireNonNull(phase, "phase");
        expectedCondition = requireText(expectedCondition, "expectedCondition");
        if (deadlineTick < startTick) {
            throw new IllegalArgumentException("deadlineTick must be >= startTick");
        }
        relevantIds = normalize(relevantIds);
        serverState = normalize(serverState);
        clientState = normalize(clientState);
        finalDump = normalize(finalDump);
    }

    boolean expired(long currentTick) {
        return currentTick > deadlineTick;
    }

    SkyforgeCompilerIntegrationDiagnostic withFinalState(
            String ids, String server, String client, String dump) {
        return new SkyforgeCompilerIntegrationDiagnostic(
                capability,
                phase,
                expectedCondition,
                startTick,
                deadlineTick,
                ids,
                server,
                client,
                dump);
    }

    String render() {
        return "capability=" + capability
                + " phase=" + phase
                + " expectedCondition=\"" + expectedCondition + "\""
                + " startTick=" + startTick
                + " deadlineTick=" + deadlineTick
                + " relevantIds=\"" + relevantIds + "\""
                + " serverState=\"" + serverState + "\""
                + " clientState=\"" + clientState + "\""
                + " finalDump=\"" + finalDump + "\"";
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "n/a" : value.replace('"', '\'');
    }
}
