package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The two normal forms Phase 2 clusters on, each hashed. The text form finds cosmetic duplicates: comments
 * dropped, continuations joined, whitespace collapsed, the first word of each line (the keyword) upper-cased.
 * Lines inside heredoc bodies are treated alike, which can only merge files that differ in a body's comments.
 * The signature finds functional duplicates: base family and major version, package managers and the sorted
 * package set of the final stage, user kind, exposed ports, entrypoint form and stage count.
 */
public final class NormalForms {

    private NormalForms() {
    }

    public static String textHash(String dockerfile) {
        return sha256(textNormal(dockerfile));
    }

    public static String signatureHash(ImageFacts facts) {
        return sha256(signature(facts));
    }

    /** Readable, so a surprising cluster can be explained; hashed for grouping. */
    public static String signature(ImageFacts facts) {
        int last = facts.stageCount() - 1;
        List<PackageCall> installs = facts.packageCalls().stream()
                .filter(c -> c.stage() == last && !c.call().packages().isEmpty())
                .map(StagedCall::call)
                .toList();
        return String.join("|",
                family(facts.base()) + ":" + major(facts.base()),
                installs.stream().map(PackageCall::manager).distinct().sorted().collect(Collectors.joining(",")),
                installs.stream().flatMap(c -> c.packages().stream()).distinct().sorted().collect(Collectors.joining(",")),
                facts.userKind().name(),
                String.join(",", facts.exposedPorts()),
                facts.entrypointForm().name(),
                "stages=" + facts.stageCount());
    }

    /**
     * The base's image path, without registry host, tag or digest, so it compares with what a build records and with
     * Artifactory's image names. As in Docker, a first segment is a host only if it holds a dot or a colon, or is
     * {@code localhost}. Null when the base is unresolved.
     */
    public static String fromPath(BaseImage base) {
        if (!base.resolved()) {
            return null;
        }
        String name = base.name();
        int slash = name.indexOf('/');
        String first = slash < 0 ? "" : name.substring(0, slash);
        boolean host = first.contains(".") || first.contains(":") || first.equals("localhost");
        return host ? name.substring(slash + 1) : name;
    }

    /** D4: the last path segment of the image name, so registries and mirrors of one image group together. */
    static String family(BaseImage base) {
        if (!base.resolved()) {
            return "unresolved";
        }
        return base.name().substring(base.name().lastIndexOf('/') + 1);
    }

    /** D4: the tag's leading digits ({@code 9.4-1227} gives {@code 9}); empty when there are none. */
    static String major(BaseImage base) {
        return base.tag() == null ? "" : base.tag().replaceFirst("^(\\d*).*", "$1");
    }

    static String textNormal(String dockerfile) {
        return dockerfile.replace("\r\n", "\n").replaceAll("\\\\\n", " ").lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> line.replaceAll("\\s+", " "))
                .map(NormalForms::upperKeyword)
                .collect(Collectors.joining("\n"));
    }

    private static String upperKeyword(String line) {
        int space = line.indexOf(' ');
        return space < 0 ? line.toUpperCase(Locale.ROOT)
                : line.substring(0, space).toUpperCase(Locale.ROOT) + line.substring(space);
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JVM provides SHA-256", e);
        }
    }
}
