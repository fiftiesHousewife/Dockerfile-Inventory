package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What counts as a secret, shared by rule S2 and the report (which must never write one out): a name that says so,
 * or a value shaped like one whatever it is called. Here, not in rules, because the report depends on no module that
 * imports OpenRewrite.
 */
public final class Secrets {

    private static final List<String> SECRET_WORDS = List.of("PASSWORD", "PASSWD", "SECRET", "TOKEN", "CREDENTIAL");
    private static final List<Pattern> SECRET_SHAPES = List.of(
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
    /** An absolute path and nothing else, quoted or not. */
    private static final Pattern ONLY_A_PATH = Pattern.compile("([\"']?)/\\S*\\1");

    private Secrets() {
    }

    /**
     * A value that is a secret: one shaped like a secret under any name, or any value under a secret's name, except
     * one that is only an absolute path, which says where a secret lives, not what it is (the owner's choice, 7 Nov).
     */
    public static boolean secret(String name, String value) {
        return secretShape(value) || secretName(name) && !ONLY_A_PATH.matcher(value.strip()).matches();
    }

    /**
     * Names are matched by their parts (split on {@code _}, {@code -} and {@code .}): {@code KEY} only as a whole part,
     * so {@code VALKEY_PREFIX} and {@code KEYCLOAK_URL} do not count; the longer words anywhere in a part, so
     * {@code DBPASSWORD} does.
     */
    public static boolean secretName(String name) {
        List<String> parts = List.of(name.toUpperCase(Locale.ROOT).split("[_.\\-]+"));
        return parts.contains("KEY")
                || parts.stream().anyMatch(part -> SECRET_WORDS.stream().anyMatch(part::contains));
    }

    /** Values that are secrets whatever they are called: a private key, an AWS access key ID, a JWT. */
    public static boolean secretShape(String value) {
        return SECRET_SHAPES.stream().anyMatch(shape -> shape.matcher(value).find());
    }

    /** The text with every secret-shaped value replaced by {@code (hidden)}. */
    public static String hideShapes(String text) {
        String hidden = text;
        for (Pattern shape : SECRET_SHAPES) {
            hidden = shape.matcher(hidden).replaceAll("(hidden)");
        }
        return hidden;
    }
}
