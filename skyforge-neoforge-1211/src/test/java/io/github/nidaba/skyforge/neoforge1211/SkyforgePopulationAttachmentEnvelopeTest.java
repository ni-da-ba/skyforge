package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class SkyforgePopulationAttachmentEnvelopeTest {
    private static final BlockPos OWNER = new BlockPos(0, 100, 0);

    @Test
    void attachmentPermissionPropagatesFromOwnerOnlyToBoundedDepth() {
        var envelope = new SkyforgePopulationAttachmentEnvelope(OWNER::equals, 2);

        assertTrue(envelope.acceptWrite(OWNER));
        assertTrue(envelope.acceptWrite(new BlockPos(0, 101, 0)));
        assertTrue(envelope.acceptWrite(new BlockPos(0, 102, 0)));
        assertFalse(envelope.acceptWrite(new BlockPos(0, 103, 0)));
        assertEquals(2, envelope.attachmentCount());
    }

    @Test
    void disconnectedWriteIsRejectedEvenWhenGeometricallyNearby() {
        var envelope = new SkyforgePopulationAttachmentEnvelope(OWNER::equals, 8);

        assertFalse(envelope.acceptWrite(new BlockPos(0, 108, 0)));
        assertEquals(0, envelope.attachmentCount());
    }

    @Test
    void diagonalNativeGeometryCanRemainConnected() {
        var envelope = new SkyforgePopulationAttachmentEnvelope(OWNER::equals, 3);

        BlockPos first = new BlockPos(1, 101, 1);
        BlockPos second = new BlockPos(2, 102, 2);
        assertTrue(envelope.acceptWrite(first));
        assertTrue(envelope.acceptWrite(second));
        assertTrue(envelope.ownsAttachment(first));
        assertTrue(envelope.ownsAttachment(second));
    }

    @Test
    void zeroDepthAllowsOnlyExactOwnerTerrain() {
        var envelope = new SkyforgePopulationAttachmentEnvelope(OWNER::equals, 0);

        assertTrue(envelope.canAcceptWrite(OWNER));
        assertFalse(envelope.canAcceptWrite(new BlockPos(0, 101, 0)));
        assertTrue(envelope.acceptWrite(OWNER));
        assertFalse(envelope.acceptWrite(new BlockPos(0, 101, 0)));
    }

    @Test
    void foreignSkyforgeTerrainIsHardVetoInsideAttachmentRadius() {
        BlockPos attachment = new BlockPos(0, 101, 0);
        BlockPos foreign = new BlockPos(0, 102, 0);
        var envelope = new SkyforgePopulationAttachmentEnvelope(
                OWNER::equals,
                foreign::equals,
                8);

        assertTrue(envelope.canAcceptWrite(attachment));
        assertEquals(0, envelope.attachmentCount());
        assertTrue(envelope.acceptWrite(attachment));
        assertFalse(envelope.canAcceptWrite(foreign));
        assertFalse(envelope.acceptWrite(foreign));
        assertFalse(envelope.ownsAttachment(foreign));
        assertEquals(1, envelope.attachmentCount());
    }

    @Test
    void attachmentPositionsExposeOnlyCommittedAttachmentGeometry() {
        var envelope = new SkyforgePopulationAttachmentEnvelope(OWNER::equals, 3);
        BlockPos first = new BlockPos(0, 101, 0);
        BlockPos second = new BlockPos(1, 102, 0);

        assertTrue(envelope.acceptWrite(first));
        assertTrue(envelope.acceptWrite(second));
        assertEquals(java.util.Set.of(first, second), envelope.attachmentPositions());
        assertThrows(
                UnsupportedOperationException.class,
                () -> envelope.attachmentPositions().clear());
        assertFalse(envelope.attachmentPositions().contains(OWNER));
    }

    @Test
    void attachmentPositionDigestDependsOnFootprintNotPropagationDepth() {
        BlockPos windingA = new BlockPos(0, 101, 0);
        BlockPos windingB = new BlockPos(1, 102, 0);
        BlockPos target = new BlockPos(2, 101, 0);
        BlockPos direct = new BlockPos(1, 100, 0);

        var windingFirst = new SkyforgePopulationAttachmentEnvelope(OWNER::equals, 4);
        assertTrue(windingFirst.acceptWrite(windingA));
        assertTrue(windingFirst.acceptWrite(windingB));
        assertTrue(windingFirst.acceptWrite(target));
        assertTrue(windingFirst.acceptWrite(direct));

        var directFirst = new SkyforgePopulationAttachmentEnvelope(OWNER::equals, 4);
        assertTrue(directFirst.acceptWrite(direct));
        assertTrue(directFirst.acceptWrite(target));
        assertTrue(directFirst.acceptWrite(windingA));
        assertTrue(directFirst.acceptWrite(windingB));

        assertEquals(windingFirst.attachmentPositions(), directFirst.attachmentPositions());
        assertEquals(
                windingFirst.attachmentPositionDigest(),
                directFirst.attachmentPositionDigest());
    }

    @Test
    void staticReachabilityIsIndependentOfFrontierVisitOrderAndPreservesForeignVeto() {
        BlockPos first = new BlockPos(4, 104, 4);
        BlockPos second = new BlockPos(5, 105, 5);
        BlockPos foreign = new BlockPos(6, 106, 6);
        java.util.Set<BlockPos> staticallyReachable =
                java.util.Set.of(first, second, foreign);

        var firstOrder = new SkyforgePopulationAttachmentEnvelope(
                OWNER::equals,
                foreign::equals,
                8,
                staticallyReachable::contains);
        assertTrue(firstOrder.acceptWrite(first));
        assertTrue(firstOrder.acceptWrite(second));
        assertFalse(firstOrder.acceptWrite(foreign));

        var reverseOrder = new SkyforgePopulationAttachmentEnvelope(
                OWNER::equals,
                foreign::equals,
                8,
                staticallyReachable::contains);
        assertTrue(reverseOrder.acceptWrite(second));
        assertTrue(reverseOrder.acceptWrite(first));
        assertFalse(reverseOrder.acceptWrite(foreign));

        assertEquals(firstOrder.attachmentPositions(), reverseOrder.attachmentPositions());
        assertEquals(
                firstOrder.attachmentPositionDigest(),
                reverseOrder.attachmentPositionDigest());
    }

    @Test
    void preflightDoesNotCreateAttachmentProvenance() {
        var envelope = new SkyforgePopulationAttachmentEnvelope(OWNER::equals, 2);
        BlockPos first = new BlockPos(0, 101, 0);
        BlockPos second = new BlockPos(0, 102, 0);

        assertTrue(envelope.canAcceptWrite(first));
        assertEquals(0, envelope.attachmentCount());
        assertFalse(envelope.canAcceptWrite(second));
        assertEquals(0, envelope.attachmentCount());

        assertTrue(envelope.acceptWrite(first));
        assertTrue(envelope.canAcceptWrite(second));
        assertEquals(1, envelope.attachmentCount());
    }
}
