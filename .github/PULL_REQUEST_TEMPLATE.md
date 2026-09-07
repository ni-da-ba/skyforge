## Summary

<!-- What problem does this change solve? -->

## Scope and invariants

<!-- What is intentionally in scope, out of scope, and required to remain true? -->

## Risk / uncertainty retired

<!-- Name the materially distinct failure mode, contract gap, product uncertainty, or integration risk this PR retires. -->

## Verification

- [ ] Routine CI / cheap deterministic contract evidence is green
- [ ] Relevant deterministic corpus evidence regenerated if identity/semantics changed
- [ ] Expensive runtime/client/soak evidence is limited to the risk-equivalence class this PR actually changes
- [ ] Any reused expensive evidence is still portable across the current dependency/contract surface
- [ ] Any sampled failure widened the affected test class until its domain was understood
- [ ] Required human visual/play/listening gate completed, if applicable
- [ ] No generated worlds, build output, logs, credentials, or third-party game assets committed

## Evidence

### New evidence

<!-- Commands, exact commit, test results, artifacts, profiling, or interactive observations produced by this PR. -->

### Reused / portable evidence

<!-- Existing expensive evidence reused under VALIDATION_POLICY.md, and why intervening changes do not invalidate it. Leave N/A if none. -->

### Heavy-test justification

<!-- For any >20 min recurring or >30 min deliberate characterization, state the uncertainty it can still falsify. Leave N/A if none. -->

## Compatibility and documentation

- [ ] Backend-neutral modules remain independent of Minecraft and NeoForge
- [ ] Public contracts, ADRs, runbooks, or acceptance records updated where needed
- [ ] Lane state / cross-lane contract changes are included in this PR when practical rather than deferred to bookkeeping-only follow-ups
- [ ] Intentional canonical-output changes are explicitly explained
- [ ] Synchronization with `main` was performed only because relevant dependency/contract drift, a real conflict, or the acceptance boundary required it

## Deferred work

<!-- Known limitations or follow-up work that does not belong in this pull request. -->
