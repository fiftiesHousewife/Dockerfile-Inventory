package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

/**
 * The dashboard's headline strip: six estate-wide shares, each good, watch or act. The thresholds were set by the
 * owner (6 Nov): a share is good at or better than its good threshold, act when worse than its act threshold, and
 * watch between.
 */
final class Health {

    enum State { GOOD, WATCH, ACT }

    /** One share and its state. */
    record Metric(String name, double value, State state) {
    }

    private Health() {
    }

    static List<Metric> of(Summary s) {
        return List.of(
                lowerIsBetter("Parse failures", s.parseFailureRate(), 0.01, 0.05),
                lowerIsBetter("Final user root", rootShare(s), 0.10, 0.30),
                higherIsBetter("Bases pinned by digest", s.pinnedShare(), 0.80, 0.20),
                lowerIsBetter("Bases at end of life", s.eolShare(), 0.0, 0.05),
                higherIsBetter("Scanned at a released commit", s.releasedShare(), 0.90, 0.50),
                lowerIsBetter("RUN too complex to read", s.opaqueRunRate(), 0.05, 0.15));
    }

    private static double rootShare(Summary s) {
        int total = s.users().values().stream().mapToInt(Integer::intValue).sum();
        return total == 0 ? 0 : (double) s.users().getOrDefault("ROOT", 0) / total;
    }

    private static Metric lowerIsBetter(String name, double value, double good, double act) {
        return new Metric(name, value, value <= good ? State.GOOD : value > act ? State.ACT : State.WATCH);
    }

    private static Metric higherIsBetter(String name, double value, double good, double act) {
        return new Metric(name, value, value >= good ? State.GOOD : value < act ? State.ACT : State.WATCH);
    }
}
