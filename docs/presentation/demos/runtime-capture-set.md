# Skyforge canonical runtime capture set

This document records the Presentation-owned acquisition path for genuine Minecraft runtime stills used by PRES-0003/PRES-0004.

It does **not** create a new producer acceptance claim. The underlying worlds, persistence proofs, morphology carriers, and ecology fixture remain owned by Implementation/Authorship acceptance.

## Durable capture path

Manual workflow:

- `.github/workflows/presentation-runtime-captures.yml`

Capture helper:

- `scripts/presentation/capture-minecraft-viewer.sh`

The workflow rebuilds the already accepted saved-world fixtures, launches their existing actual-client viewers under Xvfb/software rendering, drives the accepted guided review commands, hides the HUD, captures through Minecraft's normal F2 screenshot path, and uploads the resulting PNGs plus client/Xvfb logs.

The workflow is deliberately `workflow_dispatch`-only so Presentation capture cost does not become recurring CI cost.

## Proven capture run

Branch validation run **#2 / Actions run 34185345915** at
`55e7cd7cdc60b1e9639a1cfb0721853d5962a1b6` completed all six capture jobs successfully:

- Massif;
- Tableland;
- Spine;
- Basin;
- Lobed;
- accepted forest/taiga ecology.

The run emitted the intended **23 non-empty Minecraft PNGs**:

- four morphology views per family: `above`, `approach`, `below`, `orbit`;
- three ecology views: `panorama`, `lower_forest`, `upper_taiga`.

A first pass exposed a screenshot-file race in which Minecraft could create the PNG pathname before finishing the image bytes. The final helper waits for a nontrivial, stable file size before copying the screenshot. Run #2 reproduced the full set with that guard.

A later branch experiment attempted to force camera yaw/pitch through a vanilla self-teleport command. Minecraft 1.21.1 rejected that command form, so the experiment was removed before merge. It is not part of the durable capture path.

## Presentation selection guidance

Treat the 23 images as **source captures**, not as 23 mandatory published frames. Human-eye selection remains appropriate because the existing mutation-inert viewers were built for engineering review rather than photography.

Strong current source roles include:

- **Massif `above`** — readable upper-surface morphology and macro/meso relief;
- **Basin `above`** — basin/rim planform;
- **selected family `below` views** — useful when the top/approach camera is compositionally weak;
- **morphology `below` views** — finite 3-D ownership, underside/interior geometry, caves and exposed volume;
- **ecology `panorama` / `lower_forest` / `upper_taiga`** — accepted forest/taiga runtime ecology.

Some Tableland/Lobed and some approach/orbit frames can be compositionally weak because the existing viewer performs a position teleport and then server-side yaw/pitch mutation; that rotation is not guaranteed to synchronize to the actual client camera. This is a **viewer/capture-composition limitation**, not evidence that the accepted carrier changed.

Presentation should therefore choose the strongest actual-runtime frame for the communication task rather than pretending every guided stop is equally photogenic.

## Persistence/reopen boundary

The capture workflow launches the accepted viewer paths after saved-world preparation/reopen, and the viewer logs preserve the corresponding persistence proof.

It does **not** currently produce a rigorously matched pre-save/post-reopen visual pair. Until such a pair can be obtained without creating a special presentation-only world lifecycle, Presentation should pair a reopened-world still with the accepted deterministic/persistence evidence rather than imply that two merely similar screenshots constitute a persistence proof.

## Publication boundary

These captures may support statements such as:

- the accepted Minecraft backend realizes exact finite floating terrain volumes;
- accepted morphology families have real Minecraft carriers;
- accepted ecology population is visible in the runtime;
- accepted saved-world viewers survive reopen with their owning producer evidence intact.

They must not be used to imply:

- every Authorship semantic opportunity is physically realized in Minecraft;
- Bootstrap Province is complete;
- every guided screenshot angle is itself an acceptance gate;
- concept/generated imagery is equivalent to runtime evidence.
