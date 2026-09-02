package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(envelope.acceptWrite(OWNER));
        assertFalse(envelope.acceptWrite(new BlockPos(0, 101, 0)));
    }
}
