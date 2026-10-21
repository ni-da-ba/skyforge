package io.github.nidaba.skyforge.neoforge1211;

/**
 * Optional compatibility surface mixed into Simulated Portable Engine block entities.
 *
 * <p>The interface deliberately contains no Simulated/Create types so packaged Skyforge remains
 * loadable when the optional flight stack is absent.
 */
public interface SkyforgePortableEngineCutoffAccess {
    boolean skyforge$isRedstoneCutoffEnabled();

    void skyforge$setRedstoneCutoffEnabled(boolean enabled);

    boolean skyforge$isRedstoneCutoffActive();
}
