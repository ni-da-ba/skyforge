package io.github.nidaba.skyforge.world;

/** AUTH-0050 proof/admission status for one explicit association pair. */
public enum SkyIslandAuthoredOverlapPairStatus {
    /** Separation is proven by conservative bounds or native horizontal support bounds. */
    CERTIFIED_SEPARATE,

    /** Same-X/Z stacking is proven by a conservative vertical gap meeting policy. */
    CERTIFIED_STACKED,

    /** Pair is explicitly permitted to compose even if native authored volumes overlap. */
    ACCEPTED_EXPLICIT_COMPOSITION,

    /** Strict/non-overlap pair has an exact AUTH-0048 native-overlap witness. */
    REJECTED_WITNESSED_OVERLAP,

    /**
     * No overlap witness was found, but the available conservative proofs cannot certify
     * separation. Fail closed rather than treating finite sampling as proof.
     */
    REJECTED_UNCERTIFIED_SEPARATION,

    /** STACKED rule is malformed for the realized placement or cannot certify its required gap. */
    REJECTED_STACK_REQUIREMENT
}
