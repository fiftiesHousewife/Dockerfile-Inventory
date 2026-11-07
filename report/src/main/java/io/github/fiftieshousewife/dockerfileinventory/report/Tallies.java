package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Map;
import java.util.TreeMap;

final class Tallies {

    // How facts' FactsParser.TEMPLATE starts a template's failure message.
    static final String TEMPLATE = "a template, not a Dockerfile";

    private Tallies() {
    }

    static Map<String, Integer> managers(Estate estate) {
        Map<String, Integer> managers = new TreeMap<>();
        estate.dockerfiles().forEach(d ->
                d.managers().forEach(m -> managers.merge(m, d.locations().size(), Integer::sum)));
        return managers;
    }

    static Map<String, Integer> users(Estate estate) {
        Map<String, Integer> users = new TreeMap<>();
        estate.dockerfiles().forEach(d -> {
            UserKind effective = estate.effectiveUsers().get(d.blobSha());
            users.merge(effective == null ? d.userKind() : effective.name(), d.locations().size(), Integer::sum);
        });
        return users;
    }
}
