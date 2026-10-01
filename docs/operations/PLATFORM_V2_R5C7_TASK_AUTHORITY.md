# Platform v2 R5C7 — Trusted typed task authority

Status: **inert authority hydration; hosted production activation remains disabled**

Parent migration: #767  
Tranche: #864

## Purpose

R5C6 composes the ordinary classifier -> admission -> worker -> handoff pipeline, but
R5C4 correctly requires repository-owned `RepositoryTaskAuthority` as an explicit input.
R5C7 supplies that authority from an exact trusted GitHub issue comment without allowing
classifier prose or issue prose to manufacture edit scope.

## Transitional directive

A task comment remains compatible with the legacy wake convention and contains exactly
one marker:

`[SKYFORGE TASK AUTHORITY]`

The marker is followed by exactly one JSON object. Supported fields are:

- `lane` — required;
- `objective` — required;
- `stop_boundary` — required;
- `allowed_paths` — required and non-empty;
- `protected_paths` — optional;
- `auto_merge_eligible` — optional, default false.

Example:

```text
AUDIT NEW TASK [SKYFORGE TASK AUTHORITY]
{"lane":"Implementation","objective":"Implement bounded feature","stop_boundary":"merge boundary","allowed_paths":["src/main/java/**"],"protected_paths":["scripts/orchestrator/**"],"auto_merge_eligible":false}
```

Worker tier is intentionally absent. LUNA/TERRA remains an ephemeral classifier proposal
and cannot widen lane, objective, stop boundary, issue ownership, or file scope.

## Authority source

The durable task event binds:

- repository;
- issue number;
- exact comment ID;
- trusted actor;
- exact comment body digest;
- created timestamp;
- updated timestamp.

Before v2 may convert the task into R5C4 authority, the hydrator re-reads the exact issue
and exact comment from GitHub through a read-only allowlist.

Hydration fails closed when:

- the issue/comment no longer exists;
- the issue is closed;
- the live issue/comment identity differs;
- the actor is not trusted;
- the comment body or timestamps changed after the durable event;
- the comment belongs to a different issue/repository;
- the typed JSON is malformed;
- the marker is duplicated;
- required authority fields are absent;
- path scope is malformed or attempts traversal/absolute paths/unsupported globs.

A legacy `AUDIT NEW TASK` comment with no typed marker remains visible to v2 but returns
`NOT_EXECUTABLE_V2`. Issue title/body are bounded context only and never widen authority.

## Path authority

R5C7 accepts normalized repository-relative paths only. It rejects:

- absolute Unix or Windows paths;
- `./`, `../`, embedded dot/traversal segments;
- empty path segments;
- arbitrary wildcard syntax.

The only wildcard form is a trailing `/**` directory scope. The resulting typed directive
is then converted to the already-accepted R5C4 `RepositoryTaskAuthority`, whose dispatch
admission still enforces classifier proposal scope as a subset of repository authority.

## GitHub capability boundary

The R5C7 GitHub adapter permits only two exact read commands for its bound task event:

- read the governing issue;
- read the exact authority comment.

It cannot comment, edit issues, create branches/PRs, dispatch workflows, invoke workers,
or perform any repository mutation.

## Activation boundary

R5C7 does **not** modify or import into `platform_v2_hosted_runtime.py`.

A subsequent tranche must explicitly compose durable task events -> exact task-authority
hydration -> R5C6 ordinary pipeline before hosted activation/cutover is considered.
