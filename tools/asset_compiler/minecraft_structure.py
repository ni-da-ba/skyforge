from __future__ import annotations

import gzip
import re
import struct
from pathlib import Path
from typing import Iterable

from minecraft_adapter import MinecraftAdapter
from model import BlockState, CompiledAsset, SpecError

MINECRAFT_1_21_1_DATA_VERSION = 3955

TAG_END = 0
TAG_INT = 3
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10

_RESOURCE_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")


def _u16(value: int) -> bytes:
    return struct.pack(">H", value)


def _i32(value: int) -> bytes:
    return struct.pack(">i", value)


def _string_payload(value: str) -> bytes:
    raw = value.encode("utf-8")
    if len(raw) > 65535:
        raise SpecError("NBT string exceeds 65535 bytes")
    return _u16(len(raw)) + raw


def _named(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_type]) + _string_payload(name) + payload


def _tag_int(name: str, value: int) -> bytes:
    return _named(TAG_INT, name, _i32(value))


def _tag_string(name: str, value: str) -> bytes:
    return _named(TAG_STRING, name, _string_payload(value))


def _list_payload(element_type: int, elements: Iterable[bytes]) -> bytes:
    values = list(elements)
    return bytes([element_type]) + _i32(len(values)) + b"".join(values)


def _tag_int_list(name: str, values: Iterable[int]) -> bytes:
    return _named(TAG_LIST, name, _list_payload(TAG_INT, (_i32(v) for v in values)))


def _compound_payload(named_tags: Iterable[bytes]) -> bytes:
    return b"".join(named_tags) + bytes([TAG_END])


def _tag_compound(name: str, named_tags: Iterable[bytes]) -> bytes:
    return _named(TAG_COMPOUND, name, _compound_payload(named_tags))


def _block_state_payload(state: BlockState) -> bytes:
    tags = [_tag_string("Name", state.name)]
    if state.properties:
        tags.append(
            _tag_compound(
                "Properties",
                (_tag_string(key, value) for key, value in state.properties),
            )
        )
    return _compound_payload(tags)


def _validate_state_syntax(state: BlockState) -> None:
    """NBT/resource-location syntax only; semantic legality belongs to the target adapter."""
    if not _RESOURCE_RE.match(state.name):
        raise SpecError(f"invalid block resource location for Minecraft export: {state.name}")
    for key, value in state.properties:
        if not key or not value:
            raise SpecError(f"empty block-state property on {state.name}")


def structure_filename(asset_id: str) -> str:
    leaf = re.sub(r"[^a-z0-9_./-]+", "_", asset_id.lower().replace(".", "_"))
    leaf = re.sub(r"_+", "_", leaf).strip("_/")
    if not leaf:
        raise SpecError("assetId cannot be converted into a Minecraft structure name")
    return f"{leaf}.nbt"


def _adapter_for(compiled: CompiledAsset) -> MinecraftAdapter:
    """Select target intent policy without coupling the NBT serializer to architecture."""
    if str(compiled.summary.get("compilerVersion", "")) == "0.14-first-principles-detail":
        from minecraft_guild_profile import guild_v014_adapter
        return guild_v014_adapter()
    return MinecraftAdapter()


def _encode_realized_structure(compiled: CompiledAsset, *, data_version: int) -> bytes:
    if not compiled.summary.get("validation", {}).get("passed", False):
        raise SpecError("refusing Minecraft export for a compiler result that failed validation")
    if not compiled.model.cells:
        raise SpecError("refusing to export an empty structure")

    bounds = compiled.summary["layout"]["bounds"]
    min_x, min_y, min_z = map(int, bounds["min"])
    size = list(map(int, bounds["size"]))
    if any(component <= 0 for component in size):
        raise SpecError(f"invalid structure size: {size}")
    if any(component > 48 for component in size):
        raise SpecError(
            "vanilla structure-template proof export is limited to 48 blocks per axis; "
            f"got {size}"
        )

    states = sorted({cell.state for cell in compiled.model.cells.values()}, key=lambda s: s.canonical())
    for state in states:
        _validate_state_syntax(state)
    palette_index = {state: index for index, state in enumerate(states)}
    palette_payloads = [_block_state_payload(state) for state in states]

    block_payloads: list[bytes] = []
    for (x, y, z), cell in sorted(compiled.model.cells.items()):
        local = [x - min_x, y - min_y, z - min_z]
        if not all(0 <= local[i] < size[i] for i in range(3)):
            raise SpecError(f"block outside normalized structure bounds at {(x, y, z)}")
        block_payloads.append(
            _compound_payload([
                _tag_int_list("pos", local),
                _tag_int("state", palette_index[cell.state]),
            ])
        )

    root_payload = _compound_payload([
        _tag_int("DataVersion", int(data_version)),
        _tag_int_list("size", size),
        _named(TAG_LIST, "palette", _list_payload(TAG_COMPOUND, palette_payloads)),
        _named(TAG_LIST, "blocks", _list_payload(TAG_COMPOUND, block_payloads)),
        _named(TAG_LIST, "entities", _list_payload(TAG_COMPOUND, [])),
    ])
    raw = bytes([TAG_COMPOUND]) + _string_payload("") + root_payload
    return gzip.compress(raw, compresslevel=9, mtime=0)


def encode_structure_nbt(
    compiled: CompiledAsset,
    *,
    data_version: int = MINECRAFT_1_21_1_DATA_VERSION,
    use_adapter: bool = False,
) -> bytes:
    target = compiled
    if use_adapter:
        target, _report = _adapter_for(compiled).adapt(compiled)
    return _encode_realized_structure(target, data_version=data_version)


def write_structure_nbt(
    path: Path,
    compiled: CompiledAsset,
    *,
    data_version: int = MINECRAFT_1_21_1_DATA_VERSION,
    use_adapter: bool = False,
) -> dict | None:
    """Write a structure template and return the target-adapter report when enabled."""
    target = compiled
    report = None
    if use_adapter:
        target, report = _adapter_for(compiled).adapt(compiled)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(_encode_realized_structure(target, data_version=data_version))
    return report


def inspect_export_header(data: bytes) -> dict[str, int]:
    """Minimal deterministic smoke-check for the proof exporter, not a general NBT parser."""
    try:
        raw = gzip.decompress(data)
    except OSError as exc:
        raise SpecError("Minecraft structure export is not valid gzip") from exc
    if len(raw) < 3 or raw[0] != TAG_COMPOUND or raw[1:3] != b"\x00\x00":
        raise SpecError("Minecraft structure export does not begin with an unnamed root compound")
    return {"compressedBytes": len(data), "uncompressedBytes": len(raw)}
