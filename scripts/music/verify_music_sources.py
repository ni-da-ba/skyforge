#!/usr/bin/env python3
"""Verify persisted Skyforge MIDI/source manifests using only Python stdlib."""
from __future__ import annotations
import collections, gzip, hashlib, json, struct, sys
from pathlib import Path

LANES=["01 PICC","02 FLT","03 OBO","04 CL","05 BSN","06 HN","07 TPT","08 TBN","09 BTBN","10 TUBA","11 HC","12 PERC","13 TP","14 PNO","15 V1","16 V2","17 VLA","18 VLC","19 CB"]
RANGES={1:(74,108),2:(59,96),3:(59,89),4:(50,88),5:(34,74),6:(40,77),7:(52,84),8:(31,74),9:(28,67),10:(26,64),15:(55,97),16:(55,97),17:(48,90),18:(36,82),19:(24,54)}
MAJOR={-7:"Cb",-6:"Gb",-5:"Db",-4:"Ab",-3:"Eb",-2:"Bb",-1:"F",0:"C",1:"G",2:"D",3:"A",4:"E",5:"B",6:"F#",7:"C#"}
MINOR={-7:"Abm",-6:"Ebm",-5:"Bbm",-4:"Fm",-3:"Cm",-2:"Gm",-1:"Dm",0:"Am",1:"Em",2:"Bm",3:"F#m",4:"C#m",5:"G#m",6:"D#m",7:"A#m"}
class VError(RuntimeError): pass

def vlq(b,p,e):
    v=0
    for _ in range(4):
        if p>=e: raise VError("truncated VLQ")
        x=b[p]; p+=1; v=(v<<7)|(x&127)
        if x<128:return v,p
    raise VError("invalid VLQ")

def parse_track(b,idx):
    p=t=0; e=len(b); run=None; name=None; notes=collections.Counter(); tempos=[]; meters=[]; keys=[]
    while p<e:
        d,p=vlq(b,p,e); t+=d
        if p>=e: raise VError(f"track {idx}: truncated event")
        x=b[p]
        if x&128: status=x;p+=1;first=None
        else:
            if run is None: raise VError(f"track {idx}: bad running status")
            status=run;first=x;p+=1
        if status==255:
            if p>=e: raise VError(f"track {idx}: truncated meta")
            typ=b[p];p+=1;n,p=vlq(b,p,e)
            if p+n>e: raise VError(f"track {idx}: truncated meta payload")
            m=b[p:p+n];p+=n
            if typ==3 and name is None:name=m.decode("utf-8","replace")
            elif typ==81:
                if n!=3: raise VError(f"track {idx}: bad tempo meta")
                tempos.append((t,int.from_bytes(m,"big")))
            elif typ==88:
                if n<2: raise VError(f"track {idx}: bad meter meta")
                meters.append((t,m[0],1<<m[1]))
            elif typ==89:
                if n!=2: raise VError(f"track {idx}: bad key meta")
                keys.append((t,struct.unpack("b",m[:1])[0],bool(m[1])))
            continue
        if status in (240,247):
            run=None;n,p=vlq(b,p,e);p+=n
            if p>e:raise VError(f"track {idx}: truncated sysex")
            continue
        if status>=240: raise VError(f"track {idx}: unsupported system status {status:#x}")
        run=status; typ=status&240; need=1 if typ in (192,208) else 2
        if first is None:
            if p>=e:raise VError(f"track {idx}: truncated channel event")
            d1=b[p];p+=1
        else:d1=first
        d2=None
        if need==2:
            if p>=e:raise VError(f"track {idx}: truncated channel event")
            d2=b[p];p+=1
        if typ==144 and d2:notes[d1]+=1
    return {"name":name,"notes":notes,"tempos":tempos,"meters":meters,"keys":keys}

def parse_midi(b,label):
    if len(b)<14 or b[:4]!=b"MThd":raise VError(f"{label}: missing MThd")
    h=struct.unpack(">I",b[4:8])[0]
    if h<6 or len(b)<8+h:raise VError(f"{label}: bad header")
    fmt,ntrks,div=struct.unpack(">HHH",b[8:14])
    if div&0x8000:raise VError(f"{label}: SMPTE division unsupported")
    p=8+h;tracks=[]
    for i in range(ntrks):
        if p+8>len(b) or b[p:p+4]!=b"MTrk":raise VError(f"{label}: missing MTrk {i}")
        n=struct.unpack(">I",b[p+4:p+8])[0];s=p+8;e=s+n
        if e>len(b):raise VError(f"{label}: truncated MTrk {i}")
        tracks.append(parse_track(b[s:e],i));p=e
    if p!=len(b):raise VError(f"{label}: {len(b)-p} trailing bytes")
    return {"fmt":fmt,"ntrks":ntrks,"ppq":div,"tracks":tracks}

