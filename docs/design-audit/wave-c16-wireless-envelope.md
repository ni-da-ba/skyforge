# Wave C16 — CC:Tweaked wireless infrastructure envelope

**Status:** MERGED / ACCEPTED — PR #264, merge `d63f7c712355fb421c909f7bbdc74465a88522e6`  
**Exact synchronized acceptance head:** `03418c4e941bc394989ca4269a1fbff3fc40cab7`  
**Issue:** #263  
**Parent computing contract:** #227  
**Parent vertical slice:** #224

## Purpose

C14 established the retained CC:Tweaked + Create: Avionics stack as a viable programmable avionics substrate. C16 measures whether CC:Tweaked's stock wireless layer preserves useful geographic infrastructure or creates a universal telemetry bypass.

## Black-box specimen

The fixture boots six real CraftOS computers with real CC modem blocks.

At Y=64:

```text
normal sender       x=0
normal near         x=48
normal far          x=96
```

The normal sender transmits one modem message. The 48-block receiver receives it and the 96-block receiver times out.

A second set uses Ender Modems:

```text
Ender sender        Overworld x=0
Ender remote        Overworld x=512
Ender cross         Nether x=0
```

The 512-block receiver and the cross-dimensional Nether receiver both receive the same real CraftOS modem transmission.

Skyforge calls no CC network internals. The scripts use only the normal Lua modem peripheral surface: `peripheral.find`, `isWireless`, `open`, and `transmit`.

## Accepted runtime evidence

```text
WAVE_C16 PASS
normalNearReceived=true
normalNearDistance=48
normalFarReceived=false
enderRemoteReceived=true
enderRemoteDistance=512
enderCrossReceived=true
enderCrossDistance=nil
```

## Gameplay interpretation

Accepted:

- ordinary Wireless Modems are a useful bounded local/regional infrastructure layer and should remain unnerfed;
- Ender Modems are an explicit mature bypass because they remove same-dimension range and interdimensional separation;
- universal Ender telemetry should not become an unexamined early Bootstrap Province default;
- no replacement Skyforge networking hardware or generic network API is justified.

Not accepted by C16:

- final Ender Modem recipe/progression gating;
- GPS host-placement/navigation policy;
- turtle resource/freight throughput;
- autopilot progression;
- permanent optional-mod version lock.

## Verification

Before merge the exact synchronized head passed:

- Wave C16 wiring/pins and live modem acceptance;
- C2/C3/C5/C6/C7/C9/C10/C13/C14/C15 retained Content regressions;
- repository CI;
- Skyforge showcase and ecology persistence/client-reopen gates;
- SF-IMP-0070 performance characterization.

The measured stock boundary is therefore accepted as the baseline networking behavior for subsequent Content progression work.
