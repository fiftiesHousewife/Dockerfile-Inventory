package io.github.fiftieshousewife.dockerfileinventory.facts;

import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.FunctionParens;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.Operator;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.Word;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ShellCommands {

    private final List<List<String>> commands = new ArrayList<>();
    private final ShellCommand command = new ShellCommand();
    private final Map<String, String> assignments = new LinkedHashMap<>();
    private int subshells;
    private int cases;
    private boolean expectingPattern;
    private boolean pipedInto;
    private boolean unreadable;

    private ShellCommands() {
    }

    static Optional<ShellCommands> read(List<ShellToken> tokens) {
        ShellCommands reader = new ShellCommands();
        tokens.stream().takeWhile(token -> !reader.unreadable).forEach(reader::accept);
        reader.endCommand();
        return reader.unreadable ? Optional.empty() : Optional.of(reader);
    }

    List<List<String>> commands() {
        return List.copyOf(commands);
    }

    Map<String, String> assignments() {
        return Map.copyOf(assignments);
    }

    void accept(ShellToken token) {
        switch (token) {
            case Word word -> command.add(word, assignments);
            case FunctionParens parens -> functionParens(parens.name());
            case Operator operator -> operator(operator);
        }
    }

    void functionParens(String name) {
        if (command.isEmpty()) {
            return;
        }
        if (!name.isEmpty()) {
            unreadable = true;
            return;
        }
        operator(Operator.OPEN);
        operator(Operator.CLOSE);
    }

    void operator(Operator operator) {
        boolean inPattern = expectingPattern || command.firstWordIs("case");
        switch (operator) {
            case CLOSE -> close(inPattern);
            case PIPE -> pipe(inPattern);
            case AND_OR -> {
                if (!inPattern) {
                    endCommand();
                }
            }
            case OPEN -> {
                endCommand();
                subshells++;
            }
            case NEWLINE, SEMICOLON -> endCommand();
            case CASE_BREAK -> {
                endCommand();
                expectingPattern = cases > 0;
            }
        }
    }

    void close(boolean inPattern) {
        if (inPattern) {
            cases += command.firstWordIs("case") ? 1 : 0;
            command.clear();
            expectingPattern = false;
        } else if (subshells > 0) {
            endCommand();
            subshells--;
        } else {
            unreadable = true;
        }
    }

    void pipe(boolean inPattern) {
        if (!inPattern) {
            endCommand();
            pipedInto = true;
        }
    }

    void endCommand() {
        command.dropKeywords();
        compoundKeyword();
        if (!command.isEmpty()) {
            unreadable |= ShellKeywords.unreadable(command.first(), pipedInto);
            commands.add(command.take());
            pipedInto = false;
        }
    }

    void compoundKeyword() {
        if (command.firstWordIs("for")) {
            command.forAssignment(assignments).ifPresent(e -> assignments.put(e.getKey(), e.getValue()));
            command.clear();
        } else if (command.firstWordIs("case")) {
            cases++;
            expectingPattern = true;
            command.clear();
        } else if (command.firstWordIs("esac")) {
            cases--;
            expectingPattern = false;
            command.removeFirst();
        }
    }
}
