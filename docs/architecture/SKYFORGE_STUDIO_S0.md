# Skyforge Studio — S0 Semantic Inspection Architecture

**Status:** S0 ACTIVE  
**Issue:** #1242  
**Purpose:** backend-neutral semantic inspection and human-review acceleration

## 1. Long-term product direction

Skyforge Studio is ultimately intended to let a technically uneducated user describe, compose,
inspect, and refine an ideal procedural world in backend-neutral semantic terms, then realize that
world through any supported adapter.

The intended product boundary is:

```text
human world intent
        |
        v
Skyforge semantic world
        |
        +--> Studio inspection / authoring
        |
        +--> Minecraft adapter
        +--> future game-engine adapter
        +--> simulation / visualization adapter
        +--> other supported realizations
```

Studio is not a Minecraft editor. Minecraft is one realization backend.

## 2. Immediate S0 purpose

The current development problem is narrower: authorship defects are often discovered only after
Minecraft realization, which makes it expensive to determine whether a failure belongs to authored
semantics, reference evaluation, an adapter, or presentation.

S0 therefore begins as a **semantic renderer**.

It should make this review path possible:

```text
authored semantic intent
        |
        v
reference / runtime semantic artifact
        |
        v
Skyforge Studio
        |
        +--> human authorship judgment
        |
        v
adapter realization
        |
        v
backend-specific human judgment
```

A semantic artifact that already looks wrong should be repaired upstream. A sound semantic artifact
that becomes wrong only after realization indicates an adapter / implementation defect.

## 3. Authority model

Studio is a thin client. It does not become a new authority.

- GitHub remains source/code/PR/CI authority.
- Platform-v2 remains objective, workflow, artifact-registration, and human-gate authority.
- Skyforge reference/semantic evaluators remain backend-neutral world-semantics authority.
- External runtime authorities remain authoritative where explicitly contracted (for example A4MC
  atmosphere truth).
- Studio renders exact artifacts and typed scene projections.
- Studio does not silently synthesize missing world truth.
- Local files may be inspected, but an unbound local import cannot satisfy an artifact-bound human
  gate.

The review invariant is:

```text
exact gate
  -> exact registered artifact
  -> exact Studio semantic view
  -> explicit human judgment
```

Studio never chooses the subjective verdict.

## 4. S0 scene boundary

S0 deliberately starts with a small renderer-facing scene model instead of a universal world schema.

Current primitives:

- `VectorField`
- `PointSet`

Expected later primitives, introduced only when real consumers require them:

- `ScalarField`
- `PolylineSet`
- `RegionSet`
- `SurfaceMesh`
- `VoxelSet`
- `VolumeField`
- `TimeSeries`
- `Annotation`

Artifact-specific adapters translate existing authoritative evidence into these renderer primitives.
The browser does not reimplement procedural semantics.

## 5. First vertical slice: atmosphere

`SKYFORGE_ATMOSPHERE_PROBE_VOLUME` is the first Studio semantic artifact.

```text
A4MC authoritative gameplay samples
        |
        v
SkyforgeAtmosphereView
        |
        v
SKYFORGE_ATMOSPHERE_PROBE_VOLUME
        |
        v
Studio atmosphere adapter
        |
        v
VectorField + PointSet
        |
        v
3-D semantic viewport
```

Studio may select mean, gust, or effective wind and inspect signed vertical air, turbulence, shear,
confidence, source level, and authority. These are views of recorded provider data, not a second
atmosphere model.

## 6. Human-gate role

S0 is read-only. It prepares the visual half of a later integrated review workflow.

Near-term target:

```text
machine qualification
  -> artifact registration
  -> Open in Studio
  -> semantic inspection
  -> return to existing typed human-review operation
```

Later, the review form may be presented alongside Studio, but it must still call the same validated
Platform-v2 domain operation used by the Operations Console and MCP surfaces.

## 7. Planned progression

### S0 — semantic inspection shell

- shared Platform-v2 runtime and bearer boundary;
- artifact browser;
- exact artifact provenance;
- local diagnostic import marked non-authoritative;
- generic scene primitives;
- atmosphere renderer.

### S1 — backend-neutral terrain renderer

Render island semantic geometry/fields directly from reference-engine output before Minecraft block
materialization.

### S2 — hydrology renderer

Render drainage, channels, water surfaces, catchments, and related semantic fields without requiring
Minecraft.

### S3 — semantic vs. realized comparison

Bind semantic and backend-specific artifacts to the same specimen identity and expose:

- semantic intent;
- realized backend result;
- difference view;
- ownership/provenance cues.

This is the stage expected to make Authorship-vs-Implementation diagnosis substantially cheaper.

### Early application workflow — local world briefs

Studio may let users capture world intent before an evaluator or realization backend is connected.
The first brief workflow is a browser-local workspace: drafts can be edited, saved in browser storage,
and exported or imported as JSON. They are workspace notes only. They are not semantic artifacts,
evaluator inputs, registered evidence, or human-review evidence, and saving/exporting a brief does
not imply generation or evaluation.

The interface must make the missing generation backend explicit and preserve a clear connection
point for a future backend-neutral service. This workflow does not define world semantics, alter the
semantic scene contract, or claim a generated world exists. When a real generator is added, its
request and response contracts must be established by the owning lane.

### Later — provenance and authoring

Once read-only inspection contracts are stable:

- click-through provenance from visible result to semantic inputs/transforms;
- safe sandbox parameter edits;
- reference reevaluation;
- explicit typed source-change proposals;
- progressively more accessible non-technical authoring UX.

## 8. S0 non-goals

S0 does not add:

- a second world model;
- a second workflow state store;
- arbitrary repository/filesystem access;
- arbitrary shell/Git controls;
- Minecraft block editing;
- final consumer authoring UX;
- a frontend package/build framework;
- automatic human-gate verdicts;
- backend-specific assumptions in the semantic scene contract.

The platform must remain smaller than the game, and Studio must remain a client of semantic truth
rather than its owner.
