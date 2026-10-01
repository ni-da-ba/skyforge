# Platform v2 primary-workstation preservation audit

Status: **PASS**

Audit date: 2026-09-17 local workstation time  
Primary checkout: `C:\Users\nicho\Documents\skyforge`

## Findings

- Exactly one registered Git worktree was reported.
- The checkout was detached at the audit snapshot.
- Named local development branches tracked `origin/*`; none was reported ahead of its upstream.
- `git log --oneline --decorate --all --not --remotes` returned only stash-related objects, not ordinary unpublished development commits.
- Four stashes existed. Their names indicated historical `gradlew.bat` line-ending preservation snapshots; all four were exported independently as both patch and stat files before the gate was cleared.
- Local authored guild-structure NBT artifacts under `skyforge-neoforge-1211/src/development/resources/data/skyforge/structure` were copied outside the repository.
- Minecraft screenshots were copied outside the repository.
- Large NeoForge/Minecraft run directories, logs, saves, caches, and generated world state were treated as regenerable runtime artifacts.
- Top-level accidental files `Configure`, `Task`, `cd`, and `git` were all zero bytes.

## Independent preservation bundle

Created outside the repository:

`C:\Users\nicho\Documents\skyforge-preservation-20260917-195708.zip`

Reported archive size:

`5,254,699 bytes`

The preservation directory/archive contains:

- `SHA256SUMS.txt`;
- `stashes/stash-0.patch` through `stash-3.patch`;
- matching stash stat files;
- authored guild-structure NBT files;
- captured screenshots.

The preservation commands copied/exported data only. They did not reset, clean, drop stashes, switch branches, delete files, or otherwise mutate the Skyforge repository.

## Gate conclusion

The Release-0 preservation requirement is satisfied: no identified irreplaceable Skyforge development remains solely in a form that could be lost by the Platform-v2 migration/canary.

This PASS does **not** itself authorize live mutation. `canary_enabled` remains false until the R4B executor is accepted and a separate exact canary issue is authorized.
