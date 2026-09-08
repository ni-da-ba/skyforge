# Wave C25 — Create: Diesel Generators petroleum-authority A/B

**Status:** IN PROGRESS  
**Issue:** #376  
**Predecessor:** C24 / PR #373  
**Petroleum policy inputs:** C22-C24 + AUTH-0098/AUTH-0100  
**Parent vertical slice:** #224

## Purpose

C25 proves that the leading retained petroleum machinery library can be separated from its own
independent petroleum geography.

Accepted Skyforge policy already says:

```text
AUTH-0098 / AUTH-0100
    -> authored geological opportunity

C22
    -> STRATEGIC_NODE / R3 mature-industry policy

C23
    -> petroleum-province feasibility / replan

C24
    -> coarse extraction/refinery/freight candidate nomination
```

Create: Diesel Generators must therefore not independently decide where petroleum exists.

## Audited upstream behavior

The isolated runtime pins Create: Diesel Generators **1.21.1-1.3.15** by immutable Modrinth version
id `UoPH8lO1`, with Create 6.0.10.

The audited upstream 1.21.1 source exposes:

- `OilChunksSavedData.getBaseOilAmount(...)`, which computes oil by Minecraft chunk from world seed,
  Perlin noise, and biome tags;
- persistent per-chunk depletion under `cdg_oil_chunks`;
- server config values `DISABLE_NORMAL_OIL_CHUNKS` and `DISABLE_HIGH_OIL_CHUNKS`;
- pumpjack output tag containing `createdieselgenerators:crude_oil`;
- pumpjack validation that follows a vertical pipe until an `oil_deposit`-tagged block;
- the upstream `oil_deposit` tag contains `minecraft:bedrock`.

Those are retained-mod mechanics, not Skyforge petroleum meaning.

## A/B runtime

### Baseline

A disposable server uses the upstream defaults.

Acceptance requires:

- Create and Create: Diesel Generators load;
- normal/high native oil suppression flags are both false;
- crude oil, Pumpjack Hole, Distillation Tank, and Diesel Engine remain registered.

### Suppressed control

An otherwise isolated disposable server writes only:

```text
wave-c25-suppressed/serverconfig/createdieselgenerators-server.toml
```

with both native oil classes disabled.

Acceptance requires:

- the live server reads both flags as true;
- representative calls to the upstream native base-oil query return zero;
- the same retained machinery/fluid assets remain registered;
- KubeJS is absent from the specimen so no script listener can bypass the native config result.

The validation config is generated only inside the disposable run directory. It is not packaged under
`src/main/resources`.

## Production policy if accepted

Unlike C21's Create Zinc case, global suppression is the intended petroleum policy.

Leaving Diesel Generators native chunk oil active anywhere in the accessible Overworld would create a
parallel petroleum geography that bypasses C22/C23 strategic-node semantics.

Therefore:

```text
Diesel Generators machinery / crude / refining / engines    KEEP
Diesel Generators native chunk-noise petroleum authority    SUPPRESS GLOBALLY
AUTH-0098/AUTH-0100 + C22-C24                               SKYFORGE MEANING
literal petroleum source / depletion / pumpjack bridge      IMPLEMENTATION
```

C25 does **not** select the production adapter technique.

Implementation should choose the narrowest viable seam only after a focused proof. Candidate seams
include retained-mod integration APIs/events where genuinely available, or a thin direct compat
adapter. C25 does not justify adding KubeJS merely to obtain its optional `CDGEvents.oilAmount`
hook.

## Important pumpjack boundary

Upstream pumpjack validity currently depends on a vertical pipe reaching the `oil_deposit` tag,
whose default contains bedrock.

That must not be reinterpreted as authored geology.

A later Implementation adapter must decide how a Skyforge-owned petroleum source is exposed to the
retained pumpjack while preserving exact authored volume ownership and C23/C24 site intent.

## Non-goals

C25 defines no:

- deposit location, count, thickness, quantity, pressure, or depletion curve;
- pumpjack/refinery final site;
- refinery footprint or local grade requirement;
- strategic-node frequency;
- first-flight petroleum dependency;
- KubeJS production dependency.

## Acceptance

1. exact pinned Create + Diesel Generators runtime resolves;
2. baseline live server reads both native oil disable flags as false;
3. suppressed live server reads both flags as true;
4. suppressed native base-oil queries return zero at representative chunks;
5. crude oil, pumpjack, distillation, and diesel-engine assets remain registered in both modes;
6. validation-only config remains outside production resources;
7. dedicated C25 A/B and repository CI pass;
8. state records the downstream Implementation petroleum-source-adapter boundary.

No Minecraft human-eye or manual play gate applies to C25.
