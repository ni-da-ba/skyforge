package io.github.nidaba.skyforge.world;

/**
 * Backend-neutral semantic material families interpreted from AUTH-0031 character and AUTH-0032
 * mesoscale material domains.
 *
 * <p>Families are overlapping semantic affinities. They are not named rocks, mineral species,
 * Minecraft blocks, registry keys, or mutually exclusive material labels.
 */
public enum SkyIslandMaterialFamilyKind {
    COHERENT_MASSIVE_HOST(SkyIslandMaterialFamilyRole.HOST_FABRIC),
    LAYERED_FABRIC_RICH_HOST(SkyIslandMaterialFamilyRole.HOST_FABRIC),
    STRONGLY_ALTERED_HOST(SkyIslandMaterialFamilyRole.CONDITIONED_HOST),
    WATER_CONDITIONED_HOST(SkyIslandMaterialFamilyRole.CONDITIONED_HOST),
    MINERAL_BEARING_STRUCTURAL_HOST(SkyIslandMaterialFamilyRole.CONDITIONED_HOST);

    private final SkyIslandMaterialFamilyRole role;

    SkyIslandMaterialFamilyKind(SkyIslandMaterialFamilyRole role) {
        this.role = role;
    }

    public SkyIslandMaterialFamilyRole role() {
        return role;
    }
}
