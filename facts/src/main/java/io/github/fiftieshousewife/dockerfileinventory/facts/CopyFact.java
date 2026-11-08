package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;

/**
 * A {@code COPY} or {@code ADD}: its sources and destination, where its files come from ({@code context},
 * {@code stage}, {@code image}, {@code url} or {@code heredoc}) and the stage or image named, its owner and mode.
 */
public record CopyFact(String instruction, List<String> sources, String destination, String origin, String from,
                       String chown, String chmod) {

    public CopyFact {
        sources = List.copyOf(sources);
    }
}
