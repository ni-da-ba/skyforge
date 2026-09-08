# Wave C17 — CC:Tweaked GPS infrastructure topology

**Status:** MERGED / ACCEPTED  
**Issue:** #270  
**PR:** #272  
**Merge:** `65bd49a4e349f16bab8b796aa5b1a1e6dd72a7cc`  
**Final synchronized runtime head:** `9373c307ef6bc79a64643ad0d958f5a09ebe1c9c`  
**Parent computing contract:** #227  
**Parent vertical slice:** #224

## Purpose

C16 established that ordinary Wireless Modems are range-bounded while Ender Modems remove ordinary same-dimension range and dimension separation. C17 tests the stock CraftOS GPS layer that sits on top of those modems.

The question is not whether GPS exists. It is whether normal GPS remains legible physical infrastructure at Skyforge scale or silently grants free universal positioning.

## Black-box specimen

C17 reuses the exact accepted C9 computing runtime and activates only a development acceptance fixture. No new Gradle dependency/runtime profile and no replacement navigation hardware are introduced.

Real CraftOS computers use only:

- `peripheral.find("modem")`;
- stock `gps host x y z`;
- stock `gps.locate`.

### Deterministic host-readiness barrier

The original accepted fixture powered hosts and locators in the same server-start tick. Repeated CI
evidence later showed a narrow nondeterministic failure where the remote Ender locator could begin its
finite locate window before all stock GPS hosts had established their CraftOS listeners.

The repaired fixture boots the six stock GPS hosts first, waits **60 server ticks**, and only then
creates/powers the four locator computers. The locator behavior, host geometry, modem types, stock
CraftOS APIs, timeouts, and acceptance assertions are otherwise unchanged.

This is verification-reliability maintenance, not a change to C17's gameplay conclusion. The barrier
uses only Skyforge's own server-tick scheduling and does not inspect or call ComputerCraft GPS/network
internals.

### No-host case

A real normal-Wireless-Modem locator is isolated in the Nether while every GPS host lives in the Overworld. It must fail to establish a position.

### Ordinary local constellation

Three non-collinear normal-Wireless-Modem GPS hosts are placed at:

```text
(0, 64, 0)
(0, 64, 32)
(32, 64, 0)
```

A locator at `(16, 64, 16)` must recover its physical position. A second locator at `(160, 64, 16)` must fail against the same normal hosts because it lies outside C16's ordinary wireless envelope.

### Mature Ender constellation

Three Ender-Modem GPS hosts form a separate same-dimension constellation near Z=256. An Ender locator at `(512, 80, 272)` must recover its physical position despite being far beyond ordinary Wireless Modem range.

## Acceptance interpretation

If the specimen passes:

- ordinary stock GPS is KEEP: it requires deployed non-collinear host infrastructure within radio range;
- GPS towers/beacons can make geography and aviation navigation more legible without a bespoke Skyforge navigation system;
- Ender-backed GPS inherits C16's mature-bypass status because it removes the ordinary range requirement;
- no generic Skyforge GPS/network implementation is justified.

C17 does not select final tower spacing, Ender recipe/progression gating, turtle navigation/economics, autopilot behavior, or Bootstrap Province structure placement.


## Accepted runtime evidence

```text
noHostFound=false
normalNearFound=true  normalNear=16,64,16
normalFarFound=false
enderRemoteFound=true enderRemote=512,80,272
```

Final exact-head verification also passed retained C10/C13/C14/C15/C16, repository CI, current showcase persistence/reopen jobs, and performance characterization after synchronization to accepted AUTH-0089.
