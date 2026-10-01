# WBY Wave 1 Pin Record v0.1

**Snapshot:** 2026-09-30

| Component | Candidate | Identity / source |
|---|---|---|
| Minecraft | 1.21.1 | project platform |
| NeoForge | 21.1.249 | existing Skyforge adapter pin |
| Create | 6.0.10+mc1.21.1 | existing Wave C1 immutable pin |
| Sable | 2.0.5+mc1.21.1 | existing Wave C1 immutable pin |
| Create Aeronautics | 1.3.2+mc1.21.1 bundled | existing Wave C1 immutable pin |
| Sodium | 0.8.13 NeoForge 1.21.1 | CurseForge project 394468 / file 8756580 |
| Distant Horizons | 3.3.2 for 1.21.1 neo/fabric | Modrinth maven.modrinth:uCdwusMi:Ez3cx7Yd (version ID Ez3cx7Yd) |
| SSRD | 1.8.7 | Modrinth version iZflRKdV; filename SSRD-1.8.7-1.21.1.jar; SHA-1 7da02fc5f783bedb55a29268e4c8ba7b0c3c9e6f |

## Compatibility note

Distant Horizons 3.3.2 introduced reverse-Z rendering and explicitly rejects SSRD 1.8.6 and below. SSRD 1.8.7 is therefore the minimum Wave 1 target for the current DH line.

## Promotion rule

These renderer pins remain **candidate integration pins** until Wave 1 passes. Do not promote them to immutable pack locks from documentation alone.
