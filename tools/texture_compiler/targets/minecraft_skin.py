from __future__ import annotations

from dataclasses import dataclass

from model import DetailGroup, RGBA, Region, SourcePatch, TextureProblem, TextureSpecError
from topology import cuboid_seams


def _hex(value: str) -> RGBA:
    raw = value.strip().lstrip("#")
    if len(raw) == 6:
        raw += "ff"
    if len(raw) != 8:
        raise TextureSpecError(f"invalid color {value}")
    return tuple(int(raw[i:i+2], 16) for i in range(0, 8, 2))  # type: ignore[return-value]


@dataclass
class _Canvas:
    w: int
    h: int
    pixels: list[RGBA]
    importance: list[float]

    @classmethod
    def filled(cls, w: int, h: int, color: RGBA, importance: float = 1.0) -> "_Canvas":
        return cls(w, h, [color] * (w*h), [importance] * (w*h))

    def rect(self, x0: int, y0: int, x1: int, y1: int, color: RGBA, importance: float = 1.0) -> None:
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.pixels[y*self.w+x] = color
                self.importance[y*self.w+x] = importance

    def logical(self, x0: int, y0: int, x1: int, y1: int, scale: int, color: RGBA, importance: float = 1.0) -> None:
        self.rect(x0*scale, y0*scale, x1*scale, y1*scale, color, importance)

    def patch(self) -> SourcePatch:
        return SourcePatch(self.w, self.h, tuple(self.pixels), tuple(self.importance))


def _region(name: str, x: int, y: int, w: int, h: int) -> Region:
    return Region(name, w, h, x, y)


def _classic_regions(include_overlay: bool = False) -> dict[str, Region]:
    r: dict[str, Region] = {}

    def cube(prefix: str, top_xy: tuple[int,int], side_xy: tuple[int,int], w: int, h: int, d: int) -> None:
        tx,ty=top_xy; sx,sy=side_xy
        r[prefix+".top"]=_region(prefix+".top",tx,ty,w,d)
        r[prefix+".bottom"]=_region(prefix+".bottom",tx+w,ty,w,d)
        r[prefix+".right"]=_region(prefix+".right",sx,sy,d,h)
        r[prefix+".front"]=_region(prefix+".front",sx+d,sy,w,h)
        r[prefix+".left"]=_region(prefix+".left",sx+d+w,sy,d,h)
        r[prefix+".back"]=_region(prefix+".back",sx+d+w+d,sy,w,h)

    cube("head",(8,0),(0,8),8,8,8)
    cube("torso",(20,16),(16,20),8,12,4)
    cube("right_arm",(44,16),(40,20),4,12,4)
    cube("right_leg",(4,16),(0,20),4,12,4)
    cube("left_leg",(20,48),(16,52),4,12,4)
    cube("left_arm",(36,48),(32,52),4,12,4)
    if include_overlay:
        cube("head_overlay",(40,0),(32,8),8,8,8)
        cube("torso_overlay",(20,32),(16,36),8,12,4)
        cube("right_arm_overlay",(44,32),(40,36),4,12,4)
        cube("right_leg_overlay",(4,32),(0,36),4,12,4)
        cube("left_leg_overlay",(4,48),(0,52),4,12,4)
        cube("left_arm_overlay",(52,48),(48,52),4,12,4)
    return r


def _solid(region: Region, color: RGBA, scale: int=2, importance: float=1.0) -> _Canvas:
    return _Canvas.filled(region.width*scale, region.height*scale, color, importance)


def _transparent(region: Region, scale: int) -> _Canvas:
    return _Canvas.filled(region.width*scale, region.height*scale, (0,0,0,0), 0.35)


