# Skyforge Platform v2 Backup and Rollback Runbook

**Status:** Required preservation gate for Platform v2 migration  
**Plan:** `docs/architecture/SKYFORGE_PLATFORM_V2_MIGRATION_PLAN.md`  
**Plan-creation baseline:** `4ec14d54684ef82f48a8df4582180b89c3a87aaa`  
**Date:** 2026-09-17 (America/Chicago)

## 1. Purpose

This runbook exists so Platform v2 migration can fail without losing Skyforge's accepted code, history, control-plane state, or ability to resume the pre-migration workflow.

Migration may not grant v2 repository-mutating authority until the preservation gate in this document is complete.

The backup strategy deliberately uses independent failure domains. A normal GitHub repository clone is not considered sufficient by itself because it does not preserve ignored local orchestrator state, unpushed work, or necessarily every non-Git artifact required to reconstruct the hosted controller.

## 2. Preservation model

Use at least these independent layers:

```text
A. GitHub authoritative repository and protected baseline ref
B. Complete local/off-host Git mirror or bundle containing all refs/history
C. Second off-machine copy of the bundle/mirror
D. Separate encrypted operational-state/configuration archive for the hosted controller
```

For irreplaceable accepted evidence not stored in Git, preserve a fifth evidence archive as described below.

The repository copy and operational-state copy must be restorable independently.

## 3. What must be preserved

### Git repository

Preserve all reachable repository history and refs, not only `main`:

- `main`;
- all branches, including long-lived proof/prototype/handoff branches;
- all tags;
- merge history;
- repository-tracked source, docs, scripts, workflows, assets, and test fixtures;
- Git LFS objects if Skyforge uses Git LFS at preservation time;
- submodule identity if submodules are introduced later.

### Unpushed/local work

Before the preservation snapshot, inspect every known active producer environment:

- Nicholas' primary workstation checkout/worktrees;
- DigitalOcean controller checkout and isolated worker/worktrees;
- any other currently active manual development clone.

Record and resolve:

- commits ahead of GitHub;
- dirty tracked files;
- untracked source/docs/assets that matter;
- stashes;
- interrupted worker changes.

Important work must be committed/pushed to an explicit preservation branch or archived separately before declaring the snapshot complete.

### Hosted orchestrator state

The Git repository does not contain ignored runtime state. Preserve at minimum:

- `.skyforge-orchestrator/state.json`;
- `.skyforge-orchestrator/state.json.bak`;
- any active worktree metadata needed to understand an interrupted worker;
- local reports useful for reconstruction;
- current controller runtime/version identity;
- current pause/breaker/managed-PR/pending-worker/task ownership summary.

Do not copy access tokens or secrets into the Git repository.

### Hosted configuration

Preserve a secure reconstruction record for:

- systemd unit/configuration;
- Caddy configuration;
- firewall/provider networking assumptions;
- installed controller/runtime dependency fingerprint;
- non-secret environment configuration;
- location/names of secret-bearing files;
- GitHub webhook configuration identity;
- deployment hostname;
- selected cost/report settings.

Secret-bearing data must be stored only in an encrypted/private backup appropriate for credentials. The repository runbook should describe where secrets are expected, not contain them.

### Evidence outside Git

GitHub Actions artifacts are not a substitute for permanent project truth because artifacts may expire. Before migration, identify any accepted evidence whose only surviving copy is an expiring Actions artifact and either:

- promote the necessary machine-readable result/digest/summary into tracked repository evidence; or
- download and preserve the artifact in the off-machine evidence archive with its workflow/run/head SHA identity.

Do not archive every historical artifact indiscriminately. Preserve only evidence necessary to reproduce or substantiate an accepted boundary that is not already durably represented by repository source/tests/docs/digests.

## 4. Baseline repository ref

Immediately before the first mutating v2 canary, identify the exact accepted `main` SHA and create a clearly named pre-migration baseline ref.

Recommended naming:

```text
platform-v1-baseline-YYYYMMDD
```

