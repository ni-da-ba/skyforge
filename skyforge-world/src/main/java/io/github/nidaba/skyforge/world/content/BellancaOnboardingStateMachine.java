package io.github.nidaba.skyforge.world.content;

import java.util.Objects;

/** CONTENT C27 authoritative semantic state for the Bellanca B0-A onboarding transaction. */
public final class BellancaOnboardingStateMachine {
    public enum WreckIdentity {
        BELLANCA_B0_A
    }

    public enum State {
        CRASHED_BELLANCA,
        INITIAL_CLAIM_UNFAVORABLE,
        GUILD_LIABILITY_ESTABLISHED,
        TUTORIAL_COMPLETE
    }

    public enum Restitution {
        STANDARDIZED_BELLANCA_REPLACEMENT,
        SCRIP_PAYOUT,
        RETAIN_WRECK_WITH_PARTIAL_PAYOUT
    }

    public record Snapshot(
            WreckIdentity wreckIdentity, State state, boolean recorderRecovered, Restitution restitution) {
        public Snapshot {
            wreckIdentity = Objects.requireNonNull(wreckIdentity, "wreckIdentity");
            state = Objects.requireNonNull(state, "state");
            if (state == State.TUTORIAL_COMPLETE && restitution == null) {
                throw new IllegalArgumentException("tutorial completion requires exactly one restitution outcome");
            }
            if (state != State.TUTORIAL_COMPLETE && restitution != null) {
                throw new IllegalArgumentException("restitution cannot be selected before tutorial completion");
            }
        }

        public boolean tutorialComplete() {
            return state == State.TUTORIAL_COMPLETE;
        }
    }

    public Snapshot crash() {
        return new Snapshot(WreckIdentity.BELLANCA_B0_A, State.CRASHED_BELLANCA, false, null);
    }

    /** Recorder recovery is deliberately possible before or after the initial unfavorable claim. */
    public Snapshot recoverRecorder(Snapshot snapshot) {
        snapshot = requireActive(snapshot);
        return new Snapshot(snapshot.wreckIdentity(), snapshot.state(), true, null);
    }

    public Snapshot initiateClaim(
            Snapshot snapshot, BootstrapGuildDestinationPolicy.Candidate hall) {
        snapshot = require(snapshot, State.CRASHED_BELLANCA);
        hall = Objects.requireNonNull(hall, "hall");
        if (!hall.resolvesBellancaClaim()) {
            throw new IllegalArgumentException("Bellanca claim requires an eligible Guild Hall");
        }
        return new Snapshot(snapshot.wreckIdentity(), State.INITIAL_CLAIM_UNFAVORABLE, snapshot.recorderRecovered(), null);
    }

    public Snapshot submitRecorderEvidence(Snapshot snapshot) {
        snapshot = require(snapshot, State.INITIAL_CLAIM_UNFAVORABLE);
        if (!snapshot.recorderRecovered()) {
            throw new IllegalStateException("recorder evidence must be recovered before submission");
        }
        return new Snapshot(snapshot.wreckIdentity(), State.GUILD_LIABILITY_ESTABLISHED, true, null);
    }

    /** A single transition stores one enum value, so restitution outcomes are mutually exclusive. */
    public Snapshot chooseRestitution(Snapshot snapshot, Restitution restitution) {
        require(snapshot, State.GUILD_LIABILITY_ESTABLISHED);
        return new Snapshot(snapshot.wreckIdentity(), State.TUTORIAL_COMPLETE, true, Objects.requireNonNull(restitution, "restitution"));
    }

    private static Snapshot requireActive(Snapshot snapshot) {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.tutorialComplete()) {
            throw new IllegalStateException("Bellanca onboarding is already complete");
        }
        return snapshot;
    }

    private static Snapshot require(Snapshot snapshot, State required) {
        snapshot = requireActive(snapshot);
        if (snapshot.state() != required) {
            throw new IllegalStateException("expected " + required + " but was " + snapshot.state());
        }
        return snapshot;
    }
}
