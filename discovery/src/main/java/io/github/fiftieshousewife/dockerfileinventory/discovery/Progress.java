package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.time.Duration;
import java.time.Instant;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class Progress {

    enum Phase {
        LISTING("listing organisations' repositories"), SCANNING("scanning repositories"),
        FETCHING("fetching Dockerfiles"), YAML("fetching YAML"), CHANGES("reading last changes"),
        ASKING("asking whether to go on");

        private final String words;

        Phase(String words) {
            this.words = words;
        }
    }

    private record State(Phase phase, int total, int done, int found) {
    }

    private final LongSupplier calls;
    private final Supplier<Instant> clock;
    private final Instant started;
    private State state = new State(Phase.LISTING, 0, 0, 0);

    Progress(LongSupplier calls, Supplier<Instant> clock) {
        this.calls = calls;
        this.clock = clock;
        this.started = clock.get();
    }

    synchronized void start(Phase phase, int total) {
        state = new State(phase, total, 0, state.found());
    }

    synchronized void done(int done, int found) {
        state = new State(state.phase(), state.total(), state.done() + done, state.found() + found);
    }

    synchronized boolean asking() {
        return state.phase() == Phase.ASKING;
    }

    synchronized String line() {
        return "discovery: " + state.phase().words + ", " + state.done() + " of " + state.total() + ", "
                + state.found() + " Dockerfiles found, " + calls.getAsLong() + " calls, "
                + elapsed(Duration.between(started, clock.get())) + " in";
    }

    static String elapsed(Duration time) {
        String seconds = time.toSecondsPart() + "s";
        if (time.toMinutes() == 0) {
            return seconds;
        }
        String minutes = time.toMinutesPart() + "m " + seconds;
        return time.toHours() == 0 ? minutes : time.toHours() + "h " + minutes;
    }
}
