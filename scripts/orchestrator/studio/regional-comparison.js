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
  const api = Object.freeze({compare,formatDelta});
  if (typeof module !== "undefined" && module.exports) module.exports=api;
  if (root) root.SkyforgeRegionalComparison=api;
  if (typeof document === "undefined") return;

  const state = {left:null,right:null,leftRanking:null,rightRanking:null,leftNames:{},rightNames:{}};
  const $ = id => document.getElementById(id);
  function status(side, message, error) {
    const el=$("regional-"+side+"-status"); el.textContent=message; el.className=error?"small error":"small muted";
  }
  function safeName(file){return file ? file.name : "";}
  const delta = formatDelta;
  function cell(row,value,heading){const n=document.createElement(heading?"th":"td");if(heading)n.scope="col";n.textContent=String(value);row.appendChild(n);}
  function render() {
    const out=$("regional-compare-output");
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
  async function loadInventory(side,file){try{const invApi=root.SkyforgeRegionalInventory;if(!invApi)throw new Error("AUTH-0094 validator is unavailable");const parsed=invApi.parseInventoryCsv(await fileText(file));state[side]=parsed;state[side+"Ranking"]=null;state[side+"Names"]={inventory:safeName(file),ranking:""};$( "regional-"+side+"-ranking").value="";status(side,"Validated "+file.name+" ("+parsed.associationCount+" associations).",false);render();}catch(e){state[side]=null;state[side+"Ranking"]=null;status(side,"Could not load inventory: "+String(e.message||e),true);render();}}
  async function loadRanking(side,file){try{if(!state[side])throw new Error("Load and validate this side's inventory first");state[side+"Ranking"]=root.SkyforgeRegionalInventory.parseRankingCsv(await fileText(file),state[side]);state[side+"Names"].ranking=safeName(file);status(side,"Validated "+file.name+" against this side's inventory.",false);render();}catch(e){state[side+"Ranking"]=null;status(side,"Could not load ranking: "+String(e.message||e),true);render();}}
  function clear(){for(const side of ["left","right"]){state[side]=null;state[side+"Ranking"]=null;state[side+"Names"]={};$("regional-"+side+"-inventory").value="";$("regional-"+side+"-ranking").value="";status(side,"Choose an inventory CSV.",false);}render();}
  function init(){
    for(const side of ["left","right"]){
      $("regional-"+side+"-inventory").addEventListener("change",e=>{const f=e.target.files?.[0];if(f)loadInventory(side,f);e.target.value="";});
      $("regional-"+side+"-ranking").addEventListener("change",e=>{const f=e.target.files?.[0];if(f)loadRanking(side,f);e.target.value="";});
    }
    $("regional-compare-clear").addEventListener("click",clear);
    $("regional-compare-load-sample").addEventListener("click",async()=>{
      try{const base="sample/regional/";const [ir,rr]=await Promise.all([fetch(base+"auth-0094-inventory.csv",{cache:"no-store"}),fetch(base+"auth-0094-ranking.csv",{cache:"no-store"})]);if(!ir.ok||!rr.ok)throw new Error("Included AUTH-0094 files are unavailable in this preview.");const [it,rt]=await Promise.all([ir.text(),rr.text()]);state.left=root.SkyforgeRegionalInventory.parseInventoryCsv(it);state.leftRanking=root.SkyforgeRegionalInventory.parseRankingCsv(rt,state.left);state.leftNames={inventory:"Included AUTH-0094 evidence",ranking:"Included AUTH-0094 ranking"};status("left","Loaded included AUTH-0094 evidence and its supplied ranking.",false);render();}catch(e){state.left=null;state.leftRanking=null;status("left","Could not load sample: "+String(e.message||e),true);render();}
    });
    clear();
  }
  document.addEventListener("DOMContentLoaded",init);
})(typeof window!=="undefined"?window:globalThis);