Prefer an annotated/signed tag if convenient. Also record the SHA textually in the migration issue/runbook. The baseline ref must never be moved to a different commit.

If a protected archival branch is used in addition to a tag, use:

```text
archive/platform-v1-baseline-YYYYMMDD
```

The archival ref is convenience/redundancy; the independent bundle/mirror is the actual second failure domain.

## 5. Complete Git backup procedure

On a trusted machine with sufficient disk space, create a fresh mirror directly from GitHub:

```bash
git clone --mirror https://github.com/ni-da-ba/skyforge.git skyforge-platform-v1.git
cd skyforge-platform-v1.git
git fsck --full
```

If Git LFS is in use, also fetch all LFS objects using a normal/bare clone capable of LFS retrieval and preserve those objects alongside the mirror. A Git bundle alone does not contain Git LFS object payloads.

Create a portable bundle from the mirror:

```bash
git bundle create ../skyforge-platform-v1.bundle --all
git bundle verify ../skyforge-platform-v1.bundle
```

Compute a cryptographic checksum:

```bash
sha256sum ../skyforge-platform-v1.bundle > ../skyforge-platform-v1.bundle.sha256
```

On Windows PowerShell, an equivalent checksum can be produced with:

```powershell
Get-FileHash .\skyforge-platform-v1.bundle -Algorithm SHA256 |
  Format-List |
  Out-File .\skyforge-platform-v1.bundle.sha256.txt
```

Keep both the bundle and checksum.

## 6. Independent copy rule

Store the verified bundle/mirror in at least two places outside the working repository clone.

Recommended minimum:

```text
Copy 1: Nicholas' workstation, outside the active Skyforge checkout
Copy 2: physically/off-machine independent storage
        (external drive or separate private cloud/object storage)
```

The DigitalOcean host is not considered an independent backup of GitHub if it is simultaneously participating in migration.

For stronger resilience, retain both:

- the Git bundle; and
- the mirror directory/archive.

The bundle is convenient for integrity verification and restoration. The mirror is convenient for preserving/referring to every ref exactly as fetched.

## 7. Restore drill

A backup is not accepted until it has been restored into a disposable directory.

Example:

```bash
mkdir skyforge-restore-test
cd skyforge-restore-test
git clone ../skyforge-platform-v1.bundle skyforge
cd skyforge
git fsck --full
git rev-parse main
```

Verify that the restored `main` SHA exactly equals the recorded baseline.

Also verify that representative non-main preservation refs exist, especially any important long-lived/prototype/handoff branches that would not be recoverable from the baseline commit alone.

Where practical, run the cheapest repository integrity/build checks against the restored copy. Full expensive Minecraft characterization is not required merely to prove Git restoration unless a migration-specific uncertainty justifies it.

Record the restore date, bundle SHA-256, baseline main SHA, and PASS result in the migration tracking issue or a repository-safe preservation record.

## 8. Hosted controller state backup

Before controller cutover:

1. Pause new orchestration dispatch.
2. Allow or explicitly account for any in-flight worker/PR.
3. Record controller `/status` or equivalent non-secret health/state snapshot.
4. Stop the controller if needed to obtain a quiescent runtime-state snapshot.
5. Copy `.skyforge-orchestrator/state.json` and `.bak` to the encrypted operational backup.
6. Record the exact running controller commit/version and deployment configuration identity.
7. Restart only the controller that owns the current writer fence.

The operational backup should be timestamped and cryptographically checksummed.

Do not rely on this state backup as the only record of accepted work. Accepted project truth remains GitHub/repository based.

## 9. Secret handling

Never commit the following to Skyforge merely for backup convenience:

- GitHub authentication tokens;
- Codex/OpenAI credentials;
- webhook HMAC secret;
- SSH private keys;
- provider/cloud API credentials;
- `/etc/skyforge-orchestrator/env` contents containing secrets.

If credential recovery must be preserved, use an encrypted password manager or encrypted offline archive under Nicholas' control. Record only the existence/location/recovery procedure in non-secret documentation.

