package io.github.fiftieshousewife.dockerfileinventory.secrets;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What counts as a secret, shared by facts, rule S2 and the report, none of which may ever write one out.
 */
public final class Secrets {

    private static final List<String> SECRET_WORDS = List.of("PASSWORD", "PASSWD", "SECRET", "TOKEN", "CREDENTIAL");
    private static final List<Pattern> SECRET_SHAPES = List.of(
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
    private static final Pattern ONLY_A_PATH = Pattern.compile("([\"']?)/\\S*\\1");

    private Secrets() {
    }

    /**
     * A value shaped like a secret under any name, or any value under a secret's name, except one that is only an
     * absolute path: that says where a secret lives, not what it is.
     */
    public static boolean secret(String name, String value) {
        return secretShape(value) || secretName(name) && !ONLY_A_PATH.matcher(value.strip()).matches();
    }

    /**
     * Matched by the name's parts (split on {@code _}, {@code -} and {@code .}): {@code KEY} only as a whole part, so
     * {@code VALKEY_PREFIX} and {@code KEYCLOAK_URL} do not count; the longer words anywhere, so {@code DBPASSWORD}
     * does.
     */
    public static boolean secretName(String name) {
        List<String> parts = List.of(name.toUpperCase(Locale.ROOT).split("[_.\\-]+"));
        return parts.contains("KEY")
                || parts.stream().anyMatch(part -> SECRET_WORDS.stream().anyMatch(part::contains));
    }

    /** A private key, an AWS access key ID or a JWT: secrets whatever they are called. */
    public static boolean secretShape(String value) {
        return SECRET_SHAPES.stream().anyMatch(shape -> shape.matcher(value).find());
    }

    /** The text with every secret-shaped value replaced by {@code (hidden)}, so it can be shown. */
    public static String hideShapes(String text) {
        String hidden = text;
        for (Pattern shape : SECRET_SHAPES) {
            hidden = shape.matcher(hidden).replaceAll("(hidden)");
        }
        return hidden;
    }

    /** The value, or {@code (hidden sha256:...)} in its place when it is a secret, so a change still shows. */
    public static String shown(String name, String value) {
        return secret(name, value) ? "(hidden " + hash(value) + ")" : value;
    }

    static String hash(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
