#!/usr/bin/env python3
"""Verify Skyforge music-source persistence and BBCSO MIDI alignment.

Uses only the Python standard library so it can run in repository CI before
Gradle/JDK setup. Canonical cue manifests are the machine-readable boundary;
historical/experimental MIDI is still syntax-checked but is not promoted by
this verifier.
"""

from __future__ import annotations

import argparse
import collections
import gzip
import hashlib
import json
import struct
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable


CANONICAL_LANES = [
    "01 PICC", "02 FLT", "03 OBO", "04 CL", "05 BSN",
    "06 HN", "07 TPT", "08 TBN", "09 BTBN", "10 TUBA",
    "11 HC", "12 PERC", "13 TP", "14 PNO",
    "15 V1", "16 V2", "17 VLA", "18 VLC", "19 CB",
]

# BBC Symphony Orchestra Discover v1.8 documented ranges, expressed as
# absolute MIDI note numbers. Polymorphic/color lanes 11-14 are verified by
# cue-level manifest state instead of one generic range.
BBCSO_DISCOVER_RANGES = {
    1: (74, 108),  # Piccolo D5-C8
    2: (59, 96),   # Flutes a3 B3-C7
    3: (59, 89),   # Oboes a3 B3-F6
    4: (50, 88),   # Clarinets a3 D3-E6
    5: (34, 74),   # Bassoons a3 Bb1-D5
    6: (40, 77),   # Horns a4 E2-F5
    7: (52, 84),   # Trumpets a3 E3-C6
    8: (31, 74),   # Tenor Trombones a3 G1-D5
    9: (28, 67),   # Bass Trombones a2 E1-G4
    10: (26, 64),  # Tuba D1-E4
    15: (55, 97),  # Violins 1 G3-C#7
    16: (55, 97),  # Violins 2 G3-C#7
    17: (48, 90),  # Violas C3-F#6
    18: (36, 82),  # Celli C2-A#5
    19: (24, 54),  # Contrabasses C1-F#3
}


class VerificationError(RuntimeError):
    pass


@dataclass
class TrackInfo:
    name: str | None = None
    note_on_counts: collections.Counter[int] = field(default_factory=collections.Counter)
    tempos: list[tuple[int, int]] = field(default_factory=list)  # tick, us/quarter
    time_signatures: list[tuple[int, int, int]] = field(default_factory=list)  # tick, num, denom
    key_signatures: list[tuple[int, int, bool]] = field(default_factory=list)  # tick, sf, minor


@dataclass
class MidiInfo:
    format_type: int
    track_count: int
    ticks_per_beat: int
    tracks: list[TrackInfo]


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_vlq(data: bytes, pos: int, end: int) -> tuple[int, int]:
    value = 0
    for _ in range(4):
        if pos >= end:
            raise VerificationError("truncated MIDI variable-length quantity")
        byte = data[pos]
        pos += 1
        value = (value << 7) | (byte & 0x7F)
        if not (byte & 0x80):
            return value, pos
    raise VerificationError("invalid MIDI variable-length quantity (>4 bytes)")


def parse_track(payload: bytes, track_index: int) -> TrackInfo:
    info = TrackInfo()
    pos = 0
    end = len(payload)
    tick = 0
    running_status: int | None = None

    while pos < end:
        delta, pos = read_vlq(payload, pos, end)
        tick += delta
        if pos >= end:
            raise VerificationError(f"track {track_index}: truncated event")

        first = payload[pos]
        if first & 0x80:
            status = first
            pos += 1
            data_first: int | None = None
        else:
            if running_status is None:
                raise VerificationError(f"track {track_index}: running status without prior channel status")
            status = running_status
            data_first = first
            pos += 1

        if status == 0xFF:  # meta event
            if pos >= end:
                raise VerificationError(f"track {track_index}: truncated meta event")
            meta_type = payload[pos]
            pos += 1
            length, pos = read_vlq(payload, pos, end)
            if pos + length > end:
                raise VerificationError(f"track {track_index}: truncated meta payload")
            meta = payload[pos:pos + length]
            pos += length

            if meta_type == 0x03 and info.name is None:
                info.name = meta.decode("utf-8", errors="replace")
            elif meta_type == 0x51:
                if length != 3:
                    raise VerificationError(f"track {track_index}: tempo meta event has length {length}, expected 3")
                info.tempos.append((tick, int.from_bytes(meta, "big")))
            elif meta_type == 0x58:
                if length < 2:
                    raise VerificationError(f"track {track_index}: time-signature meta event too short")
                numerator = meta[0]
                denominator = 1 << meta[1]
                info.time_signatures.append((tick, numerator, denominator))
            elif meta_type == 0x59:
                if length != 2:
                    raise VerificationError(f"track {track_index}: key-signature meta event has length {length}, expected 2")
                sf = struct.unpack("b", meta[:1])[0]
                minor = bool(meta[1])
                info.key_signatures.append((tick, sf, minor))
            # Meta events do not become running status. Keep the most recent
            # channel status; common SMF writers rely on this permissive rule.
            continue

        if status in (0xF0, 0xF7):  # SysEx
            running_status = None
            length, pos = read_vlq(payload, pos, end)
            if pos + length > end:
                raise VerificationError(f"track {track_index}: truncated SysEx payload")
            pos += length
            continue

        if status >= 0xF0:
            raise VerificationError(f"track {track_index}: unsupported system status 0x{status:02X} in SMF")

        running_status = status
        event_type = status & 0xF0
        data_len = 1 if event_type in (0xC0, 0xD0) else 2

        if data_first is None:
            if pos >= end:
                raise VerificationError(f"track {track_index}: truncated channel event")
            d1 = payload[pos]
            pos += 1
        else:
            d1 = data_first

        d2: int | None = None
        if data_len == 2:
            if pos >= end:
                raise VerificationError(f"track {track_index}: truncated channel event")
            d2 = payload[pos]
            pos += 1

        if event_type == 0x90 and d2 is not None and d2 > 0:
            info.note_on_counts[d1] += 1

    return info


def parse_midi(data: bytes, label: str) -> MidiInfo:
    if len(data) < 14 or data[:4] != b"MThd":
        raise VerificationError(f"{label}: missing Standard MIDI File header")
    header_len = struct.unpack(">I", data[4:8])[0]
    if header_len < 6 or len(data) < 8 + header_len:
        raise VerificationError(f"{label}: invalid MIDI header length {header_len}")
    fmt, ntrks, division = struct.unpack(">HHH", data[8:14])
    if division & 0x8000:
        raise VerificationError(f"{label}: SMPTE time division is unsupported by the Skyforge authoring contract")

    pos = 8 + header_len
    tracks: list[TrackInfo] = []
    for index in range(ntrks):
        if pos + 8 > len(data) or data[pos:pos + 4] != b"MTrk":
            raise VerificationError(f"{label}: missing MTrk chunk for track {index}")
        length = struct.unpack(">I", data[pos + 4:pos + 8])[0]
        start = pos + 8
        end = start + length
        if end > len(data):
            raise VerificationError(f"{label}: truncated MTrk payload for track {index}")
        tracks.append(parse_track(data[start:end], index))
        pos = end

    if pos != len(data):
        # A few encoders can append benign NULs; accepting arbitrary trailing
        # bytes would hide persistence corruption, so fail closed.
        raise VerificationError(f"{label}: {len(data) - pos} trailing bytes after declared MIDI tracks")

    return MidiInfo(fmt, ntrks, division, tracks)
