# Contributing to Skyforge

Skyforge welcomes focused bug reports, architectural review, test improvements, and well-scoped code contributions. The project is pre-release and its contracts are evidence-driven, so discussion before a large implementation is strongly encouraged.

## Before opening a pull request

1. Search existing issues, pull requests, ADRs, and acceptance reviews for related work.
2. Open an issue before making a broad architectural or behavioral change.
3. Keep changes within one coherent problem boundary.
4. Do not combine feature work with unrelated formatting or refactoring.

## Execution and verification

Read [Execution Boundaries](docs/agent-state/EXECUTION_BOUNDARIES.md) and
[Validation Policy](docs/agent-state/VALIDATION_POLICY.md) before choosing where work runs.
Active manual producers also follow the issue-ownership protocol in
[Manual Producer Protocol](docs/agent-state/MANUAL_PRODUCER_PROTOCOL.md).

GitHub Actions owns automated builds, tests, benchmarks, generators and canonical evidence.
Bounded hosted workers edit; the owner's workstation supplies manual interactive, visual,
play and listening evidence. Neither is a fallback CI runner.

The [CI workflow](.github/workflows/ci.yml) selects lightweight or full validation from the
changed dependency surface. Full CI uses the checked-in Gradle wrapper and shared JDK
setup to run `check`, `:skyforge-reference:fixedSeedCorpus` and
`:skyforge-reference:suspendedVolumeEvidence`. Use the relevant milestone workflows
for additional evidence; record the source commit, run URL and outcome in the PR.
A pending or unavailable Actions run remains pending; do not replace it with local execution.

The build treats compiler warnings as errors and checks that backend-neutral modules do not
import Minecraft or NeoForge APIs. For canonical terrain changes, explain intentional
identity changes alongside their numerical and visual evidence. Never update golden hashes
merely to make a failing test pass.

### Windows source inspection

Use a real Git checkout and enable long paths for that checkout; some tracked evidence
filenames exceed the default Windows Git path limit. For a new inspection clone:

```shell
git -c core.longpaths=true clone https://github.com/ni-da-ba/skyforge.git
```

For an existing checkout, `git config core.longpaths true` sets the repository-local policy.
Preserve uncommitted work before repairing an incomplete checkout. A copied source folder
with an unborn branch is not a verified branch/commit handoff.

## Architecture expectations

- Descriptors express semantic intent, not backend algorithms.
- Recipes compile intent into immutable, inspectable procedural graphs.
- Backend-neutral modules must remain free of Minecraft and NeoForge dependencies.
- Exact terrain ownership and deterministic evaluation are contracts, not implementation details.
- Development fixtures may exercise artificial worlds, but must remain isolated from production artifacts and clearly state what they prove.
- New compatibility behavior should consume live registries or public backend contracts rather than copy proprietary game content.

Material architectural decisions should include or update an ADR under `docs/decisions`. Milestone acceptance should identify the exact tested commit and the evidence used to accept it.

## Pull request content

A useful pull request explains:

- the problem and scope;
- the invariants preserved or changed;
- tests and evidence executed;
- any intentional canonical-output changes;
- deferred work and known limitations.

CI selects validation by change impact; full validation runs the repository-wide build and canonical evidence generation. Pull requests from forks receive a read-only token and no repository secrets.

## Interactive Minecraft validation

Interactive NeoForge clients are development fixtures, not production launchers. Follow the corresponding runbook, use a new disposable world, and include the exact commit plus the observed pass/fail evidence in the review record. Do not commit generated worlds, runtime directories, logs, Minecraft assets, or game binaries.

## Licensing

By submitting a contribution, you agree that it may be distributed under the repository's Apache License 2.0. Do not submit code, assets, or documentation that you do not have the right to contribute.

