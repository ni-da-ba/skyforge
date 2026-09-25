# Determinism scope — authored world state vs native fleshing

**Status:** active implementation/acceptance policy  
**Applies to:** DR-40 ecology, DR-50 integrated region, later backend realizers  
**Authority:** Skyforge-owned structure is deterministic; Minecraft-native decorative fleshing is observational unless it becomes structurally significant.

## Policy

Skyforge requires exact reproducibility for state that carries authored meaning or affects stable world structure:

- island identity and descriptors;
- semantic fields and routing;
- authored terrain/carve geometry;
- hydrology topology and geometry;
- ownership and lifecycle ledgers;
- authored material/provenance state;
- canonical structure authority;
- biome authority and representative biome classification;
- save/reload persistence and cross-volume isolation.

Minecraft-native or mod-native fleshing may vary between otherwise equivalent fresh realizations when the
variation is local, decorative, and not materially noticeable to players. Examples include exact tree,
grass, flower, mushroom, vine, or similar feature attachments produced by native population.

For those systems Skyforge still requires:

- the native population phase actually runs;
- supported population areas remain bounded to authored ownership;
- successful native features remain non-zero where expected;
- persistence/reload remain valid;
- hydrology/structure/material collision invariants remain valid;
- no foreign-volume or out-of-owner writes;
- no variation may alter authored topology, water fate, major landform geometry, required structures,
  gameplay-significant access, or other semantically meaningful state.

Exact native-population block digests are therefore evidence/diagnostics, not acceptance equality
requirements.

## Gate interpretation

A/B acceptance is split into two categories.

### Strict deterministic evidence

These values must match exactly when the same authored input is regenerated:

- semantic/authorship identities;
- transform/carve/authored-change/provenance digests;
- authored air/occupied accounting;
- hydrology digest and representative authored hydrology location;
- ownership/lifecycle completion and rejected-write accounting;
- canonical structure digest/authority;
- deterministic biome authority/sample positions.

### Observational native-fleshing evidence

These are recorded and sanity-checked but may differ:

- successful native feature counts;
- native population outcome digests;
- whole decorated-region/block digests that include native feature placement;
- attachment counts from vanilla/modded vegetation/decorators.

If observational variation becomes visually large, changes navigation/gameplay, damages hydrology or
structures, escapes ownership, or causes persistence instability, it becomes a product defect and must
be promoted back into a stronger acceptance contract.

## Rationale

Skyforge's determinism promise applies to the world the project **authors**. Native decoration is a
downstream fleshing layer. Requiring block-for-block equality from systems we intentionally delegate
to Minecraft/mods adds fragility without improving authored coherence.

This policy does not relax determinism for the new hydrology reset. The continuous route, network
junctions, longitudinal hydraulics, geomorphic qualification, retained basins, and any resulting
Skyforge-authored terrain field remain strict deterministic outputs.
