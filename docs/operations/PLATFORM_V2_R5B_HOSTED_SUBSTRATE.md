# Platform v2 R5B — Hosted runtime substrate

Status: **STAGED FOR ACCEPTANCE — NO PRODUCTION AUTHORITY**

Parent migration: #767  
R5A: #847 / PR #849  
R5B authority: #850

## Purpose

R5B creates a clean hosted-process boundary for Platform v2 without performing the production authority switch.

The new runtime is:

`scripts/orchestrator/platform_v2_hosted_runtime.py`

It accepts the existing hosted command-line transport contract and is intentionally incapable of GitHub/project mutation.

## Ingress

The substrate provides:

- localhost HTTP bind/port compatible with the current systemd service;
- `GET /healthz`;
- `POST /webhook`;
- GitHub HMAC-SHA256 verification;
- repository full-name validation;
- delivery-ID replay suppression;
- semantic event replay suppression through the v2 durable event identity;
- atomic primary/backup hosted state.

Its durable state is isolated under:

`.skyforge-platform-v2/hosted-state.json(.bak)`

It does not write `.skyforge-orchestrator/state.json`.

## Classifier compatibility seam

R5B uses one explicit legacy compatibility dependency:

`skyforge_orchestrator.classify_event`

and the matching pure control-command classifier.

The adapter imports the base core module only. It does not import any runtime extension module and therefore does not install or depend on the production monkey-patch stack. The resulting `EventDecision` is immediately converted to the canonical v2 `DurableEvent`, preserving existing event identity while later policy migration continues.

This dependency is visible and testable rather than an import-time behavior replacement.

## Authority projection

At startup and before accepted webhook ingestion, the substrate reads current legacy JSON state and projects it through `LegacyOperationalProjection`.

The projection preserves:

- active external-producer claims, including #613 and #754;
- roadmap identity and blocked human-gate nodes;
- pending/managed authority if present;
- a digest of human-gate records without copying human-gate tokens into public evidence.

The hosted v2 state may persist the projection. It receives no authority merely because the projection exists.

## Hard read-only boundary

R5B has no adapter for:

- worker dispatch;
- branch push;
- PR creation/merge/closure;
- issue/comment mutation;
- roadmap mutation;
- human-gate mutation;
- Codex/provider work;
- production WriterFence acquisition.

Trusted legacy control commands are recognized for semantic compatibility but returned as read-only/rejected and cannot mutate legacy or v2 production authority.

`--auto-merge` is rejected at startup.

## Handoff mechanism

R5A discovered that the service account cannot run privileged `systemctl stop`. R5B does not add sudo.

The existing accepted controller already supplies a safer privilege-free replacement boundary:

1. operator issues trusted `/skyforge-pause`;
2. after quiescence, operator issues `/skyforge-refresh-runtime`;
3. legacy `sync_main()` performs fetch + checkout main + ff-only pull;
4. if an accepted controller-runtime path changed, legacy records restart intent;
5. the legacy process calls `os._exit(75)`;
6. only after that process has terminated, systemd `Restart=on-failure` starts the accepted source.

Therefore the writer sequence remains:

`LEGACY -> NONE -> replacement`

not `LEGACY -> V2`.

## Stable ingress

The future activation is designed to keep:

- the same systemd service;
- the same Caddy route;
- the same public HTTPS health/webhook endpoints;
- the same localhost `127.0.0.1:3000` listener;
- the same webhook secret.

The activation PR itself must be separately accepted and may switch the stable entrypoint only after a mutation-capable v2 hosted executor is accepted.

## Remaining Release-5 blockers

R5B does **not** make Platform v2 the production controller.

After R5B, the remaining blockers are:

1. live legacy checkout must be synchronized to the accepted cutover head at the actual handoff;
2. ordinary mutation-capable hosted v2 execution must be built and accepted.

Until both are cleared, `ordinary_v2_mutation_authority=false`.

## DR-70 boundary

R5B neither modifies nor passes DR-70. The existing #754/#769 authority and `dr-human-exploration-rereview` human gate remain preserved state.
