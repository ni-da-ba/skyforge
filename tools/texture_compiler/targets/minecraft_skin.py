from __future__ import annotations

from dataclasses import dataclass

from model import RGBA, Region, SourcePatch, TextureProblem, TextureSpecError
from topology import cuboid_seams


def _hex(value: str) -> RGBA:
    raw = value.strip().lstrip("#")
    if len(raw) == 6: raw += "ff"
    if len(raw) != 8: raise TextureSpecError(f"invalid color {value}")
    return tuple(int(raw[i:i+2], 16) for i in range(0, 8, 2))  # type: ignore[return-value]


@dataclass
class _Canvas:
    w: int; h: int; pixels: list[RGBA]; importance: list[float]
    @classmethod
    def filled(cls, w: int, h: int, color: RGBA, importance: float = 1.0) -> "_Canvas": return cls(w, h, [color] * (w*h), [importance] * (w*h))
    def rect(self, x0: int, y0: int, x1: int, y1: int, color: RGBA, importance: float = 1.0) -> None:
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.pixels[y*self.w+x] = color; self.importance[y*self.w+x] = importance
    def patch(self) -> SourcePatch: return SourcePatch(self.w, self.h, tuple(self.pixels), tuple(self.importance))


def _region(name: str, x: int, y: int, w: int, h: int) -> Region: return Region(name, w, h, x, y)


def _classic_regions() -> dict[str, Region]:
    r: dict[str, Region] = {}
    def cube(prefix: str, top_xy: tuple[int,int], side_xy: tuple[int,int], w: int, h: int, d: int) -> None:
        tx,ty=top_xy; sx,sy=side_xy
        r[prefix+".top"]=_region(prefix+".top",tx,ty,w,d); r[prefix+".bottom"]=_region(prefix+".bottom",tx+w,ty,w,d); r[prefix+".right"]=_region(prefix+".right",sx,sy,d,h); r[prefix+".front"]=_region(prefix+".front",sx+d,sy,w,h); r[prefix+".left"]=_region(prefix+".left",sx+d+w,sy,d,h); r[prefix+".back"]=_region(prefix+".back",sx+d+w+d,sy,w,h)
    cube("head",(8,0),(0,8),8,8,8); cube("torso",(20,16),(16,20),8,12,4); cube("right_arm",(44,16),(40,20),4,12,4); cube("right_leg",(4,16),(0,20),4,12,4); cube("left_leg",(20,48),(16,52),4,12,4); cube("left_arm",(36,48),(32,52),4,12,4)
    return r


def _solid(region: Region, color: RGBA, scale: int=2, importance: float=1.0) -> _Canvas: return _Canvas.filled(region.width*scale, region.height*scale, color, importance)


