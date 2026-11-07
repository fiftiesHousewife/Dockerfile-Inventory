package io.github.fiftieshousewife.dockerfileinventory.report;

import io.github.fiftieshousewife.dockerfileinventory.report.Figures.Count;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class Health {

    static final String PARSE_FAILURES = "Parse failures";
    static final String ROOT = "Final user root";
    static final String PINNED = "Bases pinned by digest";
    static final String EOL = "Bases at end of life";
    static final String RELEASED = "Scanned at a released commit";
    static final String OPAQUE = "RUN too complex to read";
    static final List<String> NAMES = List.of(PARSE_FAILURES, ROOT, PINNED, EOL, RELEASED, OPAQUE);

    enum State {
        GOOD("good"), WATCH("watch"), ACT("act"), NO_DATA("no data");

        private final String word;

        State(String word) {
            this.word = word;
        }

        String word() {
            return word;
        }
    }

    record Metric(String name, Count count, State state) {
    }

    // How many of an organisation's locations (files for parse failures, RUNs for complexity) sit on the wrong side.
    record Contributor(String organisation, int count, int whole) {
    }

    record Rule(boolean higherIsBetter, double good, double act) {

        int wrongSide(Count count) {
            return higherIsBetter ? count.whole() - count.part() : count.part();
        }
    }

    static final Map<String, Rule> RULES = Map.of(
            PARSE_FAILURES, new Rule(false, 0.01, 0.05),
            ROOT, new Rule(false, 0.10, 0.30),
            PINNED, new Rule(true, 0.80, 0.20),
            EOL, new Rule(false, 0.0, 0.05),
            RELEASED, new Rule(true, 0.90, 0.50),
            OPAQUE, new Rule(false, 0.05, 0.15));

    private Health() {
    }

    static List<Metric> of(Figures figures) {
        return figures.counts().entrySet().stream().map(e -> metric(e.getKey(), e.getValue())).toList();
    }

    static Metric metric(String name, Count count) {
        Rule rule = RULES.get(name);
        State state = !count.known() ? State.NO_DATA
                : rule.higherIsBetter() ? higherIsBetter(count.share(), rule.good(), rule.act())
                : lowerIsBetter(count.share(), rule.good(), rule.act());
        return new Metric(name, count, state);
    }

    static Optional<Contributor> contributor(String name, List<Figures> organisations) {
        Rule rule = RULES.get(name);
        Map<String, Integer> wrong = organisations.stream()
                .filter(f -> f.counts().containsKey(name) && f.counts().get(name).known())
                .collect(Collectors.toMap(Figures::name, f -> rule.wrongSide(f.counts().get(name))));
        int whole = wrong.values().stream().mapToInt(Integer::intValue).sum();
        return wrong.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(
                        Comparator.reverseOrder())))
                .map(e -> new Contributor(e.getKey(), e.getValue(), whole));
    }

    static State lowerIsBetter(double value, double good, double act) {
        return value <= good ? State.GOOD : value > act ? State.ACT : State.WATCH;
    }

    static State higherIsBetter(double value, double good, double act) {
        return value >= good ? State.GOOD : value < act ? State.ACT : State.WATCH;
    }
}
