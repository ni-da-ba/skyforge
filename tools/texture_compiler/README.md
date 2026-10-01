# Skyforge constrained texture compiler

`tools/texture_compiler` is a backend-neutral deterministic compiler for low-resolution texture artifacts. The first production target is a classic 64×64 Minecraft skin, but the compiler core does not know about Minecraft UVs, human anatomy, or skins. Target adapters translate a domain-specific topology and source description into a generic `TextureProblem` made of regions, source sample fields, palette limits, spatial adjacencies, and seam contracts.

The v0.1 lowering path is:

```text
source/reference signal
→ linear-light resampling
→ perceptual palette selection
→ topology + seam equivalence graph
→ contrast-sensitive Potts energy
→ alpha-expansion graph-cut optimization
→ atlas realization
→ deterministic PNG
→ topology/game QA renders
```

The core data term uses CIEDE2000 color difference. Palette selection is deterministic weighted k-medoids over observed source colors. Piecewise coherence is represented by a contrast-sensitive Potts MRF: neighboring texels are encouraged to share labels in low-gradient regions while strong source edges reduce the smoothing penalty. Multi-label optimization uses deterministic alpha-expansion moves solved by s-t minimum cuts. Hard UV seams are not penalties: their texels are collapsed into the same optimization variable with union-find, making seam equality an exact invariant.

This is deliberately not a generic image generator. Creative intent stays upstream. The compiler's job is to preserve that intent under a severely constrained target representation and produce a valid, inspectable artifact.

## Run the first skin proof

```bash
python tools/texture_compiler/compile.py \
  tools/texture_compiler/specimens/guild_clerk_skin_v0.1.json \
  --out build/texture-compiler-guild-clerk
```

The output includes `texture.png`, an atlas preview, front/back/side wrapped-model previews, a head close-up, `resolved.json`, and `validation.txt`.

## Genericity proof

`specimens/generic_patch_v0.1.json` uses the same compiler through the `explicit_atlas` adapter. It contains no skin semantics and verifies that topology, palette optimization, seam constraints, PNG emission, and QA are reusable for later sprite sheets, block textures, decals, UI atlases, creature surfaces, aircraft markings, and other bounded texture families.
