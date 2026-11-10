package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;
import java.util.Map;
import java.util.Objects;

// What an image starts: its entrypoint, then its command. An archetype is one recipe; the programs it starts say how
// many different things that recipe builds.
final class Programs {

    record Split(List<String> same, List<String> other) {

        Split {
            same = List.copyOf(same);
            other = List.copyOf(other);
        }
    }

    private Programs() {
    }

    static String of(CatalogueEntry.Command entrypoint, CatalogueEntry.Command cmd) {
        String program = String.join(" ", java.util.stream.Stream.of(entrypoint, cmd).filter(Objects::nonNull)
                .flatMap(command -> command.arguments().stream()).toList());
        return program.isEmpty() ? "its base's" : program;
    }

    static String of(CatalogueEntry entry) {
        return entry.image() == null ? null : of(entry.image().entrypoint(), entry.image().cmd());
    }

    static int distinct(List<String> blobShas, Map<String, String> programs) {
        return (int) blobShas.stream().map(programs::get).filter(Objects::nonNull).distinct().count();
    }

    static Split split(String program, List<String> others, Map<String, String> programs) {
        return new Split(others.stream().filter(sha -> Objects.equals(program, programs.get(sha))).toList(),
                others.stream().filter(sha -> !Objects.equals(program, programs.get(sha))).toList());
    }
}
