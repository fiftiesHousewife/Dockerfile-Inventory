package io.github.fiftieshousewife.dockerfileinventory.survey;

import java.util.List;

record Arguments(List<String> urls, boolean trial) {

    static final String USAGE = "usage: survey [--try] <organisation URL>..., e.g. survey --try "
            + "https://github.com/acme";

    Arguments {
        urls = List.copyOf(urls);
    }

    static Arguments of(String... args) {
        List<String> words = List.of(args);
        boolean trial = words.contains("--try");
        List<String> rest = words.stream().filter(word -> !word.equals("--try")).toList();
        if (rest.isEmpty() || rest.stream().anyMatch(word -> word.startsWith("-"))) {
            throw new IllegalArgumentException(USAGE);
        }
        return new Arguments(rest, trial);
    }
}
