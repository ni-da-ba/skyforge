# ASSET-001 Minecraft adapter v0.4 — explicit block-entity payloads

v0.4 keeps the accepted v0.14 Guild architecture and the v0.3 Minecraft state/capability lowering unchanged, then closes the first functional-NBT gap in structure-template export.

```text
Guild architectural result
    -> semantic BlockIntent lowering
    -> Minecraft state/topology/support realization
    -> bounded support repair
    -> block-entity payload policy
    -> structure-template block entries with explicit `nbt`
```

## Scope

The current Guild specimen contains vanilla barrels and chests. They are functional Minecraft blocks whose structure-template block entries can carry block-entity NBT independently of the palette state. v0.4 gives those containers an explicit deterministic payload rather than relying on unspecified/default runtime block-entity creation.

For each realized `minecraft:barrel` or `minecraft:chest`, export now attaches:

- the correct block-entity `id`;
- an explicit empty `Items` list.

Inventory contents, loot-table ownership, custom names, signs, books, and modded functional machinery remain later semantic-content stages. The adapter does not invent gameplay contents merely because the target block supports them.

## Fail-closed coverage

The adapter already reports how many realized blocks require block entities. Structure export independently counts how many realized blocks have a bounded payload policy and rejects the artifact if the two counts differ. This prevents a newly introduced block-entity-bearing palette entry from silently exporting without a corresponding NBT policy.

## Acceptance

The v0.14 architecture remains 1,885 cells and retains the same architecture digest. Acceptance requires:

- deterministic compiler tests green;
- `blockEntityNbtCount == blockEntityBlockCount > 0`;
- adapter report policy `explicit_empty_container_nbt_v0.4`;
- generated structure NBT contains per-block `nbt` compounds and explicit `Items` lists for the current Guild containers;
- no change to architectural massing, circulation, facade grammar, or detail placement.

The canonical CI artifact after this pass reports four block-entity-bearing Guild blocks and four encoded block-entity payloads.