def problem_from_spec(spec: dict) -> TextureProblem:
    style=spec.get("style",{}); colors={k:_hex(v) for k,v in style.get("colors",{}).items()}; required={"skin","skinShadow","hair","hairHighlight","eye","brow","mouth","coat","coatShadow","shirt","brass","trouser","boot"}; missing=required-set(colors)
    if missing: raise TextureSpecError("minecraft skin style missing colors: "+", ".join(sorted(missing)))
    regions=_classic_regions(); sources: dict[str, SourcePatch]={}; scale=int(spec.get("sourceScale",2))
    if scale<1: raise TextureSpecError("sourceScale must be >=1")
    front=_solid(regions["head.front"],colors["skin"],scale,1.4); front.rect(0,0,front.w,3*scale,colors["hair"],2.4); front.rect(0,2*scale,3*scale,4*scale,colors["hair"],2.1); front.rect(4*scale,2*scale,8*scale,3*scale,colors["hairHighlight"],1.8); front.rect(2*scale,3*scale,3*scale,4*scale,colors["brow"],3.0); front.rect(5*scale,3*scale,6*scale,4*scale,colors["brow"],3.0); front.rect(2*scale,4*scale,3*scale,5*scale,colors["eye"],5.0); front.rect(5*scale,4*scale,6*scale,5*scale,colors["eye"],5.0); front.rect(4*scale,5*scale,5*scale,6*scale,colors["skinShadow"],2.8); front.rect(3*scale,6*scale,5*scale,7*scale,colors["mouth"],3.5); sources["head.front"]=front.patch()
    for side in ("right","left"):
        c=_solid(regions[f"head.{side}"],colors["skin"],scale,1.2); c.rect(0,0,c.w,4*scale,colors["hair"],2.0); c.rect(0,3*scale,scale,6*scale,colors["hair"],1.8); c.rect(c.w-scale,3*scale,c.w,6*scale,colors["hair"],1.8); sources[f"head.{side}"]=c.patch()
    back=_solid(regions["head.back"],colors["hair"],scale,1.8); back.rect(2*scale,6*scale,6*scale,8*scale,colors["skinShadow"],1.2); sources["head.back"]=back.patch(); sources["head.top"]=_solid(regions["head.top"],colors["hair"],scale,1.7).patch(); sources["head.bottom"]=_solid(regions["head.bottom"],colors["skinShadow"],scale,1.0).patch()
    for face in ("front","back","left","right","top","bottom"):
        c=_solid(regions[f"torso.{face}"],colors["coat"],scale,1.0)
        if face=="front":
            mid=c.w//2; c.rect(mid-scale,0,mid+scale,4*scale,colors["shirt"],2.0); c.rect(mid-2*scale,0,mid+2*scale,scale,colors["shirt"],1.6)
            for y in (4,6,8): c.rect(mid,y*scale,mid+scale,(y+1)*scale,colors["brass"],2.8)
            c.rect(0,10*scale,c.w,12*scale,colors["coatShadow"],1.2)
        elif face in ("left","right"): c.rect(0,10*scale,c.w,12*scale,colors["coatShadow"],1.1)
        sources[f"torso.{face}"]=c.patch()
    for limb in ("right_arm","left_arm"):
        for face in ("front","back","left","right","top","bottom"):
            c=_solid(regions[f"{limb}.{face}"],colors["coat"],scale,1.0)
            if face in ("front","back","left","right"): c.rect(0,8*scale,c.w,10*scale,colors["coatShadow"],1.2); c.rect(0,10*scale,c.w,12*scale,colors["skin"],1.8)
            sources[f"{limb}.{face}"]=c.patch()
    for limb in ("right_leg","left_leg"):
        for face in ("front","back","left","right","top","bottom"):
            c=_solid(regions[f"{limb}.{face}"],colors["trouser"],scale,1.0)
            if face in ("front","back","left","right"): c.rect(0,8*scale,c.w,12*scale,colors["boot"],1.4)
            sources[f"{limb}.{face}"]=c.patch()
    seams=[]
    for prefix in ("head","torso","right_arm","left_arm","right_leg","left_leg"):
        faces={face:regions[f"{prefix}.{face}"] for face in ("front","back","left","right","top","bottom")}; seams.extend(cuboid_seams(prefix,faces,hard=True))
    opt=spec.get("optimization",{}); locked=tuple(_hex(v) for v in opt.get("lockedColors",[]))
    return TextureProblem(asset_id=str(spec["assetId"]),atlas_width=64,atlas_height=64,regions=tuple(regions[k] for k in sorted(regions)),sources=sources,seams=tuple(seams),max_colors=int(opt.get("maxColors",12)),locked_colors=locked,smoothness=float(opt.get("smoothness",0.9)),contrast_beta=float(opt.get("contrastBeta",0.012)),metadata={"targetKind":"minecraft_skin_classic","sourceMode":"semantic_high_resolution_reference","sourceScale":scale,"topologyAuthority":"fixed_target_adapter","uvContinuity":"hard_equivalence_constraints","creativeSource":spec.get("creativeSource","semantic Guild clerk proof")})
