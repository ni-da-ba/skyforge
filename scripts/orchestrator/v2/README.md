# Skyforge Platform v2 control-plane primitives

This directory is the inert starting point for the Platform v2 migration tracked by issue #767.

## Current authority boundary

These modules have **no production authority**. The current hosted controller must not import them yet.
They exist only to establish separately testable primitives before any shadow/canary wiring occurs.

Release 1A may define:

- frozen task/spec/attempt identity;
- exact-SHA acceptance identity;
- replay-record digests;
- a single-writer fence primitive;
- deterministic unit tests for those concepts.

Release 1A must not:

- modify or replace the active controller entrypoint;
- mutate live controller state;
- deploy/restart/reload the hosted service;
- alter roadmap or DR-70 human-gate authority;
- change Java/NeoForge/world-generation semantics;
- grant v2 repository write/merge authority.

The package becomes runtime-reachable only in a later explicitly authorized release after replay parity,
shadow operation, and the migration preservation gates are satisfied.