def sha(b):return hashlib.sha256(b).hexdigest()
def source_of(m):
    s=m.get("source") or m.get("repository_source")
    if not isinstance(s,str) or not s:raise VError("manifest missing source")
    return s
def hash_of(m):
    vals=[m.get("uncompressed_midi_sha256"),m.get("source_provenance",{}).get("canonical_persisted_midi_sha256")]
    vals=[x.lower() for x in vals if isinstance(x,str) and x]
    if len(vals)!=1:raise VError("manifest must pin exactly one uncompressed MIDI SHA-256")
    return vals[0]
def tempo_of(m):
    for x in (m.get("midi",{}).get("tempo_bpm"),m.get("conductor",{}).get("tempo_bpm"),m.get("tempo_bpm")):
        if x is not None:return float(x)
def meter_of(m):
    x=m.get("midi",{}).get("meter") or m.get("conductor",{}).get("meter") or m.get("meter")
    if x is None:return None
    a,b=str(x).split("/");return int(a),int(b)
def key_name(sf,minor):return (MINOR if minor else MAJOR)[sf]
def key_of(m):return m.get("conductor",{}).get("key_signature_meta")

def declared_exceptions(m):
    a=m.get("library_audit")
    if not isinstance(a,dict):raise VError("canonical manifest missing library_audit")
    xs=a.get("range_exceptions")
    if not isinstance(xs,list):raise VError("library_audit.range_exceptions must be a list")
    out={}
    for x in xs:
        i=x.get("track_index");ns=x.get("notes");reason=x.get("reason")
        if i not in RANGES or not isinstance(ns,dict) or not ns or not isinstance(reason,str) or not reason.strip():raise VError(f"invalid range exception {x!r}")
        lo,hi=RANGES[i];c=collections.Counter()
        for k,v in ns.items():
            n=int(k)
            if lo<=n<=hi or not isinstance(v,int) or v<=0:raise VError(f"invalid range exception note/count {i}:{k}={v}")
            c[n]+=v
        out[i]=c
    return out

def actual_exceptions(md):
    out={}
    for i,(lo,hi) in RANGES.items():
        c=collections.Counter({n:v for n,v in md["tracks"][i]["notes"].items() if n<lo or n>hi})
        if c:out[i]=c
    return out

