# TEX-001 — mathematical foundation for a constrained texture compiler

## Scope

This proof addresses low-resolution texture realization, with a Minecraft character skin as the first target. The compiler core is target-agnostic. A target adapter provides atlas regions, topology/seams, source sample fields, and semantic importance. The output is a deterministic atlas plus QA evidence.

## Research adopted

### Perceptual color error

Sharma, Wu, and Dalal (2005) document correct implementation and test data for CIEDE2000. The compiler uses ΔE00 rather than Euclidean RGB distance for its unary color-error term. A unit test reproduces their canonical first supplementary pair (2.0425).

Reference: G. Sharma, W. Wu, E. N. Dalal, “The CIEDE2000 Color-Difference Formula: Implementation Notes, Supplementary Test Data, and Mathematical Observations,” *Color Research & Application* 30(1), 2005, DOI 10.1002/col.20070.

### Color quantization

Heckbert (1982) decomposes color quantization into sampling source color statistics, choosing a colormap, mapping samples to the colormap, and redrawing, with dithering optional. TEX-001 keeps the same separation but uses deterministic weighted k-medoids and a perceptual ΔE00 metric. Dithering is intentionally disabled for this proof because sparse pixel-art features and UV seams benefit from piecewise-coherent labels rather than added high-frequency noise.

Reference: P. Heckbert, “Color Image Quantization for Frame Buffer Display,” *SIGGRAPH Computer Graphics* 16(3), 1982, DOI 10.1145/965145.801294.

### Discontinuity-preserving spatial regularization

Rudin, Osher, and Fatemi (1992) established total-variation regularization as an edge-preserving alternative to indiscriminate smoothing. For a discrete limited-palette texture, TEX-001 uses the closely related piecewise-constant Potts form rather than continuous TV. Neighbor penalties are contrast-sensitive so high-gradient source boundaries are preserved while flat fields are regularized.

Reference: L. Rudin, S. Osher, E. Fatemi, “Nonlinear total variation based noise removal algorithms,” *Physica D* 60, 1992, DOI 10.1016/0167-2789(92)90242-F.

### Multi-label graph-cut optimization

Boykov, Veksler, and Zabih (2001) formulate pixel labeling with data and discontinuity-preserving smoothness terms and introduce expansion/swap graph-cut moves. TEX-001 uses alpha-expansion over a metric Potts label cost; each expansion move is solved by an s-t minimum cut. This turns “make the pixels less noisy” into an explicit energy minimization problem rather than an ad-hoc filter.

Reference: Y. Boykov, O. Veksler, R. Zabih, “Fast Approximate Energy Minimization via Graph Cuts,” *IEEE TPAMI* 23(11), 2001, DOI 10.1109/34.969114.

### Seam optimization and texture topology

Kwatra et al. (2003) demonstrate graph cuts as a practical way to choose texture stitching boundaries. TEX-001 does not copy their patch-synthesis formulation, but adopts the underlying principle that texture continuity should be part of the optimization topology rather than repaired after rasterization. For topology edges that must be identical—such as selected UV-wrap seams—the compiler goes further: seam-linked texels are collapsed into one decision variable, giving exact equality rather than merely a low seam cost.

Reference: V. Kwatra et al., “Graphcut Textures: Image and Video Synthesis Using Graph Cuts,” *ACM Transactions on Graphics* 22(3), 2003, DOI 10.1145/882262.882264.

## Energy model

After source resampling and palette construction, each target texel `p` receives a palette label `l_p`. The compiler minimizes

\[
E(L)=\sum_p w_p\,\Delta E_{00}(s_p,c_{l_p})^2 +
\sum_{(p,q)\in N}\lambda_{pq}[l_p\ne l_q].
\]

`w_p` is semantic/source importance. The contrast-sensitive pair weight is

\[
\lambda_{pq}=\lambda\exp(-\beta\,\Delta E_{00}(s_p,s_q)^2),
\]

so flat source areas prefer coherent patches while meaningful edges remain inexpensive to preserve.

Hard seam pairs are first converted into equivalence classes. The MRF therefore optimizes over seam-consistent variables from the beginning. This is stronger than adding a large seam penalty and eliminates post-hoc seam repair.

## Downsampling

Source patches may be higher resolution than target regions. Samples are integrated over each destination texel using overlap areas and semantic importance; RGB channels are averaged in linear light before re-encoding to sRGB. This avoids gamma-space averaging artifacts and lets high-information features such as eyes or insignia carry more weight than broad low-information fields.

## Current Minecraft-skin target

The target adapter defines the classic 64×64 base-layer UV topology for head, torso, both arms, and both legs. It generates a 2× semantic source field for the first Guild-clerk proof, then sends that field through the same generic compiler used by the explicit-atlas test target. The adapter, not the compiler core, knows which regions are `head.front`, `torso.back`, and so on.

The human gate should judge the *wrapped result*, not just the atlas: facial readability, hair wrap, coat/limb continuity, palette economy, seam behavior, and whether the result feels deliberately pixel-authored rather than mechanically reduced.

## Deliberate boundaries

- v0.1 does not infer character semantics directly from a portrait. A portrait-normalization / semantic feature extraction front-end can later emit the same generic source-field IR.
- The alpha-expansion stage is a mathematically established approximate multi-label solver; this proof does not claim global optimality for the full multi-label energy.
- Weighted k-medoids is deterministic but locally optimized; future palette work can compare exact small-candidate search or branch-and-bound where palette cardinality is sufficiently small.
- Structural UV topology is exact only where the target adapter declares hard seams. Target-specific seam maps remain reviewable data rather than hidden compiler behavior.
