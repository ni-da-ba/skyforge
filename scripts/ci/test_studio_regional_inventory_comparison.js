"use strict";
const assert=require("node:assert/strict");
const {compare,formatDelta}=require("../orchestrator/studio/regional-comparison.js");
const row=(association,metal,volumeId,eligible,mean,peak)=>({association,metal,volumeId,eligible,mean,peak,meanText:String(mean),peakText:String(peak)});
const inventory=entries=>({entries,associationCount:new Set(entries.map(x=>x.association)).size});
const left=inventory([row("a","IRON","v1",true,4,8),row("a","COPPER","v1",false,0,0),row("b","IRON","v2",true,2,3)]);
const right=inventory([row("a","IRON","v9",true,5,9),row("a","COPPER","v9",true,1,2),row("c","IRON","v3",true,7,8)]);
const result=compare(left,right,[{association:"a",metal:"IRON",rankText:"0"}],[{association:"a",metal:"IRON",rankText:"1"}]);
assert.deepEqual(result.rows.map(x=>[x.association,x.metal,x.status]),[["a","IRON","changed"],["a","COPPER","changed"],["b","IRON","removed"],["c","IRON","added"]]);
assert.equal(result.rows[0].meanDelta,1);
assert.equal(result.rows[0].rankDelta,1);
assert.equal(result.counts.find(x=>x.metal==="IRON").delta,0);
assert.equal(result.counts.find(x=>x.metal==="COPPER").delta,1);
assert.equal(compare(left,right,null,null).rows[0].rankDelta,null);
assert.equal(formatDelta(0.00000005), "+5e-8");
assert.equal(formatDelta(-0.00000005), "-5e-8");
assert.equal(formatDelta(0), "0");
const {createPackage,parsePackage,validatePackageInputs,packageType,packageVersion,maximumPackageBytes}=require("../orchestrator/studio/regional-comparison.js");
const leftSource={inventory_file:"left.csv",inventory_csv:"valid-left-inventory",ranking_file:"left-ranking.csv",ranking_csv:"valid-left-ranking"};
const rightSource={inventory_file:"right.csv",inventory_csv:"valid-right-inventory",ranking_file:null,ranking_csv:null};
const packageDocument=createPackage(leftSource,rightSource);
assert.equal(packageDocument.document_type,packageType);
assert.equal(packageDocument.format_version,packageVersion);
assert.equal(packageDocument.diagnostic_only,true);
assert.equal(packageDocument.human_review_evidence,false);
assert.deepEqual(parsePackage(JSON.stringify(packageDocument)).left,leftSource);
assert.deepEqual(parsePackage(JSON.stringify(packageDocument)).right,rightSource);
assert.throws(()=>parsePackage("{}",maximumPackageBytes+1),/10 MB/);
assert.throws(()=>parsePackage(JSON.stringify({...packageDocument,extra:"field"})),/invalid shape/);
assert.throws(()=>parsePackage(JSON.stringify({...packageDocument,format_version:99})),/unsupported/);
assert.throws(()=>parsePackage(JSON.stringify({...packageDocument,diagnostic_only:false})),/must remain diagnostic/);
assert.throws(()=>createPackage({...leftSource,ranking_file:null}),/both be present or absent/);
assert.throws(()=>createPackage({...leftSource,inventory_csv:"x".repeat(2*1024*1024+1)}),/too large/);
const fakeInventoryApi={
  parseInventoryCsv(csv){if(!csv.startsWith("valid-"))throw new Error("invalid inventory");return {source:csv};},
  parseRankingCsv(csv,inventory){if(!csv.startsWith("valid-")||!inventory.source)throw new Error("invalid ranking");return {source:csv};}
};
const validated=validatePackageInputs(JSON.stringify(packageDocument),undefined,fakeInventoryApi);
assert.equal(validated.left.source,leftSource.inventory_csv);
assert.equal(validated.leftRanking.source,leftSource.ranking_csv);
assert.equal(validated.rightRanking,null);
let visibleState="existing comparison";
function applyPackage(json) {
  const loaded=validatePackageInputs(json,undefined,fakeInventoryApi);
  visibleState=loaded;
}
const invalidPackage=createPackage({...leftSource,inventory_csv:"invalid"},rightSource);
assert.throws(()=>applyPackage(JSON.stringify(invalidPackage)),/invalid inventory/);
assert.equal(visibleState,"existing comparison","validation failure leaves the previously visible comparison untouched");
const mismatchedRanking=createPackage({...leftSource,ranking_csv:"invalid"},rightSource);
assert.throws(()=>applyPackage(JSON.stringify(mismatchedRanking)),/invalid ranking/);
assert.equal(visibleState,"existing comparison");
console.log("Studio regional inventory comparison contract passed.");
