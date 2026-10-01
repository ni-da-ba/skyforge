# TEX-001 v0.2 — semantic detail and overlay pass

## Human-gate input

The v0.1 compiler produced the first clearly legible Minecraft-scale face in the Skyforge skin work, but garment and overall detail remained sparse. v0.2 therefore keeps the proven perceptual/topological core and spends additional representation capacity through explicit semantic resource allocation rather than globally weakening regularization.

## Mathematical basis

The v0.1 authorities remain active: CIEDE2000 perceptual color fidelity, deterministic weighted k-medoids, a contrast-sensitive Potts MRF, alpha-expansion graph-cut moves, and exact UV equality through hard seam equivalence classes.

v0.2 adds two established multiscale/resource-allocation ideas:

1. **Laplacian-pyramid saliency.** A separable binomial low-pass kernel and repeated residual extraction form a fixed-resolution analogue of the Burt–Adelson Laplacian pyramid. The residual energy raises importance around meaningful multiscale structure before downsampling, so high-information features receive more distortion budget.
2. **Rate–distortion resource allocation.** Each semantic detail group exposes a distortion curve as palette capacity increases. An exact dynamic program minimizes the sum of group distortions subject to the global discrete palette budget. This prevents the face, clothing, lower body, and overlay from competing through one undifferentiated palette objective.

The final atlas is still solved globally by alpha-expansion, so regional budgets guide representation capacity without fragmenting UV/topology optimization.

## Generic compiler boundary

`DetailGroup` belongs to the generic `TextureProblem`; it only names arbitrary regions, bounded palette capacity, perceptual weight and a smoothness scale. The compiler core has no knowledge of faces, coats, Minecraft anatomy or skins. Target adapters decide which regions belong to which semantic groups.

## Minecraft skin v0.2 target grammar

The skin adapter adds the real second-layer UV topology and uses it deliberately for geometry-bearing details: hair fringe/highlights, collar/lapels, pocket/hem structure, Guild insignia, epaulettes/cuffs, trouser crease and boot-top cues. Base-layer macro color planes remain separately represented. The face layout is intentionally conservative so the v0.1 readability result is not traded away for clothing noise.

## Human gate

Compare v0.1 and v0.2 wrapped-model renders before judging the atlas. Check:

- face remains immediately legible;
- clothing reads as a tailored Guild uniform rather than broad flat color;
- lapel/collar/cuff/insignia cues survive at normal model scale;
- overlay improves depth without becoming visual static;
- head and garment seams remain continuous;
- side/back views feel authored rather than front-only;
- arms-raised pose does not expose broken sleeve topology.

This pass still uses a semantic source description. Portrait normalization and learned/assisted feature extraction remain upstream future work and should compile into the same generic IR rather than changing the optimizer.
