package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Deterministic digest bridge from accepted AIRCRAFT-RUNTIME-003 authority into later static compiler layers. */
public record SkyforgeAircraftRudderControlAuthority(
        String sourceYawControlDigestSha256,
        AircraftBlockspaceIR.LatticePoint swivelBearingCoordinate,
        AircraftBlockspaceIR.LatticePoint driveCogCoordinate,
        AircraftBlockspaceIR.LatticePoint temporarySourceCoordinate,
        int sourceRpmMagnitude,
        double targetNeutralToleranceDegrees,
        double physicalNeutralToleranceDegrees,
        double minimumPhysicalDeflectionDegrees,
        boolean sourceOffHoldQualified,
        boolean inverseCommandNeutralReturnQualified,
        boolean passiveSelfCenteringQualified,
        String actuationDigestSha256,
        String neutralReturnDigestSha256) {
    private static final AircraftBlockspaceIR.LatticePoint EXPECTED_SWIVEL = point(18, 3, 0);
    private static final AircraftBlockspaceIR.LatticePoint EXPECTED_DRIVE_COG = point(18, 3, 1);
    private static final AircraftBlockspaceIR.LatticePoint EXPECTED_TEMPORARY_SOURCE = point(18, 2, 1);

    public SkyforgeAircraftRudderControlAuthority {
        sourceYawControlDigestSha256 = requireSha256(sourceYawControlDigestSha256);
        swivelBearingCoordinate = Objects.requireNonNull(swivelBearingCoordinate, "swivelBearingCoordinate");
        driveCogCoordinate = Objects.requireNonNull(driveCogCoordinate, "driveCogCoordinate");
        temporarySourceCoordinate = Objects.requireNonNull(temporarySourceCoordinate, "temporarySourceCoordinate");
        actuationDigestSha256 = requireSha256(actuationDigestSha256);
        neutralReturnDigestSha256 = requireSha256(neutralReturnDigestSha256);
        if (!EXPECTED_SWIVEL.equals(swivelBearingCoordinate)
                || !EXPECTED_DRIVE_COG.equals(driveCogCoordinate)
                || !EXPECTED_TEMPORARY_SOURCE.equals(temporarySourceCoordinate)) {
            throw new IllegalArgumentException("accepted rudder-control coordinate authority changed");
        }
        if (!driveCogCoordinate.equals(point(swivelBearingCoordinate.x(), swivelBearingCoordinate.y(), swivelBearingCoordinate.z() + 1))) {
            throw new IllegalArgumentException("accepted tail drive cog is no longer one block starboard of the Swivel");
        }
        if (!temporarySourceCoordinate.equals(point(driveCogCoordinate.x(), driveCogCoordinate.y() - 1, driveCogCoordinate.z()))) {
            throw new IllegalArgumentException("accepted temporary yaw source is no longer directly below the drive cog");
        }
        if (sourceRpmMagnitude != 16
                || Double.compare(targetNeutralToleranceDegrees, 0.1) != 0
                || Double.compare(physicalNeutralToleranceDegrees, 2.0) != 0
                || Double.compare(minimumPhysicalDeflectionDegrees, 5.0) != 0) {
            throw new IllegalArgumentException("accepted AIRCRAFT-RUNTIME-003 command/tolerance authority changed");
        }
        if (!sourceOffHoldQualified || !inverseCommandNeutralReturnQualified || passiveSelfCenteringQualified) {
            throw new IllegalArgumentException("accepted AIRCRAFT-RUNTIME-003 neutral-return semantics changed");
        }
        String expectedActuation = computeActuationDigest(
                sourceYawControlDigestSha256,
                swivelBearingCoordinate,
                driveCogCoordinate,
                temporarySourceCoordinate,
                sourceRpmMagnitude,
                targetNeutralToleranceDegrees,
                physicalNeutralToleranceDegrees,
                minimumPhysicalDeflectionDegrees);
        if (!expectedActuation.equals(actuationDigestSha256)) {
            throw new IllegalArgumentException("rudder actuation authority digest mismatch");
        }
        String expectedNeutral = computeNeutralReturnDigest(
                actuationDigestSha256,
                sourceOffHoldQualified,
                inverseCommandNeutralReturnQualified,
                passiveSelfCenteringQualified);
        if (!expectedNeutral.equals(neutralReturnDigestSha256)) {
            throw new IllegalArgumentException("rudder neutral-return authority digest mismatch");
        }
    }

    public static SkyforgeAircraftRudderControlAuthority accepted(SkyforgeAircraftYawControlIR yawControl) {
        Objects.requireNonNull(yawControl, "yawControl");
        if (!yawControl.validation().passed() || !yawControl.topologyChecks().passed() || !yawControl.countChecks().passed()) {
            throw new IllegalArgumentException("rudder runtime authority requires accepted current yaw-control IR");
        }
        if (!EXPECTED_SWIVEL.equals(yawControl.swivelBearingCoordinate())
                || yawControl.metrics().v0131MovingParentMainBodyPlacementCount() != 118
                || yawControl.metrics().yawControlChildPlacementCount() != 4) {
            throw new IllegalArgumentException("current yaw-control IR no longer matches accepted AIRCRAFT-RUNTIME-003 topology");
        }
        String yawDigest = yawControl.sha256();
        String actuation = computeActuationDigest(yawDigest, EXPECTED_SWIVEL, EXPECTED_DRIVE_COG, EXPECTED_TEMPORARY_SOURCE, 16, 0.1, 2.0, 5.0);
        String neutral = computeNeutralReturnDigest(actuation, true, true, false);
        return new SkyforgeAircraftRudderControlAuthority(
                yawDigest,
                EXPECTED_SWIVEL,
                EXPECTED_DRIVE_COG,
                EXPECTED_TEMPORARY_SOURCE,
                16,
                0.1,
                2.0,
                5.0,
                true,
                true,
                false,
                actuation,
                neutral);
    }

    private static String computeActuationDigest(
            String yawDigest,
            AircraftBlockspaceIR.LatticePoint swivel,
            AircraftBlockspaceIR.LatticePoint driveCog,
            AircraftBlockspaceIR.LatticePoint source,
            int rpm,
            double targetTolerance,
            double physicalTolerance,
            double minimumDeflection) {
        return sha256(String.join("|",
                "AIRCRAFT-RUNTIME-003",
                "run=35153062672",
                "job=104986575772",
                "yaw=" + yawDigest,
                "swivel=" + swivel,
                "driveCog=" + driveCog,
                "temporarySource=" + source,
                "rpm=" + rpm,
                "targetNeutralTolerance=" + targetTolerance,
                "physicalNeutralTolerance=" + physicalTolerance,
                "minimumPhysicalDeflection=" + minimumDeflection,
                "directTargetMutation=false"));
    }

    private static String computeNeutralReturnDigest(
            String actuationDigest,
            boolean sourceOffHold,
            boolean inverseReturn,
            boolean passiveSelfCentering) {
        return sha256(String.join("|",
                "AIRCRAFT-RUNTIME-003-NEUTRAL-RETURN",
                "actuation=" + actuationDigest,
                "sourceOffHold=" + sourceOffHold,
                "inverseCommandNeutralReturn=" + inverseReturn,
                "passiveSelfCentering=" + passiveSelfCentering));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String requireSha256(String value) {
        Objects.requireNonNull(value, "sha256");
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        return value;
    }

    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) {
        return new AircraftBlockspaceIR.LatticePoint(x, y, z);
    }
}
