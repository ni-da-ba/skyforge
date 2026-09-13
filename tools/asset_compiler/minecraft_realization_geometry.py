from __future__ import annotations

from collections import defaultdict

from minecraft_adapter import BlockIntent, MinecraftAdapter
from model import BlockState, CompiledAsset, SpecError, VoxelModel

CARDINALS = {
    "north": (0, 0, -1),
    "east": (1, 0, 0),
    "south": (0, 0, 1),
    "west": (-1, 0, 0),
}
VERTICALS = {"up": (0, 1, 0), "down": (0, -1, 0)}
DIRECTIONS = {**CARDINALS, **VERTICALS}
OPPOSITE = {
    "north": "south",
    "south": "north",
    "east": "west",
    "west": "east",
    "up": "down",
    "down": "up",
}

# Bounded aperture families exercised by the accepted v0.14 Guild specimen. The normal points
# toward the exterior facade; tangent identifies the horizontal axis along the opening.
_APERTURES: dict[str, tuple[tuple[int, int, int], tuple[int, int, int]]] = {
    "fp_north_window_pane": ((0, 0, -1), (1, 0, 0)),
    "fp_south_window_pane": ((0, 0, 1), (1, 0, 0)),
    "fp_west_window_pane": ((-1, 0, 0), (0, 0, 1)),
    "fp_repair_transom": ((1, 0, 0), (0, 0, 1)),
    "fp_freight_transom": ((1, 0, 0), (0, 0, 1)),
}

_WORKING_PORTALS = frozenset({"fp_repair_door", "fp_freight_door"})


def _add(pos: tuple[int, int, int], delta: tuple[int, int, int]) -> tuple[int, int, int]:
    return pos[0] + delta[0], pos[1] + delta[1], pos[2] + delta[2]


def _scale(delta: tuple[int, int, int], factor: int) -> tuple[int, int, int]:
    return delta[0] * factor, delta[1] * factor, delta[2] * factor


def _structural_return_state(adapter: MinecraftAdapter) -> BlockState:
    return adapter.resolve_intent(
        BlockIntent(
            families=("dark_timber", "structural_timber"),
            required_capabilities=frozenset({"full_cube", "solid_support", "axis_orientable"}),
            properties=(("axis", "y"),),
        )
    )


def _repair_working_portal_clearance(model: VoxelModel, adapter: MinecraftAdapter) -> tuple[list[dict], list[str]]:
    """Clear bounded side-approach intrusions discovered by the first in-engine Guild gate.

    A service portal needs one clear interior cell directly behind every door and one clear cell
    beyond each lateral edge of the opening. Only the known working-partition infill is eligible for
    automatic deletion; anything else fails closed instead of silently rewriting architecture.
    """
    groups: dict[str, list[tuple[int, int, int]]] = defaultdict(list)
    for pos, cell in sorted(model.cells.items()):
        if cell.module in _WORKING_PORTALS and "door" in adapter.capability(cell.state.name).capabilities:
            props = adapter.normalize_defaults(cell.state).property_dict()
            if props.get("half") == "lower":
                groups[cell.module].append(pos)

    repairs: list[dict] = []
    issues: list[str] = []
    for module, doors in sorted(groups.items()):
        if not doors:
            continue
        facings = {
            adapter.normalize_defaults(model.cells[pos].state).property_dict()["facing"]
            for pos in doors
        }
        if len(facings) != 1:
            issues.append(f"working portal {module} has inconsistent lower-door facings: {sorted(facings)}")
            continue
        facing = next(iter(facings))
        outward = CARDINALS[facing]
        inward = _scale(outward, -1)
        tangent = (0, 0, 1) if facing in {"east", "west"} else (1, 0, 0)
        tangent_index = 2 if tangent[2] else 0
        ordered = sorted(doors, key=lambda pos: pos[tangent_index])

        # Direct approach is never an aesthetic preference: two player-height cells immediately
        # inside each door must remain unoccupied after target realization.
        for door in ordered:
            approach = _add(door, inward)
            for dy in (0, 1):
                q = (approach[0], approach[1] + dy, approach[2])
                blocker = model.cells.get(q)
                if blocker is not None:
                    issues.append(
                        f"working portal {module} direct approach blocked at {q} by "
                        f"role={blocker.role} module={blocker.module}"
                    )

        # Side clearance is the defect exposed by the freight-door screenshot: the partition ended
        # flush against the opening. Remove only the final partition column. A third cell is cleared
        # above player height so the portal does not retain a visually awkward calcite tooth; the
        # aperture pass may subsequently insert a timber transom return at that height.
        side_specs = ((ordered[0], -1), (ordered[-1], 1))
        for edge_door, sign in side_specs:
            approach = _add(edge_door, inward)
            margin = _add(approach, _scale(tangent, sign))
            for dy in (0, 1, 2):
                q = (margin[0], margin[1] + dy, margin[2])
                blocker = model.cells.get(q)
                if blocker is None:
                    continue
                if blocker.role == "wall_infill" and blocker.module == "fp_working_partition":
                    model.clear(*q)
                    repairs.append(
                        {
                            "position": list(q),
                            "mode": "clear_working_partition_from_portal_margin",
                            "portalModule": module,
                            "removed": blocker.state.canonical(),
                        }
                    )
                elif dy < 2:
                    issues.append(
                        f"working portal {module} lateral clearance blocked at {q} by "
                        f"role={blocker.role} module={blocker.module}"
                    )

    return repairs, issues


