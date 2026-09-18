# Platform v2 R5C14 — Durable roadmap and human-gate authority

Status: **restart-safe roadmap/gate authority in isolation; hosted production activation remains disabled**

Parent migration: #767  
Tranche: #878  
Predecessor: R5C13 durable external/manual producer ownership.

## Audit defect closed

The earlier cutover projection proved that human-gate state existed by retaining only a
digest of `human_gate_records`, and it omitted roadmap `completed_runs`.

That was sufficient for comparison/readiness reporting but insufficient for a real
authority handoff: after restart v2 could not reconstruct exact gate deduplication records
or determine which roadmap prerequisites had already completed.

R5C14 makes the semantic state reconstructible.

## Cutover projection

`LegacyOperationalProjection` now preserves exact semantic human-gate records:

- gate key;
- gate token;
- target issue/PR;
- whether visibility was seeded from existing GitHub truth.

It retains the aggregate digest as a derived compatibility property.

`RoadmapProjection` now preserves:

- roadmap ID;
- accepted manifest fingerprint;
- completed run counts;
- blocked nodes and their semantic reasons;
- active authority;
- daily claim date;
- daily claim count.

Diagnostics such as timestamps and counters unrelated to authority remain non-authoritative.

## Durable v2 roadmap ledger

The v2 roadmap authority store is:

`.skyforge-platform-v2/roadmap-authority.json`

with the standard atomic backup.

Its semantic contents are:

- roadmap ID / accepted manifest fingerprint;
- completed run counts;
- blocked node records;
- at most one active roadmap authority;
- daily claim budget;
- exact human-gate records.

Unknown nodes, duplicate authority, malformed counts, or manifest-fingerprint mismatch
fail closed.

## Frozen active task meaning

Selecting an eligible roadmap task creates one `RoadmapActiveAuthority`.

It includes the exact durable roadmap task event, including:

- roadmap/node/run identity;
- governing issue;
- lane;
- objective hint;
- stop boundary.

The event identity is frozen at claim time. A later manifest edit cannot silently change
the meaning of already-active work.

A stale `RoadmapShadowDecision` is rejected if its source-state digest no longer equals
the current durable roadmap state.

## Roadmap transitions

R5C14 consumes the already-accepted pure roadmap policy.

### Closed blocked tasks

When a blocked task's governing issue is provably closed, its completed count is advanced
to the node's accepted `max_runs`, the task block is retired, and selection continues.

### Open eligible task

If the daily claim ceiling permits it, exactly one active authority/event is created.

The daily budget resets only when the supplied UTC day changes.

### Active issue recovery

The accepted closed-active recovery policy is used directly:

- active issue OPEN with no PR -> ordinary roadmap lifecycle retains authority;
- active issue CLOSED with no PR -> complete the node to `max_runs` and clear active ownership;
- active issue with a bound PR -> delegate to the managed-PR lifecycle;
- unknown truth -> block.

Managed PR binding is exact issue-scoped and cannot replace an existing different PR.

## Human gates

Selecting a roadmap gate never increments completion.

Instead, R5C14:

1. durably blocks the exact gate node;
2. derives the same generic program-gate identity convention used by the legacy
   controller;
3. reports whether visibility needs to be surfaced.

Recording a visibility result updates only the gate-record ledger. It does **not** remove
the roadmap block or complete the gate.

The machine therefore has no transition that self-passes a roadmap human gate.

## DR-70 re-review

The current accepted roadmap ends at `dr-human-exploration-rereview`.

Its project direction explicitly requires local human inspection and states that machines
must not self-pass the re-review.

R5C14 preserves this behavior mechanically:

- accepted DR-70 completion may make the re-review node eligible;
- v2 can convert that eligibility only into a durable blocked gate;
- the blocked gate remains blocked across restart;
- terminal-gate quiescence may retire ordinary lifecycle noise while preserving protected
  task/roadmap authority;
- no R5C14 function completes, removes, or auto-accepts that gate.

R5C14 does not modify the DR-70 implementation, evidence, roadmap order, or live
production controller state.

## Terminal-gate quiescence

The existing pure `classify_terminal_gate_quiescence(...)` policy composes directly with
the durable ledger.

At a terminal blocked gate it may identify ordinary event noise for retirement.

It delegates instead when:

- protected task/roadmap authority exists;
- another eligible roadmap node remains;
- a blocked task requires reconciliation;
- a worker/decision/controller block is in flight.

Protected authority therefore cannot be erased merely to make the controller appear idle.

## Safety boundary

R5C14 does not:

- import into or activate the hosted production runtime;
- post a human-gate comment;
- mutate GitHub;
- invoke classifier or worker providers;
- mutate legacy controller state;
- change production writer authority;
- alter DR-70 behavior or pass its human re-review.

The next Release-5 integration work can now consume reconstructible roadmap, external,
ordinary-task, and human-gate authority rather than depending on legacy runtime
monkey-patches or chat history.
