from __future__ import annotations

import json
from html import escape
from pathlib import Path
from typing import Iterable


def _svg(width: int, height: int, body: str, title: str) -> str:
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
<rect width="100%" height="100%" fill="#ffffff"/>
<text x="24" y="34" font-family="sans-serif" font-size="22" font-weight="700">{escape(title)}</text>
{body}
</svg>\n'''


def _poly(points: Iterable[tuple[float, float]], *, fill: str = "#d8dde3", stroke: str = "#222", sw: float = 2.0, opacity: float = 1.0) -> str:
    pts = " ".join(f"{x:.1f},{y:.1f}" for x, y in points)
    return f'<polygon points="{pts}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" opacity="{opacity}"/>'


def _line(x1, y1, x2, y2, *, stroke="#333", sw=2, dash=None) -> str:
    extra = f' stroke-dasharray="{dash}"' if dash else ""
    return f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{stroke}" stroke-width="{sw}"{extra}/>'


def _text(x, y, value, *, size=16, weight=400, anchor="start", fill="#111") -> str:
    return f'<text x="{x:.1f}" y="{y:.1f}" font-family="sans-serif" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}" fill="{fill}">{escape(str(value))}</text>'


def _top_view(r: dict, *, width=900, height=560, title="AIRCRAFT-001 v0.1 — top orthographic") -> str:
    g = r["geometry"]
    L = g["fuselage"]["lengthM"]
    max_span = max(g["wing"]["spanM"], g["horizontalTail"]["spanM"])
    sx = 700.0 / (L + 1.0)
    sz = 430.0 / (max_span + 1.0)
    scale = min(sx, sz)
    ox, oy = 80.0, height / 2.0
    def p(x, z): return ox + scale * x, oy - scale * z

    b = g["wing"]["spanM"]; cr = g["wing"]["rootChordM"]; ct = g["wing"]["tipChordM"]; xw = g["wing"]["leadingEdgeXM"]
    wing = [p(xw,0), p(xw,b/2), p(xw+ct,b/2), p(xw+cr,0), p(xw+ct,-b/2), p(xw,-b/2)]
    bh = g["horizontalTail"]["spanM"]; chr_=g["horizontalTail"]["rootChordM"]; cht=g["horizontalTail"]["tipChordM"]; xh=g["horizontalTail"]["leadingEdgeXM"]
    htail=[p(xh,0),p(xh,bh/2),p(xh+cht,bh/2),p(xh+chr_,0),p(xh+cht,-bh/2),p(xh,-bh/2)]
    fw=g["fuselage"]["maxWidthM"]
    fus=[p(0,0),p(0.55,fw/2),p(L*0.72,fw/2),p(L,0),p(L*0.72,-fw/2),p(0.55,-fw/2)]
    body = _poly(wing, fill="#cfd8e3") + _poly(htail, fill="#dfe6ec") + _poly(fus, fill="#8b7355")
    body += _line(*p(0,0), *p(L,0), stroke="#666", sw=1, dash="5,5")
    body += _text(610, 74, f"span {b:.2f} m   S {r['metrics']['wingAreaM2']:.2f} m²   AR {r['metrics']['wingAspectRatio']:.2f}", size=16)
    body += _text(610, 99, f"MAC {r['metrics']['wingMacM']:.2f} m   taper {r['metrics']['wingTaperRatio']:.3f}", size=16)
    body += _text(610, 124, "straight / unswept v0.1", size=14, fill="#555")
    return _svg(width,height,body,title)


def _side_view(r: dict, *, width=900, height=520, title="AIRCRAFT-001 v0.1 — side orthographic") -> str:
    g=r["geometry"]; L=g["fuselage"]["lengthM"]; H=g["fuselage"]["maxHeightM"]
    scale=min(720/(L+1),330/(H+2.8)); ox=80; ground=410
    def p(x,y): return ox+scale*x, ground-scale*y
    fus=[p(0.0,1.0),p(0.55,1.75),p(L*0.28,1.95),p(L*0.72,1.7),p(L,1.15),p(L*0.78,0.45),p(0.7,0.45)]
    body=_poly(fus,fill="#8b7355")
    xw=g["wing"]["leadingEdgeXM"]; cr=g["wing"]["rootChordM"]
    body+=_poly([p(xw,1.85),p(xw+cr,1.85),p(xw+cr,2.02),p(xw,2.02)],fill="#cfd8e3")
    xv=g["verticalTail"]["leadingEdgeXM"]; vr=g["verticalTail"]["rootChordM"]; vh=g["verticalTail"]["heightM"]
    body+=_poly([p(xv,1.45),p(xv+vr,1.45),p(xv+0.55*vr,1.45+vh),p(xv+0.12*vr,1.45+vh)],fill="#dfe6ec")
    xh=g["horizontalTail"]["leadingEdgeXM"]; hr=g["horizontalTail"]["rootChordM"]
    body+=_poly([p(xh,1.58),p(xh+hr,1.58),p(xh+hr,1.72),p(xh,1.72)],fill="#cfd8e3")
    prop=g["propellerEnvelope"]; cx,cy=p(prop["centerXM"],prop["centerYM"]); radius=scale*prop["diameterM"]/2
    body+=f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{radius:.1f}" fill="none" stroke="#4a6a8a" stroke-width="3" stroke-dasharray="8,6"/>'
    cgx=r["metrics"]["cgXM"]; body+=_line(*p(cgx,0.25),*p(cgx,2.65),stroke="#b32121",sw=2,dash="6,4")
    body+=_text(*p(cgx,2.82),"CG",size=14,weight=700,anchor="middle",fill="#b32121")
    body+=_text(610,74,f"fuselage {L:.2f} m",size=16)
    body+=_text(610,99,f"CG x = {cgx:.2f} m",size=16)
    body+=_text(610,124,f"CG = {r['metrics']['cgMacFraction']:.3f} MAC",size=16)
    return _svg(width,height,body,title)


def _front_view(r: dict, *, width=700, height=520, title="AIRCRAFT-001 v0.1 — front orthographic") -> str:
    g=r["geometry"]; b=g["wing"]["spanM"]; fw=g["fuselage"]["maxWidthM"]; fh=g["fuselage"]["maxHeightM"]
    scale=min(560/(b+1),330/(fh+2)); cx=350; cy=390
    body=""
    ywing=cy-scale*1.7
    body+=_poly([(cx-scale*b/2,ywing),(cx+scale*b/2,ywing),(cx+scale*b/2,ywing+9),(cx-scale*b/2,ywing+9)],fill="#cfd8e3")
    body+=f'<ellipse cx="{cx}" cy="{cy-scale*0.9:.1f}" rx="{scale*fw/2:.1f}" ry="{scale*fh/2:.1f}" fill="#8b7355" stroke="#222" stroke-width="2"/>'
    prop=g["propellerEnvelope"]; rr=scale*prop["diameterM"]/2; py=cy-scale*prop["centerYM"]
    body+=f'<circle cx="{cx}" cy="{py:.1f}" r="{rr:.1f}" fill="none" stroke="#4a6a8a" stroke-width="3" stroke-dasharray="8,6"/>'
    body+=_line(cx,cy-scale*4.0,cx,cy,stroke="#666",sw=1,dash="5,5")
    body+=_text(465,86,f"wing span {b:.2f} m",size=16)
    body+=_text(465,111,f"fuselage width {fw:.2f} m",size=16)
    body+=_text(465,136,f"prop disk {prop['diameterM']:.2f} m",size=16)
    return _svg(width,height,body,title)


def _balance_view(r: dict, *, width=1000, height=520, title="AIRCRAFT-001 v0.1 — mass / CG ledger") -> str:
    g=r["geometry"]; L=g["fuselage"]["lengthM"]; m=r["metrics"]; sx=820/(L+0.6); ox=90; axis_y=310
    def px(x): return ox+sx*x
    body=_line(px(0),axis_y,px(L),axis_y,stroke="#333",sw=4)
    mac=m["wingMacM"]; xle=g["wing"]["leadingEdgeXM"]
    body+=f'<rect x="{px(xle):.1f}" y="{axis_y-36}" width="{sx*mac:.1f}" height="72" fill="#cfd8e3" opacity="0.75" stroke="#3a5068" stroke-width="2"/>'
    body+=_text(px(xle+mac/2),axis_y+62,"wing MAC",size=14,anchor="middle")
    levels=[115,155,195,235,275]
    for i,item in enumerate(r["massLedger"]):
        x=px(item["stationXM"]); y=levels[i%len(levels)]
        radius=max(7,min(21,item["massKg"]/15))
        body+=_line(x,y+radius,x,axis_y-5,stroke="#888",sw=1,dash="3,3")
        body+=f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{radius:.1f}" fill="#d8c39a" stroke="#5b482e" stroke-width="2"/>'
        body+=_text(x,y-radius-8,f"{item['name']} {item['massKg']:.0f} kg",size=12,anchor="middle")
    cg=px(m["cgXM"]); body+=_line(cg,75,cg,365,stroke="#b32121",sw=4)
    body+=_text(cg,62,f"CG {m['cgXM']:.3f} m",size=15,weight=700,anchor="middle",fill="#b32121")
    body+=_text(70,420,f"CG fraction of MAC = {m['cgMacFraction']:.4f}",size=17,weight=700)
    body+=_text(70,450,"This is a mass-balance result, not a static-stability proof.",size=15,fill="#555")
    return _svg(width,height,body,title)


def _lift_view(r: dict, *, width=1000, height=520, title="AIRCRAFT-001 v0.1 — analytical design-condition check") -> str:
    m=r["metrics"]; mission=r["mission"]
    W=m["weightN"]; L=m["cruiseLiftN"]; ratio=L/W
    maxv=max(W,L); barmax=650
    body=_text(60,90,"L = ½ ρ V² S Cₗ",size=26,weight=700)
    body+=_text(60,125,f"ρ={mission['airDensityKgM3']} kg/m³   V={mission['cruiseSpeedMS']} m/s   Cₗ={mission['designLiftCoefficient']}",size=17)
    body+=_text(60,154,f"q = {m['dynamicPressurePa']:.2f} Pa   S = {m['wingAreaM2']:.3f} m²   required S = {m['requiredWingAreaM2AtDesignCL']:.3f} m²",size=17)
    y1,y2=230,315
    body+=_text(60,y1+6,"Weight",size=16); body+=f'<rect x="150" y="{y1-18}" width="{barmax*W/maxv:.1f}" height="32" fill="#9b6f53"/>'
    body+=_text(160+barmax*W/maxv,y1+6,f"{W:.1f} N",size=15)
    body+=_text(60,y2+6,"Lift",size=16); body+=f'<rect x="150" y="{y2-18}" width="{barmax*L/maxv:.1f}" height="32" fill="#6c8ebf"/>'
    body+=_text(160+barmax*L/maxv,y2+6,f"{L:.1f} N",size=15)
    body+=_text(60,390,f"|L-W|/W = {m['cruiseLiftResidualFraction']:.5f}   L/W = {ratio:.5f}",size=20,weight=700)
    body+=_text(60,425,f"C_Di proxy = {m['analyticalInducedDragCoefficient']:.5f} (e={r['declaredAssumptions']['spanEfficiency']}; declared assumption)",size=16)
    body+=_text(60,458,"Analytical pre-design only — not a Create Aeronautics force model.",size=16,fill="#555")
    return _svg(width,height,body,title)


def _mobile_review(r: dict) -> str:
    m=r["metrics"]; g=r["geometry"]
    body=""
    L=g["fuselage"]["lengthM"]; b=g["wing"]["spanM"]; ox,oy,sc=50,245,38
    def p(x,z): return ox+sc*x,oy-sc*z
    xw=g["wing"]["leadingEdgeXM"]; cr=g["wing"]["rootChordM"]; ct=g["wing"]["tipChordM"]
    body+=_poly([p(xw,0),p(xw,b/2),p(xw+ct,b/2),p(xw+cr,0),p(xw+ct,-b/2),p(xw,-b/2)],fill="#cfd8e3",sw=1.5)
    fw=g["fuselage"]["maxWidthM"]; body+=_poly([p(0,0),p(.5,fw/2),p(L*.72,fw/2),p(L,0),p(L*.72,-fw/2),p(.5,-fw/2)],fill="#8b7355",sw=1.5)
    xh=g["horizontalTail"]["leadingEdgeXM"]; bh=g["horizontalTail"]["spanM"]; hr=g["horizontalTail"]["rootChordM"]; ht=g["horizontalTail"]["tipChordM"]
    body+=_poly([p(xh,0),p(xh,bh/2),p(xh+ht,bh/2),p(xh+hr,0),p(xh+ht,-bh/2),p(xh,-bh/2)],fill="#dfe6ec",sw=1.5)
    x=620; lines=[
        ("selected geometry",20,700),
        (f"fuselage {L:.1f} m",17,400),
        (f"wing {b:.1f} m span / {m['wingAreaM2']:.2f} m²",17,400),
        (f"AR {m['wingAspectRatio']:.3f} / MAC {m['wingMacM']:.3f} m",17,400),
        (f"CG {m['cgXM']:.3f} m = {m['cgMacFraction']:.3f} MAC",17,400),
        (f"V_H {m['horizontalTailVolume']:.4f} (ref 0.70)",17,400),
        (f"V_V {m['verticalTailVolume']:.4f} (ref 0.04)",17,400),
        (f"lift residual {100*m['cruiseLiftResidualFraction']:.2f}%",17,400),
        (f"C_Di proxy {m['analyticalInducedDragCoefficient']:.5f}",17,400),
    ]
    yy=86
    for value,size,weight in lines:
        body+=_text(x,yy,value,size=size,weight=weight); yy+=30
    body+=f'<rect x="45" y="505" width="510" height="250" rx="10" fill="#f4f6f8" stroke="#c6ccd2"/>'
    body+=_text(65,540,"Math authority",size=20,weight=700)
    for i,line in enumerate(["L = ½ρV²SCₗ", "AR = b²/S", "MAC = ⅔ cᵣ(1+λ+λ²)/(1+λ)", "xCG = Σmᵢxᵢ / Σmᵢ", "V_H = S_H l_H/(S c̄)", "V_V = S_V l_V/(S b)"]):
        body+=_text(70,580+i*30,line,size=17)
    body+=f'<rect x="585" y="505" width="570" height="250" rx="10" fill="#f4f6f8" stroke="#c6ccd2"/>'
    body+=_text(605,540,"v0.1 boundary",size=20,weight=700)
    for i,line in enumerate(["✓ deterministic exhaustive search", "✓ explicit component mass ledger", "✓ source-backed planform relations", "✓ target-neutral design IR", "× no Create/Aeronautics force claim", "× no structural/dynamic-stability proof"]):
        body+=_text(610,580+i*30,line,size=17)
    body+=_text(45,805,f"digest {r['digestSha256'][:24]}…",size=14,fill="#555")
    return _svg(1200,840,body,"AIRCRAFT-001 v0.1 — mobile engineering review")


def emit_outputs(resolved: dict, out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    (out/"resolved.json").write_text(json.dumps(resolved,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    m=resolved["metrics"]
    lines=[
        "PASS",
        f"assetId={resolved['assetId']}",
        f"digestSha256={resolved['digestSha256']}",
        f"feasibleCandidates={resolved['solver']['candidateCountFeasible']}",
        f"wingAreaM2={m['wingAreaM2']:.12f}",
        f"wingAspectRatio={m['wingAspectRatio']:.12f}",
        f"wingMacM={m['wingMacM']:.12f}",
        f"cruiseLiftResidualFraction={m['cruiseLiftResidualFraction']:.12f}",
        f"cgMacFraction={m['cgMacFraction']:.12f}",
        f"horizontalTailVolume={m['horizontalTailVolume']:.12f}",
        f"verticalTailVolume={m['verticalTailVolume']:.12f}",
        "scope=analytical_geometry_and_balance_only",
    ]
    (out/"validation.txt").write_text("\n".join(lines)+"\n",encoding="utf-8")
    (out/"top.svg").write_text(_top_view(resolved),encoding="utf-8")
    (out/"side.svg").write_text(_side_view(resolved),encoding="utf-8")
    (out/"front.svg").write_text(_front_view(resolved),encoding="utf-8")
    (out/"mass_balance.svg").write_text(_balance_view(resolved),encoding="utf-8")
    (out/"lift_check.svg").write_text(_lift_view(resolved),encoding="utf-8")
    (out/"mobile_review.svg").write_text(_mobile_review(resolved),encoding="utf-8")
