# Skyforge SF-IMP-0047 Piece-Aware Structure Footprints

## Purpose

SF-IMP-0047 narrows Skyforge's native-structure geometry from one enclosing `StructureStart` rectangle to the actual native `StructurePiece` geometry that touches the already-resolved support plane.

The goal is compatibility through better generic observation, not structure classification. Skyforge still does not know what a mansion, village, ruin, tower, or modded structure means. It only observes geometry Minecraft already produced.

## Problem

SF-IMP-0045 and SF-IMP-0046 deliberately began with `StructureStart.getBoundingBox()` as a conservative footprint. That proved the admission and accommodation seams, but an enclosing box can contain large areas where no native structure piece exists.

For sparse or multi-building starts this creates two avoidable errors:

- support admission can reject a valid native structure because empty courtyard/gap space lacks terrain;
- fill-only accommodation can add foundation beneath empty space between native pieces.

Both errors become more likely for unknown modded structures because Skyforge cannot rely on assumptions about their layouts.

## Decision boundary

SF-IMP-0047 changes only horizontal support geometry. It intentionally retains the proven SF-IMP-0046 vertical-resolution seam.

A native start must still:

1. have retained Skyforge height provenance;
2. resolve its overall start minimum Y within the existing one-block occupied/free tolerance;
3. resolve to one unambiguous Skyforge volume.

Only after those conditions hold does 0047 derive a piece-aware X/Z footprint.

## Floor-contact piece extraction

For this milestone, a native piece contributes support geometry when:

```text
piece.boundingBox.minY == StructureStart.boundingBox.minY
```

Pieces above that plane are superstructure and do not enlarge the support footprint. Exact duplicate horizontal boxes are removed while preserving native order.

If a valid native start exposes no piece at the resolved floor, Skyforge falls back to the historical enclosing start box. This is the compatibility-biased fallback:

```text
known floor-contact geometry → use it
unknown/empty floor geometry → conservative 0046 envelope
```

No structure type lookup is involved.

## Neutral composite footprint

`skyforge-world` now represents support area as a `SurfaceFootprint`: a non-empty union of axis-aligned `SurfaceFootprintRectangle` values.

The historical rectangular `SurfaceSupportRequirements` constructor remains available and creates a one-rectangle footprint, so existing callers preserve their semantics.

Sampling over a composite footprint uses the union itself, not its enclosing rectangle:

```text
+------+       +------+
|piece |       |piece |
|  A   |  GAP  |  B   |
+------+       +------+

sample A + B
ignore GAP
```

The clearance region is likewise the union of the individually expanded rectangles minus the footprint union. Corners of the overall envelope that are unrelated to any piece are not clearance requirements.

## Coherence semantics

A composite footprint may intentionally contain multiple disconnected components. Therefore `coherentSurface` no longer means that the entire footprint must produce exactly one supported component.

Instead, every sampled required footprint component must retain exactly one supported component of its own.

This preserves the original anti-fragmentation invariant inside each required region while permitting intentionally separated buildings.

## Minecraft admission and accommodation

`MinecraftStructureSupportGeometry` extracts distinct floor-contact boxes from the native start.

`MinecraftStructureSupportPolicy` converts those boxes to one neutral `SurfaceFootprint` for both:

- natural support admission;
- bounded fill-only foundation feasibility.

The thresholds and vertical rules from SF-IMP-0046 do not change.

## Persistent foundation footprint

`SkyforgeFoundationPiece` remains one ordinary serialized Minecraft structure piece so persistence and chunk-clipped placement remain native.

Its Minecraft bounding box is the envelope of all admitted floor-contact rectangles, but it separately serializes the exact horizontal rectangle union. During `postProcess(...)`, a column is eligible only if it lies in at least one serialized footprint rectangle.

Therefore the piece can participate in Minecraft's normal bounding-box lifecycle without filling empty inter-piece gaps.

Older serialized 0046 foundations that lack the new footprint array fall back to their serialized bounding-box envelope. This preserves compatibility with previously generated development worlds.

## Invariants

- Vanilla/modded structure selection and piece generation remain authoritative.
- Skyforge never requires a per-structure compatibility registry.
- Unknown structures automatically benefit when they expose ordinary `StructurePiece` bounding boxes.
- Higher native pieces do not create support requirements merely through X/Z projection.
- Empty gaps between floor-contact pieces are not terrain requirements.
- Empty gaps are not filled by accommodation.
- Every required footprint sample still belongs to one independently evaluated Skyforge volume.
- Vertically stacked Skyforge volumes are never fused.
- Natural admission still precedes accommodation.
- Fill-only accommodation retains the existing no-cut, no-edge-bridge, exact-volume, and bounded-depth rules.
- If floor-contact piece geometry cannot be established, the conservative 0046 envelope is preserved rather than guessing.

## Deferred work

SF-IMP-0047 does **not** attempt to solve:

- structures with multiple independently resolved support elevations;
- underground occupancy envelopes;
- excavation or terrain cutting;
- roads, bridges, stairs, courtyards, or semantic settlement adaptation;
- exact per-block structure masks.

Those require separate evidence and should not be smuggled into the generic footprint seam.

## Compatibility test

The design remains subject to the project-wide naturalization test:

> If a mod we have never seen adds an ordinary multi-piece structure tomorrow, does this improvement help it without a Skyforge-specific adapter?

For SF-IMP-0047 the answer is yes: its native floor-contact piece boxes become the support footprint automatically.
