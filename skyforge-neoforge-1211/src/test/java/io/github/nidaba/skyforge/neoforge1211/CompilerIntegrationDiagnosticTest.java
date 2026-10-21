package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CompilerIntegrationDiagnosticTest {
    @Test
    void deadlineIsBoundedAndStrictlyClassifiesOverrun() {
        SkyforgeCompilerIntegrationDiagnostic diagnostic = new SkyforgeCompilerIntegrationDiagnostic(
                "SABLE_PRIMARY_ASSEMBLY_LIFECYCLE",
                SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                "one new canonical Sable body registered",
                100L,
                140L,
                "assembler=0,202,0",
                "server=running",
                "headless",
                "pending");

        assertFalse(diagnostic.expired(140L));
        assertTrue(diagnostic.expired(141L));
    }

    @Test
    void renderedDiagnosticCarriesRequiredWaitContext() {
        String rendered = new SkyforgeCompilerIntegrationDiagnostic(
                        "SABLE_PRIMARY_ASSEMBLY_LIFECYCLE",
                        SkyforgeCompilerIntegrationPhase.PHYSICS_PROGRESSION,
                        "canonical body translates after bounded velocity nudge",
                        200L,
                        260L,
                        "subLevel=00000000-0000-0000-0000-000000000001",
                        "physicsHandleValid=true",
                        "headless",
                        "deltaX=0.01")
                .render();

        assertTrue(rendered.contains("expectedCondition="));
        assertTrue(rendered.contains("startTick=200"));
        assertTrue(rendered.contains("deadlineTick=260"));
        assertTrue(rendered.contains("relevantIds="));
        assertTrue(rendered.contains("serverState="));
        assertTrue(rendered.contains("clientState=\"headless\""));
        assertTrue(rendered.contains("finalDump="));
    }

    @Test
    void rejectsUnboundedOrUnnamedWaits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeCompilerIntegrationDiagnostic(
                        "SABLE_PRIMARY_ASSEMBLY_LIFECYCLE",
                        SkyforgeCompilerIntegrationPhase.ASSEMBLY,
                        "registered",
                        20L,
                        19L,
                        "ids",
                        "server",
                        "headless",
                        "dump"));
    }
}
