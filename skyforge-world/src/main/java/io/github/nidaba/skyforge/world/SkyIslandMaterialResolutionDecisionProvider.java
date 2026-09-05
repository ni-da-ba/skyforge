package io.github.nidaba.skyforge.world;

/**
 * Backend-neutral AUTH-0047 injection point for one accepted AUTH-0042 material-resolution
 * decision.
 *
 * <p>The provider receives the exact semantic request required at the sampled point. It must
 * return a decision for that exact request. Concrete backend material identity is not part of this
 * contract.
 */
@FunctionalInterface
public interface SkyIslandMaterialResolutionDecisionProvider {
    SkyIslandMaterialResolutionDecision decision(SkyIslandMaterialBindingRequest request);
}
