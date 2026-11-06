package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Splits a shell-form {@code RUN} body into simple commands, each a list of words. It understands only the
 * subset Dockerfiles mostly use: words, quotes, backslash escapes and continuations, comments, redirections
 * (dropped with their targets), and the separators newline,
 * {@code &&}, {@code ||}, {@code ;} and pipes. Anything else (control flow, subshells, command substitution, groups,
 * functions, heredocs, a pipe into a shell) makes the whole body opaque (empty), which callers count rather than
 * guess at.
 */
public final class ShellTokeniser {

    /** {@code $NAME} or {@code ${NAME}} in an assigned value, expanded from the assignments before it. */
    private static final Pattern REFERENCE =
            Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}|\\$([A-Za-z_][A-Za-z0-9_]*)");
    /** {@code NAME=value} before a command's name sets its environment; it is not the command. */
    private static final Pattern ASSIGNMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*=.*", Pattern.DOTALL);

    /** A redirection operator, optionally with its target joined on ({@code >/dev/null}, {@code 2>&1}). */
    private static final Pattern REDIRECT = Pattern.compile("\\d*(&>>?|>>?|<)(.*)");

    /** First words that open compound commands this reader does not follow. */
    private static final Set<String> COMPOUND = Set.of(
            "select");

    /**
     * An {@code if}'s keywords, a group's braces and a loop's: dropped, so a condition, every branch and a loop's body
     * are read (the owner's choices, 6 Nov): a package installed on one branch, or in a loop, counts as one the
     * Dockerfile can install.
     */
    private static final Set<String> BRANCHES =
            Set.of("if", "then", "elif", "else", "fi", "{", "}", "while", "until", "do", "done");

    /** A pipe into one of these runs downloaded or generated code nobody can read here. */
    private static final Set<String> SHELLS = Set.of("sh", "bash", "ash", "dash", "zsh", "ksh");

    /**
     * Stands for a dollar that is literal (single-quoted or escaped) while a word is read, so an assignment's value
     * expands only the shell's own references; it is a private-use character, so no script holds it.
     */
    private static final char LITERAL_DOLLAR = '\uE000';

    /** Inside double quotes a backslash escapes only these, as in the shell; before anything else it is kept. */
    private static final String ESCAPED_IN_DOUBLE_QUOTES = "\"\\$`";

    private final String script;
    private final List<List<String>> commands = new ArrayList<>();
    private List<String> command = new ArrayList<>();
    private final StringBuilder word = new StringBuilder();
    private boolean opaque;
    /** Open {@code ( ... )} subshells: their commands are read as the RUN's own. */
    private int subshells;
    /**
     * Open {@code case}s, and whether a pattern ({@code amd64)}, {@code a|b)}) comes next: every branch's commands
     * are read, as an {@code if}'s are, and the patterns are not commands.
     */
    private int cases;
    private boolean expectingPattern;
    private boolean pipedInto;
    private boolean targetNext;
    private boolean wordQuoted;

    private final Map<String, String> assignments = new LinkedHashMap<>();

    private ShellTokeniser(String script) {
        this.script = script;
    }

    public static Optional<List<List<String>>> parse(String script) {
        return new ShellTokeniser(script).run();
    }

    /**
     * The variables a script sets with leading assignments ({@code INSTALL_PKGS="a b" && yum install $INSTALL_PKGS}),
     * quotes removed, later ones winning; empty when the script is opaque.
     */
    public static Map<String, String> assignments(String script) {
        ShellTokeniser tokeniser = new ShellTokeniser(script);
        return tokeniser.run().isPresent() ? Map.copyOf(tokeniser.assignments) : Map.of();
    }

    private Optional<List<List<String>>> run() {
        for (int i = 0; i < script.length() && !opaque; i++) {
            char c = script.charAt(i);
            if (c == '\\') {
                if (next(i) != '\n') {
                    word.append(literal(next(i)));
                }
                i++;
            } else if (c == '\'' || c == '"') {
                i = quoted(i, c);
            } else if (c == '$' && next(i) == '(') {
                i = substitution(i);
            } else if (c == '`') {
                i = backticks(i);
            } else if (c == ')' && (expectingPattern || caseHeader())) {
                cases += caseHeader() ? 1 : 0;
                word.setLength(0);
                command.clear();
                expectingPattern = false;
            } else if (c == '|' && (expectingPattern || caseHeader())) {
                word.append(c);
            } else if (c == '(' && next(i) == ')' && command.isEmpty()) {
                word.setLength(0);
                i++;
            } else if (c == '(' && word.isEmpty()) {
                endCommand();
                subshells++;
            } else if (c == ')' && subshells > 0) {
                endCommand();
                subshells--;
            } else if (c == '(' || c == ')' || (c == '<' && next(i) == '<')) {
                opaque = true;
            } else if (c == '#' && word.isEmpty()) {
                i = script.indexOf('\n', i) < 0 ? script.length() : script.indexOf('\n', i) - 1;
            } else if (c == '\n') {
                endCommand();
            } else if (Character.isWhitespace(c)) {
                endWord();
            } else if (c == ';') {
                endCommand();
                if (next(i) == ';') {
                    expectingPattern = cases > 0;
                    i++;
                }
            } else if ((c == '&' || c == '|') && next(i) == c) {
                endCommand();
                i++;
            } else if (c == '|') {
                endCommand();
                pipedInto = true;
            } else {
                word.append(c);
            }
        }
        endCommand();
        return opaque ? Optional.empty() : Optional.of(List.copyOf(commands));
    }

    /**
     * Appends a quoted span to the current word, without its quotes; returns the index of the closing quote. Inside
     * double quotes a backslash-newline is removed, as the shell removes it; inside single quotes nothing changes.
     */
    private int quoted(int open, char quote) {
        if (quote == '\'') {
            int close = script.indexOf(quote, open + 1);
            if (close < 0) {
                opaque = true;
                return script.length();
            }
            word.append(script.substring(open + 1, close).replace('$', LITERAL_DOLLAR));
            wordQuoted = true;
            return close;
        }
        for (int i = open + 1; i < script.length(); i++) {
            char c = script.charAt(i);
            if (c == '"') {
                wordQuoted = true;
                return i;
            } else if (c == '\\' && next(i) == '\n') {
                i++;
            } else if (c == '\\' && ESCAPED_IN_DOUBLE_QUOTES.indexOf(next(i)) >= 0) {
                word.append(literal(next(i)));
                i++;
            } else if (c == '\\' && i + 1 == script.length()) {
                break;
            } else if (c == '$' && next(i) == '(') {
                int close = substitutionEnd(i);
                if (close < 0) {
                    break;
                }
                word.append(script, i, close + 1);
                i = close;
            } else if (c == '`') {
                int close = script.indexOf('`', i + 1);
                if (close < 0) {
                    break;
                }
                word.append(script, i, close + 1);
                i = close;
            } else {
                word.append(c);
            }
        }
        opaque = true;
        return script.length();
    }

    /**
     * Appends a command substitution {@code $( ... )}, nested or quoted inside, to the current word as written: one
     * word whose value is not known here. Returns the index of its closing parenthesis; unclosed is opaque.
     */
    private int substitution(int open) {
        int close = substitutionEnd(open);
        if (close < 0) {
            opaque = true;
            return script.length();
        }
        word.append(script, open, close + 1);
        return close;
    }

    /** The index of the parenthesis closing the substitution opened at {@code open}, or -1. */
    private int substitutionEnd(int open) {
        int depth = 0;
        for (int i = open + 1; i < script.length(); i++) {
            char c = script.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '\'' || c == '"') {
                int close = script.indexOf(c, i + 1);
                if (close < 0) {
                    break;
                }
                i = close;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** As {@link #substitution}, for the older backtick form. */
    private int backticks(int open) {
        int close = script.indexOf('`', open + 1);
        if (close < 0) {
            opaque = true;
            return script.length();
        }
        word.append(script, open, close + 1);
        return close;
    }

    /** A character taken as itself: a dollar marked so that assignments do not expand it. */
    private static char literal(char c) {
        return c == '$' ? LITERAL_DOLLAR : c;
    }

    /**
     * {@code for NAME in WORDS}: the loop variable takes the words, so {@code $NAME} in the body expands to them;
     * unknown when a word is a glob or command output, whose values only the build knows.
     */
    private void forList(List<String> header) {
        if (header.size() < 3 || !header.get(2).equals("in")) {
            return;
        }
        List<String> words = header.subList(3, header.size());
        if (words.stream().noneMatch(w -> w.contains("*") || w.contains("?") || PackageCalls.substituted(w))) {
            assignments.put(header.get(1), expanded(String.join(" ", words)));
        }
    }

    /** Inside {@code case WORD in}, before its first pattern has closed. */
    private boolean caseHeader() {
        return !command.isEmpty() && command.getFirst().equals("case");
    }

    private char next(int i) {
        return i + 1 < script.length() ? script.charAt(i + 1) : 0;
    }

    private void endWord() {
        if (!word.isEmpty()) {
            String marked = word.toString();
            String w = marked.replace(LITERAL_DOLLAR, '$');
            boolean quoted = wordQuoted;
            word.setLength(0);
            wordQuoted = false;
            var redirect = REDIRECT.matcher(w);
            if (targetNext) {
                targetNext = false;
            } else if (!quoted && redirect.matches()) {
                targetNext = redirect.group(2).isEmpty();
            } else if (BRANCHES.containsAll(command) && ASSIGNMENT.matcher(w).matches()) {
                assignments.put(w.substring(0, w.indexOf('=')),
                        expanded(marked.substring(marked.indexOf('=') + 1)).replace(LITERAL_DOLLAR, '$'));
            } else {
                command.add(w);
            }
        }
    }

    /** A value with earlier assignments substituted, so {@code A="$A more"} keeps what {@code A} held. */
    private String expanded(String value) {
        return REFERENCE.matcher(value).replaceAll(m -> java.util.regex.Matcher.quoteReplacement(
                assignments.getOrDefault(m.group(1) != null ? m.group(1) : m.group(2), m.group())));
    }

    private void endCommand() {
        endWord();
        while (!command.isEmpty() && BRANCHES.contains(command.getFirst())) {
            command.removeFirst();
        }
        if (command.size() >= 2 && command.getFirst().equals("function")) {
            command.subList(0, command.size() > 2 && command.get(2).equals("{") ? 3 : 2).clear();
        }
        if (!command.isEmpty() && command.getFirst().equals("for")) {
            forList(command);
            command.clear();
        }
        if (!command.isEmpty() && command.getFirst().equals("case")) {
            cases++;
            expectingPattern = true;
            command.clear();
        } else if (!command.isEmpty() && command.getFirst().equals("esac")) {
            cases--;
            expectingPattern = false;
            command.removeFirst();
        }
        if (!command.isEmpty()) {
            if (COMPOUND.contains(command.getFirst()) || (pipedInto && SHELLS.contains(command.getFirst()))) {
                opaque = true;
            }
            commands.add(List.copyOf(command));
            command = new ArrayList<>();
            pipedInto = false;
        }
    }
}
