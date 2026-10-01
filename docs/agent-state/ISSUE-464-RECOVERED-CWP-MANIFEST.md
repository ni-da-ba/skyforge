# Issue #464 — owner-recovered CWP source manifest

**Status:** provisional recovery evidence accepted by project owner on 2026-09-10.

This manifest records original owner-supplied Cakewalk/Sonar project binaries recovered during the #464 human-gate pass. The files are preserved unchanged in the Project Skyforge persistent Library under:

`/Project Skyforge/Music/Recovered CWP/`

A deterministic archive of the six supplied binaries is preserved there as:

`skyforge-cwp-recovery-2026-09-10.tar.gz`

Archive SHA-256: `bf52c0b77341fd5e940c03df604e9733dbfa4571acecde9868bfb9958bf86d68`

The binaries are not promoted to canonical MIDI identities merely because their names are plausible. This manifest records what is directly supported by the supplied CWP state and preserves unresolved identity caveats explicitly.

## Supplied files

| Supplied CWP | SHA-256 | Embedded project identity | Provisional cue association |
| --- | --- | --- | --- |
| `Skyforge_01_Rambling_Through_the_Gentle_Blue.cwp` | `1caf5d2a05749c1f1f5c71b4600bd5a43497b8ac2a57c774ed7096d723667f56` | `Skyforge_01_Rambling_Through_the_Gentle_Blue` | Track 01 — strong/direct |
| `Skyforge_03_Count_the_Leagues.cwp` | `1fb04c2704234c6cdf30d35b37bc133512c70bb4c039bbdb067714ed3aec15d8` | `Skyforge_03_Count_the_Leagues` | Track 03 — strong/direct |
| `Skyforge_Storm.cwp` | `7a34aa708fe3f25be72a82b50711e6d4a92db306b3300334fa6ea74f22af04e7` | `Skyforge_Storm` | Track 06 storm session — strong; exact Draft 02.3 MIDI identity remains unproven |
| `Skyforge_v1_FINAL.cwp` | `6c5dd22ff1388564e69a651e8c2cdb91d3867077022e2a359fc8d0a5e44b48ad` | `Skyforge_v1_FINAL` | Track 00-family candidate / historical source; canonical cue identity unproven |
| `Skyforge_v2.cwp` | `e7202a62af3d83a62ce675c379054f1ba66858b279a3a79f59e3734a9c4404f6` | `Skyforge_v2` | strongest surviving Track 00-family candidate; canonical cue identity unproven |
| `TLoEM_Fixed.cwp` | `642989b5d01bdfeb708fe46f409ebc0a17815bad6def407a43bb43c42bed4cb4` | `Skyforge_BBSCO_Discover_Template_v1` | historical/source corroboration only; no canonical cue assignment |

All six are distinct binaries and contain 19 embedded BBC Symphony Orchestra plugin-state documents.

## Directly recovered BBCSO provenance

For every supplied session, the embedded `Discover - Harp and Celeste` plugin state records:

- `c - Harp - Short Sustained Discover` — `a_active=2` (active), `t_keyswitch=0`, MIDI channel 1, CC32 articulation selector;
- `e - Celeste - Short Sustained Discover` — `a_active=0` (inactive), `t_keyswitch=1`, MIDI channel 1, CC32 articulation selector.

Therefore the previously unresolved Track 11 `HC` Harp/Celeste question is resolved as **Harp active / Celeste inactive** for the directly identified Track 01 and Track 03 sessions, and is independently corroborated by both surviving Track-00-family candidates.

### Track 01

`Skyforge_01_Rambling_Through_the_Gentle_Blue.cwp` directly names the Track 01 cue and records Track 11 `HC` as Harp active / Celeste inactive. The provenance question represented by Gate A is accepted for Track 01.

### Track 03

`Skyforge_03_Count_the_Leagues.cwp` directly names the Track 03 cue and records Track 11 `HC` as Harp active / Celeste inactive. The provenance question represented by Gate A is accepted for Track 03.

### Track 00

Both surviving generic Skyforge candidates (`Skyforge_v1_FINAL.cwp`, `Skyforge_v2.cwp`) record the same Track 11 `HC` state: Harp active / Celeste inactive. This resolves the instrumentation ambiguity provisionally, but neither binary internally identifies itself as `A Windborne Fantasia`; do not claim an exact accepted-session identity from filename alone.

## Track 06 Storm evidence

`Skyforge_Storm.cwp` directly identifies itself as `Skyforge_Storm` and its embedded plugin state matches the documented accepted Storm-session instrumentation in the high-value discriminators:

- Track 12 / `Discover - Percussion`: `a - Untuned Percussion - Hits`, active (`a_active=2`), selected primary articulation index 1;
- Track 13 / `Discover - Tuned Percussion`: `g - Glockenspiel - Short Hits Discover`, active (`a_active=2`), selected primary articulation index 3;
- Track 11 `HC`: Harp active / Celeste inactive.

This is strong evidence that the supplied CWP is an original Track 06 storm authoring session. However, the CWP does not itself encode a trustworthy human-readable `Draft 02.3` identity, and no exact accepted Draft 02.3 full-MIDI checksum has been recovered from it. Therefore:

- preserve this CWP as provisional original-session evidence;
- do **not** promote the historical Draft 02 MIDI as Draft 02.3;
- do **not** reconstruct a new MIDI and label it exact 02.3;
- exact Draft 02.3 MIDI provenance remains an archival uncertainty, not a Bootstrap/development blocker.

## Project-owner gate disposition

The project owner has stated that no better local source set is presently known and authorized these recovered files to be archived provisionally and the manual source-recovery gate to be cleared on that basis.

Accordingly:

- Gate A (Track 00/01/03 CWP plugin-state recovery): **PASS / CLOSED**, with Track 00 exact-session identity caveat retained;
- Gate B source-recovery obligation: **PROVISIONALLY CLOSED FOR DEVELOPMENT**, because the surviving `Skyforge_Storm.cwp` is preserved and strongly matches the accepted session configuration, while exact Draft 02.3 full-MIDI identity remains explicitly unproven;
- any future discovery of a checksum-identifiable exact Draft 02.3 MIDI may upgrade archival provenance without reopening the development gate;
- optional Track 00 GAME/OST mastering remains separate and non-blocking.

No musical content, orchestration, arrangement, plugin state, or MIDI data was reconstructed or altered to obtain this disposition.
