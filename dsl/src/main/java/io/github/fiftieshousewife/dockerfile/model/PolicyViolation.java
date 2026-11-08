package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;
import java.util.stream.Collectors;

/** Every invariant an image breaks, each with its Phase 3 rule ID, so one build shows everything to fix. */
public final class PolicyViolation extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** One breach: the rule's ID and what to change. */
    public record Breach(String rule, String message) {
    }

    private final transient List<Breach> breaches;

    /** A failed build, with every breach it found. */
    public PolicyViolation(List<Breach> breaches) {
        super(breaches.stream().map(b -> b.rule() + ": " + b.message()).collect(Collectors.joining("; ")));
        this.breaches = List.copyOf(breaches);
    }

    /** Every breach the build found, so one run shows everything to fix. */
    public List<Breach> breaches() {
        return breaches;
    }
}
