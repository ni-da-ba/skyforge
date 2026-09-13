from __future__ import annotations
import collections, copy, hashlib, json
from typing import Any
from articulation import contiguous_groups
from guild_branch_finish import compile_guild_branch_v10
from model import BlockState, CompiledAsset, SpecError

def _mat(m: dict[str,str], role: str, fallback: str|None=None) -> str:
    v=m.get(role) or (m.get(fallback) if fallback else None)
    if not v: raise SpecError(f"materialRoles.{role}" + (f" or fallback {fallback}" if fallback else "") + " is required")
    return v

def _roof_y(z:int,z0:int,z1:int,rise:int,base:int)->int:
    s=min(max(z,z0),z1); half=max((z1-z0)//2,1); edge=min(s-z0,z1-s)
    return base + (rise*edge)//half

def compile_guild_branch_v11(spec: dict[str,Any]) -> CompiledAsset:
    if spec.get("schemaVersion")!="0.11": raise SpecError("v0.11 continuity compiler requires schemaVersion=0.11")
    cfg=spec.get("continuityResolution")
    if not isinstance(cfg,dict): raise SpecError("v0.11 requires continuityResolution object")
    mats=spec.get("materialRoles",{})
    need={"structuralFrame","foundation","window","roof","roofStair","roofSlab","exteriorTrimSlab","masonryTrimSlab","publicBoard","recordsShelf"}
    miss=sorted(need-set(mats))
    if miss: raise SpecError("missing v0.11 material roles: "+", ".join(miss))
    base=copy.deepcopy(spec); base["schemaVersion"]="0.10"
    c=compile_guild_branch_v10(base)
    if not c.summary["validation"]["passed"]: return c
    model,s=c.model,c.summary; layout=s["layout"]; rp=layout["resolvedParameters"]; st=spec["structure"]
    bw,hw,d,wh,ww=map(int,(rp["bayWidth"],rp["hallWidth"],rp["hallDepth"],rp["wallHeight"],rp["workingWingWidth"]))
    cz=int(rp["counterZ"]); ra,rb=map(int,rp["repairOpeningZ"]); fa,fb=map(int,rp["freightOpeningZ"]); dx0,dx1=map(int,rp["publicEntranceSpan"])
    z0=4; z1=z0+d-1; wx0=hw; wx1=hw+ww-1; inner=wx1-1
    rcfg=st.get("roof",{}); mr=int(rcfg.get("mainRise",4)); wr=int(rcfg.get("workingWingRise",2)); ov=int(rcfg.get("overhang",1)); rbase=wh+1
    fy=BlockState.of(_mat(mats,"structuralFrame"),axis="y"); fx=BlockState.of(_mat(mats,"structuralFrame"),axis="x"); fz=BlockState.of(_mat(mats,"structuralFrame"),axis="z")
    foundation=BlockState.of(_mat(mats,"foundation")); pane=BlockState.of(_mat(mats,"window"))
    mslab=BlockState.of(_mat(mats,"masonryTrimSlab","foundation"),type="bottom"); tslab=BlockState.of(_mat(mats,"exteriorTrimSlab","structuralFrame"),type="top")
    board=BlockState.of(_mat(mats,"publicBoard","structuralFrame")); shelf=BlockState.of(_mat(mats,"recordsShelf","structuralFrame"))
    rslab=BlockState.of(_mat(mats,"roofSlab","roof"),type="bottom")
    rn=BlockState.of(_mat(mats,"roofStair","roof"),facing="north",half="bottom",shape="straight"); rs=BlockState.of(_mat(mats,"roofStair","roof"),facing="south",half="bottom",shape="straight")

    wc=cfg.get("windowsAndWalls",{}); front=[]; north=[]; west=[]
    if wc.get("enabled",True):
        front=contiguous_groups(sorted({x for (x,_y,_z),cell in model.cells.items() if cell.module=="public_window_recess_v06"}))
        north=[tuple(map(int,g)) for g in layout.get("finishResolution",{}).get("rearElevation",{}).get("northWindowGroups",[])]
        west=[tuple(map(int,g)) for g in layout.get("detailResolution",{}).get("secondaryElevation",{}).get("westWindowGroups",[])]
        def zface(groups,wallz,panez,trimz,prefix):
            for i,(a,b) in enumerate(groups):
                reveal=f"{prefix}_window_reveal_v11_{i}"
                for x in range(a,b+1):
                    for y in (3,4): model.clear(x,y,wallz); model.set(x,y,panez,"window",pane,f"{prefix}_window_pane_v11")
                    model.set(x,2,panez,"foundation",foundation,reveal); model.set(x,5,panez,"structural_frame",fx,reveal)
                    model.set(x,2,trimz,"foundation",mslab,f"{prefix}_window_sill_v11_{i}"); model.set(x,5,trimz,"structural_frame",tslab,f"{prefix}_window_hood_v11_{i}")
                for j in (a-1,b+1):
                    if 0<=j<hw:
                        for zz in (wallz,panez):
                            for y in (3,4): model.set(j,y,zz,"structural_frame",fy,reveal)
        zface(front,z1,z1-1,z1+1,"south"); zface(north,z0,z0+1,z0-1,"north")
        for i,(a,b) in enumerate(west):
            reveal=f"west_window_reveal_v11_{i}"
            for z in range(a,b+1):
                for y in (3,4): model.clear(0,y,z); model.set(1,y,z,"window",pane,"west_window_pane_v11")
                model.set(1,2,z,"foundation",foundation,reveal); model.set(1,5,z,"structural_frame",fz,reveal)
                model.set(-1,2,z,"foundation",mslab,f"west_window_sill_v11_{i}"); model.set(-1,5,z,"structural_frame",tslab,f"west_window_hood_v11_{i}")
            for j in (a-1,b+1):
                if z0<=j<=z1:
                    for x in (0,1):
                        for y in (3,4): model.set(x,y,j,"structural_frame",fy,reveal)
        displaced={"contract_board","route_info_panel","contract_board_frame_v10","route_info_frame_v10","records_wall_v10","clerk_records_v10"}
        for p,cell in list(model.cells.items()):
            if cell.module in displaced: model.clear(*p)
        iz=z1-1
        for x,an,mod in ((max(1,dx0-2),"CONTRACT_BOARD","contract_board_wall_v11"),(min(hw-2,dx1+2),"ROUTE_INFO","route_info_wall_v11")):
            model.set(x,3,iz,"board",board,mod); model.set(x,4,iz,"structural_frame",tslab,mod+"_cap"); layout["anchors"][an]=[x,1,iz-1]
        for z in range(z0+2,max(z0+2,cz-2)+1):
            if z==layout["anchors"]["BACK_OFFICE_WORK"][2]: continue
            model.set(hw-2,2,z,"records",shelf,"records_wall_v11")
            if z%2==0: model.set(hw-2,3,z,"records",shelf,"records_wall_v11")

    sc=cfg.get("structureAndSpace",{}); spine=[]; lanes=[]; posts=sorted(set(range(0,hw,bw))|{hw-1})
    if sc.get("enabled",True):
        for x in posts:
            for zz in (z0,z1):
                for y in range(2,wh+1): model.set(x,y,zz,"structural_frame",fy,"continuous_bay_post_v11")
        for x in range(hw):
            model.set(x,wh,z0,"structural_frame",fx,"public_wall_plate_v11"); model.set(x,wh,z1,"structural_frame",fx,"public_wall_plate_v11")
        for x in range(dx0,dx1+1):
            for z in range(cz+1,z1):
                for y in (2,3):
                    cell=model.cells.get((x,y,z))
                    if cell and cell.role!="door" and cell.module!="service_counter_finish_v10": model.clear(x,y,z)
                    spine.append([x,y,z])
        for a,b,an in ((ra,rb,"REPAIR_BAY"),(fa,fb,"FREIGHT_PICKUP")):
            lz=max(a,min(b,int(layout["anchors"][an][2])))
            for x in range(wx0+1,inner):
                for y in (2,3):
                    cell=model.cells.get((x,y,lz))
                    if cell and cell.role!="door": model.clear(x,y,lz)
                    lanes.append([x,y,lz])

    roofc=cfg.get("roofDetail",{}); pr=[]; wrf=[]
    if roofc.get("enabled",True):
        for x in range(hw):
            model.set(x,_roof_y(z0-ov,z0,z1,mr,rbase),z0-ov,"roof",rn,"public_roof_eave_north_v11")
            model.set(x,_roof_y(z1+ov,z0,z1,mr,rbase),z1+ov,"roof",rs,"public_roof_eave_south_v11")
        for x in range(wx0,wx1+1):
            model.set(x,_roof_y(-ov,0,z1,wr,rbase),-ov,"roof",rn,"working_roof_eave_north_v11")
            model.set(x,_roof_y(z1+ov,0,z1,wr,rbase),z1+ov,"roof",rs,"working_roof_eave_south_v11")
        prz=max(range(z0,z1+1),key=lambda z:_roof_y(z,z0,z1,mr,rbase)); pry=_roof_y(prz,z0,z1,mr,rbase)
        for x in range(hw): model.set(x,pry+1,prz,"roof",rslab,"public_roof_ridge_cap_v11")
        wrz=max(range(0,z1+1),key=lambda z:_roof_y(z,0,z1,wr,rbase)); wry=_roof_y(wrz,0,z1,wr,rbase)
        for x in range(wx0,wx1+1): model.set(x,wry+1,wrz,"roof",rslab,"working_roof_ridge_cap_v11")
        for x in posts:
            for z in range(z0+1,z1):
                y=_roof_y(z,z0,z1,mr,rbase)-1; model.set(x,y,z,"structural_frame",fz,"public_roof_rafter_v11"); pr.append([x,y,z])
        wposts=sorted({wx0,wx0+ww//2,wx1})
        for x in wposts:
            for z in range(1,z1):
                y=_roof_y(z,0,z1,wr,rbase)-1; model.set(x,y,z,"structural_frame",fz,"working_roof_rafter_v11"); wrf.append([x,y,z])
        for z in sorted({z0+2,(z0+z1)//2,z1-2}):
            for x in range(1,hw-1): model.set(x,wh,z,"structural_frame",fx,"public_roof_tie_beam_v11")

    layout["continuityResolution"]={
        "stage":"structure_space_roof_continuity",
        "windowsAndWalls":{"southWindowGroups":[list(g) for g in front],"northWindowGroups":[list(g) for g in north],"westWindowGroups":[list(g) for g in west],"assembly":"complete_two_high_recessed_pane_boxed_reveal_sill_hood","interiorFixturesMayOccupyGlazing":False},
        "structureAndSpace":{"continuousBayPosts":posts,"publicCirculationWidth":dx1-dx0+1,"publicSpineCells":spine,"workingLaneCells":lanes},
        "roofDetail":{"weatherSkin":"solid_stepped_full_block_preserved","eaves":"single_directional_stair_row","ridge":"slab_cap","interior":"bay_aligned_exposed_rafters_and_tie_beams","publicRafterCells":pr,"workingRafterCells":wrf},
    }
    layout["resolvedParameters"].update({"runtimeCorrectionVersion":"0.11","continuityResolutionStage":"explicit","windowWallTreatment":"normalized_complete_recessed_assemblies","publicCirculationSpineWidth":dx1-dx0+1,"roofTreatment":"solid_skin_detailed_eaves_ridge_exposed_interior_frame","interiorRoofTreatment":"bay_aligned_rafters_tie_beams"})

    issues=[]; missing=sorted(set(spec.get("requestedAnchors",[]))-set(layout["anchors"]))
    if missing: issues.append("missing requested anchors: "+", ".join(missing))
    if wc.get("enabled",True):
        if not front or not north or not west: issues.append("v0.11 requires south, north, and west window groups")
        for prefix,groups,wallz,panez in (("south",front,z1,z1-1),("north",north,z0,z0+1)):
            for a,b in groups:
                for x in range(a,b+1):
                    for y in (3,4):
                        cell=model.cells.get((x,y,panez))
                        if cell is None or cell.role!="window" or cell.module!=f"{prefix}_window_pane_v11": issues.append(f"{prefix} complete pane missing at {(x,y,panez)}")
                        if model.cells.get((x,y,wallz)) is not None: issues.append(f"{prefix} outer window aperture blocked at {(x,y,wallz)}")
        for a,b in west:
            for z in range(a,b+1):
                for y in (3,4):
                    cell=model.cells.get((1,y,z))
                    if cell is None or cell.role!="window" or cell.module!="west_window_pane_v11": issues.append(f"west complete pane missing at {(1,y,z)}")
                    if model.cells.get((0,y,z)) is not None: issues.append(f"west outer window aperture blocked at {(0,y,z)}")
    if sc.get("enabled",True):
        for x in posts:
            for zz in (z0,z1):
                for y in range(2,wh+1):
                    cell=model.cells.get((x,y,zz)); allowed={"continuous_bay_post_v11","public_wall_plate_v11"}
                    if cell is None or cell.module not in allowed: issues.append(f"bay post discontinuity at {(x,y,zz)}"); break
        for x in range(dx0,dx1+1):
            for z in range(cz+1,z1):
                for y in (2,3):
                    cell=model.cells.get((x,y,z))
                    if cell and cell.role!="door" and cell.module!="service_counter_finish_v10": issues.append(f"public two-wide circulation spine blocked at {(x,y,z)}")
    if roofc.get("enabled",True):
        for name,x0,x1,a,b,rise in (("public",0,hw-1,z0,z1,mr),("working",wx0,wx1,0,z1,wr)):
            for z in range(a-ov,b+ov+1):
                y=_roof_y(z,a,b,rise,rbase)
                if any((model.cells.get((x,y,z)) is None or model.cells[(x,y,z)].role!="roof") for x in range(x0,x1+1)):
                    issues.append(f"{name} weather skin gap at z={z}"); break
        for x in posts:
            for z in range(z0+1,z1):
                y=_roof_y(z,z0,z1,mr,rbase)-1; cell=model.cells.get((x,y,z))
                if cell is None or cell.module!="public_roof_rafter_v11": issues.append(f"public interior rafter discontinuity at {(x,y,z)}"); break
        for mod in ("public_roof_eave_north_v11","public_roof_eave_south_v11","public_roof_ridge_cap_v11","public_roof_tie_beam_v11","working_roof_eave_north_v11","working_roof_eave_south_v11","working_roof_ridge_cap_v11","working_roof_rafter_v11"):
            if not any(cell.module==mod for cell in model.cells.values()): issues.append("missing v0.11 roof module: "+mod)
    if layout.get("paletteReview",{}).get("status") not in {None,"provisional"}: issues.append("v0.11 continuity pass must not finalize the Guild palette")
    xs=[p[0] for p in model.cells]; ys=[p[1] for p in model.cells]; zs=[p[2] for p in model.cells]
    layout["bounds"]={"min":[min(xs),min(ys),min(zs)],"max":[max(xs),max(ys),max(zs)],"size":[max(xs)-min(xs)+1,max(ys)-min(ys)+1,max(zs)-min(zs)+1]}
    canonical=json.dumps({"assetId":spec["assetId"],"layout":layout,"cells":[{"pos":[x,y,z],**cell.to_dict()} for (x,y,z),cell in sorted(model.cells.items())]},sort_keys=True,separators=(",",":"))
    s.update({"schemaVersion":"0.11","compilerVersion":"0.11","assetId":spec["assetId"],"layout":layout,"blockCount":len(model.cells),
              "materialCounts":dict(sorted(collections.Counter(c.state.canonical() for c in model.cells.values()).items())),
              "roleCounts":dict(sorted(collections.Counter(c.role for c in model.cells.values()).items())),
              "moduleBlockCounts":dict(sorted(collections.Counter(c.module for c in model.cells.values() if c.module).items())),
              "digestSha256":hashlib.sha256(canonical.encode()).hexdigest(),"validation":{"passed":not issues,"issues":issues}})
    return CompiledAsset(s,model)
