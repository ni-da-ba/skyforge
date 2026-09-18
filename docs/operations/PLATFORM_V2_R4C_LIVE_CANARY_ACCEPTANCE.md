# Platform v2 R4C — First live mutation canary acceptance

Status: **PASS — live canary authority returned to disabled**

Parent migration: #767  
Canary authority: #833  
Live canary PR: #840  
Acceptance tranche: #841

## Frozen authority identity

- base SHA: `b05c5c0fe59202d364bbff947fd8fcce62fffcda`
- candidate SHA: `914124d9aa03c449f4d5a3b5effca4cefcb8cba9`
- branch: `platform/v2-canary/833-first-live`
- authorized path: `docs/operations/platform-v2-canary/r4c-first-live-canary.md`
- task-spec hash: `5175d82968cb114e1186c2398ecb23aa5a91c744b35786ac44eb5bfb0915f038`
- attempt ID: `6d5cfa98b0cfb94d95a736ee5ec7031eea422d0c45c64a7ce93b74a8afc7c14d`

The candidate was pre-staged outside Platform v2 from the exact frozen base and was one commit ahead with exactly one changed documentation file.

## Legacy exclusion

Before Platform v2 mutation, the trusted legacy command recorded an active external-producer claim for:

- issue #833;
- lane `Implementation`;
- branch `platform/v2-canary/833-first-live`.

No legacy pending worker, pending decision, or controller block existed at canary start.

## Invocation 1 — CREATE_PR

A fresh Platform-v2 executor process acquired the dedicated writer fence and created exactly one PR.

- PR: #840
- disposition: `PR_CREATED`
- CREATE_PR effect ID: `d6febc29d2f379938f88014b657455fafad2cd7078a1e1c158f57d23c5737ab5`
- remote identity: `pr:840`
- state digest after create: `9863d7326e977d01e5d697f2f1943a4a78f1b5187d2d086784eabf17f4019d7e`
- result digest: `1727202705bc3d80eb9f1216ff0cab0ef6f18d6263db5c3f1f85deb50c34aa1c`

The durable effect was COMPLETE and no merge effect existed.

## Exact-head CI and invocation 2

GitHub Actions run `35294692866` completed with required job/context `build = success` on exact candidate SHA `914124d9...`.

A second fresh executor process loaded the existing durable state and established:

- evidence SHA == exact candidate SHA;
- reviewed SHA == exact candidate SHA;
- accepted task-spec hash == frozen task-spec hash;
- mechanical transition == `MERGE_ELIGIBLE`.

It then persisted and executed exactly one MERGE_PR effect.

- disposition: `MERGED`
- MERGE_PR effect ID: `30f293d80070ab4606c1f7345cce1caa6a2ac5e3cbd774bc01104c834202831e`
- remote identity: `merge:pr:840@914124d9aa03c449f4d5a3b5effca4cefcb8cba9`
- final durable state digest: `dd46ffed190263fc7888d49871c11081adbb1702d1fdd36b98f06e08957680fe`
- result digest: `8773a96d29a797b2e5737d803ddcbad2d3e67f867cdccf67aa73c505ab8b91e9`

GitHub independently reports:

- PR #840 merged;
- base SHA = frozen base;
- head SHA = frozen candidate;
- changed files = 1;
- merge commit = `579ad221371f4f2b0ffb585fea4a253d06ec03f4`;
- exactly one PR exists for the canary branch.

## Invocation 3 — completed-state idempotence

After `main` had advanced to the merge commit, a third fresh process ran the same frozen attempt.

- disposition: `COMPLETE`
- no remote PR or merge action;
- durable state unchanged;
- result digest: `cba0632e864a52ad5f2df78e636978ca01adbb3176544b3115dc2b85885400c1`.

This proves the completed attempt remains idempotent after base movement.

## Host invariants after canary

- Platform-v2 primary state SHA-256:
  `088ecc64e8e280a11282adbe18f8f6bf7370abefe42be3cb649b0614e6636221`
- Platform-v2 backup state SHA-256:
  `088ecc64e8e280a11282adbe18f8f6bf7370abefe42be3cb649b0614e6636221`
- writer fence: released/free;
- legacy production service: active;
- legacy pending worker: none;
- legacy pending decision: none;
- legacy controller block: none.

Platform v2 wrote only its dedicated `.skyforge-platform-v2/` state/fence namespace. The legacy controller retained the #833 exclusion claim while the issue remained open.

## Acceptance conclusion

The first live Platform-v2 mutation canary is accepted.

It demonstrated:

1. exact bounded authority;
2. legacy/v2 mutual exclusion;
3. writer fencing;
4. durable pre-effect persistence;
5. exact remote identity reconciliation;
6. separate-process restart boundary;
7. exact protected-CI acceptance;
8. exact frozen task/spec/head identity;
9. exact-head merge;
10. persistent completion/idempotence after `main` movement;
11. isolated v2 state with legacy production remaining healthy.

This acceptance does **not** authorize a broader mutation class. The repository canary gate is returned to disabled and must be explicitly re-authorized for any subsequent Release-4 tranche.
