package io.github.nidaba.skyforge.neoforge1211;

/**
 * Pure Wave C6 thermal-soaring admission state machine.
 *
 * <p>The decision consumes only an authority bit, physical updraft in metres per second and the
 * server game tick. It deliberately knows nothing about Fowl Play or Aerodynamics4MC classes so
 * the policy can be proven without either optional mod on the test classpath.
 */
final class SkyforgeThermalSoaringDecision {
    static final double ENTER_UPDRAFT_MPS = 1.5;
    static final double EXIT_UPDRAFT_MPS = 0.75;
    static final long MIN_HOLD_TICKS = 100L;

    private SkyforgeThermalSoaringDecision() {}

    record State(boolean soaring, long enteredAtTick) {
        static State inactive() {
            return new State(false, Long.MIN_VALUE);
        }
    }

    static State update(State state, boolean trusted, double updraftMetersPerSecond, long gameTick) {
        if (!trusted || !Double.isFinite(updraftMetersPerSecond)) {
            return State.inactive();
        }

        if (!state.soaring()) {
            return updraftMetersPerSecond >= ENTER_UPDRAFT_MPS
                    ? new State(true, gameTick)
                    : state;
        }

        if (gameTick - state.enteredAtTick() < MIN_HOLD_TICKS) {
            return state;
        }

        return updraftMetersPerSecond <= EXIT_UPDRAFT_MPS ? State.inactive() : state;
    }

    static boolean isStockRaptorHuntWindow(long dayTime) {
        long tick = Math.floorMod(dayTime, 24_000L);
        return (tick >= 1_000L && tick < 6_000L) || (tick >= 8_000L && tick < 11_000L);
    }
}
