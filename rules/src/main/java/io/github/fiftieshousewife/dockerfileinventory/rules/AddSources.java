package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.reader.internal.KeyValuePairs;
import java.util.List;

// An ADD as written on one line: its flags and its sources, in either form.
record AddSources(List<String> flags, List<String> sources) {

    AddSources {
        flags = List.copyOf(flags);
        sources = List.copyOf(sources);
    }

    static AddSources of(String add) {
        List<String> words = KeyValuePairs.words(add);
        List<String> flags = words.subList(1, words.size()).stream().takeWhile(w -> w.startsWith("--")).toList();
        List<String> written = words.subList(1 + flags.size(), words.size()).stream()
                .map(word -> KeyValuePairs.unquoted(word.replaceAll("^\\[|[],]$", ""))).toList();
        return new AddSources(flags, written.isEmpty() ? List.of() : written.subList(0, written.size() - 1));
    }

    static boolean remote(String source) {
        return source.startsWith("http://") || source.startsWith("https://") || source.startsWith("git@");
    }
}
