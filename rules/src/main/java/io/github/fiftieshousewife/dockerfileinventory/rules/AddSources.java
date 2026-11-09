package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.reader.internal.KeyValuePairs;
import java.util.List;

// An ADD or COPY as written on one line: its flags, its sources and its destination, in either form.
record AddSources(List<String> flags, List<String> sources, String destination) {

    AddSources {
        flags = List.copyOf(flags);
        sources = List.copyOf(sources);
    }

    static AddSources of(String add) {
        List<String> words = KeyValuePairs.words(add);
        List<String> flags = words.subList(1, words.size()).stream().takeWhile(w -> w.startsWith("--")).toList();
        List<String> written = words.subList(1 + flags.size(), words.size()).stream()
                .map(word -> KeyValuePairs.unquoted(word.replaceAll("^\\[|[],]$", ""))).toList();
        return written.isEmpty() ? new AddSources(flags, List.of(), null)
                : new AddSources(flags, written.subList(0, written.size() - 1), written.getLast());
    }

    static boolean remote(String source) {
        return source.startsWith("http://") || source.startsWith("https://") || source.startsWith("git@");
    }
}
