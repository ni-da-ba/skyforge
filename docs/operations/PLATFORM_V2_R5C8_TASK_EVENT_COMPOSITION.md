# Platform v2 R5C8 — Durable task-event authority composition

Status: **accepted components composed; hosted production activation remains disabled**

Parent migration: #767  
Tranche: #866  
Predecessors: R5C6 ordinary pipeline and R5C7 trusted task-authority hydration.

## Why a separate authority-event record exists

The accepted `DurableEvent` identity must remain compatible with the legacy replay key.
Task events already preserve issue number, comment ID, body text, and observed time, but
the accepted event schema does not include the trusted actor or comment `updated_at`.

Those missing fields cannot be inferred later without weakening R5C7's exact revision
identity, and adding them to the old event hash would break replay parity.

R5C8 therefore leaves `DurableEvent` unchanged and captures a separate
`TaskAuthorityEventRecord` keyed by the unchanged event ID.

## Capture

For a task event, the signed/trusted raw webhook must agree exactly with the classified
event on:

- event/action shape: `issue_comment/audit_signal`;
- issue number;
- comment ID;
- exact comment body, including leading/trailing whitespace;
- comment `created_at`.

The authority record additionally preserves:

- repository;
- trusted comment actor;
- comment `updated_at`;
- optional delivery ID.

An untrusted actor or any event/payload disagreement fails closed.

Non-task events produce no authority record.

## Durable ledger

The isolated ledger is:

`.skyforge-platform-v2/task-authority-events.json`

with atomic backup:

`.skyforge-platform-v2/task-authority-events.json.bak`

The record is keyed by the unchanged legacy-compatible `event_id`.

- identical semantic redelivery is idempotent even when transport delivery IDs differ;
- a conflicting authority revision for the same event ID is rejected;
- reference digests are verified on reload;
- the legacy `.skyforge-orchestrator/state.json` is never written.

Delivery ID is diagnostics only. Repository authority is event ID + exact source
reference identity.

## Fresh hydration

The durable authority-event record is not sufficient by itself to execute.

Before R5C6 input can be constructed, accepted R5C7 must re-read the exact GitHub issue
and comment and return `EXECUTABLE_V2`.

Deletion, closure, edit/revision drift, actor drift, malformed typed authority, or an
unstructured legacy task therefore remains fail-closed.

R5C8 also hardened R5C7 body identity so a whitespace-only comment edit changes the
authority revision and cannot silently reuse an earlier task event.

## Classifier seed

After fresh hydration, R5C8 creates a bounded `ClassifierRequest` whose semantic input
contains:

- exact current accepted main;
- exact authority event ID;
- governing issue;
- R5C7 authority identity digest;
- R5C4 repository-authority digest;
- repository-owned lane/objective/stop boundary;
- allowed/protected paths;
- auto-merge policy;
- bounded issue/context text.

This is context for a proposal only. The classifier cannot create or widen authority.

## R5C6 request composition

`compose_ordinary_task_request(...)` constructs the existing
`OrdinaryPipelineRequest` using:

- the exact R5C7 `RepositoryTaskAuthority`;
- the exact classifier request seed;
- explicit freshness observation;
- explicit external-producer claims;
- explicit provider/local quota observations;
- explicit attempt number;
- explicit PR metadata.

The task event is recorded as both `event_keys` and `authority_event_keys`; no ordinary
event is manufactured.

R5C4 admission remains mandatory after classifier output, so lane/objective/stop-boundary,
issue ownership, path scope, freshness, external ownership, and quota are still checked
before a worker can exist.

## Activation boundary

R5C8 does **not** import this composition into `platform_v2_hosted_runtime.py`.

It performs no provider call, worktree creation, Git/GitHub mutation, roadmap mutation,
or authority switch.

The next tranche may wire signed webhook capture and this durable composition into the
hosted runtime in a still-fail-closed activation/preflight mode before the final
`LEGACY -> NONE -> V2` writer-authority transition.
