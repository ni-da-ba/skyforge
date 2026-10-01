(function (root) {
  "use strict";

  const GRID = Object.freeze({
    minimum_x: -20, minimum_y: 0, minimum_z: -20,
    spacing_x: 20, spacing_y: 2, spacing_z: 20,
    x_samples: 3, y_samples: 3, z_samples: 3, sample_count: 27,
  });
  const NAMES = Object.freeze(["AIR", "LOWLAND", "UPLAND"]);

  function cellIndex(x, y, z) {
    return x + GRID.x_samples * (z + GRID.z_samples * y);
  }

  function artifact(cells) {
    const ordinals = new Map(NAMES.map((name, ordinal) => [name, ordinal]));
    const semantics = Uint8Array.from(cells.map(name => ordinals.get(name)));
    return {
      schema_version: 1,
      artifact_kind: "SKYFORGE_TERRAIN_SEMANTIC_VOLUME",
      semantic_sha256: "",
      skyforge_version: "Studio synthetic walkthrough",
      grid: { ...GRID },
      semantic_legend: NAMES.map((name, ordinal) => ({
        ordinal, name, solid: name !== "AIR",
      })),
      encoding: {
        kind: "BASE64_UINT8_ORDINAL",
        linear_index: "x + x_samples * (z + z_samples * y)",
        sample_count: semantics.length,
      },
      semantics_base64: btoa(String.fromCharCode(...semantics)),
    };
  }

  function createInputs() {
    const reference = new Array(GRID.sample_count).fill("AIR");
    for (let z = 0; z < GRID.z_samples; z++) {
      for (let x = 0; x < GRID.x_samples; x++) {
        reference[cellIndex(x, 0, z)] = "LOWLAND";
      }
    }
    reference[cellIndex(1, 1, 1)] = "UPLAND";

    const candidate = reference.slice();
    candidate[cellIndex(0, 0, 0)] = "UPLAND";
    candidate[cellIndex(2, 1, 2)] = "UPLAND";
    candidate[cellIndex(2, 0, 0)] = "AIR";

    return Object.freeze([
      Object.freeze({
        title: "Synthetic example · reference",
        artifact: artifact(reference),
      }),
      Object.freeze({
        title: "Synthetic example · candidate",
        artifact: artifact(candidate),
      }),
    ]);
  }

  root.SkyforgeStudioTerrainComparisonDemo = Object.freeze({ createInputs });
  if (typeof module !== "undefined" && module.exports) {
    module.exports = root.SkyforgeStudioTerrainComparisonDemo;
  }
})(typeof window !== "undefined" ? window : globalThis);
