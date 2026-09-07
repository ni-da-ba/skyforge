#!/usr/bin/env python3
"""Verify canonical Skyforge soundtrack MIDI source integrity.

Uses only the Python standard library so CI can validate persisted soundtrack
sources without Sonar, BBCSO, mido, or other music-specific dependencies.
"""

from __future__ import annotations

import gzip
import hashlib
import json
import math
import struct
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class VerificationError(RuntimeError):
    pass


@dataclass(frozen=True)
class CueSpec:
    label: str
    source: str
    manifest: str
    sha256: str
    tempo_bpm: float
    meter: tuple[int, int]
    ppq: int


CUES = (
    CueSpec(
        "Track 00 — A Windborne Fantasia",
        "assets/music/source/frozen/track-00-a-windborne-fantasia-v2f1.mid.gz",
        "assets/music/source/frozen/track-00-a-windborne-fantasia-v2f1.manifest.json",
        "beb0c9d7d5625c7764207d140cce6b5496bbf1ad0803b2f0d78c26ce3cde9695",
        104.0,
        (6, 8),
        480,
    ),
    CueSpec(
        "Track 01 — Rambling Through the Gentle Blue",
        "assets/music/source/frozen/track-01-rambling-through-the-gentle-blue-d5-1.mid.gz",
        "assets/music/source/frozen/track-01-rambling-through-the-gentle-blue-d5-1.manifest.json",
        "5fed750e6650b8996785e0197214b4d94517cb88e904a73ff03a4b85ad5a2b65",
        96.0,
        (4, 4),
        480,
    ),
    CueSpec(
        "Track 02 — The Lord of Empty Miles",
        "assets/music/source/frozen/track-02-the-lord-of-empty-miles-d4-2.mid.gz",
        "assets/music/source/frozen/track-02-the-lord-of-empty-miles-d4-2.manifest.json",
        "79be46fd4ca2d712a727a571265d8521740148a550e05c04288cd14d5b9bf50d",
        132.0,
        (4, 4),
        960,
    ),
    CueSpec(
        "Track 03 — Count the Leagues",
        "assets/music/source/track-03-count-the-leagues-full-draft-02-second-horizon-72bar.mid.gz",
        "assets/music/source/track-03-count-the-leagues-full-draft-02-second-horizon-72bar.manifest.json",
        "7e1e968af4e92a4b1937b23f2e7a0e2e04bda2873c46930d0b7cc1f4e0faaec1",
        76.0,
        (3, 4),
        480,
    ),
)

TRACK_MARKERS = (
    "PICC", "FLT", "OBO", "CL", "BSN", "HN", "TPT", "TBN", "BTBN",
    "TUBA", "HC", "PERC", "TP", "PNO", "V1", "V2", "VLA", "VLC", "CB",
)


@dataclass
class ParsedTrack:
    name: str | None
    note_ons: Counter[int]
    tempos: list[int]
    meters: list[tuple[int, int]]


@dataclass
class ParsedMidi:
    fmt: int
    track_count: int
    ppq: int
    tracks: list[ParsedTrack]


def fail(message: str) -> None:
    raise VerificationError(message)


