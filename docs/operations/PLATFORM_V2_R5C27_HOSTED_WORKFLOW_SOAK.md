# Platform v2 R5C27 — Disposable hosted workflow soak and maturity evidence

R5C27 exercises the accepted R5C24–R5C26 hosted workflow as a reusable background
service in disposable isolation. It does **not** stage or switch the production writer.

## Maturity gap closed

The first repeated-task soak exposed a real one-shot-runtime gap: after a managed PR
completed, the hosted coordinator retained its singleton task-plan, admission, and local
handoff ownership. A second bounded task therefore could not acquire clean ownership.

R5C27 adds a restart-safe hosted completion ledger. Managed lifecycle completion is
persisted before cleanup. A later durable cleanup boundary then retires the exact inbox
event, marks it completed for semantic replay suppression, clears only the matching
singleton plan/admission/local-commit slots, and finally marks the completion record
cleaned.

If the process stops between completion recording and cleanup, the next coordinator
advance sees the pending completion before it can claim another task. This makes cleanup
idempotent and prevents partial retirement from opening authority for a new task.

The hosted runtime reloads its in-memory ingress snapshot after every coordinator advance,
so durable completion cleanup cannot later be overwritten by stale process memory.
Health reports total/cleaned completions and any pending cleanup identity.

## Disposable soak

The soak uses:

- a temporary deterministic Git repository;
- the real \`HostedV2Substrate\`;
- the real R5C25 \`HostedExecutionDriver\`;
- signed webhook ingress;
- the real durable task/classifier/admission/worker/commit/effect/completion stores;
- the real R5C25 durable budget wrappers;
- deterministic fake classifier and worker delegates;
- a deterministic in-memory GitHub-shaped remote and fake effect transport.

It never invokes live Codex providers, GitHub effects, systemd, Caddy, or the production
writer.

Three sequential bounded tasks are driven through the background runtime. Coverage
includes:

1. same-delivery suppression;
2. restart after the first task reaches remote handoff;
3. managed lifecycle completion and cleanup after restart;
4. semantic replay suppression after completed authority;
5. a second task completed by the bounded periodic wake without a second webhook;
6. an additional idle periodic interval with zero budget change;
7. a third task with one injected managed-PR truth interruption;
8. recovery on a later wake without repeating classifier, worker, push, or PR-creation work;
9. same-day legacy budget seeding and shared Luna accounting;
10. UTC-day rollover without stale legacy re-seeding;
11. a separate injected classifier-provider failure that remains durably failed across
    restart/wake with exactly one provider attempt and zero worker/remote effects.

## Reproducible evidence

Two fresh disposable runs produced the same stable evidence digest:

\`8facc71b9d3e451d2ae2b3d9fdad873e1e6621effe4948f013098aa5d72acee0\`

The separate durable provider-failure scenario reproduced:

\`f5da73a30607260753386ba2cdd6da44bf076a0dc0c1bd306f978046bce22064\`

The machine-readable report is retained at:

\`docs/operations/evidence/platform_v2_r5c27_hosted_soak.json\`

The stable report records exact task event/attempt/handoff identities, effect identities,
provider call totals, budget transitions, replay suppression, restart count, final durable
idle state, and explicit assertions that production systemd/Caddy/writer authority and
DR-70 were untouched.

## DR-70 and production authority

DR-70 remains deferred and unresolved per operator direction. The disposable soak does
not consume, clear, infer, or machine-pass it.

Production writer authority remains \`LEGACY\`. R5C27 performs no:

- production unit staging or service restart;
- Caddy/webhook-route mutation;
- live provider spend;
- live GitHub mutation;
- controller sudo grant;
- \`LEGACY -> NONE -> V2\` transition.

R5C27 is workflow-maturity evidence only. It does not authorize a live cutover.
