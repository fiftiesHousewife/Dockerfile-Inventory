package io.github.fiftieshousewife.dockerfile.reader.internal;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

public final class Values {

    private static final Pattern BARE_VARIABLE = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

    private Values() {
    }

    public static String braced(String value) {
        return BARE_VARIABLE.matcher(value).replaceAll("\\${$1}");
    }

    public static String text(Docker.Argument argument) {
        return argument.getContents().stream()
                .map(c -> c instanceof Docker.Literal l ? l.getText()
                        : "${" + ((Docker.EnvironmentVariable) c).getName() + "}")
                .collect(Collectors.joining());
    }

    // The parser keeps a literal's text without its quotes.
    public static String written(Docker.Argument argument) {
        return argument.getContents().stream()
                .map(c -> c instanceof Docker.Literal l ? quoted(l)
                        : "${" + ((Docker.EnvironmentVariable) c).getName() + "}")
                .collect(Collectors.joining());
    }

    public static String quoted(Docker.Literal literal) {
        return switch (literal.getQuoteStyle()) {
            case DOUBLE -> "\"" + literal.getText() + "\"";
            case SINGLE -> "'" + literal.getText() + "'";
            case null, default -> literal.getText();
        };
    }

    public static List<Docker.Flag> flags(List<Docker.Flag> flags) {
        return flags == null ? List.of() : flags;
    }

    public static String flagName(Docker.Flag flag) {
        return flag.getName().replaceFirst("^-+", "");
    }

    public static String flagValue(Docker.Flag flag) {
        Docker.Argument value = flag.getValue();
        return value == null ? "" : written(value);
    }

    public static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
