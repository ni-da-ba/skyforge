#!/usr/bin/env python3
"""Render diagnostic-only Skyforge atmosphere observatory artifacts from the real-provider probe."""
import argparse, html, json, math
from pathlib import Path

ENTER=1.5
EXIT=0.75
BASELINE=-0.05
SMOOTHING=0.20

def glider(up):
    if up <= 0: return BASELINE
    target=up/20.0-0.05
    return BASELINE if target <= BASELINE else BASELINE+SMOOTHING*(target-BASELINE)

def stats(vals):
    return {"min":min(vals),"max":max(vals),"mean":sum(vals)/len(vals)}

def esc(s): return html.escape(str(s))

def color(v, lo, hi):
    if hi <= lo: t=.5
    else: t=max(0,min(1,(v-lo)/(hi-lo)))
    # blue -> neutral -> orange; diagnostic only
    r=int(45+190*t); g=int(105+70*(1-abs(2*t-1))); b=int(220-175*t)
    return f"rgb({r},{g},{b})"

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("input",type=Path); ap.add_argument("output",type=Path)
    a=ap.parse_args(); data=json.loads(a.input.read_text()); out=a.output; out.mkdir(parents=True,exist_ok=True)
    samples=data["samples"]
    for s in samples:
        s["x"],s["y"],s["z"]=s["position"]
        s["hmag"]=math.hypot(s["effective"][0],s["effective"][2])
        s["glider_y"]=glider(s["signed_vertical_air"])
        s["hawk_enter"]=s["trusted_for_gameplay"] and s["signed_vertical_air"]>=ENTER
    ups=[s["signed_vertical_air"] for s in samples]; hm=[s["hmag"] for s in samples]
    turb=[s["turbulence"] for s in samples]; shear=[s["shear"] for s in samples]
    levels=sorted({s["y"] for s in samples}); xs=sorted({s["x"] for s in samples}); zs=sorted({s["z"] for s in samples})
    # Adjacent spatial deltas at each level: useful first-order coherence diagnostic.
    lookup={(s["x"],s["y"],s["z"]):s for s in samples}; deltas=[]
    for y in levels:
      for z in zs:
       for x in xs:
        s=lookup[(x,y,z)]
        for dx,dz in ((64,0),(0,64)):
         q=lookup.get((x+dx,y,z+dz))
         if q: deltas.append(abs(s["signed_vertical_air"]-q["signed_vertical_air"]))
    summary={
      "schema_version":1,"artifact_kind":"SKYFORGE_ATMOSPHERE_OBSERVATORY_DERIVED",
      "source_artifact_kind":data["artifact_kind"],"source_digest":data["ordered_sample_digest"],
      "measured":{"updraft_mps":stats(ups),"horizontal_effective_wind_mps":stats(hm),
                  "turbulence":stats(turb),"shear_per_block":stats(shear)},
      "derived":{"adjacent_64_block_updraft_abs_delta_mps":stats(deltas),
                 "hawk_enter_cells":sum(s["hawk_enter"] for s in samples),
                 "glider_improved_cells":sum(s["glider_y"]>BASELINE for s in samples)},
      "policy_constants":{"hawk_enter_updraft_mps":ENTER,"hawk_exit_updraft_mps":EXIT,
                          "glider_baseline_y_blocks_per_tick":BASELINE,"glider_smoothing":SMOOTHING},
      "interpretation":"Raw provider measurements remain authoritative; all coherence and consumer fields are derived diagnostics."
    }
    temporal=data.get("temporal",{})
    frames=temporal.get("frames",[])
    if frames:
        tracks=list(zip(*[frame["samples"] for frame in frames]))
        temporal_summary=[]
        for idx,track in enumerate(tracks):
            uv=[p["signed_vertical_air"] for p in track]
            hv=[math.hypot(p["effective"][0],p["effective"][2]) for p in track]
            temporal_summary.append({"point_index":idx,"position":track[0]["position"],
                "updraft_mps":stats(uv),"horizontal_effective_wind_mps":stats(hv),
                "max_step_updraft_delta_mps":max(abs(b-a) for a,b in zip(uv,uv[1:])),
                "hawk_enter_frame_count":sum(p["trusted_for_gameplay"] and p["signed_vertical_air"]>=ENTER for p in track),
                "glider_improved_frame_count":sum(glider(p["signed_vertical_air"])>BASELINE for p in track)})
        summary["temporal"]={"period_ticks":temporal["period_ticks"],"frame_count":len(frames),
                             "duration_ticks":frames[-1]["game_tick"]-frames[0]["game_tick"],
                             "points":temporal_summary}
    (out/"summary.json").write_text(json.dumps(summary,indent=2)+"\n")
    W=780; cell=92; margin=80; panel=cell*5
    sv=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{len(levels)*(panel+75)+60}" viewBox="0 0 {W} {len(levels)*(panel+75)+60}">',
        '<style>text{font-family:system-ui,sans-serif;fill:#222}.small{font-size:11px}.label{font-size:14px;font-weight:600}.arrow{stroke:#111;stroke-width:2}</style>',
        '<text x="20" y="28" class="label">Skyforge real-provider atmosphere field — updraft color + effective horizontal wind</text>']
    ulo,uhi=min(ups),max(ups)
    for li,y in enumerate(levels):
      oy=55+li*(panel+75); sv.append(f'<text x="20" y="{oy+18}" class="label">Y={y:g}</text>')
      for zi,z in enumerate(zs):
       for xi,x in enumerate(xs):
        s=lookup[(x,y,z)]; px=margin+xi*cell; py=oy+30+zi*cell
        sv.append(f'<rect x="{px}" y="{py}" width="{cell-3}" height="{cell-3}" fill="{color(s["signed_vertical_air"],ulo,uhi)}" opacity=".72"/>')
        ex,ez=s["effective"][0],s["effective"][2]; mag=max(.001,math.hypot(ex,ez)); scale=min(30,7*mag)
        x2=px+cell/2+ex/mag*scale; y2=py+cell/2+ez/mag*scale
        sv.append(f'<line x1="{px+cell/2:.1f}" y1="{py+cell/2:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" class="arrow"/>')
        sv.append(f'<circle cx="{x2:.1f}" cy="{y2:.1f}" r="2.5" fill="#111"/>')
        sv.append(f'<text x="{px+4}" y="{py+14}" class="small">{s["signed_vertical_air"]:+.2f} m/s</text>')
        if s["hawk_enter"]: sv.append(f'<text x="{px+4}" y="{py+29}" class="small">HAWK ENTER</text>')
    sv.append('</svg>'); (out/"field-atlas.svg").write_text("\n".join(sv))
    temporal_html=""
    if frames:
        CW,CH=900,360; pad=55
        allu=[p["signed_vertical_air"] for frame in frames for p in frame["samples"]]
        lo,hi=min(allu+[EXIT]),max(allu+[ENTER]); span=max(.01,hi-lo)
        tv=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{CW}" height="{CH}" viewBox="0 0 {CW} {CH}">',
            '<style>text{font-family:system-ui,sans-serif;fill:#222}.trace{fill:none;stroke-width:2}</style>']
        def yy(v): return pad+(hi-v)/span*(CH-2*pad)
        for val,label in ((ENTER,"hawk enter 1.5"),(EXIT,"hawk exit 0.75")):
            y=yy(val); tv.append(f'<line x1="{pad}" y1="{y:.1f}" x2="{CW-pad}" y2="{y:.1f}" stroke="#777" stroke-dasharray="5 5"/><text x="{pad+5}" y="{y-5:.1f}">{label}</text>')
        palette=["#111","#555","#888","#bbb"]
        for idx,track in enumerate(zip(*[frame["samples"] for frame in frames])):
            pts=[]
            for fi,p in enumerate(track):
                x=pad+fi/(len(frames)-1)*(CW-2*pad); pts.append(f'{x:.1f},{yy(p["signed_vertical_air"]):.1f}')
            tv.append(f'<polyline points="{" ".join(pts)}" class="trace" stroke="{palette[idx]}"/>')
            tv.append(f'<text x="{CW-pad-180}" y="{20+idx*16}">P{idx}: {track[0]["position"]}</text>')
        tv.append('</svg>'); (out/"temporal-updraft.svg").write_text("\n".join(tv))
        temporal_html='<h2>Temporal updraft traces</h2><p>31 frames at 20-tick cadence (about 30 seconds at nominal 20 TPS). Horizontal guides are the current hawk hysteresis thresholds.</p><img src="temporal-updraft.svg" alt="Measured fixed-point updraft time series" style="max-width:100%;height:auto">'
    rows=[]
    for y in levels:
      ss=[s for s in samples if s["y"]==y]
      rows.append((y,stats([s["signed_vertical_air"] for s in ss]),stats([s["hmag"] for s in ss]),stats([s["turbulence"] for s in ss]),sum(s["hawk_enter"] for s in ss)))
    trs="".join(f"<tr><td>{y:g}</td><td>{u['min']:+.3f} / {u['mean']:+.3f} / {u['max']:+.3f}</td><td>{w['min']:.3f} / {w['mean']:.3f} / {w['max']:.3f}</td><td>{t['min']:.3f} / {t['mean']:.3f} / {t['max']:.3f}</td><td>{hc}/25</td></tr>" for y,u,w,t,hc in rows)
    doc=f"""<!doctype html><meta charset="utf-8"><title>Skyforge Atmosphere Observatory</title>
<style>body{{font:15px system-ui,sans-serif;max-width:1100px;margin:32px auto;padding:0 20px;color:#222}}table{{border-collapse:collapse;width:100%}}th,td{{padding:8px;border-bottom:1px solid #ccc;text-align:right}}th:first-child,td:first-child{{text-align:left}}code{{background:#eee;padding:2px 4px}}.note{{padding:12px;background:#f3f3f3}}</style>
<h1>Skyforge Atmosphere Observatory</h1>
<p class="note"><b>Diagnostic artifact.</b> Raw values below are measured from the accepted real A4MC server-authoritative gameplay sample. Coherence metrics and consumer overlays are derived and do not create atmosphere authority.</p>
<p>Source digest: <code>{esc(data["ordered_sample_digest"])}</code>. Samples: {len(samples)}. Provider: {esc(data["provider_identity"]["mod_id"])} {esc(data["provider_identity"]["version"])}.</p>
<h2>Spatial field atlas</h2><p>Cell color encodes signed vertical air; arrows show effective horizontal wind (mean + gust). “HAWK ENTER” marks cells meeting the current 1.5 m/s soaring-entry threshold.</p>
<img src="field-atlas.svg" alt="Four altitude slices of measured atmosphere" style="max-width:100%;height:auto">
{temporal_html}<h2>Altitude slice summary</h2><table><thead><tr><th>Y</th><th>Updraft min / mean / max (m/s)</th><th>Horizontal wind min / mean / max (m/s)</th><th>Turbulence min / mean / max</th><th>Hawk-enter cells</th></tr></thead><tbody>{trs}</tbody></table>
<h2>Derived diagnostics</h2><p>64-block adjacent updraft |Δ|: mean <b>{summary["derived"]["adjacent_64_block_updraft_abs_delta_mps"]["mean"]:.3f} m/s</b>, max {summary["derived"]["adjacent_64_block_updraft_abs_delta_mps"]["max"]:.3f}. Glider coupling improves the -0.05 blocks/tick baseline in <b>{summary["derived"]["glider_improved_cells"]}/{len(samples)}</b> sampled cells; hawk entry threshold is met in <b>{summary["derived"]["hawk_enter_cells"]}/{len(samples)}</b>.</p>
<p>The temporal section is a bounded 31-frame observation, not a claim of long-term climatology. It supports short-horizon persistence/coherence inspection only.</p>"""
    (out/"index.html").write_text(doc)
    print(json.dumps(summary,indent=2))

if __name__=="__main__": main()
