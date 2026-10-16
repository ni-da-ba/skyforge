# HEC-RAS Applications Guide, Example 10 — Stream Junction (energy inputs)

These are the four small, unmodified text inputs used by the official HEC-RAS 6.6
subcritical energy-method example. They are copied from the HEC-managed release
archive below to make the regression inputs reviewable and stable without
requiring every test run to download the 432 MB archive.

- Official example: https://www.hec.usace.army.mil/confluence/rasdocs/rasappguide/latest/stream-junction-example-10
- HEC-managed archive: https://github.com/HydrologicEngineeringCenter/hec-downloads/releases/download/1.0.33/Example_Projects_6_6.zip
- Archive SHA-256: `ea239b506155a2dfeda2af80b3c2af948eef42c40218bcd65de472cfed386887`
- Archive member directory: `Applications Guide/Example 10 - Stream Junction/`

The fixture is input data only. It does not assert that Skyforge reproduces HEC-RAS,
nor does it include HEC-RAS output files or imply implementation approval.

| File | Bytes | SHA-256 |
|---|---:|---|
| `JUNCTION.F01` | 560 | `c4a56991b29ea269fd64339e03df13361ba29cd53dd74eed315c6d4d5ebdc9a7` |
| `JUNCTION.g01` | 17,958 | `7907b42b93e480e7cde94137ac27059dbd672ec325c085e53e4598e215e46eb9` |
| `JUNCTION.P01` | 446 | `8420b799c2fcd7683c507ccde10dfa77547bdd6480c6808e4c6e42c2b9fa826c` |
| `JUNCTION.prj` | 600 | `3554ecab1eb36c8d13930367033ed7712fb7831d7d636dc94553b6a37426507a` |

The momentum-method geometry and plan are intentionally excluded: this work starts
with the published subcritical combining-junction energy method only.