def read_vlq(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    for _ in range(4):
        if offset >= len(data):
            fail("truncated MIDI variable-length quantity")
        byte = data[offset]
        offset += 1
        value = (value << 7) | (byte & 0x7F)
        if not byte & 0x80:
            return value, offset
    fail("invalid MIDI variable-length quantity")


def parse_track(data: bytes) -> ParsedTrack:
    offset = 0
    running_status: int | None = None
    name: str | None = None
    note_ons: Counter[int] = Counter()
    tempos: list[int] = []
    meters: list[tuple[int, int]] = []

    while offset < len(data):
        _, offset = read_vlq(data, offset)
        if offset >= len(data):
            fail("track ends after delta time")

        first = data[offset]
        if first & 0x80:
            status = first
            offset += 1
            running_status = status if 0x80 <= status <= 0xEF else None
        else:
            if running_status is None:
                fail("running-status byte without prior channel status")
            status = running_status

        if status == 0xFF:
            if offset >= len(data):
                fail("truncated meta event")
            meta_type = data[offset]
            offset += 1
            length, offset = read_vlq(data, offset)
            end = offset + length
            if end > len(data):
                fail("truncated meta payload")
            payload = data[offset:end]
            offset = end

            if meta_type == 0x03:
                name = payload.decode("utf-8", errors="replace")
            elif meta_type == 0x51:
                if len(payload) != 3:
                    fail("tempo meta event is not three bytes")
                tempos.append(int.from_bytes(payload, "big"))
            elif meta_type == 0x58:
                if len(payload) < 2:
                    fail("time-signature meta event is too short")
                meters.append((payload[0], 1 << payload[1]))
            continue

        if status in (0xF0, 0xF7):
            length, offset = read_vlq(data, offset)
            offset += length
            if offset > len(data):
                fail("truncated SysEx payload")
            continue

        if not 0x80 <= status <= 0xEF:
            fail(f"unsupported MIDI status 0x{status:02X}")

        kind = status & 0xF0
        data_len = 1 if kind in (0xC0, 0xD0) else 2
        if offset + data_len > len(data):
            fail("truncated channel message")
        values = data[offset:offset + data_len]
        offset += data_len

        if kind == 0x90 and values[1] > 0:
            note_ons[values[0]] += 1

    return ParsedTrack(name, note_ons, tempos, meters)


def parse_midi(data: bytes) -> ParsedMidi:
    if len(data) < 14 or data[:4] != b"MThd":
        fail("missing Standard MIDI File MThd header")
    header_len = struct.unpack(">I", data[4:8])[0]
    if header_len != 6:
        fail(f"unexpected MIDI header length {header_len}")
    fmt, track_count, division = struct.unpack(">HHH", data[8:14])
    if division & 0x8000:
        fail("SMPTE time division is not valid for canonical Skyforge sources")

    offset = 14
    tracks: list[ParsedTrack] = []
    for index in range(track_count):
        if offset + 8 > len(data) or data[offset:offset + 4] != b"MTrk":
            fail(f"track {index}: missing MTrk header")
        length = struct.unpack(">I", data[offset + 4:offset + 8])[0]
        start = offset + 8
        end = start + length
        if end > len(data):
            fail(f"track {index}: chunk exceeds file size")
        tracks.append(parse_track(data[start:end]))
        offset = end

    if offset != len(data):
        fail(f"unexpected {len(data) - offset} trailing MIDI bytes")
    return ParsedMidi(fmt, track_count, division, tracks)


def manifest_source_identity(manifest: dict) -> tuple[str, str]:
    source = manifest.get("source") or manifest.get("repository_source")
    digest = manifest.get("uncompressed_midi_sha256")
    if digest is None:
        digest = manifest.get("source_provenance", {}).get("canonical_persisted_midi_sha256")
    if not isinstance(source, str) or not isinstance(digest, str):
        fail("manifest lacks canonical source path/SHA-256")
    return source, digest


def manifest_tempo_meter(manifest: dict) -> tuple[float, tuple[int, int]]:
    if "midi" in manifest:
        tempo = manifest["midi"].get("tempo_bpm")
        meter = manifest["midi"].get("meter")
    else:
        conductor = manifest.get("conductor", {})
        tempo = conductor.get("tempo_bpm", manifest.get("tempo_bpm"))
        meter = conductor.get("meter", manifest.get("meter"))
    if not isinstance(tempo, (int, float)) or not isinstance(meter, str) or "/" not in meter:
        fail("manifest lacks valid tempo/meter")
    numerator, denominator = meter.split("/", 1)
    return float(tempo), (int(numerator), int(denominator))


def assert_track_order(parsed: ParsedMidi, label: str) -> None:
    if parsed.track_count != 20:
        fail(f"{label}: expected conductor + 19 instrument tracks, found {parsed.track_count}")
    for index, marker in enumerate(TRACK_MARKERS, start=1):
        name = (parsed.tracks[index].name or "").upper().replace(" ", "")
        if marker not in name:
            fail(f"{label}: track {index} name {parsed.tracks[index].name!r} lacks marker {marker!r}")


def assert_conductor(parsed: ParsedMidi, spec: CueSpec) -> None:
    conductor = parsed.tracks[0]
    if len(conductor.tempos) != 1:
        fail(f"{spec.label}: expected one conductor tempo event, found {len(conductor.tempos)}")
    bpm = 60_000_000 / conductor.tempos[0]
    if not math.isclose(bpm, spec.tempo_bpm, abs_tol=0.01):
        fail(f"{spec.label}: MIDI tempo {bpm:.5f} != {spec.tempo_bpm:g} BPM")
    if conductor.meters != [spec.meter]:
        fail(f"{spec.label}: MIDI meter {conductor.meters!r} != {[spec.meter]!r}")


def assert_lane_state(parsed: ParsedMidi, manifest: dict, spec: CueSpec) -> None:
    perc = parsed.tracks[12].note_ons
    tuned = parsed.tracks[13].note_ons
    audit = manifest.get("library_audit", {})

    if audit.get("track_12_perc_used") is False and sum(perc.values()):
        fail(f"{spec.label}: Track 12 declared unused but contains notes")
    if audit.get("track_13_tp_used") is False and sum(tuned.values()):
        fail(f"{spec.label}: Track 13 declared unused but contains notes")

    if spec.label.startswith("Track 02"):
        expected = Counter({48: 97, 50: 26, 71: 14})
        if perc != expected:
            fail(f"{spec.label}: PERC counts {dict(perc)} != accepted {dict(expected)}")
        declared = manifest.get("track_12_percussion", {}).get("absolute_midi_map", [])
        declared_counter = Counter({int(row["note"]): int(row["attacks"]) for row in declared})
        if declared_counter != expected:
            fail(f"{spec.label}: manifest PERC map/counts do not match accepted map")
        if manifest.get("track_13_tuned_percussion", {}).get("used") is not False:
            fail(f"{spec.label}: Track 13 must remain explicitly unused")


def assert_known_range_exception_stability(parsed: ParsedMidi, spec: CueSpec) -> None:
    if spec.label.startswith("Track 00"):
        horn_high = sum(count for note, count in parsed.tracks[6].note_ons.items() if note > 77)
        viola_low = sum(count for note, count in parsed.tracks[17].note_ons.items() if note < 48)
        if (horn_high, viola_low) != (10, 3):
            fail(f"{spec.label}: documented range exceptions drifted: horn={horn_high}, viola={viola_low}")
    elif spec.label.startswith("Track 02"):
        trumpet_high = Counter({note: count for note, count in parsed.tracks[7].note_ons.items() if note > 84})
        if trumpet_high != Counter({85: 2}):
            fail(f"{spec.label}: documented Trumpet exception drifted: {dict(trumpet_high)}")


def verify_cue(spec: CueSpec) -> None:
    source_path = ROOT / spec.source
    manifest_path = ROOT / spec.manifest
    if not source_path.is_file() or not manifest_path.is_file():
        fail(f"{spec.label}: missing canonical source or manifest")

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("cue") != spec.label:
        fail(f"{spec.label}: manifest cue identity is {manifest.get('cue')!r}")

    manifest_source, manifest_sha = manifest_source_identity(manifest)
    if manifest_source != spec.source or manifest_sha != spec.sha256:
        fail(f"{spec.label}: manifest source identity differs from accepted identity")

    manifest_tempo, manifest_meter = manifest_tempo_meter(manifest)
    if not math.isclose(manifest_tempo, spec.tempo_bpm, abs_tol=1e-9) or manifest_meter != spec.meter:
        fail(f"{spec.label}: manifest tempo/meter differs from accepted state")

    compressed = source_path.read_bytes()
    expected_gzip = manifest.get("source_provenance", {}).get("canonical_gzip_sha256")
    if expected_gzip:
        actual_gzip = hashlib.sha256(compressed).hexdigest()
        if actual_gzip != expected_gzip:
            fail(f"{spec.label}: gzip SHA-256 {actual_gzip} != manifest {expected_gzip}")

    try:
        midi_bytes = gzip.decompress(compressed)
    except (OSError, EOFError) as exc:
        fail(f"{spec.label}: gzip decompression failed: {exc}")

    actual_sha = hashlib.sha256(midi_bytes).hexdigest()
    if actual_sha != spec.sha256:
        fail(f"{spec.label}: decompressed SHA-256 {actual_sha} != accepted {spec.sha256}")

    parsed = parse_midi(midi_bytes)
    if parsed.fmt != 1:
        fail(f"{spec.label}: MIDI format {parsed.fmt} != 1")
    if parsed.ppq != spec.ppq:
        fail(f"{spec.label}: MIDI PPQ {parsed.ppq} != accepted {spec.ppq}")

    assert_track_order(parsed, spec.label)
    assert_conductor(parsed, spec)
    assert_lane_state(parsed, manifest, spec)
    assert_known_range_exception_stability(parsed, spec)

    print(
        f"PASS  {spec.label}: sha={actual_sha[:12]}… tempo={spec.tempo_bpm:g} "
        f"meter={spec.meter[0]}/{spec.meter[1]} tracks=20 ppq={spec.ppq}"
    )


def main() -> int:
    try:
        for cue in CUES:
            verify_cue(cue)
    except (VerificationError, json.JSONDecodeError, KeyError, ValueError) as exc:
        print(f"MUSIC SOURCE VERIFICATION FAILED: {exc}", file=sys.stderr)
        return 1
    print(f"Music source integrity verified for {len(CUES)} canonical cues.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
