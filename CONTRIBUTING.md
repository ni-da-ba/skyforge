# Contributing to Skyforge

Skyforge welcomes focused bug reports, architectural review, test improvements, and well-scoped code contributions. The project is pre-release and its contracts are evidence-driven, so discussion before a large implementation is strongly encouraged.

## Before opening a pull request

1. Search existing issues, pull requests, ADRs, and acceptance reviews for related work.
2. Open an issue before making a broad architectural or behavioral change.
3. Keep changes within one coherent problem boundary.
4. Do not combine feature work with unrelated formatting or refactoring.

## Local verification

Use a 64-bit JDK 25 and the checked-in Gradle wrapper:

```shell
./gradlew check
```

The build treats compiler warnings as errors and checks that backend-neutral modules do not import Minecraft or NeoForge APIs.

If a change affects canonical terrain behavior, also regenerate the relevant evidence task and explain any intentional identity changes. The primary cross-project evidence gates are:

```shell
./gradlew :skyforge-reference:fixedSeedCorpus
./gradlew :skyforge-reference:suspendedVolumeEvidence
```

Never update golden hashes merely to make a failing test pass. A changed identity must be explained by the change's semantic contract and reviewed alongside its numerical and visual evidence.

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

CI runs the repository-wide build and canonical evidence generation. Pull requests from forks receive a read-only token and no repository secrets.

## Interactive Minecraft validation

Interactive NeoForge clients are development fixtures, not production launchers. Follow the corresponding runbook, use a new disposable world, and include the exact commit plus the observed pass/fail evidence in the review record. Do not commit generated worlds, runtime directories, logs, Minecraft assets, or game binaries.

## Licensing

By submitting a contribution, you agree that it may be distributed under the repository's Apache License 2.0. Do not submit code, assets, or documentation that you do not have the right to contribute.

