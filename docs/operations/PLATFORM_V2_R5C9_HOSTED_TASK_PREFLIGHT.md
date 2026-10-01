# Platform v2 R5C9 — Hosted task-authority capture and dry preflight

Status: **hosted ingress composition accepted for testing; mutation authority remains disabled**

Parent migration: #767  
Tranche: #868  
Predecessors: R5B hosted read-only substrate, R5C7 trusted task authority, R5C8 durable task-event composition.

## Purpose

R5C9 is the first tranche that intentionally wires trusted task-authority metadata into
`platform_v2_hosted_runtime.py`. It does not activate ordinary execution.

The hosted process remains incapable of:

- classifier/provider invocation;
- worker/worktree creation;
- branch push;
- issue/comment mutation;
- PR creation/merge;
- writer-fence acquisition;
- roadmap mutation.

## Fail-closed webhook ordering

For a signed task comment:

1. verify HMAC and repository identity;
2. classify through the accepted legacy-compatible ingress adapter;
3. construct the unchanged `DurableEvent`;
4. capture the exact R5C8 task-authority record from the same raw signed payload;
5. durably persist the task-authority record;
6. only then enqueue/persist the durable task event.

This ordering is deliberate.

A crash after step 5 and before step 6 leaves harmless authority metadata that owns no
queued work. The inverse state—queued task event without captured actor/revision
identity—is forbidden.

Capture validation failure returns a non-successful task acceptance response and does not
enqueue the task event. Authority-store persistence failure likewise prevents event
enqueue.

Non-task webhooks retain the accepted R5B ingress path and create no authority record.

## Redelivery and restart

Transport delivery identity is not repository authority.

- repeated identical delivery IDs are suppressed by hosted ingress;
- semantic redelivery under a different delivery ID reuses the R5C8 authority record and
  existing durable event identity;
- hosted restart reloads the inbox and task-authority ledger independently from their
  atomic primary/backup files.

## Dry preflight

The runtime exposes an internal Python method, not an HTTP endpoint:

`preflight_task_event(event_id)`

It performs only:

1. load the exact captured authority record;
2. fresh R5C7 read-only issue/comment hydration;
3. strict read-only GitHub accepted-main lookup;
4. R5C8 classifier-seed construction.

The result is one of:

- `READY_FOR_CLASSIFIER`;
- `NOT_EXECUTABLE_V2`;
- `REJECTED`.

This method does not call the classifier. It does not construct/advance an R5C6 ordinary
pipeline request. It does not invoke a worker or remote effect.

An old unstructured `AUDIT NEW TASK` remains durable and visible but preflights as
`NOT_EXECUTABLE_V2`.

Edited/deleted/closed/untrusted authority fails closed before accepted-main lookup when
possible.

## Accepted-main capability

The current-main reader permits exactly:

```text
gh api repos/<owner>/<repo>/commits/main --jq .sha
```

No method override or other Git/GitHub command is accepted by the validator.

## Health contract

Hosted health continues to report:

- `mutation_authority = false`;
- `worker_dispatch_enabled = false`;
- `remote_effect_execution_enabled = false`;
- `ordinary_v2_mutation_authority = false`.

R5C9 additionally reports:

- `task_authority_capture_enabled = true`;
- durable task-authority record count;
- `task_preflight_enabled = true`.

## Network/control surface

The HTTP surface remains:

- `GET /healthz`;
- `POST /webhook`.

No task-preflight HTTP endpoint is added. Trusted legacy control directives remain
recognized but rejected/read-only as in R5B.

## Production boundary

R5C9 does not change the production systemd entrypoint and does not run a live authority
switch. The legacy production controller remains the sole ordinary writer.

A subsequent tranche must explicitly wire the accepted preflight result into bounded
classifier/pipeline execution under an activation gate, then satisfy the Release-5
cutover readiness contract before `LEGACY -> NONE -> V2` writer transfer.
