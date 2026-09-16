# DR-60 canonical dressed-region human review packet

Status: machine-complete handoff for the terminal local human/exploration gate.

This packet records the exact canonical specimen. It does not make an aesthetic, pacing,
composition, density, gameplay-quality, or “worth exploring” judgment.

## 1. Exact specimen identity

- Specimen ID: `P2_DRESSED_REGION_A`
- Current main basis: `1901b269ae9384ad0c6bfe8a1ca839a20cee7d7a`
- World seed / Minecraft level seed: `493030`
- Authored archipelago root seed: `6001989086914692933`
- Authored identity:
  - province key: `8`
  - cluster key: `81`
  - island key: `1471`
- Authorship seed: `389656995481184958`
- Morphology family: `massif`
- Authored provenance: `AUTH-0001`, `AUTH-0030`
- Exact canonical volume:
  `6001989086914692933/sf-imp-0068-production-composed-cave/0/0/680068`
- Geometry seed: `680068`
- Volume bounds:
  - X: `-103.93216077880793 .. 103.93216077880793`
  - Y: `110 .. 300`
  - Z: `-103.93216077880793 .. 103.93216077880793`
- Implementation provenance: `SF-IMP-0068`, `SF-IMP-0082`
- Accepted dressing authorities: DR-00, DR-10, DR-20, DR-30, DR-40, DR-50
- Consumed systems: production morphology/geography, Iron/Copper/Zinc realization, authored
  hydrology, composed caves and native interior population, generic native structure realization,
  and production ecology.

## 2. Runtime/configuration identity

- Minecraft: `1.21.1`
- NeoForge: `21.1.249`
- World type: `skyforge:development`
- Runtime: `SkyforgeNeoForge1211ProductionComposedCaveDevRuntime` with
  `SkyforgeDr50IntegratedRegionEvidence`
- DR-50 integration property: `skyforge.dev.dr50IntegratedRegion=true`
- Reload property: `skyforge.dev.dr50IntegratedRegionReload=true`
- Acceptance level seed: `493030`
- Acceptance server mode: creative, peaceful, online mode disabled, spawn protection `0`,
  view distance `7`

The packet is tied to the exact SHA above. If main moves, regenerate this packet rather than
silently reusing it.

## 3. Windows/PowerShell launch and run directories

From the repository root in PowerShell:

```powershell
.\gradlew :skyforge-neoforge-1211:dr50IntegratedRegionAcceptance
```

This is the complete machine preparation sequence. It creates and uses:

- `.\run-dr50-auto-a\saves\acceptance`
- `.\run-dr50-auto-b\saves\acceptance`
- `.\build\acceptance\dr-50\`

The sequence runs the A and B canonical server preparations, the actual-client reload, and the
stacked-volume support checks. For owner inspection, use the prepared B world:

```powershell
.\gradlew :skyforge-neoforge-1211:runDr50IntegratedRegionAcceptanceReloadClient
```

The client task reopens the `acceptance` world from `.\run-dr50-auto-b`. Do not reuse an older
world directory after changing the SHA, seed, or configuration. If a clean inspection is needed,
rerun the complete preparation task so the configured server-directory setup recreates the
acceptance directories.

The machine result files are:

- `.\build\acceptance\dr-50\production-a.properties`
- `.\build\acceptance\dr-50\production-b.properties`
- `.\build\acceptance\dr-50\reload.properties`

## 4. Initialization and entry point

1. Check out/build from the exact main SHA recorded above.
2. Run the complete DR-50 preparation command.
3. Allow the canonical world to finish initial generation before inspecting.
4. Open the `acceptance` world from `run-dr50-auto-b`.
5. Set the player to creative flight.
6. Enter at the canonical center, above the volume:

```text
/gamemode creative
/tp @s 0 301 0
```

Entry coordinate: block coordinate `(0, 301, 0)`, directly above the canonical volume center.
The volume's highest nominal bound is Y=300, so this is an entry/overview coordinate, not a
claim that the center is the best-looking location.

Required inspection mode: creative mode with flight enabled. Peaceful difficulty is the
machine-preparation default; changing difficulty is outside this packet.

## 5. Feature-presence-derived inspection route

The route is ordered to expose the accepted systems. Coordinates are navigation anchors based on
the exact volume bounds and known system presence; they are not aesthetic selections.

1. **Macro flight view** — from `(0, 301, 0)`, fly upward to approximately Y=360 and orbit
   the bounded region, then descend around the X/Z bounds. Inspect the complete floating
   morphology and underside.
2. **Water** — use the DR-50 hydrology representative position recorded in
   `build\acceptance\dr-50\production-b.properties` as `dr50HydrologyRepresentativePos`.
   Convert the packed `BlockPos` with the normal Minecraft position tooling and fly to that
   position. Confirm the authored water is physically present and remains inside the owned
   volume.
3. **Geology/materials** — inspect the `dr50MaterialPos` value from the same result file. The
   expected material identity is `minecraft:iron_ore`; inspect the surrounding terrain and
   underground access without treating the placement as a mine-site or aesthetic selection.
4. **Interior/geology** — descend through an exposed or safely accessible interior opening
   within the Y range `110..300`. Inspect cave/interior fluids and decoration only as present;
   do not infer completeness beyond the machine evidence.
5. **Structure** — inspect the canonical native structure realization in the accepted volume.
   Its machine proof authority is `DR-30_NATIVE_STRUCTURE_ACCEPTANCE`; its presence does not
   authorize conclusions about final settlement architecture, route geometry, or beauty.
6. **Ecology** — inspect surface carriers and transitions, including the machine-recorded
   land carriers `minecraft:savanna`, `minecraft:plains`, and wet/riparian carrier
   `minecraft:swamp`. The recorded population outcome digest is
   `c61b945e0450800`.
7. **Boundary/vertical stop** — visit the outer bounds near X/Z `±104`, and compare the upper
   surface near Y=300 with the underside near Y=110. Confirm the visible boundary and vertical
   layering without judging whether the span or emptiness is desirable.

If a result file is absent or does not report `PASS`, stop inspection and report the machine
preparation failure instead of substituting coordinates or another specimen.

## 6. Passed machine invariants

The DR-50 acceptance contract records these required invariants:

- deterministic fresh A equals fresh B;
- server and actual-client reload agree;
- exact-volume isolation holds;
- stacked-volume isolation holds;
- authored hydrology survives final composition;
- starting-cluster Iron persists;
- generic structure runtime composes without forcing unrelated content;
- DR-30 structure realization and persistence evidence is reused;
- production ecology persists;
- post-cave interior population completes;
- no escaped generated fluid, overwritten hydrology, invalid structure support, impossible
