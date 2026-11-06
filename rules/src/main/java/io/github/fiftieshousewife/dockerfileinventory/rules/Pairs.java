package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.Steps;
import java.util.List;

/**
 * Keys of {@code key=value} pairs read from text. rewrite-docker 8.90.4 folds {@code LABEL a=$X b=$Y} into one pair
 * {@code a}, so label keys are read from the printed instruction instead, with facts' reader ({@link Steps#keys}),
 * so a quoting fix is made once.
 */
final class Pairs {

    private Pairs() {
    }

    static List<String> keys(String pairs) {
        return Steps.keys(pairs);
    }
}
