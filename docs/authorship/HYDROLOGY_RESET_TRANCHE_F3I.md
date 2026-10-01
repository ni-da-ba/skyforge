# Hydrology reset tranche F3I — joint transition assembly

**Status:** machine validation pending  
**Governing authority:** issue #1084  
**Depends on:** F3B/F3C/F3D/F3E and F3H  
**Terrain and Minecraft mutation:** none

## Purpose

F3H proves a narrow confluence/CASCADE head solution, but F3D and F3E previously ignored it. A solved joint QP could not close ordinary-span boundaries or retire the independent transition deferrals in a terminal component. F3I carries the same joint outcome through those two evidence stages.

## Admission contract

A joint solution can replace the independent F3B/F3C evidence only when exactly one F3H candidate owns its confluence node and CASCADE site, the F3B outcome reports CASCADE coupling, the site and leg match the same transition geometry, and the independent F3C outcome has no physical or numerical failure. Other combinations remain on the previous fail-closed path. The span plan records the joint plan it actually used; assembly consumes that recorded plan rather than recomputing a potentially different answer.

For a supported overlap, the confluence node head fixes the coupled reach at the node, the joint ordinary-leg heads fix the other confluence finite boundaries, and the joint CASCADE near/remote heads fix the CASCADE boundaries. F3D solves every remaining ordinary span under its existing pointwise D2 envelopes, directional grade bounds, and geomorphic qualification. F3E may clear a transition blocker only for the admitted joint outcome; ordinary-span rejection, other transition failures, and terminal-fate deferral still propagate.

The generated key-512 geometry does not naturally produce the supported joint overlap. The integration regression uses the explicitly test-local controlled overlap from F3H and checks admitted heads, ordinary-span boundary solving, network blocker accounting, and duplicate-candidate fail-closed behavior. It does not claim production specimen acceptance.

## Boundary

F3I grants no terrain, water, voxel, or Minecraft authority. Key 287 remains rejected under its current D2/corridor contract. More general multi-transition, terminal, or basin coupling remains deferred. Human visual review awaits a qualified Minecraft-discretized key-287 specimen.