def verify_manifest(root,p,warns):
    m=json.loads(p.read_text("utf-8")); rel=p.relative_to(root); srel=source_of(m); src=root/srel
    if not srel.startswith("assets/music/source/") or not srel.endswith(".mid.gz") or not src.is_file():raise VError(f"{rel}: invalid/missing canonical source {srel}")
    gz=src.read_bytes(); gh=m.get("source_provenance",{}).get("canonical_gzip_sha256")
    if gh and sha(gz)!=str(gh).lower():raise VError(f"{rel}: gzip SHA mismatch")
    try:raw=gzip.decompress(gz)
    except Exception as e:raise VError(f"{rel}: gzip decode failed: {e}") from e
    digest=sha(raw)
    if digest!=hash_of(m):raise VError(f"{rel}: MIDI SHA mismatch actual={digest}")
    md=parse_midi(raw,srel)
    if md["fmt"]!=1 or md["ntrks"]!=20:raise VError(f"{srel}: expected format 1 / 20 tracks")
    for i,pfx in enumerate(LANES,1):
        nm=md["tracks"][i]["name"]
        if not nm or not nm.startswith(pfx):raise VError(f"{srel}: track {i} name {nm!r} != lane {pfx!r}")
    mm=m.get("midi",{})
    if "ticks_per_beat" in mm and md["ppq"]!=int(mm["ticks_per_beat"]):raise VError(f"{srel}: PPQ mismatch")
    bpm=tempo_of(m)
    if bpm is not None:
        target=round(60000000/bpm);allx=[x for t in md["tracks"] for x in t["tempos"]]
        if not any(t==0 and abs(us-target)<=1 for t,us in md["tracks"][0]["tempos"]):raise VError(f"{srel}: conductor tick-0 tempo != {bpm:g}")
        if any(abs(us-target)>1 for t,us in allx):raise VError(f"{srel}: conflicting tempo events")
    meter=meter_of(m)
    if meter:
        allx=[x for t in md["tracks"] for x in t["meters"]]
        if not any(t==0 and (a,b)==meter for t,a,b in md["tracks"][0]["meters"]):raise VError(f"{srel}: conductor tick-0 meter != {meter[0]}/{meter[1]}")
        if any((a,b)!=meter for t,a,b in allx):raise VError(f"{srel}: conflicting meter events")
    key=key_of(m)
    if key:
        allx=[x for t in md["tracks"] for x in t["keys"]]
        if not any(t==0 and key_name(sf,mi)==key for t,sf,mi in md["tracks"][0]["keys"]):raise VError(f"{srel}: conductor tick-0 key != {key}")
        if any(key_name(sf,mi)!=key for t,sf,mi in allx):raise VError(f"{srel}: conflicting key events")
    if actual_exceptions(md)!=declared_exceptions(m):raise VError(f"{rel}: BBCSO range exceptions differ from manifest; actual={actual_exceptions(md)} declared={declared_exceptions(m)}")
    audit=m["library_audit"]
    hc=sum(md["tracks"][11]["notes"].values()); state=audit.get("track_11_hc_state")
    if hc and (not isinstance(state,str) or not state.strip()):raise VError(f"{srel}: HC notes exist but plugin state is undocumented")
    if hc and any(x in state.lower() for x in ("not recoverable","unknown","recover")):warns.append(f"{srel}: HC plugin state explicitly unresolved ({hc} note-ons)")
    pc=sum(md["tracks"][12]["notes"].values()); pm=m.get("track_12_percussion")
    if isinstance(pm,dict):
        if not pm.get("bbcso_preset") or not isinstance(pm.get("absolute_midi_map"),list):raise VError(f"{srel}: incomplete PERC manifest")
        exp=collections.Counter({int(x["note"]):int(x["attacks"]) for x in pm["absolute_midi_map"]})
        if md["tracks"][12]["notes"]!=exp:raise VError(f"{srel}: PERC note/count map mismatch")
    elif "track_12_perc_used" in audit:
        if bool(pc)!=bool(audit["track_12_perc_used"]):raise VError(f"{srel}: PERC used-state mismatch")
    elif pc:raise VError(f"{srel}: PERC notes exist without preset/map")
    tc=sum(md["tracks"][13]["notes"].values()); tm=m.get("track_13_tuned_percussion")
    if isinstance(tm,dict):
        if bool(tc)!=bool(tm.get("used")):raise VError(f"{srel}: TP used-state mismatch")
        if tm.get("used") and not tm.get("bbcso_preset"):raise VError(f"{srel}: TP used without preset")
    elif "track_13_tp_used" in audit:
        if bool(tc)!=bool(audit["track_13_tp_used"]):raise VError(f"{srel}: TP used-state mismatch")
    elif tc:raise VError(f"{srel}: TP notes exist without preset declaration")
    return srel,digest

def main():
    here=Path(__file__).resolve();root=next((p for p in here.parents if (p/"assets/music/source").is_dir()),Path.cwd())
    try:
        wavs=list((root/"assets/music").rglob("*.wav"))
        if wavs:raise VError("ordinary Git music assets contain WAV: "+", ".join(str(x.relative_to(root)) for x in wavs[:5]))
        source=root/"assets/music/source"; count=0; archive_errors=[]
        for p in sorted([*source.rglob("*.mid"),*source.rglob("*.mid.gz")]):
            try:
                b=p.read_bytes()
                if p.name.endswith(".mid.gz"):b=gzip.decompress(b)
                parse_midi(b,str(p.relative_to(root)));count+=1
            except Exception as e:
                archive_errors.append(f"{p.relative_to(root)}: {e}")
        if archive_errors:raise VError("invalid MIDI artifacts:\n  - "+"\n  - ".join(archive_errors))
        manifests=sorted(source.rglob("*.manifest.json"))
        if not manifests:raise VError("no canonical manifests found")
        warns=[];seen_s=set();seen_h=set()
        for p in manifests:
            s,h=verify_manifest(root,p,warns)
            if s in seen_s or h in seen_h:raise VError(f"duplicate canonical source/hash: {s} {h}")
            seen_s.add(s);seen_h.add(h);print(f"PASS {p.relative_to(root)} -> {s} [{h[:12]}]")
        for w in warns:print("WARN",w)
        print(f"PASS parsed {count} MIDI artifacts; verified {len(manifests)} canonical manifests")
        return 0
    except (VError,KeyError,ValueError,json.JSONDecodeError) as e:
        print("MUSIC SOURCE VERIFICATION FAILED:",e,file=sys.stderr);return 1
if __name__=="__main__":raise SystemExit(main())
