package app.navgo.core;

import java.util.Objects;

public final class Navigation {
    public enum State { IDLE, NAVIGATING, ARRIVED, UNAVAILABLE }
    public enum Turn { UNKNOWN, STRAIGHT, LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT,
        SHARP_LEFT, SHARP_RIGHT, U_TURN, ROUNDABOUT, MERGE, EXIT }

    public final State state;
    public final Turn turn;
    public final String distance;
    public final String instruction;
    public final String summary;

    public Navigation(State state, Turn turn, String distance, String instruction, String summary) {
        this.state = Objects.requireNonNull(state);
        this.turn = Objects.requireNonNull(turn);
        this.distance = Objects.requireNonNull(distance);
        this.instruction = Objects.requireNonNull(instruction);
        this.summary = Objects.requireNonNull(summary);
    }

    public static Navigation idle() {
        return new Navigation(State.IDLE, Turn.UNKNOWN, "", "Start Google Maps navigation", "");
    }

    public static Navigation unavailable() {
        return new Navigation(State.UNAVAILABLE, Turn.UNKNOWN, "", "Check navigation on phone", "");
    }
}
