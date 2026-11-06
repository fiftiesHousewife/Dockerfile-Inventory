package io.github.fiftieshousewife.dockerfileinventory.facts;

import io.github.fiftieshousewife.dockerfileinventory.facts.ShellWords.FunctionParens;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellWords.Operator;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellWords.Token;
import io.github.fiftieshousewife.dockerfileinventory.facts.ShellWords.Word;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The second half of reading a shell-form {@code RUN}: words and operators to simple commands, and the variables
 * the script assigns. Compound commands are read for the commands inside them (the owner's choices, 6 Nov): an
 * {@code if}'s condition and every branch, every {@code case} branch without its patterns, a loop's body once with a
 * {@code for} variable taking its list, a group's or subshell's commands, and a function's body where it is defined.
 * A pipe into a shell runs code nobody can read here, so it makes the script unreadable, as {@code select} does.
 */
final class ShellCommands {

    /** {@code NAME=value} before a command's name sets its environment; it is not the command. */
    private static final Pattern ASSIGNMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*=.*", Pattern.DOTALL);
    /** A redirection operator, optionally with its target joined on ({@code >/dev/null}, {@code 2>&1}). */
    private static final Pattern REDIRECT = Pattern.compile("\\d*(&>>?|>>?|<)(.*)");
    /** An {@code if}'s keywords, a group's braces and a loop's: dropped, so what they hold is read. */
    private static final Set<String> BRANCHES =
            Set.of("if", "then", "elif", "else", "fi", "{", "}", "while", "until", "do", "done");
    /** First words that open compound commands this reader does not follow. */
    private static final Set<String> UNREAD = Set.of("select");
    /** A pipe into one of these runs downloaded or generated code. */
    private static final Set<String> SHELLS = Set.of("sh", "bash", "ash", "dash", "zsh", "ksh");

    private final List<List<String>> commands = new ArrayList<>();
    private final List<String> command = new ArrayList<>();
    private final Map<String, String> assignments = new LinkedHashMap<>();
    /** Open {@code ( ... )} subshells, whose commands are read as the script's own. */
    private int subshells;
    /** Open {@code case}s, and whether a pattern ({@code amd64)}, {@code a|b)}) comes next. */
    private int cases;
    private boolean expectingPattern;
    private boolean pipedInto;
    private boolean redirectTargetNext;
    private boolean unreadable;

    private ShellCommands() {
    }

    static Optional<ShellCommands> read(List<Token> tokens) {
        ShellCommands reader = new ShellCommands();
        tokens.stream().takeWhile(token -> !reader.unreadable).forEach(reader::accept);
        reader.endCommand();
        return reader.unreadable ? Optional.empty() : Optional.of(reader);
    }

    List<List<String>> commands() {
        return List.copyOf(commands);
    }

    /** Leading assignments' values, quotes removed and earlier assignments expanded, later ones winning. */
    Map<String, String> assignments() {
        return Map.copyOf(assignments);
    }

    private void accept(Token token) {
        switch (token) {
            case Word word -> word(word);
            case FunctionParens parens -> functionParens(parens.name());
            case Operator operator -> operator(operator);
        }
    }

    private void word(Word word) {
        String text = word.text();
        Matcher redirect = REDIRECT.matcher(text);
        if (redirectTargetNext) {
            redirectTargetNext = false;
        } else if (!word.quoted() && redirect.matches()) {
            redirectTargetNext = redirect.group(2).isEmpty();
        } else if (BRANCHES.containsAll(command) && ASSIGNMENT.matcher(text).matches()) {
            assign(word.marked());
        } else {
            command.add(text);
        }
    }

    private void assign(String marked) {
        String value = marked.substring(marked.indexOf('=') + 1);
        assignments.put(marked.substring(0, marked.indexOf('=')),
                Expansions.expanded(value, assignments).replace(ShellWords.LITERAL_DOLLAR, '$'));
    }

    /** {@code name()} at the start of a command defines a function, whose body follows; elsewhere {@code ()}. */
    private void functionParens(String name) {
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

    private void operator(Operator operator) {
        boolean inPattern = expectingPattern || inCaseHeader();
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

    /** A {@code )} ends a {@code case} pattern or a subshell; any other is something this reader does not follow. */
    private void close(boolean inPattern) {
        if (inPattern) {
            cases += inCaseHeader() ? 1 : 0;
            command.clear();
            expectingPattern = false;
        } else if (subshells > 0) {
            endCommand();
            subshells--;
        } else {
            unreadable = true;
        }
    }

    /** In a {@code case} pattern a {@code |} separates alternatives, which are dropped with the pattern. */
    private void pipe(boolean inPattern) {
        if (!inPattern) {
            endCommand();
            pipedInto = true;
        }
    }

    /** Inside {@code case WORD in}, before its first pattern has closed. */
    private boolean inCaseHeader() {
        return !command.isEmpty() && command.getFirst().equals("case");
    }

    private void endCommand() {
        dropLeadingKeywords();
        dropFunctionHeader();
        if (firstWordIs("for")) {
            forList();
            command.clear();
        } else if (firstWordIs("case")) {
            cases++;
            expectingPattern = true;
            command.clear();
        } else if (firstWordIs("esac")) {
            cases--;
            expectingPattern = false;
            command.removeFirst();
        }
        if (!command.isEmpty()) {
            unreadable |= UNREAD.contains(command.getFirst()) || pipedInto && SHELLS.contains(command.getFirst());
            commands.add(List.copyOf(command));
            command.clear();
            pipedInto = false;
        }
    }

    private void dropLeadingKeywords() {
        while (!command.isEmpty() && BRANCHES.contains(command.getFirst())) {
            command.removeFirst();
        }
    }

    /** {@code function name} or {@code function name {}: the body's first command follows on the same line. */
    private void dropFunctionHeader() {
        if (command.size() >= 2 && command.getFirst().equals("function")) {
            command.subList(0, command.size() > 2 && command.get(2).equals("{") ? 3 : 2).clear();
        }
    }

    /**
     * {@code for NAME in WORDS}: the loop variable takes the words, so {@code $NAME} in the body expands to them;
     * unknown when a word is a glob or command output, whose values only the build knows.
     */
    private void forList() {
        if (command.size() < 3 || !command.get(2).equals("in")) {
            return;
        }
        List<String> words = command.subList(3, command.size());
        if (words.stream().noneMatch(ShellCommands::onlyTheBuildKnows)) {
            assignments.put(command.get(1), Expansions.expanded(String.join(" ", words), assignments));
        }
    }

    /** A glob or a command's output, whose words only the build knows. */
    private static boolean onlyTheBuildKnows(String word) {
        return word.contains("*") || word.contains("?") || Expansions.holdsCommandSubstitution(word);
    }

    private boolean firstWordIs(String keyword) {
        return !command.isEmpty() && command.getFirst().equals(keyword);
    }
}
