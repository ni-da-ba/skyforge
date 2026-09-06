# Skyforge Wave C2 Personal-Mobility Datapack

Development-only Minecraft 1.21.1 datapack fallback for the Wave C2 early-glider recipe and maintenance closure.

It mirrors the conditional `reliable_gliders:glider` recipe placed in Skyforge's development
resource set. The prototype preserves the upstream recipe shape and leather/stick frame while
replacing the two Phantom Membranes with the ordinary `minecraft:wool` item tag. It also replaces the upstream Phantom-only repair tag with ordinary leather and wool.

This pack intentionally does **not** override Reliable Gliders' `updraft_blocks` tag. The first
acceptance pass must test the upstream fire/campfire/lava/magma behavior and default strength/height
before Skyforge tunes it.

Do not ship this development fixture as a production dependency without completing Wave C2
gameplay acceptance.
