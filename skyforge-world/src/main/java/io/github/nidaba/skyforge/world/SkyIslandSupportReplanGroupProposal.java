package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import java.util.Objects;

/**
 * AUTH-0055 immutable proposal for one group template.
 *
 * <p>The proposed outer radius is a floor suitable for constructing the candidate request. If
 * member/layout spacing changes, it is not a proof of the as-yet-unrealized fresh placement.
 */
public record SkyIslandSupportReplanGroupProposal(
        int groupOrdinal,
        String groupIdentifier,
        SkyIslandGroupTemplate originalTemplate,
        SkyIslandGroupTemplate proposedTemplate,
        SkyIslandSupportReplanValue memberHorizontal,
        SkyIslandSupportReplanValue layoutMinimumCenterSpacing,
        SkyIslandSupportReplanValue provisionalGroupRadius,
        boolean freshPlacementValidationRequired) {

    public SkyIslandSupportReplanGroupProposal {
        if (groupOrdinal < 0) {
            throw new IllegalArgumentException("groupOrdinal must be non-negative");
        }
        groupIdentifier = Objects.requireNonNull(groupIdentifier, "groupIdentifier");
        originalTemplate = Objects.requireNonNull(originalTemplate, "originalTemplate");
        proposedTemplate = Objects.requireNonNull(proposedTemplate, "proposedTemplate");
        memberHorizontal = Objects.requireNonNull(memberHorizontal, "memberHorizontal");
        layoutMinimumCenterSpacing =
                Objects.requireNonNull(layoutMinimumCenterSpacing, "layoutMinimumCenterSpacing");
        provisionalGroupRadius =
                Objects.requireNonNull(provisionalGroupRadius, "provisionalGroupRadius");

        if (!originalTemplate.identifier().equals(groupIdentifier)
                || !proposedTemplate.identifier().equals(groupIdentifier)) {
            throw new IllegalArgumentException("group proposal identifier mismatch");
        }
        if (proposedTemplate.reservedHorizontalRadius() != memberHorizontal.proposedValue()) {
            throw new IllegalArgumentException(
                    "proposed template horizontal reservation does not match proposal value");
        }
        if (proposedTemplate.layout().minimumCenterSpacing()
                != layoutMinimumCenterSpacing.proposedValue()) {
            throw new IllegalArgumentException(
                    "proposed template layout spacing does not match proposal value");
        }
        if (proposedTemplate.reservedGroupRadius() != provisionalGroupRadius.proposedValue()) {
            throw new IllegalArgumentException(
                    "proposed template group reservation does not match proposal value");
        }
    }

    public boolean changesPlanningGeometry() {
        return memberHorizontal.raisedByProofOrMargin()
                || layoutMinimumCenterSpacing.raisedByProofOrMargin()
                || provisionalGroupRadius.raisedByProofOrMargin();
    }
}
