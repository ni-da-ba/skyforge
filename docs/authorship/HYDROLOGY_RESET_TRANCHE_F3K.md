# Hydrology reset tranche F3K — natural coupled-component census

**Status:** three fixed census intervals complete; targeted key-700 D2 diagnosis active  
**Governing authority:** issues [#1084](https://github.com/ni-da-ba/skyforge/issues/1084), [#1392](https://github.com/ni-da-ba/skyforge/issues/1392), and [#1396](https://github.com/ni-da-ba/skyforge/issues/1396)  
**Depends on:** accepted C2, D2, F3B/F3C/F3H/F3I/F3E  
**Terrain mutation:** none  
**Minecraft changes:** none

## Purpose

F3H/F3I prove one narrow joint confluence/CASCADE solve and its propagation through ordinary spans and component assembly, but only using controlled test-local geometry. The generated reference keys inspected so far do not emit that supported overlap naturally. F3K screens a fixed proving-ground tranche for another naturally authored candidate, so later work can target an actual component rather than further modifying the existing fixtures.

## Fixed sample

The first deterministic tranche is the existing proving-ground seed `0x534B59464F524745`, namespaces 6/61 and 8/81, keys 1–256 in each namespace (512 identities). The manifest records every identity in sorted namespace/key order. Potential cases are screened by semantic confluence and CASCADE presence; the expensive F3H and complete F3I/F3E path runs only where both occur.

The completed first tranche produced 512 screened identities and 119 topology candidates, with no natural F3H SOLVED identity and no F3I/F3E-qualified component. Its candidate outcomes were predominantly no natural F3H boundary match; remaining outcomes included empty ordinary D2-derived pointwise head envelopes, CASCADE-coupled boundary deferrals, and two semantic-corridor hard rejections (6/61 key 235 and 8/81 key 29). No policy was changed.

The second interval (keys 257–512) produced 512 more identities and 143 topology candidates, again with no natural F3H SOLVED identity or F3I/F3E-qualified component; one candidate hit the semantic-corridor hard check.

The final fixed interval (keys 513–768) produced 512 identities and 114 topology candidates. One natural F3H solve was found at namespace 8/81, key 700: confluence node 801 with CASCADE reach 801→1951. Its full component is rejected during F3I/F3E assembly because incident ordinary reaches have empty or incompatible D2-derived head envelopes and infeasible coupled difference/box constraints. The interval produced zero qualified components and zero unexpected planner failures.

Across keys 1–768, the census screened 1,536 identities and 376 topology candidates. Key 287 remains rejected under the unchanged corridor and D2 policy; terminal CASCADE edge outlets remain fail-closed. The next bounded task is issue #1396: diagnose whether key 700's physical rejection can be resolved by a deterministic geometry refinement within the existing C2 corridor and D2 limits. No fourth discovery interval, threshold change, or terrain/Minecraft realization is authorized by this census.

## Acceptance

A candidate is useful only when all of the following hold on its generated geometry:

1. F3H reports a naturally emitted SOLVED joint outcome;
2. the existing F3I admission matches that exact confluence/CASCADE ownership;
3. all ordinary spans pass their existing D2 envelopes, grade, and geomorphic checks;
4. F3E reports the complete terminal component QUALIFIED under existing terminal-fate authority.

All candidates and blockers remain visible in the Actions manifest. An F3H local solve alone is not acceptance.

## Boundaries

No corridor, D2/E2 limit, transition formula, terminal-CASCADE boundary, or sample-dependent production default changes. Key 287 remains rejected. Terminal CASCADE edge outlets remain fail-closed. This tranche grants no terrain, water, voxel, Minecraft, or product-review authority. A qualified component would be a prerequisite for a later, separately validated realization step—not a visual acceptance claim.