def _repair_aperture_returns(model: VoxelModel, adapter: MinecraftAdapter) -> tuple[list[dict], list[str]]:
    """Seal recessed panes against their facade jambs without flattening the recess.

    v0.14 intentionally recesses many panes one block behind the primary facade. The original
    architecture supplied front-plane jambs but omitted the one-block reveal return at several edge
    cells, which is visually open in Minecraft even though the pane state itself is legal. A return
    is added only when an empty pane-edge cell has a structural jamb/post exactly one block diagonally
    toward the exterior; this makes the repair local, deterministic, and evidence-backed.
    """
    return_state = _structural_return_state(adapter)
    repairs: list[dict] = []
    issues: list[str] = []
    additions: set[tuple[int, int, int]] = set()

    snapshot = list(sorted(model.cells.items()))
    for pos, cell in snapshot:
        aperture = _APERTURES.get(cell.module or "")
        if aperture is None or "pane" not in adapter.capability(cell.state.name).capabilities:
            continue
        outward, tangent = aperture
        for sign in (-1, 1):
            edge = _add(pos, _scale(tangent, sign))
            if edge in model.cells or edge in additions:
                continue
            diagonal = _add(edge, outward)
            jamb = model.cells.get(diagonal)
            if jamb is None or jamb.role != "structural_frame":
                continue
            jamb_module = jamb.module or ""
            if "jamb" not in jamb_module and "post" not in jamb_module:
                continue
            model.set(
                edge[0],
                edge[1],
                edge[2],
                "structural_frame",
                return_state,
                "minecraft_adapter_aperture_return",
            )
            additions.add(edge)
            repairs.append(
                {
                    "position": list(edge),
                    "mode": "seal_recessed_aperture_return",
                    "sourcePane": list(pos),
                    "diagonalJamb": list(diagonal),
                    "state": return_state.canonical(),
                }
            )

    # Verify the exact condition we repaired no longer exists. This catches any future aperture
    # family that is added to the bounded map but cannot be sealed without overwriting real geometry.
    for pos, cell in sorted(model.cells.items()):
        aperture = _APERTURES.get(cell.module or "")
        if aperture is None or "pane" not in adapter.capability(cell.state.name).capabilities:
            continue
        outward, tangent = aperture
        for sign in (-1, 1):
            edge = _add(pos, _scale(tangent, sign))
            if edge in model.cells:
                continue
            diagonal = _add(edge, outward)
            jamb = model.cells.get(diagonal)
            if jamb is not None and jamb.role == "structural_frame" and (
                "jamb" in (jamb.module or "") or "post" in (jamb.module or "")
            ):
                issues.append(f"recessed aperture at {pos} remains open toward jamb {diagonal}")

    return repairs, issues


