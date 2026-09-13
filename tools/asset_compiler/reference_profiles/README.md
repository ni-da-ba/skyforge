# Reference-profile discipline

The asset compiler may use numeric proportion profiles, but provenance must remain explicit.

Two profile statuses are allowed:

- `working_reference` — an internal bounded hypothesis derived from Skyforge canon, accepted specimens, and in-game review. It is useful for deterministic validation but MUST NOT be described as measured master-builder evidence.
- `measured_reference` — generated from explicitly measured donor/reference builds through `reference_analysis.py`. The source list and sample count must travel with the profile.

The current small temperate Guild profile is a `working_reference`. It deliberately centers the proportions of the v0.7 accepted specimen so the compiler can establish the mechanism without inventing external evidence.

When suitable reference builds are selected, record raw dimensions in a JSON array and run:

```bash
python tools/asset_compiler/reference_analysis.py measurements.json \
  --profile-id guild.branch.temperate.small.measured.v1 \
  --out tools/asset_compiler/reference_profiles/guild_branch_temperate_small_measured_v1.json
```

Required raw measurements per donor are: bay width, wall height, main depth, hall width, working-wing width, main roof rise, roof overhang, public/working canopy depth, public entrance width, and average public-window width.

The aggregator emits min / median(target) / max ratio bounds. Those statistics are evidence, not automatic art direction; human review still decides whether a donor belongs in the reference set and whether the resulting profile should become authoritative.
