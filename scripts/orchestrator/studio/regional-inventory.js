(() => {
  "use strict";

  const INVENTORY_HEADER = ["association", "volumeId", "metal", "eligible", "meanOpportunity", "peakOpportunity"];
  const RANKING_HEADER = ["metal", "rank", "association", "meanOpportunity"];
  const METALS = new Set(["IRON", "COPPER", "ZINC"]);
  const MAX_FILE_BYTES = 2 * 1024 * 1024;
  const MAX_DATA_ROWS = 10000;
  const $ = (id) => document.getElementById(id);
  let current = null;

  function fail(message) {
    throw new Error(message);
  }

  function parseCsv(text, expectedHeader, label) {
    if (typeof text !== "string") fail(label + " is not text");
    if (new Blob([text]).size > MAX_FILE_BYTES) fail(label + " exceeds the 2 MB limit");
    if (text.charCodeAt(0) === 0xfeff) text = text.slice(1);
    if (text.includes("\0")) fail(label + " contains an unsupported null character");

    const rows = [];
    let row = [];
    let cell = "";
    let quoted = false;
    let closedQuote = false;

    const finishCell = () => {
      row.push(cell);
      cell = "";
      closedQuote = false;
    };
    const finishRow = () => {
      finishCell();
      rows.push(row);
      row = [];
    };

    for (let index = 0; index < text.length; index += 1) {
      const character = text[index];
      if (quoted) {
        if (character === '"') {
          if (text[index + 1] === '"') {
            cell += '"';
            index += 1;
          } else {
            quoted = false;
            closedQuote = true;
          }
        } else {
          cell += character;
        }
        continue;
      }
      if (character === '"') {
        if (cell.length !== 0 || closedQuote) fail(label + " has a quote in an invalid position");
        quoted = true;
      } else if (character === ",") {
        finishCell();
      } else if (character === "\n" || character === "\r") {
        finishRow();
        if (character === "\r" && text[index + 1] === "\n") index += 1;
      } else {
        if (closedQuote) fail(label + " has data after a quoted field");
        cell += character;
      }
      if (rows.length > MAX_DATA_ROWS + 1) fail(label + " has too many rows");
    }
    if (quoted) fail(label + " ends inside a quoted field");
    if (cell.length > 0 || row.length > 0 || closedQuote) finishRow();
    if (rows.length > 1 && rows[rows.length - 1].length === 1 && rows[rows.length - 1][0] === "") {
      rows.pop();
    }
    if (rows.length < 2) fail(label + " has no data rows");
    if (rows.length > MAX_DATA_ROWS + 1) fail(label + " has too many rows");
    if (JSON.stringify(rows[0]) !== JSON.stringify(expectedHeader)) {
      fail(label + " does not have the expected AUTH-0094 columns");
    }
    for (let index = 1; index < rows.length; index += 1) {
      if (rows[index].length !== expectedHeader.length || rows[index].some((value) => value === "")) {
        fail(label + " row " + (index + 1) + " has missing or extra fields");
      }
    }
    return rows.slice(1);
  }

  function validIdentifier(value, field, rowNumber) {
    if (value.length > 512 || /[\u0000-\u001f\u007f]/.test(value)) {
      fail("AUTH-0094 row " + rowNumber + " has an invalid " + field);
    }
    return value;
  }

  function validOpportunity(value, field, rowNumber) {
    if (!/^(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(value)) {
      fail("AUTH-0094 row " + rowNumber + " has an invalid " + field);
    }
    const number = Number(value);
    if (!Number.isFinite(number) || number < 0) {
      fail("AUTH-0094 row " + rowNumber + " has an invalid " + field);
    }
    return number;
  }

  function parseInventoryCsv(text) {
    const rows = parseCsv(text, INVENTORY_HEADER, "inventory.csv");
    const entries = [];
    const byAssociationMetal = new Map();
    const associationVolumes = new Map();
    for (let index = 0; index < rows.length; index += 1) {
      const rowNumber = index + 2;
      const [rawAssociation, rawVolumeId, metal, rawEligible, meanText, peakText] = rows[index];
      const association = validIdentifier(rawAssociation, "association", rowNumber);
      const volumeId = validIdentifier(rawVolumeId, "volumeId", rowNumber);
      if (!METALS.has(metal)) fail("AUTH-0094 row " + rowNumber + " has an unsupported metal");
      if (rawEligible !== "true" && rawEligible !== "false") {
        fail("AUTH-0094 row " + rowNumber + " has an invalid eligibility value");
      }
      const mean = validOpportunity(meanText, "meanOpportunity", rowNumber);
      const peak = validOpportunity(peakText, "peakOpportunity", rowNumber);
      if ((rawEligible === "true") !== (peak > 0)) {
        fail("AUTH-0094 row " + rowNumber + " disagrees with the zero/nonzero eligibility rule");
      }
      const priorVolume = associationVolumes.get(association);
      if (priorVolume !== undefined && priorVolume !== volumeId) {
        fail("AUTH-0094 repeats an association with different volume identifiers");
      }
      associationVolumes.set(association, volumeId);
      const key = association + "\u0000" + metal;
      if (byAssociationMetal.has(key)) fail("AUTH-0094 repeats an association and metal");
      const entry = {
        association,
        volumeId,
        metal,
        eligible: rawEligible === "true",
        meanText,
        peakText,
        mean,
        peak
      };
      entries.push(entry);
      byAssociationMetal.set(key, entry);
    }
    if (entries.length !== associationVolumes.size * METALS.size) {
      fail("inventory.csv must contain one Iron, Copper, and Zinc row for every association");
    }
    for (const association of associationVolumes.keys()) {
      for (const metal of METALS) {
        if (!byAssociationMetal.has(association + "\u0000" + metal)) {
          fail("inventory.csv is missing a metal entry for an association");
        }
      }
    }
    return { entries, byAssociationMetal, associationCount: associationVolumes.size };
  }

  function parseRankingCsv(text, inventory) {
    const rows = parseCsv(text, RANKING_HEADER, "ranking.csv");
    const entries = [];
    const nextRankByMetal = new Map();
    const seen = new Set();
    for (let index = 0; index < rows.length; index += 1) {
      const rowNumber = index + 2;
      const [metal, rankText, rawAssociation, meanText] = rows[index];
      const association = validIdentifier(rawAssociation, "association", rowNumber);
      if (!METALS.has(metal)) fail("ranking.csv row " + rowNumber + " has an unsupported metal");
      if (!/^(?:0|[1-9]\d*)$/.test(rankText)) fail("ranking.csv row " + rowNumber + " has an invalid rank");
      const rank = Number(rankText);
      if (!Number.isSafeInteger(rank)) fail("ranking.csv row " + rowNumber + " has an invalid rank");
      const expectedRank = nextRankByMetal.get(metal) || 0;
      if (rank !== expectedRank) fail("ranking.csv ranks must be consecutive and start at zero for each metal");
      nextRankByMetal.set(metal, expectedRank + 1);
      const key = association + "\u0000" + metal;
      if (seen.has(key)) fail("ranking.csv repeats an association and metal");
      seen.add(key);
      const inventoryEntry = inventory.byAssociationMetal.get(key);
      if (!inventoryEntry || !inventoryEntry.eligible) {
        fail("ranking.csv includes an association without eligible inventory evidence");
      }
      const mean = validOpportunity(meanText, "meanOpportunity", rowNumber);
      if (mean !== inventoryEntry.mean) {
        fail("ranking.csv mean opportunity does not match inventory.csv");
      }
      entries.push({ metal, rankText, association, meanText });
    }
    return entries;
  }

  function appendCell(row, value, header) {
    const cell = document.createElement(header ? "th" : "td");
    if (header) cell.scope = "col";
    cell.textContent = String(value);
    row.appendChild(cell);
  }

  function renderTable(table, headers, entries, values) {
    const head = document.createElement("thead");
    const headerRow = document.createElement("tr");
    for (const header of headers) appendCell(headerRow, header, true);
    head.appendChild(headerRow);
    const body = document.createElement("tbody");
    for (const entry of entries) {
      const row = document.createElement("tr");
      for (const value of values(entry)) appendCell(row, value, false);
      body.appendChild(row);
    }
    table.replaceChildren(head, body);
  }

  function render() {
    const output = $("regional-inventory-output");
    const empty = $("regional-inventory-empty");
    const summary = $("regional-inventory-summary");
    const inventoryTable = $("regional-inventory-table");
    const rankingPanel = $("regional-ranking-panel");
    const rankingTable = $("regional-ranking-table");
    if (!current) {
      output.hidden = true;
      empty.hidden = false;
      return;
    }

    empty.hidden = true;
    output.hidden = false;
    $("regional-inventory-source").textContent = current.inventoryName;
    summary.replaceChildren();
    for (const metal of ["IRON", "COPPER", "ZINC"]) {
      const metalEntries = current.inventory.entries.filter((entry) => entry.metal === metal);
      const eligibleCount = metalEntries.filter((entry) => entry.eligible).length;
      const card = document.createElement("article");
      card.className = "regional-summary-card";
      const heading = document.createElement("h3");
      heading.textContent = metal.charAt(0) + metal.slice(1).toLowerCase();
      const total = document.createElement("strong");
      total.textContent = eligibleCount + " / " + metalEntries.length;
      const detail = document.createElement("p");
      detail.textContent = "associations with geological opportunity";
      card.append(heading, total, detail);
      summary.appendChild(card);
    }

    renderTable(
      inventoryTable,
      ["Association", "Volume", "Metal", "Eligible", "Mean opportunity", "Peak opportunity"],
      current.inventory.entries,
      (entry) => [entry.association, entry.volumeId, entry.metal, entry.eligible ? "Yes" : "No", entry.meanText, entry.peakText]
    );

    $("regional-ranking-source").textContent = current.rankingName || "No ranking.csv loaded";
    rankingPanel.hidden = !current.ranking;
    if (current.ranking) {
      renderTable(
        rankingTable,
        ["Metal", "Rank", "Association", "Mean opportunity"],
        current.ranking,
        (entry) => [entry.metal, entry.rankText, entry.association, entry.meanText]
      );
    }
  }

  async function readFile(file) {
    if (!file) fail("Choose a CSV file first");
    if (file.size > MAX_FILE_BYTES) fail("CSV files must be 2 MB or smaller");
    return file.text();
  }

  function setStatus(message, error) {
    const node = $("regional-inventory-status");
    node.textContent = message;
    node.className = error ? "small error" : "small muted";
  }

  function initialize() {
    $("regional-inventory-import").addEventListener("click", () => $("regional-inventory-file").click());
    $("regional-ranking-import").addEventListener("click", () => $("regional-ranking-file").click());
    $("regional-inventory-clear").addEventListener("click", () => {
      current = null;
      $("regional-inventory-file").value = "";
      $("regional-ranking-file").value = "";
      setStatus("Local inventory cleared.", false);
      render();
    });
    $("regional-inventory-file").addEventListener("change", async (event) => {
      const file = event.target.files && event.target.files[0];
      if (!file) return;
      try {
        const inventory = parseInventoryCsv(await readFile(file));
        current = { inventory, inventoryName: file.name, ranking: null, rankingName: null };
        $("regional-ranking-file").value = "";
        setStatus("Loaded " + file.name + " as unbound diagnostic data.", false);
        render();
      } catch (error) {
        setStatus("Could not load inventory: " + String(error.message || error), true);
      } finally {
        event.target.value = "";
      }
    });
    $("regional-ranking-file").addEventListener("change", async (event) => {
      const file = event.target.files && event.target.files[0];
      if (!file) return;
      try {
        if (!current) fail("Load inventory.csv before adding ranking.csv");
        const ranking = parseRankingCsv(await readFile(file), current.inventory);
        current = { ...current, ranking, rankingName: file.name };
        setStatus("Loaded " + file.name + " and matched it to the current local inventory.", false);
        render();
      } catch (error) {
        setStatus("Could not load ranking: " + String(error.message || error), true);
      } finally {
        event.target.value = "";
      }
    });
    render();
  }

  document.addEventListener("DOMContentLoaded", initialize);
})();