(function (root) {
  "use strict";
  const METALS = ["IRON", "COPPER", "ZINC"];
  function key(row) { return row.association + "\u0000" + row.metal; }
  function formatDelta(value) {
    if (value === null) return "—";
    const normalized = Number(value.toPrecision(12));
    const text = Object.is(normalized, -0) ? "0" : String(normalized);
    return normalized > 0 ? "+" + text : text;
  }
  function compare(left, right, leftRanking, rightRanking) {
    if (!left || !right || !Array.isArray(left.entries) || !Array.isArray(right.entries)) throw new Error("Both validated inventories are required");
    const lmap = new Map(left.entries.map(row => [key(row), row]));
    const rmap = new Map(right.entries.map(row => [key(row), row]));
    const rows = [];
    const seen = new Set();
    for (const source of left.entries.concat(right.entries)) {
      const id = key(source);
      if (seen.has(id)) continue;
      seen.add(id);
      const l = lmap.get(id) || null, r = rmap.get(id) || null;
      const status = !l ? "added" : !r ? "removed" :
        (l.volumeId !== r.volumeId || l.eligible !== r.eligible || l.mean !== r.mean || l.peak !== r.peak) ? "changed" : "unchanged";
      rows.push({association: source.association, metal: source.metal, left:l, right:r, status,
        meanDelta:l && r ? r.mean-l.mean : null, peakDelta:l && r ? r.peak-l.peak : null,
        eligibleDelta:l && r ? Number(r.eligible)-Number(l.eligible) : null});
    }
    const counts = METALS.map(metal => {
      const lc=left.entries.filter(x=>x.metal===metal&&x.eligible).length;
      const rc=right.entries.filter(x=>x.metal===metal&&x.eligible).length;
      return {metal,left:lc,right:rc,delta:rc-lc};
    });
    const rankAvailable = Array.isArray(leftRanking) && Array.isArray(rightRanking);
    const lr = new Map((leftRanking||[]).map(row=>[key(row),Number(row.rankText)]));
    const rr = new Map((rightRanking||[]).map(row=>[key(row),Number(row.rankText)]));
    if (rankAvailable) for (const row of rows) {
      row.leftRank = row.left && lr.has(key(row)) ? lr.get(key(row)) : null;
      row.rightRank = row.right && rr.has(key(row)) ? rr.get(key(row)) : null;
      row.rankDelta = row.leftRank !== null && row.rightRank !== null ? row.rightRank-row.leftRank : null;
    } else {
      for (const row of rows) { row.leftRank=null; row.rightRank=null; row.rankDelta=null; }
    }
    return {rows,counts,rankAvailable,leftAssociationCount:left.associationCount,rightAssociationCount:right.associationCount};
  }
  const PACKAGE_TYPE = "SKYFORGE_STUDIO_REGIONAL_COMPARISON_PACKAGE";
  const PACKAGE_VERSION = 1;
  const MAX_PACKAGE_BYTES = 10 * 1024 * 1024;
  const MAX_SOURCE_BYTES = 2 * 1024 * 1024;
  function isRecord(value) { return value !== null && typeof value === "object" && !Array.isArray(value); }
  function packageSource(value, side) {
    if (!isRecord(value) || Object.keys(value).sort().join(",") !== "inventory_csv,inventory_file,ranking_csv,ranking_file") {
      throw new Error(side + " source package has an invalid shape");
    }
    if (typeof value.inventory_file !== "string" || !value.inventory_file.trim() || value.inventory_file.length > 512 ||
        typeof value.inventory_csv !== "string" || new TextEncoder().encode(value.inventory_csv).length > MAX_SOURCE_BYTES) {
      throw new Error(side + " inventory source is missing or too large");
    }
    const hasRanking = value.ranking_csv !== null && value.ranking_file !== null;
    if (!hasRanking && !(value.ranking_csv === null && value.ranking_file === null)) {
      throw new Error(side + " ranking filename and CSV must both be present or absent");
    }
    if (hasRanking && (typeof value.ranking_file !== "string" || !value.ranking_file.trim() ||
        value.ranking_file.length > 512 || typeof value.ranking_csv !== "string" ||
        new TextEncoder().encode(value.ranking_csv).length > MAX_SOURCE_BYTES)) {
      throw new Error(side + " ranking source is invalid or too large");
    }
    return Object.freeze({
      inventory_file: value.inventory_file,
      inventory_csv: value.inventory_csv,
      ranking_file: hasRanking ? value.ranking_file : null,
      ranking_csv: hasRanking ? value.ranking_csv : null,
    });
  }
  function createPackage(left, right) {
    return Object.freeze({
      document_type: PACKAGE_TYPE,
      format_version: PACKAGE_VERSION,
      diagnostic_only: true,
      human_review_evidence: false,
      left: packageSource(left, "left"),
      right: packageSource(right, "right"),
    });
  }
  function parsePackage(value, fileBytes) {
    if (typeof value !== "string") throw new Error("comparison package must be read as JSON text");
    const size = Number.isFinite(fileBytes) ? fileBytes : new TextEncoder().encode(value).length;
    if (size < 0 || size > MAX_PACKAGE_BYTES) throw new Error("comparison package files must be 10 MB or smaller");
    let document;
    try { document = JSON.parse(value); }
    catch { throw new Error("comparison package is not valid JSON"); }
    const expected = ["diagnostic_only","document_type","format_version","human_review_evidence","left","right"];
    if (!isRecord(document) || Object.keys(document).sort().join(",") !== expected.sort().join(",")) {
      throw new Error("comparison package document has an invalid shape");
    }
    if (document.document_type !== PACKAGE_TYPE) throw new Error("file is not a Skyforge Studio regional comparison package");
    if (document.format_version !== PACKAGE_VERSION) throw new Error("unsupported comparison package format version");
    if (document.diagnostic_only !== true || document.human_review_evidence !== false) {
      throw new Error("comparison package must remain diagnostic and cannot claim human-review evidence");
    }
    return Object.freeze({
      left: packageSource(document.left, "left"),
      right: packageSource(document.right, "right"),
      fileBytes: size,
    });
  }
  function validatePackageInputs(value, fileBytes, inventoryApi) {
    if (!inventoryApi || typeof inventoryApi.parseInventoryCsv !== "function" ||
        typeof inventoryApi.parseRankingCsv !== "function") throw new Error("AUTH-0094 validator is unavailable");
    const report = parsePackage(value, fileBytes);
    const left = inventoryApi.parseInventoryCsv(report.left.inventory_csv);
    const right = inventoryApi.parseInventoryCsv(report.right.inventory_csv);
    const leftRanking = report.left.ranking_csv === null ? null : inventoryApi.parseRankingCsv(report.left.ranking_csv, left);
    const rightRanking = report.right.ranking_csv === null ? null : inventoryApi.parseRankingCsv(report.right.ranking_csv, right);
    return {report,left,right,leftRanking,rightRanking};
  }
  const api = Object.freeze({compare,formatDelta,createPackage,parsePackage,validatePackageInputs,packageType:PACKAGE_TYPE,packageVersion:PACKAGE_VERSION,maximumPackageBytes:MAX_PACKAGE_BYTES});
  if (typeof module !== "undefined" && module.exports) module.exports=api;
  if (root) root.SkyforgeRegionalComparison=api;
  if (typeof document === "undefined") return;

  const state = {left:null,right:null,leftRanking:null,rightRanking:null,leftNames:{},rightNames:{},leftSource:null,rightSource:null};
  const $ = id => document.getElementById(id);
  function status(side, message, error) {
    const el=$("regional-"+side+"-status"); el.textContent=message; el.className=error?"small error":"small muted";
  }
  function safeName(file){return file ? file.name : "";}
  const delta = formatDelta;
  function cell(row,value,heading){const n=document.createElement(heading?"th":"td");if(heading)n.scope="col";n.textContent=String(value);row.appendChild(n);}
  function render() {
    const out=$("regional-compare-output");
    $("regional-compare-export-report").disabled = !state.leftSource || !state.rightSource;
    if (!state.left || !state.right) {out.hidden=true; $("regional-compare-table").replaceChildren(); $("regional-compare-counts").replaceChildren(); return;}
    let result;
    try { result=compare(state.left,state.right,state.leftRanking,state.rightRanking); }
    catch(error) {out.hidden=true;status("right","Comparison unavailable: "+error.message,true);return;}
    out.hidden=false;
    $("regional-compare-summary").textContent=state.left.associationCount+" left associations · "+state.right.associationCount+" right associations · "+result.rows.length+" exact association/metal keys";
    const counts=$("regional-compare-counts");counts.replaceChildren();
    for(const item of result.counts){const card=document.createElement("article");card.className="regional-compare-count";const h=document.createElement("h4");h.textContent=item.metal;const p=document.createElement("p");p.textContent="Eligible associations";const value=document.createElement("strong");value.textContent=item.left+" → "+item.right+" (Δ "+(item.delta>0?"+":"")+item.delta+")";card.append(h,p,value);counts.appendChild(card);}
    const table=$("regional-compare-table"),head=document.createElement("thead"),hr=document.createElement("tr"),body=document.createElement("tbody");
    ["Status","Association","Metal","Left volume","Right volume","Left eligible","Right eligible","Mean left","Mean right","Mean Δ","Peak left","Peak right","Peak Δ", ...(result.rankAvailable?["Rank left","Rank right","Rank Δ"]:["Rank comparison"])].forEach(x=>cell(hr,x,true));head.appendChild(hr);
    for(const item of result.rows){const tr=document.createElement("tr");tr.className="regional-row-"+item.status;
      const vals=[item.status,item.association,item.metal,item.left?.volumeId??"—",item.right?.volumeId??"—",item.left?String(item.left.eligible):"—",item.right?String(item.right.eligible):"—",item.left?.meanText??"—",item.right?.meanText??"—",delta(item.meanDelta),item.left?.peakText??"—",item.right?.peakText??"—",delta(item.peakDelta)];
      if(result.rankAvailable) vals.push(item.leftRank??"—",item.rightRank??"—",item.rankDelta===null?"—":(item.rankDelta>0?"+":"")+item.rankDelta);
      else vals.push("Unavailable: both ranking.csv files are required");
      vals.forEach(x=>cell(tr,x,false));body.appendChild(tr);
    }
    table.replaceChildren(head,body);
  }
  async function fileText(file){if(!file)throw new Error("Choose a CSV file first");if(file.size>2*1024*1024)throw new Error("CSV files must be 2 MB or smaller");return file.text();}
  async function loadInventory(side,file){try{const invApi=root.SkyforgeRegionalInventory;if(!invApi)throw new Error("AUTH-0094 validator is unavailable");const text=await fileText(file);const parsed=invApi.parseInventoryCsv(text);state[side]=parsed;state[side+"Ranking"]=null;state[side+"Source"]={inventory:{name:safeName(file),text},ranking:null};state[side+"Names"]={inventory:safeName(file),ranking:""};$( "regional-"+side+"-ranking").value="";status(side,"Validated "+file.name+" ("+parsed.associationCount+" associations).",false);render();}catch(e){state[side]=null;state[side+"Ranking"]=null;state[side+"Source"]=null;state[side+"Names"]={};status(side,"Could not load inventory: "+String(e.message||e),true);render();}}
  async function loadRanking(side,file){try{if(!state[side])throw new Error("Load and validate this side's inventory first");const text=await fileText(file);state[side+"Ranking"]=root.SkyforgeRegionalInventory.parseRankingCsv(text,state[side]);state[side+"Source"].ranking={name:safeName(file),text};state[side+"Names"].ranking=safeName(file);status(side,"Validated "+file.name+" against this side's inventory.",false);render();}catch(e){state[side+"Ranking"]=null;if(state[side+"Source"])state[side+"Source"].ranking=null;if(state[side+"Names"])state[side+"Names"].ranking="";status(side,"Could not load ranking: "+String(e.message||e),true);render();}}
  function clear(){for(const side of ["left","right"]){state[side]=null;state[side+"Ranking"]=null;state[side+"Source"]=null;state[side+"Names"]={};$("regional-"+side+"-inventory").value="";$("regional-"+side+"-ranking").value="";status(side,"Choose an inventory CSV.",false);}render();}
  function init(){
    for(const side of ["left","right"]){
      $("regional-"+side+"-inventory").addEventListener("change",e=>{const f=e.target.files?.[0];if(f)loadInventory(side,f);e.target.value="";});
      $("regional-"+side+"-ranking").addEventListener("change",e=>{const f=e.target.files?.[0];if(f)loadRanking(side,f);e.target.value="";});
    }
    $("regional-compare-clear").addEventListener("click",clear);
    $("regional-compare-export-report").addEventListener("click",()=>{
      try{
        if(!state.leftSource||!state.rightSource)throw new Error("Load and validate both inventories first");
        const report=createPackage(
          {inventory_file:state.leftSource.inventory.name,inventory_csv:state.leftSource.inventory.text,ranking_file:state.leftSource.ranking?.name??null,ranking_csv:state.leftSource.ranking?.text??null},
          {inventory_file:state.rightSource.inventory.name,inventory_csv:state.rightSource.inventory.text,ranking_file:state.rightSource.ranking?.name??null,ranking_csv:state.rightSource.ranking?.text??null}
        );
        const text=JSON.stringify(report,null,2)+"\n";
        if(new TextEncoder().encode(text).length>MAX_PACKAGE_BYTES)throw new Error("Comparison package exceeds the 10 MB limit");
        const link=document.createElement("a"),url=URL.createObjectURL(new Blob([text],{type:"application/json"}));
        link.href=url;link.download="skyforge-regional-comparison.json";link.click();setTimeout(()=>URL.revokeObjectURL(url),0);
        $("regional-compare-package-status").textContent="Downloaded a portable, unbound diagnostic package with the original CSV inputs.";
        $("regional-compare-package-status").className="small muted";
      }catch(e){$("regional-compare-package-status").textContent="Could not export comparison: "+String(e.message||e);$("regional-compare-package-status").className="small error";}
    });
    $("regional-compare-open-report").addEventListener("click",()=>$("regional-compare-report-file").click());
    $("regional-compare-report-file").addEventListener("change",async e=>{
      const file=e.target.files?.[0];e.target.value="";if(!file)return;
      try{
        if(file.size>MAX_PACKAGE_BYTES)throw new Error("Comparison package files must be 10 MB or smaller");
        const loaded=validatePackageInputs(await file.text(),file.size,root.SkyforgeRegionalInventory),report=loaded.report;
        state.left=loaded.left;state.right=loaded.right;state.leftRanking=loaded.leftRanking;state.rightRanking=loaded.rightRanking;
        state.leftSource={inventory:{name:report.left.inventory_file,text:report.left.inventory_csv},ranking:report.left.ranking_csv===null?null:{name:report.left.ranking_file,text:report.left.ranking_csv}};
        state.rightSource={inventory:{name:report.right.inventory_file,text:report.right.inventory_csv},ranking:report.right.ranking_csv===null?null:{name:report.right.ranking_file,text:report.right.ranking_csv}};
        state.leftNames={inventory:report.left.inventory_file,ranking:report.left.ranking_file||""};state.rightNames={inventory:report.right.inventory_file,ranking:report.right.ranking_file||""};
        status("left","Reopened and revalidated "+report.left.inventory_file+".",false);status("right","Reopened and revalidated "+report.right.inventory_file+".",false);render();
        $("regional-compare-package-status").textContent="Package reopened; both inventories and any supplied rankings passed validation.";
        $("regional-compare-package-status").className="small muted";
      }catch(e){$("regional-compare-package-status").textContent="Could not open comparison package: "+String(e.message||e);$("regional-compare-package-status").className="small error";}
    });
    $("regional-compare-load-sample").addEventListener("click",async()=>{
      try{const base="sample/regional/";const [ir,rr]=await Promise.all([fetch(base+"auth-0094-inventory.csv",{cache:"no-store"}),fetch(base+"auth-0094-ranking.csv",{cache:"no-store"})]);if(!ir.ok||!rr.ok)throw new Error("Included AUTH-0094 files are unavailable in this preview.");const [it,rt]=await Promise.all([ir.text(),rr.text()]);state.left=root.SkyforgeRegionalInventory.parseInventoryCsv(it);state.leftRanking=root.SkyforgeRegionalInventory.parseRankingCsv(rt,state.left);state.leftSource={inventory:{name:"Included AUTH-0094 evidence",text:it},ranking:{name:"Included AUTH-0094 ranking",text:rt}};state.leftNames={inventory:"Included AUTH-0094 evidence",ranking:"Included AUTH-0094 ranking"};status("left","Loaded included AUTH-0094 evidence and its supplied ranking.",false);render();}catch(e){state.left=null;state.leftRanking=null;status("left","Could not load sample: "+String(e.message||e),true);render();}
    });
    clear();
  }
  document.addEventListener("DOMContentLoaded",init);
})(typeof window!=="undefined"?window:globalThis);
