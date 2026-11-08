package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

final class ArbitraryUid implements EffectiveRule {

    private static final Pattern GROUP_ZERO = Pattern.compile(
            "\\bchgrp\\s+(-\\S+\\s+)*0\\b"
                    + "|\\bchmod\\s+(-\\S+\\s+)*\\S*g(=u|\\+\\w*w)"
                    + "|--chown=\\S*:0\\b"
                    + "|\\bchown\\s+(-\\S+\\s+)*\\S*:0\\b");

    @Override
    public String id() {
        return "S4";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public List<Finding> check(ImageView image, Function<String, String> blobText) {
        String kind = kind(image.user());
        if (!kind.equals("numeric")) {
            return List.of(finding(image, "image %s runs as %s user %s".formatted(image.configDigest(), kind,
                    Objects.requireNonNullElse(image.user(), "root (none set)")), image.userSetByBlobs()));
        }
        return groupZeroWritable(image, blobText) ? List.of()
                : List.of(finding(image, "image %s runs as %s, but no Dockerfile in its chain makes paths writable "
                        .formatted(image.configDigest(), image.user()) + "by group 0", image.ownBlobs()));
    }

    private Finding finding(ImageView image, String message, List<String> contributing) {
        return new Finding(id(), severity(), image.ownBlob(), null, message, null, contributing);
    }

    static boolean groupZeroWritable(ImageView image, Function<String, String> blobText) {
        return image.chain().stream()
                .flatMap(group -> group.blobShas().stream())
                .map(blobText)
                .filter(Objects::nonNull)
                .map(Continuations::removed)
                .anyMatch(text -> GROUP_ZERO.matcher(text).find());
    }

    static String kind(String user) {
        String name = user == null ? "" : user.split(":", 2)[0].strip();
        if (name.isEmpty() || name.equals("root") || name.equals("0")) {
            return "root";
        }
        if (name.contains("$")) {
            return "unresolved";
        }
        return name.chars().allMatch(Character::isDigit) ? "numeric" : "named";
    }
}
