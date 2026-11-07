package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;

final class Health {

    enum State { GOOD, WATCH, ACT }

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

    static double rootShare(Summary s) {
        int total = s.users().values().stream().mapToInt(Integer::intValue).sum();
        return total == 0 ? 0 : (double) s.users().getOrDefault("ROOT", 0) / total;
    }

    static Metric lowerIsBetter(String name, double value, double good, double act) {
        return new Metric(name, value, value <= good ? State.GOOD : value > act ? State.ACT : State.WATCH);
    }

    static Metric higherIsBetter(String name, double value, double good, double act) {
        return new Metric(name, value, value >= good ? State.GOOD : value < act ? State.ACT : State.WATCH);
    }
}