def _refresh_connective_states(model: VoxelModel, adapter: MinecraftAdapter) -> int:
    """Recompute pane/fence cardinal states after target-side additions/removals."""
    changes = 0
    for pos, cell in sorted(list(model.cells.items())):
        state = adapter.normalize_defaults(cell.state)
        cap = adapter.capability(state.name)
        if not ({"pane", "fence"} & cap.capabilities):
            continue
        props = state.property_dict()
        x, y, z = pos
        for direction, (dx, dy, dz) in CARDINALS.items():
            neighbor = model.cells.get((x + dx, y + dy, z + dz))
            connected = False
            if neighbor is not None:
                ncap = adapter.capability(neighbor.state.name)
                connectable = (
                    {"pane", "full_cube", "solid_support"}
                    if "pane" in cap.capabilities
                    else {"fence", "full_cube", "solid_support"}
                )
                connected = bool(connectable & ncap.capabilities)
            props[direction] = str(connected).lower()
        realized = BlockState.of(state.name, **props)
        adapter.validate_state(realized)
        if realized != cell.state:
            model.set(pos[0], pos[1], pos[2], cell.role, realized, cell.module)
            changes += 1
    return changes


def _boundary_faces(state: BlockState, adapter: MinecraftAdapter) -> frozenset[str]:
    state = adapter.normalize_defaults(state)
    cap = adapter.capability(state.name)
    props = state.property_dict()
    if "full_cube" in cap.capabilities:
        return frozenset(DIRECTIONS)
    if "slab" in cap.capabilities:
        faces = {"north", "east", "south", "west"}
        slab_type = props["type"]
        if slab_type in {"bottom", "double"}:
            faces.add("down")
        if slab_type in {"top", "double"}:
            faces.add("up")
        return frozenset(faces)
    if "fence" in cap.capabilities or "pane" in cap.capabilities:
        faces = {"up", "down"}
        for direction in CARDINALS:
            if props.get(direction) == "true":
                faces.add(direction)
        return frozenset(faces)
    # This contact pass is intentionally bounded to the shapes implicated by the human gate.
    return frozenset()


def _contact_count(model: VoxelModel, pos: tuple[int, int, int], adapter: MinecraftAdapter) -> int:
    cell = model.cells[pos]
    faces = _boundary_faces(cell.state, adapter)
    contacts = 0
    for face, delta in DIRECTIONS.items():
        if face not in faces:
            continue
        neighbor = model.cells.get(_add(pos, delta))
        if neighbor is None:
            continue
        if OPPOSITE[face] in _boundary_faces(neighbor.state, adapter):
            contacts += 1
    return contacts


def _repair_known_isolated_detail(model: VoxelModel, adapter: MinecraftAdapter) -> list[dict]:
    """Remove only proven non-contact decorative detail from the first human gate.

    The waiting-bench backs were fences one cell above bottom slabs. In Minecraft shape-space the
    bottom slab ends half a block below the fence, so the fence is visibly levitating even though both
    states are individually legal. There is no faithful one-cell support repair with the current
    bounded palette that preserves the half-block seat height, so the minimum target-side edit is to
    omit the unsupported decorative back. Future Guild institutionalization can reintroduce a backed
    bench through a richer furniture grammar.
    """
    repairs: list[dict] = []
    for pos, cell in sorted(list(model.cells.items())):
        if cell.role != "seating_detail" or cell.module != "fp14_waiting_back":
            continue
        if "fence" not in adapter.capability(cell.state.name).capabilities:
            continue
        if _contact_count(model, pos, adapter) != 0:
            continue
        model.clear(*pos)
        repairs.append(
            {
                "position": list(pos),
                "mode": "omit_unsupported_waiting_bench_back",
                "removed": cell.state.canonical(),
            }
        )
    return repairs


