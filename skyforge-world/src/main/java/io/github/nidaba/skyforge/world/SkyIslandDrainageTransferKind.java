package io.github.nidaba.skyforge.world;

/** Physical interpretation of one routed watershed edge. */
public enum SkyIslandDrainageTransferKind {
    /** Raw authored terrain descends or is effectively level along the routed edge. */
    DESCENDING_CHANNEL,

    /**
     * Flow is being transferred across one Priority-Flood depression interior.
     *
     * <p>This edge contributes to semantic discharge continuity but must not be realized as a
     * literal river trench. Standing/shallow water or wetland semantics own the depression.
     */
    BASIN_INTERIOR,

    /**
     * The routed edge leaves or crosses a depression through a raw-terrain rise.
     *
     * <p>This is a localized geomorphic breach/spillway candidate, not an ordinary channel reach.
     */
    BREACH
}
