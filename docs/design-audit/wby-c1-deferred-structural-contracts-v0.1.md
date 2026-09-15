# WBY C1 Deferred Structural Contracts v0.1

## Decision

The exact Create 6.0.10 Wave C1 runtime has now been queried for the structural-detail resources most likely to matter to future structure compilation. This document records observed concrete state surfaces only. It grants **no automatic placement authority**.

The authoritative machine-readable evidence is `tools/asset_compiler/minecraft_data/wby_c1_create_6_0_10_capabilities.json` under `deferredStateContracts`. The WBY resolver continues to admit only entries in `blocks` whose `status` is `active`.

## Runtime provenance

The proof resolves Wave C1's immutable Create coordinate `maven.modrinth:LNytGWDc:UjX6dr61`, then launches the isolated Create-capable acceptance server. The runtime reported Create `6.0.10`; `WBY_C1_STRUCTURE_REGISTRY` passed with ten deferred structural contracts observed.

## Observed contracts

| Resources | Runtime class | State surface | Default state | Remaining compiler gate |
|---|---|---|---|---|
| `create:andesite_bars`, `create:brass_bars`, `create:copper_bars` | `IronBarsBlock` | `north/east/south/west/waterlogged`: boolean | all false | target-neutral railing/bars role and generic cardinal-connectable thin topology |
| `create:andesite_ladder`, `create:brass_ladder`, `create:copper_ladder` | `MetalLadderBlock` | `facing`: N/E/S/W; `waterlogged`: boolean | north, false | climbable role plus attachment/survival validation |
| `create:andesite_scaffolding`, `create:brass_scaffolding`, `create:copper_scaffolding` | `MetalScaffoldingBlock` | `bottom`: boolean; `distance`: 0–7; `waterlogged`: boolean | false, 7, false | dedicated scaffolding state, collision, and support semantics |
| `create:metal_girder` | `GirderBlock` | `axis`: x/y/z; `bottom/top/waterlogged/x/z`: boolean | y; all booleans false | dedicated girder/brace role plus axis/connectivity lowering |

The exact Create source audit agrees with the live registry: bars are ordinary `IronBarsBlock` variants; metal ladders inherit ladder behavior; metal scaffolding has dedicated survival/shape behavior; and the girder owns its own connectivity/orientation state.

## Non-authority invariant

These resources are intentionally **not** added to the selectable `blocks` array. They therefore cannot enter `wby_c1_create_registry()` and cannot appear in generated Guild structures through capability matching. Unit tests freeze both the observed state contracts and this exclusion.

This matters because the current Guild semantic profile has no neutral railing/bars, ladder, scaffolding, or metal-girder role. Reusing existing `fence`, `window`, or timber `structural_frame` semantics merely to obtain an industrial-looking block would make concrete Minecraft vocabulary upstream-authoritative, violating the compiler boundary.

## Next compiler tranche

Future support should begin with backend-neutral primitives and topology rules, tested independently of Create. Only after those semantics exist should a WBY target profile map them to the observed Create resources. Bars are the smallest likely first candidate because their cardinal-connectable state is structurally simple; ladders, scaffolding, and girders require progressively richer attachment/support/connectivity rules.

Aircraft-owned Sable, Create Aeronautics, and Create Propulsion capabilities remain outside this structure contract.
