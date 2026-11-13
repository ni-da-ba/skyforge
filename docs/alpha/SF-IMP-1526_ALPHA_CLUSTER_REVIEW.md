# SF-IMP-1526 Alpha Cluster Review

## Launch

From the `skyforge-alpha-cluster` checkout, run:

```powershell
.\gradlew.bat :skyforge-neoforge-1211:runAlphaClusterBiomePopulationReviewClient --no-configuration-cache
```

Create a **new** world and select **Skyforge Development** as its world type. The run uses the pinned
Tectonic and Biomes O' Plenty review stack. Spawn is placed on the Rocky Shrubland member. Fly toward
the other island to compare the Forest member.

## What this review is checking

- Two islands are placed side by side by the Skyforge archipelago/group planners and carry stable
  group/member identities.
- Massif and Basin members have visibly different suspension elevations while retaining clear void
  separation.
- Tectonic shapes the island-local terrain under the Skyforge-owned footprint, body, underside, and
  exact-volume boundary.
- Forest and Biomes O' Plenty Rocky Shrubland produce visibly distinct native vegetation.
- The player spawn lands on the generated island rather than in open sky.

Please call out missing islands, island overlap, an incorrect spawn, apparent ground crossing the
void gap, or vegetation that does not read as distinct. This fixture does not yet claim cluster
hydrology or caves; those follow after this cluster is accepted.