def _validate_partial_detail_contact(model: VoxelModel, adapter: MinecraftAdapter) -> tuple[int, list[str]]:
    """Reject isolated slab/fence detail that is legal Minecraft but visually unsupported."""
    checked = 0
    issues: list[str] = []
    for pos, cell in sorted(model.cells.items()):
        cap = adapter.capability(cell.state.name)
        if not ({"slab", "fence"} & cap.capabilities):
            continue
        checked += 1
        if _contact_count(model, pos, adapter) == 0:
            issues.append(
                f"partial detail at {pos} is visually isolated: "
                f"{cell.state.canonical()} role={cell.role} module={cell.module}"
            )
    return checked, issues


def _validate_portal_clearance(model: VoxelModel, adapter: MinecraftAdapter) -> tuple[int, list[str]]:
    checked = 0
    issues: list[str] = []
    for pos, cell in sorted(model.cells.items()):
        if cell.module not in _WORKING_PORTALS:
            continue
        if "door" not in adapter.capability(cell.state.name).capabilities:
            continue
        props = adapter.normalize_defaults(cell.state).property_dict()
        if props.get("half") != "lower":
            continue
        checked += 1
        facing = props["facing"]
        inward = _scale(CARDINALS[facing], -1)
        approach = _add(pos, inward)
        for dy in (0, 1):
            q = (approach[0], approach[1] + dy, approach[2])
            if q in model.cells:
                blocker = model.cells[q]
                issues.append(
                    f"working portal {cell.module} remains blocked at {q} by "
                    f"role={blocker.role} module={blocker.module}"
                )
    return checked, issues


def enforce_realized_geometry_correctness(
    compiled: CompiledAsset,
    adapter: MinecraftAdapter,
) -> tuple[CompiledAsset, dict]:
    """Apply the bounded v0.5 Minecraft geometry-correctness gate.

    This pass operates only on target-created Minecraft states. It does not change architectural
    massing or the architecture digest. Automatic edits are intentionally narrow: remove only the
    known working-partition intrusion and unsupported waiting-back fences, and add only jamb-backed
    aperture returns. Ambiguous defects fail closed for a human/compiler decision.
    """
    model = compiled.model
    before = len(model.cells)
    issues: list[str] = []

    clearance_repairs, clearance_repair_issues = _repair_working_portal_clearance(model, adapter)
    issues.extend(clearance_repair_issues)
    aperture_repairs, aperture_issues = _repair_aperture_returns(model, adapter)
    issues.extend(aperture_issues)
    connectivity_changes = _refresh_connective_states(model, adapter)
    isolated_detail_repairs = _repair_known_isolated_detail(model, adapter)

    contact_checks, contact_issues = _validate_partial_detail_contact(model, adapter)
    issues.extend(contact_issues)
    portal_checks, portal_issues = _validate_portal_clearance(model, adapter)
    issues.extend(portal_issues)

    report = {
        "version": "minecraft-realized-geometry-0.5",
        "passed": not issues,
        "issues": issues,
        "targetCellCountBefore": before,
        "targetCellCountAfter": len(model.cells),
        "apertureReturnRepairs": aperture_repairs,
        "apertureReturnRepairCount": len(aperture_repairs),
        "operationalClearanceRepairs": clearance_repairs,
        "operationalClearanceRepairCount": len(clearance_repairs),
        "isolatedDetailRepairs": isolated_detail_repairs,
        "isolatedDetailRepairCount": len(isolated_detail_repairs),
        "connectiveStatesRefreshed": connectivity_changes,
        "partialDetailContactChecks": contact_checks,
        "workingPortalChecks": portal_checks,
        "repairPolicy": "bounded_jamb_returns_portal_clearance_and_proven_isolated_detail_v0.5",
    }
    if issues:
        raise SpecError("Minecraft realized-geometry gate rejected target: " + "; ".join(issues[:8]))
    return CompiledAsset(compiled.summary, model), report
