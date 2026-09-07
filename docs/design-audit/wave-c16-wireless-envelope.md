# Wave C16 — CC:Tweaked wireless infrastructure envelope

**Status:** IN PROGRESS until exact-head runtime acceptance is green and merged  
**Issue:** #263  
**Parent computing contract:** #227  
**Parent vertical slice:** #224

## Purpose

C14 established that the retained CC:Tweaked + Create: Avionics stack is a viable programmable
avionics substrate. C16 measures whether CC:Tweaked's stock wireless layer preserves useful geographic
infrastructure or creates a universal telemetry bypass.

## Black-box specimen

The fixture boots six real CraftOS computers with real CC modem blocks.

At Y=64:

```text
normal sender       x=0
normal near         x=48
normal far          x=96
```

The normal sender transmits one modem message. Acceptance requires the 48-block receiver to receive it
and the 96-block receiver to time out.

A second pair uses Ender Modems:

```text
Ender sender        Overworld x=0
Ender remote        Overworld x=512
Ender cross         Nether x=0
```

The 512-block receiver and the cross-dimensional Nether receiver must both receive the same real
CraftOS modem transmission.

Skyforge calls no CC network internals. The only integration surface exercised by the scripts is the
normal Lua modem peripheral: `peripheral.find`, `isWireless`, `open`, and `transmit`.

## Gameplay interpretation

If the stock boundary is confirmed:

- ordinary Wireless Modems are suitable local/regional infrastructure and should remain unnerfed;
- Ender Modems are an explicit mature bypass because they remove same-dimension range and
  interdimensional separation;
- universal Ender telemetry should not become an unexamined early Bootstrap Province default;
- a later progression/configuration decision may recipe-gate or reserve Ender Modems, but C16 does
  not invent replacement networking hardware.

GPS host placement, turtle throughput, and autopilot progression remain separate #227 questions.
