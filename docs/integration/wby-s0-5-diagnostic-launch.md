# WBY S0.5 converged diagnostic launch

This is the pre-tuning launch surface for the admitted S0/S0.5 stack.

## Goal

Launch the accepted stack together, reproduce cross-mod behavior in one runtime, and preserve enough evidence to diagnose failures before changing gameplay or performance settings.

Do **not** tune individual mods during this pass unless a defect prevents the combined profile from launching or makes diagnosis impossible.

## Launch

From the repository root:

```bash
bash scripts/wby-s0-5-diagnostic-launch.sh client
```

For a dedicated server:

```bash
bash scripts/wby-s0-5-diagnostic-launch.sh server
```

The wrapper invokes the non-acceptance Gradle profiles `runWbyS05BDiagnosticClient` or `runWbyS05BDiagnosticServer`. Both reuse `skyforge-neoforge-1211/run-wby-s05b`, the same cumulative S0.5 runtime exercised by machine acceptance.

## What is intentionally included

The diagnostic profile inherits the S0 physical/runtime shell, S0.5 pack authority, and S0.5 information/QoL shell. The conservative policy fixtures from `wby-s0-5-policy/` are staged before launch so the human run matches the machine-gated policy surface.

## Evidence

Each wrapper invocation writes a timestamped bundle under `.skyforge-diagnostics/` containing:

- complete wrapper/Gradle console output;
- Minecraft `latest.log` when produced;
- crash reports when produced;
- the effective runtime config directory;
- the staged mod filename list.

Use that bundle to classify failures as loader/dependency, world/data, rendering/visibility, UI/QoL, policy/config, or performance. Only after the combined profile is reproducibly launchable should tuning begin.