def problem_from_spec(spec: dict) -> TextureProblem:
    schema = str(spec.get("schemaVersion", "0.1"))
    detail_enabled = schema >= "0.2" or bool(spec.get("detailPass", {}).get("enabled", False))
    style=spec.get("style",{})
    colors={k:_hex(v) for k,v in style.get("colors",{}).items()}
    required={"skin","skinShadow","hair","hairHighlight","eye","brow","mouth","coat","coatShadow","shirt","brass","trouser","boot"}
    missing=required-set(colors)
    if missing:
        raise TextureSpecError("minecraft skin style missing colors: "+", ".join(sorted(missing)))
    colors.setdefault("skinHighlight", colors["skin"])
    colors.setdefault("hairDeep", colors["hair"])
    colors.setdefault("coatHighlight", colors["coat"])
    colors.setdefault("shirtShadow", colors["shirt"])
    colors.setdefault("brassDark", colors["brass"])
    colors.setdefault("trouserHighlight", colors["trouser"])
    colors.setdefault("bootHighlight", colors["boot"])

    regions=_classic_regions(include_overlay=detail_enabled)
    sources: dict[str, SourcePatch]={}
    scale=int(spec.get("sourceScale",4 if detail_enabled else 2))
    if scale<1:
        raise TextureSpecError("sourceScale must be >=1")

    front=_solid(regions["head.front"],colors["skin"],scale,1.4)
    front.logical(0,0,8,3,scale,colors["hair"],2.4)
    front.logical(0,2,3,4,scale,colors["hair"],2.1)
    front.logical(4,2,8,3,scale,colors["hairHighlight"],1.8)
    front.logical(2,3,3,4,scale,colors["brow"],3.0)
    front.logical(5,3,6,4,scale,colors["brow"],3.0)
    front.logical(2,4,3,5,scale,colors["eye"],5.2)
    front.logical(5,4,6,5,scale,colors["eye"],5.2)
    front.logical(4,5,5,6,scale,colors["skinShadow"],2.8)
    front.logical(3,6,5,7,scale,colors["mouth"],3.5)
    if detail_enabled:
        front.logical(1,5,2,6,scale,colors["skinHighlight"],1.9)
        front.logical(6,5,7,6,scale,colors["skinShadow"],1.8)
        front.logical(0,1,2,2,scale,colors["hairDeep"],1.8)
    sources["head.front"]=front.patch()
    for side in ("right","left"):
        c=_solid(regions[f"head.{side}"],colors["skin"],scale,1.2)
        c.logical(0,0,8,4,scale,colors["hair"],2.0)
        c.logical(0,3,1,6,scale,colors["hairDeep"],1.9)
        c.logical(7,3,8,6,scale,colors["hair"],1.8)
        if detail_enabled:
            c.logical(2,1,6,2,scale,colors["hairHighlight"],1.5)
        sources[f"head.{side}"]=c.patch()
    back=_solid(regions["head.back"],colors["hair"],scale,1.8)
    back.logical(2,6,6,8,scale,colors["skinShadow"],1.2)
    if detail_enabled:
        back.logical(2,1,6,2,scale,colors["hairHighlight"],1.5)
        back.logical(0,0,2,5,scale,colors["hairDeep"],1.5)
    sources["head.back"]=back.patch()
    sources["head.top"]=_solid(regions["head.top"],colors["hair"],scale,1.7).patch()
    sources["head.bottom"]=_solid(regions["head.bottom"],colors["skinShadow"],scale,1.0).patch()

    for face in ("front","back","left","right","top","bottom"):
        c=_solid(regions[f"torso.{face}"],colors["coat"],scale,1.0)
        if face=="front":
            c.logical(3,0,5,4,scale,colors["shirt"],2.0)
            c.logical(2,0,6,1,scale,colors["shirt"],1.7)
            if detail_enabled:
                c.logical(2,1,3,5,scale,colors["coatHighlight"],1.8)
                c.logical(5,1,6,5,scale,colors["coatShadow"],1.8)
                c.logical(3,3,5,4,scale,colors["shirtShadow"],1.5)
                c.logical(0,1,1,10,scale,colors["coatHighlight"],1.2)
                c.logical(7,1,8,10,scale,colors["coatShadow"],1.2)
            for y in (4,6,8):
                c.logical(4,y,5,y+1,scale,colors["brass"],3.0)
            c.logical(0,10,8,12,scale,colors["coatShadow"],1.3)
        elif face=="back":
            if detail_enabled:
                c.logical(1,1,7,2,scale,colors["coatHighlight"],1.45)
                c.logical(3,2,5,10,scale,colors["coatShadow"],1.15)
                c.logical(0,10,8,12,scale,colors["coatShadow"],1.25)
        elif face in ("left","right"):
            if detail_enabled:
                c.logical(0,1,1,9,scale,colors["coatHighlight"],1.25)
            c.logical(0,10,4,12,scale,colors["coatShadow"],1.1)
        sources[f"torso.{face}"]=c.patch()

    for limb in ("right_arm","left_arm"):
        for face in ("front","back","left","right","top","bottom"):
            c=_solid(regions[f"{limb}.{face}"],colors["coat"],scale,1.0)
            if face in ("front","back","left","right"):
                if detail_enabled:
                    c.logical(0,1,1,8,scale,colors["coatHighlight"],1.25)
                    c.logical(3,1,4,8,scale,colors["coatShadow"],1.2)
                c.logical(0,8,4,10,scale,colors["coatShadow"],1.4)
                c.logical(0,10,4,12,scale,colors["skin"],1.9)
            sources[f"{limb}.{face}"]=c.patch()

    for limb in ("right_leg","left_leg"):
        for face in ("front","back","left","right","top","bottom"):
            c=_solid(regions[f"{limb}.{face}"],colors["trouser"],scale,1.0)
            if face in ("front","back","left","right"):
                if detail_enabled:
                    c.logical(0,1,1,8,scale,colors["trouserHighlight"],1.15)
                    c.logical(3,1,4,8,scale,colors["coatShadow"],1.05)
                c.logical(0,8,4,12,scale,colors["boot"],1.5)
                if detail_enabled:
                    c.logical(0,8,4,9,scale,colors["bootHighlight"],1.35)
            sources[f"{limb}.{face}"]=c.patch()

    if detail_enabled:
        for face in ("front","back","left","right","top","bottom"):
            c=_transparent(regions[f"head_overlay.{face}"],scale)
            if face=="front":
                c.logical(0,1,2,3,scale,colors["hairDeep"],2.7)
                c.logical(2,1,5,2,scale,colors["hair"],2.4)
                c.logical(5,1,8,3,scale,colors["hair"],2.5)
                c.logical(4,1,6,2,scale,colors["hairHighlight"],2.0)
            elif face in ("left","right"):
                c.logical(0,1,2,4,scale,colors["hair"],2.1)
                c.logical(2,1,4,2,scale,colors["hairHighlight"],1.7)
            elif face=="back":
                c.logical(1,1,7,2,scale,colors["hairHighlight"],1.7)
            sources[f"head_overlay.{face}"]=c.patch()

        for face in ("front","back","left","right","top","bottom"):
            c=_transparent(regions[f"torso_overlay.{face}"],scale)
            if face=="front":
                c.logical(2,0,6,1,scale,colors["coatHighlight"],2.3)
                for y,x in ((1,2),(2,2),(3,3),(4,3)):
                    c.logical(x,y,x+1,y+1,scale,colors["coatHighlight"],2.35)
                for y,x in ((1,5),(2,5),(3,4),(4,4)):
                    c.logical(x,y,x+1,y+1,scale,colors["coatShadow"],2.25)
                c.logical(1,6,3,7,scale,colors["coatHighlight"],1.9)
                c.logical(5,6,7,7,scale,colors["coatShadow"],1.9)
                c.logical(1,9,7,10,scale,colors["coatHighlight"],1.55)
                c.logical(0,10,8,12,scale,colors["coatShadow"],1.85)
                c.logical(6,2,7,3,scale,colors["brass"],3.6)
                c.logical(6,3,7,4,scale,colors["brassDark"],2.8)
            elif face=="back":
                c.logical(1,1,7,2,scale,colors["coatHighlight"],1.8)
                c.logical(0,10,8,12,scale,colors["coatShadow"],1.75)
            elif face in ("left","right"):
                c.logical(0,1,4,2,scale,colors["coatHighlight"],1.7)
                c.logical(0,10,4,12,scale,colors["coatShadow"],1.7)
            sources[f"torso_overlay.{face}"]=c.patch()

        for limb in ("right_arm_overlay","left_arm_overlay"):
            for face in ("front","back","left","right","top","bottom"):
                c=_transparent(regions[f"{limb}.{face}"],scale)
                if face in ("front","back","left","right"):
                    c.logical(0,0,4,1,scale,colors["coatHighlight"],2.0)
                    c.logical(0,7,4,9,scale,colors["coatShadow"],2.0)
                    c.logical(0,7,4,8,scale,colors["brassDark"],1.75)
                sources[f"{limb}.{face}"]=c.patch()

        for limb in ("right_leg_overlay","left_leg_overlay"):
            for face in ("front","back","left","right","top","bottom"):
                c=_transparent(regions[f"{limb}.{face}"],scale)
                if face=="front":
                    c.logical(1,1,2,7,scale,colors["trouserHighlight"],1.45)
                    c.logical(0,8,4,9,scale,colors["bootHighlight"],1.55)
                sources[f"{limb}.{face}"]=c.patch()

    seams=[]
    prefixes=["head","torso","right_arm","left_arm","right_leg","left_leg"]
    if detail_enabled:
        prefixes += ["head_overlay","torso_overlay","right_arm_overlay","left_arm_overlay","right_leg_overlay","left_leg_overlay"]
    for prefix in prefixes:
        faces={face:regions[f"{prefix}.{face}"] for face in ("front","back","left","right","top","bottom")}
        seams.extend(cuboid_seams(prefix,faces,hard=True))

    opt=spec.get("optimization",{})
    locked=tuple(_hex(v) for v in opt.get("lockedColors",[]))
    detail_groups: tuple[DetailGroup,...]=()
    multiscale=0.0
    if detail_enabled:
        group_cfg=spec.get("detailPass",{}).get("groups",{})
        def g(name:str, region_ids:list[str], defaults:tuple[int,int,float,float]) -> DetailGroup:
            cfg=group_cfg.get(name,{})
            mi,ma,we,sm=defaults
            return DetailGroup(name,tuple(region_ids),int(cfg.get("minColors",mi)),int(cfg.get("maxColors",ma)),float(cfg.get("weight",we)),float(cfg.get("smoothnessScale",sm)))
        face_regions=[f"head.{f}" for f in ("front","back","left","right","top","bottom")]
        garment_regions=[f"{p}.{f}" for p in ("torso","right_arm","left_arm") for f in ("front","back","left","right","top","bottom")]
        lower_regions=[f"{p}.{f}" for p in ("right_leg","left_leg") for f in ("front","back","left","right","top","bottom")]
        overlay_regions=[rid for rid in regions if "_overlay." in rid]
        detail_groups=(
            g("face_hair",face_regions,(3,6,3.0,0.60)),
            g("garment_base",garment_regions,(3,6,2.0,0.72)),
            g("lower_body",lower_regions,(2,4,1.0,1.05)),
            g("overlay_detail",overlay_regions,(2,5,2.4,0.48)),
        )
        multiscale=float(spec.get("detailPass",{}).get("multiscaleDetailWeight",1.4))

    return TextureProblem(
        asset_id=str(spec["assetId"]),
        atlas_width=64,
        atlas_height=64,
        regions=tuple(regions[k] for k in sorted(regions)),
        sources=sources,
        seams=tuple(seams),
        max_colors=int(opt.get("maxColors",20 if detail_enabled else 12)),
        locked_colors=locked,
        smoothness=float(opt.get("smoothness",0.9)),
        contrast_beta=float(opt.get("contrastBeta",0.012)),
        detail_groups=detail_groups,
        multiscale_detail_weight=multiscale,
        metadata={
            "targetKind":"minecraft_skin_classic",
            "sourceMode":"semantic_multiscale_reference" if detail_enabled else "semantic_high_resolution_reference",
            "sourceScale":scale,
            "topologyAuthority":"fixed_target_adapter",
            "uvContinuity":"hard_equivalence_constraints",
            "creativeSource":spec.get("creativeSource","semantic Guild clerk proof"),
            "detailPass":"semantic_region_budget_clothing_grammar_overlay" if detail_enabled else "none",
            "overlayEnabled":detail_enabled,
            "mathematicalAuthorities":[
                "cie_de_2000_perceptual_fidelity",
                "contrast_sensitive_potts_mrf",
                "alpha_expansion_graph_cut",
                "laplacian_pyramid_multiscale_saliency",
                "rate_distortion_resource_allocation",
                "hard_uv_equivalence_constraints",
            ] if detail_enabled else [],
        },
    )
