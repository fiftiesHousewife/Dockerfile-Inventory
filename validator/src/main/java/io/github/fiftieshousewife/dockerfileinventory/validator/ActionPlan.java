package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ActionPlan {

    private ActionPlan() {
    }

    static Map<String, Object> summary(Profile profile, Map<String, Verdict> verdicts, int notRunnable,
                                       List<Action> actions, int fixes) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("profile", profile.name());
        summary.put("describes", profile.describes());
        summary.put("images", verdicts.size());
        summary.put("notRunnable", notRunnable);
        summary.put("verdicts", counts(verdicts.values().stream()));
        summary.put("actions", actions.size());
        summary.put("fixes", fixes);
        summary.put("repositories", Actions.byRepository(actions).entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().size(), (a, b) -> a, LinkedHashMap::new)));
        return summary;
    }

    static Map<String, Long> counts(Stream<Verdict> verdicts) {
        Map<Verdict, Long> counted = verdicts.collect(Collectors.groupingBy(verdict -> verdict,
                () -> new EnumMap<>(Verdict.class), Collectors.counting()));
        return counted.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name().toLowerCase(Locale.ROOT),
                Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }
}
