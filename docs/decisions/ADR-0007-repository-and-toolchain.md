# ADR-0007: Repository governance and initial toolchain

- **Status:** Accepted
- **Date:** 2026-08-03
- **Supersedes:** SF-OPEN-001, SF-OPEN-002, SF-OPEN-003, and the toolchain portion of SF-OPEN-004

## Context

Sprint One needs a durable repository, a reproducible build, an explicit legal default, and a permanent Java identity. The authenticated GitHub owner is `ni-da-ba`; Java identifiers omit the account-name hyphens.

## Decisions

1. Host the project in the private GitHub repository `ni-da-ba/skyforge`.
2. Do not add a license during private development. No permission to copy, modify, or redistribute is granted by default.
3. Use Java 25 as the initial toolchain. Current NeoForge development documentation requires a 64-bit JDK 25, although NeoForge remains deferred from v0.1.
4. Use Gradle 9.6.1 through the checked-in wrapper.
5. Verify both the Gradle distribution and wrapper JAR against Gradle's published SHA-256 checksums.
6. Use `io.github.nidaba.skyforge` as the durable Java package root.

## Consequences

- The backend-neutral modules can target the currently documented backend-era Java runtime without importing any backend code.
- A clean checkout can bootstrap the exact Gradle version without a system Gradle installation.
- Java source creation may proceed under the permanent package root `io.github.nidaba.skyforge`.
- A future public release requires a separate explicit license decision.

## Evidence

- NeoForge getting started: <https://docs.neoforged.net/docs/gettingstarted/>
- Gradle 9.6.1 release: <https://gradle.org/releases/>
- Gradle checksums: <https://gradle.org/release-checksums/>