## 10. GitHub metadata and issue/PR history

The Git repository backup preserves code/history but not the complete GitHub issue/PR/comment database.

GitHub remains the primary hosted copy of that coordination history. Before high-risk cutover, preserve the migration-critical subset in repository-tracked records where necessary:

- active task specifications;
- current authority relationships;
- required human-gate results;
- baseline SHA;
- migration issue/PR IDs;
- accepted evidence identities needed for rollback decisions.

A full GitHub issue export is optional rather than required for code safety. If the project later treats GitHub discussions as irreplaceable long-term records, add a separate periodic metadata export policy.

## 11. Rollback snapshots by migration release

Do not create only one backup at the beginning and then rely on it forever.

Create a lightweight named checkpoint before each authority-changing boundary:

```text
pre-v2-shadow       (software baseline; no v2 writes yet)
pre-v2-canary       (last old-controller-only writer state)
pre-v2-cutover      (last accepted mixed-era repository boundary)
pre-sqlite-cutover  (last accepted JSON-state controller boundary)
```

The repository itself already retains code history; these checkpoints primarily make recovery unambiguous and pair code SHA with controller-state backup.

## 12. Rollback procedure: v2 canary/control-plane failure

If v2 exhibits unsafe or unexplained behavior:

1. Pause/stop v2 immediately.
2. Revoke or release its writer fence.
3. Preserve v2 database/state/logs for diagnosis.
4. Confirm no unresolved remote side effect is still in progress.
5. Restore the last compatible old-controller runtime/state snapshot if necessary.
6. Start the old controller and acquire a new writer fence generation.
7. Reconcile against current GitHub truth before dispatching new work.
8. Do not undo accepted Git commits solely because v2 created them. Revert product changes only through normal repository authority if they are actually incorrect.

The old controller must never be started with mutation authority while v2 still owns the fence.

## 13. Rollback procedure: SQLite migration failure

The SQLite cutover occurs only after v2 semantics are stable.

Before cutover, retain:

- final JSON state snapshot;
- imported SQLite database;
- import report/checksum;
- exact controller SHA.

If SQLite behavior is suspect:

1. stop controller;
2. preserve SQLite database for diagnosis;
3. restore JSON-state adapter and compatible JSON snapshot;
4. reconcile current GitHub truth;
5. resume under a new writer fence generation.

No product repository rollback should be required simply because the state-store adapter changes.

## 14. Preservation gate checklist

Platform v2 may not receive mutating production authority until all items are PASS:

```text
[ ] Exact baseline main SHA recorded
[ ] Pre-migration tag/ref created and verified
[ ] GitHub main remains protected
[ ] Fresh `--mirror` backup created from GitHub
[ ] `git fsck --full` passes on mirror
[ ] `git bundle --all` created
[ ] `git bundle verify` passes
[ ] SHA-256 recorded for bundle
[ ] Bundle copied to a second independent storage location
[ ] Restore drill from bundle passes
[ ] Important non-main refs confirmed in restore
[ ] Active workstation/DigitalOcean worktrees audited for unpushed work
[ ] Important unpushed work preserved
[ ] Current orchestrator state/config backup captured securely
[ ] Migration-critical expiring evidence audited/preserved where needed
[ ] Rollback owner/procedure understood
```

The migration tracking issue should record evidence for each item.

## 15. Recommended minimum for Skyforge

For this project, the recommended practical preservation set is:

```text
1. GitHub protected repository
2. immutable pre-Platform-v2 baseline tag/ref
3. full Git mirror + verified `--all` bundle on Nicholas' PC
4. second copy of that bundle on an external/off-machine location
5. encrypted snapshot of hosted orchestrator state/configuration
6. restore drill before v2 can write
```

This is sufficient to make catastrophic code loss extremely unlikely without introducing a new permanent infrastructure service.

## 16. Principle

Migration convenience never outranks recoverability.

If the project cannot demonstrate where the pre-migration code, refs, controller state, and rollback instructions are stored and prove that the Git backup restores successfully, the migration is not ready to advance.