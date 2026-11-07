package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;

import io.github.fiftieshousewife.dockerfileinventory.report.Figures.Count;
import io.github.fiftieshousewife.dockerfileinventory.report.Health.Metric;
import io.github.fiftieshousewife.dockerfileinventory.report.Health.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class AppliesTo {

    static final double DIFFERENCE = 0.20;
    static final double DOMINANT = 0.5;
    private static final int FAMILIES_NAMED = 3;
    static final Set<String> NOT_BASES = Set.of("scratch", BaseCatalogue.UNRESOLVED);

    private static final Map<String, String> PHRASES = Map.of(
            Health.PARSE_FAILURES, "of files could not be parsed",
            Health.ROOT, "of locations run as root",
            Health.PINNED, "of locations pin their base by digest",
            Health.EOL, "of locations build on an end-of-life base",
            Health.RELEASED, "of locations were scanned at a released commit",
            Health.OPAQUE, "of RUN instructions are too complex to read");

    private AppliesTo() {
    }

    static List<String> sentences(Figures organisation, Figures estate, List<Figures> organisations) {
        List<String> sentences = new ArrayList<>();
        if (organisation.bundles().locations() > 0) {
            sentences.add(count(organisation.bundles().locations()) + " locations are metadata-only bundles, left "
                    + "out of the user, base and archetype figures.");
        }
        dominant(organisation).ifPresent(sentences::add);
        Health.of(organisation).forEach(m -> compared(m, estate.counts().get(m.name())).ifPresent(sentences::add));
        alone(organisation, organisations).ifPresent(sentences::add);
        return sentences;
    }

    static Optional<String> dominant(Figures organisation) {
        double share = organisation.locations() == 0 ? 0
                : (double) organisation.largestRepositoryLocations() / organisation.locations();
        return organisation.repositories() > 1 && share >= DOMINANT
                ? Optional.of("One repository, " + organisation.largestRepository() + ", holds " + percent(share)
                        + " of the locations.")
                : Optional.empty();
    }

    static Optional<String> compared(Metric metric, Count estate) {
        if (metric.state() == State.NO_DATA || estate == null || !estate.known()) {
            return Optional.empty();
        }
        State estateState = Health.metric(metric.name(), estate).state();
        double value = metric.count().share();
        boolean differs = estateState != metric.state() || Math.abs(value - estate.share()) > DIFFERENCE;
        return differs ? Optional.of(percent(value) + " " + PHRASES.get(metric.name()) + " (" + metric.state().word()
                + "), against " + percent(estate.share()) + " across the estate.") : Optional.empty();
    }

    static Optional<String> alone(Figures organisation, List<Figures> organisations) {
        Set<String> elsewhere = organisations.stream().filter(f -> !f.name().equals(organisation.name()))
                .flatMap(f -> f.families().keySet().stream()).collect(Collectors.toSet());
        List<Map.Entry<String, Integer>> only = organisation.families().entrySet().stream()
                .filter(e -> !elsewhere.contains(e.getKey()) && !NOT_BASES.contains(e.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();
        if (only.isEmpty()) {
            return Optional.empty();
        }
        String named = only.stream().limit(FAMILIES_NAMED)
                .map(e -> e.getKey() + " (" + count(e.getValue()) + (e.getValue() == 1 ? " location)" : " locations)"))
                .collect(Collectors.joining(", "));
        return Optional.of("The only organisation building on " + named
                + (only.size() > FAMILIES_NAMED ? " and " + count(only.size() - FAMILIES_NAMED) + " more families" : "")
                + ".");
    }
}
